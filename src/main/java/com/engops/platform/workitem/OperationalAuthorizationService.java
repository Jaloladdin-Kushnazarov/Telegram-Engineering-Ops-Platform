package com.engops.platform.workitem;

import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.identity.model.AppUserRoleBinding;
import com.engops.platform.identity.model.RolePermission;
import com.engops.platform.identity.repository.AppUserRoleBindingRepository;
import com.engops.platform.identity.repository.RolePermissionRepository;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Operational work item API uchun authorization collaborator (Phase 139).
 *
 * Application service boundary'da chaqirilib, joriy actor'ning tenant-scoped
 * ruxsatlarini tekshiradi. Ruxsat topilmasa AccessDeniedException (403) otadi.
 *
 * Permission modeli:
 * - WORK_ITEM_CREATE — intake (yangi work item yaratish) operatsiyasi uchun
 * - WORK_ITEM_TRANSITION — workflow status o'tkazish operatsiyasi uchun
 * - WORK_ITEM_UPDATE — work item field'larini yangilash (priority/severity) uchun (Phase 190)
 * - WORK_ITEM_ASSIGN — work item owner'ini tayinlash uchun (Phase 190)
 *
 * Permission kodlari V2 Flyway seed'da allaqachon mavjud — yangi migration
 * talab qilinmaydi. WORK_ITEM_UPDATE / WORK_ITEM_ASSIGN seed bog'lanishlari
 * V6'da ADMIN/ENGINEER/TESTER role'larida ham mavjud.
 *
 * Delegation: IdentityQueryService.resolvePermissionCodes() orqali
 * permission zanjirini hal qiladi:
 *   Actor userId -> Membership (tenantId, ACTIVE) -> Roles -> Permissions -> codes
 *
 * Fail-closed: actorUserId null bo'lsa yoki ruxsat topilmasa — rad etiladi.
 *
 * Bu service AdminAuthorizationService'dan ataylab alohida saqlanadi —
 * admin (tenant-config) va operational (work item) authorization sirtlari
 * mustaqil evolutsiya qila olishi uchun. Ikkala servis ham bir xil
 * IdentityQueryService primitive'idan foydalanadi.
 */
