package com.engops.platform.admin;

import com.engops.platform.audit.AuditService;
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
 *
 * <p><strong>Phase 189 — denial audit.</strong> Har bir rad etish holati
 * uchun {@code ADMIN_AUTH_DENIED} audit qatori
 * {@link AuditService#recordEventInNewTransaction(UUID, String, UUID, String, UUID, String, String, String)
 * REQUIRES_NEW} orqali yoziladi. Audit yozish fail-soft:
 * persistence xatosi 403 javobiga ta'sir qilmaydi va exception type ham
 * o'zgartirilmaydi. Payload tarkibi bounded — faqat {@code permission} va
 * {@code reason}; request body, header, JWT token, exception message
 * hech qachon kirmaydi.</p>
 */
@Service
public class AdminAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthorizationService.class);

    public static final String TENANT_CONFIG_READ = "TENANT_CONFIG_READ";
    public static final String TENANT_CONFIG_WRITE = "TENANT_CONFIG_WRITE";

    /** Phase 189 — denial audit eventType. */
    static final String DENIED_EVENT_TYPE = "ADMIN_AUTH_DENIED";

    /** Phase 189 — denial audit entityType. */
    static final String DENIED_ENTITY_TYPE = "ADMIN_API";

    /** Phase 189 — denial audit actionSource. */
    static final String DENIED_ACTION_SOURCE = "ADMIN_API";

    /**
     * Phase 189 — denial reason enumeration (bounded, low-cardinality, audit
     * payload qiymati sifatida ishlatiladi).
     */
    enum DenialReason {
        /** actorUserId null — JWT yo'q yoki noto'g'ri parse qilingan. */
        MISSING_ACTOR,
        /**
         * Kerakli permission set'da yo'q. Bu sabab membership umuman yo'q
         * (resolved set bo'sh) va membership bor lekin permission yo'q
         * holatlarini birlashtiradi — service joriy holatda ularni
         * cheap tarzda ajrata olmaydi.
         */
        PERMISSION_DENIED
    }

    private final IdentityQueryService identityQueryService;
    private final AuditService auditService;

    public AdminAuthorizationService(IdentityQueryService identityQueryService,
                                      AuditService auditService) {
        this.identityQueryService = identityQueryService;
        this.auditService = auditService;
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
            auditDenialSafely(tenantId, null, permissionCode, DenialReason.MISSING_ACTOR);
            throw new AccessDeniedException("Actor identifikatsiyasi talab qilinadi");
        }

        Set<String> permissions = identityQueryService.resolvePermissionCodes(tenantId, actorUserId);

        if (!permissions.contains(permissionCode)) {
            log.warn("Admin authorization rad etildi: actor={}, tenant={}, kerakli ruxsat={}",
                    actorUserId, tenantId, permissionCode);
            auditDenialSafely(tenantId, actorUserId, permissionCode, DenialReason.PERMISSION_DENIED);
            throw new AccessDeniedException(
                    "Bu operatsiya uchun " + permissionCode + " ruxsati talab qilinadi");
        }
    }

    /**
     * Phase 189 — admin authorization denial uchun audit qatori yozadi
     * (fail-soft). Audit yozish xatosi 403 javobiga ta'sir qilmaydi va
     * AccessDeniedException tashlash davom etadi.
     *
     * <p>Payload tarkibi bounded: faqat {@code permission} va
     * {@code reason}. Request body, header, JWT, endpoint URL,
     * exception message — hech qachon kirmaydi.</p>
     *
     * <p>{@code entityId} sifatida {@code tenantId} ishlatiladi —
     * {@code audit_event.entity_id} NOT NULL schema constraint'ini
     * qondiradi va denial tenant darajasida tushuriladi.
     * actorUserId null bo'lsa (MISSING_ACTOR yo'li), audit qatorida
     * {@code actor_user_id} ham null saqlanadi (column nullable).</p>
     */
    private void auditDenialSafely(UUID tenantId,
                                     UUID actorUserId,
                                     String permissionCode,
                                     DenialReason reason) {
        if (tenantId == null) {
            // Defense-in-depth: tenantId yo'q bo'lsa entity_id NOT NULL
            // constraint'i buziladi. Bu yo'l hozirgi caller'larda bo'lmaydi
            // (controller'lar har doim tenantId taqdim etadi) — silent skip.
            log.warn("Admin authorization denial audit skip reason=missing-tenant-id permission={}",
                    permissionCode);
            return;
        }
        String payload = buildDenialPayload(permissionCode, reason);
        try {
            auditService.recordEventInNewTransaction(tenantId,
                    DENIED_ENTITY_TYPE,
                    tenantId,
                    DENIED_EVENT_TYPE,
                    actorUserId,
                    DENIED_ACTION_SOURCE,
                    null,
                    payload);
        } catch (RuntimeException ex) {
            // Fail-soft: audit persistence xatosi authorization xulq-atvoriga
            // ta'sir qilmaydi. exception message ataylab log'ga
            // chiqarilmaydi (token-leak guard pattern).
            log.warn("Admin authorization denial audit swallowed reason={} exceptionType={}",
                    reason, ex.getClass().getSimpleName());
        }
    }

    /**
     * Denial audit qatorining {@code newValueJson} maydoni uchun bounded
     * JSON-like payload quradi. Faqat permission va reason kiritiladi.
     */
    static String buildDenialPayload(String permissionCode, DenialReason reason) {
        return "{"
                + "\"permission\":" + jsonStringOrNull(permissionCode)
                + ",\"reason\":" + jsonStringOrNull(reason == null ? null : reason.name())
                + "}";
    }

    private static String jsonStringOrNull(String value) {
        if (value == null) {
            return "null";
        }
        // Defense-in-depth escape — permission code va reason bounded
        // internal identifier'lar bo'lib, kelajakda kengayish bo'lsa ham
        // JSON struktura saqlanishi uchun.
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }
}
