package com.engops.platform.infrastructure.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collections;
import java.util.UUID;

/**
 * Phase 125 — JWT'ni {@link AuthenticatedActor} ga aylantiruvchi converter.
 *
 * <p>Claim contract:</p>
 * <ul>
 *   <li>{@code sub} (String) — platform AppUser identifikatori UUID formatida.
 *       Talab qilingan. Noto'g'ri/yo'q bo'lsa {@link IllegalArgumentException}.</li>
 *   <li>{@code telegram_user_id} (Long/Integer/String) — Telegram identifikatori.
 *       Ixtiyoriy. Yo'q yoki konvertatsiya qilib bo'lmasa null sifatida saqlanadi.</li>
 * </ul>
 *
 * <p>Authority'lar atayin {@code emptyList()} sifatida belgilanadi: JWT identity-only.
 * Application permission'lari Membership → Role → RolePermission zanjiri orqali
 * DB'dan resolve qilinadi (mavjud {@code AdminAuthorizationService} +
 * {@code IdentityQueryService.resolvePermissionCodes} pattern).</p>
 *
 * <p>Phase 125'da bu converter SecurityConfig'ga ulanmaydi — foundation sifatida
 * tayyor. Phase 126+ da {@code oauth2ResourceServer(jwt -> jwt.jwtAuthenticationConverter(...))}
 * orqali wire qilinadi.</p>
 */
public class JwtActorConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    static final String TELEGRAM_USER_ID_CLAIM = "telegram_user_id";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID appUserId = parseAppUserId(jwt.getSubject());
        Long telegramUserId = parseTelegramUserId(jwt.getClaim(TELEGRAM_USER_ID_CLAIM));

        AuthenticatedActor actor = new AuthenticatedActor(appUserId, telegramUserId);

        // Identity-only token: authorities ataylab bo'sh — permissions DB-backed
        JwtAuthenticationToken token = new JwtAuthenticationToken(
                jwt, Collections.emptyList());
        token.setAuthenticated(true);
        token.setDetails(actor);

        // Spring Security principal'ni JwtAuthenticationToken o'zining sub'idan oladi.
        // Resolver bizning AuthenticatedActor'ga muhtoj — shuning uchun custom
        // Authentication wrapper ishlatamiz.
        return new ActorAuthenticationToken(jwt, actor);
    }

    private static UUID parseAppUserId(String sub) {
        if (sub == null || sub.isBlank()) {
            throw new IllegalArgumentException("JWT 'sub' claim'i talab qilinadi");
        }
        try {
            return UUID.fromString(sub);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "JWT 'sub' claim'i UUID formatida bo'lishi kerak: '" + sub + "'", ex);
        }
    }

    private static Long parseTelegramUserId(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Integer intValue) {
            return intValue.longValue();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str && !str.isBlank()) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    /**
     * Authentication wrapper: principal'ni {@link AuthenticatedActor} sifatida
     * ochiq qiladi (Spring Security'ning default {@code JwtAuthenticationToken}
     * principal sifatida {@code Jwt} ni qaytaradi — biz {@link AuthenticatedActor}
     * kutamiz).
     */
    public static class ActorAuthenticationToken extends AbstractAuthenticationToken {

        private final Jwt jwt;
        private final AuthenticatedActor actor;

        public ActorAuthenticationToken(Jwt jwt, AuthenticatedActor actor) {
            super(Collections.emptyList());
            this.jwt = jwt;
            this.actor = actor;
            super.setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return jwt;
        }

        @Override
        public Object getPrincipal() {
            return actor;
        }

        @Override
        public String getName() {
            return actor.appUserId().toString();
        }
    }
}
