package com.engops.platform.workitem;

import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipStatus;
import com.engops.platform.identity.repository.MembershipRepository;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
    private final MembershipRepository membershipRepository;

    public OperationalAuthorizationService(IdentityQueryService identityQueryService,
                                            MembershipRepository membershipRepository) {
        this.identityQueryService = identityQueryService;
        this.membershipRepository = membershipRepository;
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
     * Phase 199 — "global" authorization tekshiruvi: actor'ning HER QANDAY
     * aktiv membership'ida ko'rsatilgan permission kodi bo'lsa, ruxsat
     * beriladi. Tenant onboarding (POST /api/admin/tenants) uchun ishlatiladi —
     * yangi tenant yaratayotgan actor hali shu tenantning a'zosi emas, lekin
     * boshqa biror tenantida {@code TENANT_ONBOARD} ruxsatiga ega bo'lishi
     * shart.
     *
     * Fail-closed:
     * - actorUserId null → 403
     * - actor'da bironta aktiv membership yo'q → 403
     * - bironta aktiv membership permissionga ega emas → 403
     *
     * @param actorUserId joriy actor identifikatori
     * @param permissionCode kerakli ruxsat kodi (masalan "TENANT_ONBOARD")
     * @throws AccessDeniedException ruxsat bo'lmasa
     */
    public void authorizeGlobal(UUID actorUserId, String permissionCode) {
        if (actorUserId == null) {
            log.warn("Global authorization rad etildi: actorUserId taqdim etilmadi, kerakli ruxsat={}",
                    permissionCode);
            throw new AccessDeniedException("Actor identifikatsiyasi talab qilinadi");
        }

        List<Membership> memberships = membershipRepository.findByUserId(actorUserId);
        for (Membership membership : memberships) {
            if (membership.getStatus() != MembershipStatus.ACTIVE) {
                continue;
            }
            Set<String> permissions = identityQueryService.resolvePermissionCodes(
                    membership.getTenantId(), actorUserId);
            if (permissions.contains(permissionCode)) {
                return;
            }
        }

        log.warn("Global authorization rad etildi: actor={}, kerakli ruxsat={} (aktiv membership'larda topilmadi)",
                actorUserId, permissionCode);
        throw new AccessDeniedException(
                "Bu operatsiya uchun " + permissionCode + " ruxsati talab qilinadi");
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
