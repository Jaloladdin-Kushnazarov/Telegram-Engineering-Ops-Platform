package com.engops.platform.admin;

import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * Admin tenant-config API uchun authorization collaborator.
 *
 * Facade boundary'da chaqirilib, joriy actor'ning tenant-scoped
 * ruxsatlarini tekshiradi. Ruxsat topilmasa AccessDeniedException (403) otadi.
 *
 * Permission modeli:
 * - TENANT_CONFIG_READ — read operatsiyalar uchun
 * - TENANT_CONFIG_WRITE — write operatsiyalar uchun (read'ni o'z ichiga olmaydi)
 *
 * Delegation: IdentityQueryService.resolvePermissionCodes() orqali
 * permission zanjirini hal qiladi:
 *   Actor userId -> Membership (tenantId) -> Roles -> Permissions -> codes
 *
 * Fail-closed: actorUserId null bo'lsa yoki ruxsat topilmasa — rad etiladi.
 */
@Service
public class AdminAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthorizationService.class);

    public static final String TENANT_CONFIG_READ = "TENANT_CONFIG_READ";
    public static final String TENANT_CONFIG_WRITE = "TENANT_CONFIG_WRITE";

    private final IdentityQueryService identityQueryService;

    public AdminAuthorizationService(IdentityQueryService identityQueryService) {
        this.identityQueryService = identityQueryService;
    }

    /**
     * Read operatsiyasi uchun ruxsatni tekshiradi.
     *
     * @param tenantId tenant identifikatori
     * @param actorUserId joriy actor identifikatori
     * @throws AccessDeniedException ruxsat bo'lmasa
     */
    public void authorizeRead(UUID tenantId, UUID actorUserId) {
        requirePermission(tenantId, actorUserId, TENANT_CONFIG_READ);
    }

    /**
     * Write operatsiyasi uchun ruxsatni tekshiradi.
     *
     * @param tenantId tenant identifikatori
     * @param actorUserId joriy actor identifikatori
     * @throws AccessDeniedException ruxsat bo'lmasa
     */
    public void authorizeWrite(UUID tenantId, UUID actorUserId) {
        requirePermission(tenantId, actorUserId, TENANT_CONFIG_WRITE);
    }

    private void requirePermission(UUID tenantId, UUID actorUserId, String permissionCode) {
        if (actorUserId == null) {
            log.warn("Admin authorization rad etildi: actorUserId taqdim etilmadi, tenant={}", tenantId);
            throw new AccessDeniedException("Actor identifikatsiyasi talab qilinadi");
        }

        Set<String> permissions = identityQueryService.resolvePermissionCodes(tenantId, actorUserId);

        if (!permissions.contains(permissionCode)) {
            log.warn("Admin authorization rad etildi: actor={}, tenant={}, kerakli ruxsat={}",
                    actorUserId, tenantId, permissionCode);
            throw new AccessDeniedException(
                    "Bu operatsiya uchun " + permissionCode + " ruxsati talab qilinadi");
        }
    }
}
