package com.engops.platform.workitem;

import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
 *
 * Permission kodlari V2 Flyway seed'da allaqachon mavjud — yangi migration
 * talab qilinmaydi. Role-permission seed bog'lanishlari operator tomonidan
 * TenantConfig API orqali yaratiladi (alohida masala, bu phase'da hal
 * qilinmaydi).
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

    private final IdentityQueryService identityQueryService;

    public OperationalAuthorizationService(IdentityQueryService identityQueryService) {
        this.identityQueryService = identityQueryService;
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