@Service
public class OperationalAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(OperationalAuthorizationService.class);

    public static final String WORK_ITEM_CREATE = "WORK_ITEM_CREATE";
    public static final String WORK_ITEM_TRANSITION = "WORK_ITEM_TRANSITION";
    /** Phase 190 — work item field'larini yangilash (priority/severity) uchun. */
    public static final String WORK_ITEM_UPDATE = "WORK_ITEM_UPDATE";
    /** Phase 190 — work item owner'ini tayinlash uchun. */
    public static final String WORK_ITEM_ASSIGN = "WORK_ITEM_ASSIGN";

    private final IdentityQueryService identityQueryService;
    private final AppUserRoleBindingRepository appUserRoleBindingRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public OperationalAuthorizationService(IdentityQueryService identityQueryService,
                                            AppUserRoleBindingRepository appUserRoleBindingRepository,
                                            RolePermissionRepository rolePermissionRepository) {
        this.identityQueryService = identityQueryService;
        this.appUserRoleBindingRepository = appUserRoleBindingRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    /**
     * Intake operatsiyasi (yangi work item yaratish) uchun ruxsatni tekshiradi.
     *
     * @param tenantId tenant identifikatori
     * @param actorUserId joriy actor identifikatori
     * @throws AccessDeniedException ruxsat bo'lmasa
     */
    public void authorizeIntake(UUID tenantId, UUID actorUserId) {
        requirePermission(tenantId, actorUserId, WORK_ITEM_CREATE);
    }

    /**
     * Workflow transition operatsiyasi uchun ruxsatni tekshiradi.
     *
     * @param tenantId tenant identifikatori
     * @param actorUserId joriy actor identifikatori
     * @throws AccessDeniedException ruxsat bo'lmasa
     */
    public void authorizeTransition(UUID tenantId, UUID actorUserId) {
        requirePermission(tenantId, actorUserId, WORK_ITEM_TRANSITION);
    }

    /**
     * Work item field'larini yangilash (priority/severity) uchun ruxsatni tekshiradi
     * (Phase 190).
     *
     * @param tenantId tenant identifikatori
     * @param actorUserId joriy actor identifikatori
     * @throws AccessDeniedException ruxsat bo'lmasa
     */
    public void authorizeUpdate(UUID tenantId, UUID actorUserId) {
        requirePermission(tenantId, actorUserId, WORK_ITEM_UPDATE);
    }

    /**
     * Work item owner'ini tayinlash uchun ruxsatni tekshiradi (Phase 190).
     *
     * @param tenantId tenant identifikatori
     * @param actorUserId joriy actor identifikatori
     * @throws AccessDeniedException ruxsat bo'lmasa
     */
    public void authorizeAssignOwner(UUID tenantId, UUID actorUserId) {
        requirePermission(tenantId, actorUserId, WORK_ITEM_ASSIGN);
    }

    /**
     * Phase 216 — "global" authorization tekshiruvi: platform-level
     * {@link AppUserRoleBinding} orqali biriktirilgan role'lardan kerakli
     * ruxsat kelib chiqadimi tekshiradi.
     *
     * <p><strong>Old behavior (Phase 199, REMOVED):</strong> actor'ning har
     * qanday aktiv membership'idagi role permissions'larni cascade qilib
     * tekshirgan edi. Bu har bir tenant admin'iga {@code TENANT_ONBOARD}'ni
     * cascade qilib bergan (tenant izolyatsiyasi sindiq edi).</p>
     *
     * <p><strong>New behavior (Phase 216):</strong> faqat
     * {@code app_user_role_binding} (V9 jadval) — platform-level rollar.
     * Bootstrap admin'ga {@code PLATFORM_OWNER} role
     * {@code DevBootstrapInitializer.seedPlatformOwners()} orqali
     * biriktiriladi. Per-tenant {@code ADMIN}/{@code TENANT_OWNER} role
     * binding'lari endi {@code TENANT_ONBOARD}'ni cascade qilmaydi.</p>
     *
     * <p><strong>Fail-closed:</strong></p>
     * <ul>
     *   <li>actorUserId null → 403</li>
     *   <li>actor'da platform-level binding yo'q → 403</li>
     *   <li>platform-level binding'lar permissionga ega emas → 403</li>
     * </ul>
     *
     * <p><strong>Lazy navigation:</strong> {@code @Transactional(readOnly=true)}
     * — {@code spring.jpa.open-in-view=false} sharoitida {@code RolePermission}
     * va {@code Permission} lazy proxy'larini xavfsiz traversal qilish uchun.</p>
     *
     * @param actorUserId joriy actor identifikatori
     * @param permissionCode kerakli ruxsat kodi (masalan "TENANT_ONBOARD")
     * @throws AccessDeniedException ruxsat bo'lmasa
     */
    @Transactional(readOnly = true)
    public void authorizeGlobal(UUID actorUserId, String permissionCode) {
        if (actorUserId == null) {
            log.warn("Global authorization rad etildi: actorUserId taqdim etilmadi, kerakli ruxsat={}",
                    permissionCode);
            throw new AccessDeniedException("Actor identifikatsiyasi talab qilinadi");
        }

        List<AppUserRoleBinding> bindings =
                appUserRoleBindingRepository.findByUserId(actorUserId);
        if (bindings.isEmpty()) {
            log.warn("Global authorization rad etildi: actor={}, platform-level role yo'q, "
                    + "kerakli ruxsat={}", actorUserId, permissionCode);
            throw new AccessDeniedException(
                    "Bu operatsiya uchun " + permissionCode + " ruxsati talab qilinadi");
        }

        for (AppUserRoleBinding binding : bindings) {
            if (rolePermitsCode(binding.getRoleId(), permissionCode)) {
                return;
            }
        }

        log.warn("Global authorization rad etildi: actor={}, kerakli ruxsat={} "
                + "(platform-level role'lar ichida topilmadi)",
                actorUserId, permissionCode);
        throw new AccessDeniedException(
                "Bu operatsiya uchun " + permissionCode + " ruxsati talab qilinadi");
    }

    /**
     * Phase 216 — yordamchi: berilgan role'ning aktiv ekanini va so'ralgan
     * permission kodiga ega ekanligini tekshiradi. Role inactive bo'lsa
     * (Phase 77 {@code active=FALSE}), permission'lar e'tibordan chetda
     * qoldiriladi.
     */
    private boolean rolePermitsCode(UUID roleId, String permissionCode) {
        List<RolePermission> bindings = rolePermissionRepository.findByRoleId(roleId);
        for (RolePermission rp : bindings) {
            if (!rp.getRole().isActive()) {
                return false;
            }
            if (permissionCode.equals(rp.getPermission().getCode())) {
                return true;
            }
        }
        return false;
    }

    private void requirePermission(UUID tenantId, UUID actorUserId, String permissionCode) {
        if (actorUserId == null) {
            log.warn("Operational authorization rad etildi: actorUserId taqdim etilmadi, tenant={}",
                    tenantId);
            throw new AccessDeniedException("Actor identifikatsiyasi talab qilinadi");
        }

        Set<String> permissions = identityQueryService.resolvePermissionCodes(tenantId, actorUserId);

        if (!permissions.contains(permissionCode)) {
            log.warn("Operational authorization rad etildi: actor={}, tenant={}, kerakli ruxsat={}",
                    actorUserId, tenantId, permissionCode);
            throw new AccessDeniedException(
                    "Bu operatsiya uchun " + permissionCode + " ruxsati talab qilinadi");
        }
    }
}
