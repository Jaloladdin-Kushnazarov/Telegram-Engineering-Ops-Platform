package com.engops.platform.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtActorConverterTest {

    private final JwtActorConverter converter = new JwtActorConverter();

    @Test
    void mapsValidJwtToAuthenticatedActor() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = jwt(userId.toString(), Map.of("telegram_user_id", 123456789L));

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.isAuthenticated()).isTrue();
        assertThat(token.getPrincipal()).isInstanceOf(AuthenticatedActor.class);
        AuthenticatedActor actor = (AuthenticatedActor) token.getPrincipal();
        assertThat(actor.appUserId()).isEqualTo(userId);
        assertThat(actor.telegramUserId()).isEqualTo(123456789L);
        assertThat(token.getAuthorities()).isEmpty(); // identity-only — no permissions
    }

    @Test
    void acceptsJwtWithoutTelegramUserIdClaim() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = jwt(userId.toString(), Map.of());

        AbstractAuthenticationToken token = converter.convert(jwt);

        AuthenticatedActor actor = (AuthenticatedActor) token.getPrincipal();
        assertThat(actor.appUserId()).isEqualTo(userId);
        assertThat(actor.telegramUserId()).isNull();
    }

    @Test
    void acceptsTelegramUserIdAsInteger() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = jwt(userId.toString(), Map.of("telegram_user_id", 12345));

        AuthenticatedActor actor = (AuthenticatedActor) converter.convert(jwt).getPrincipal();
        assertThat(actor.telegramUserId()).isEqualTo(12345L);
    }

    @Test
    void acceptsTelegramUserIdAsNumericString() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = jwt(userId.toString(), Map.of("telegram_user_id", "  9876  "));

        AuthenticatedActor actor = (AuthenticatedActor) converter.convert(jwt).getPrincipal();
        assertThat(actor.telegramUserId()).isEqualTo(9876L);
    }

    @Test
    void ignoresTelegramUserIdWhenNonNumericString() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = jwt(userId.toString(), Map.of("telegram_user_id", "not-a-number"));

        AuthenticatedActor actor = (AuthenticatedActor) converter.convert(jwt).getPrincipal();
        assertThat(actor.telegramUserId()).isNull();
    }

    @Test
    void rejectsJwtWithInvalidUuidSub() {
        Jwt jwt = jwt("not-a-uuid", Map.of());

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UUID");
    }

    private static Jwt jwt(String sub, Map<String, Object> additionalClaims) {
        Map<String, Object> claims = new HashMap<>(additionalClaims);
        claims.put("sub", sub);
        return new Jwt(
                "test-token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("alg", "HS256"),
                claims);
    }
}
