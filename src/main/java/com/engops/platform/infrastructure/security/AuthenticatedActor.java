package com.engops.platform.infrastructure.security;

import java.util.Objects;
import java.util.UUID;

/**
 * Phase 125 — autentifikatsiyalangan actor identifikatsiyasi.
 *
 * <p>JWT (yoki kelajakdagi boshqa autentifikatsiya manbai) bitta so'rov uchun
 * shu modelga aylantiriladi. JWT identity-only — application permission'lari
 * (TENANT_CONFIG_WRITE, WORK_ITEM_CREATE va h.k.) bu yerda tashilmaydi va
 * Membership → Role → RolePermission zanjiri orqali har so'rovda DB'dan
 * resolve qilinadi (`AdminAuthorizationService` mavjud nafaqasidan davom etadi).</p>
 *
 * @param appUserId platform AppUser identifikatori (JWT {@code sub} claim'idan)
 *                   — never null
 * @param telegramUserId Telegram identifikatori (JWT {@code telegram_user_id}
 *                        claim'idan) — nullable, chunki Telegram-aware login
 *                        flow Phase 125'da implement qilinmagan; future-aware
 *                        token issuer'lari uni qoldirishi mumkin
 */
public record AuthenticatedActor(UUID appUserId, Long telegramUserId) {

    public AuthenticatedActor {
        Objects.requireNonNull(appUserId, "appUserId null bo'lishi mumkin emas");
    }
}
