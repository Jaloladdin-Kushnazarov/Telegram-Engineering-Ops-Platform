package com.engops.platform.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedActorTest {

    @Test
    void storesAppUserIdAndTelegramUserId() {
        UUID userId = UUID.randomUUID();
        AuthenticatedActor actor = new AuthenticatedActor(userId, 123456789L);

        assertThat(actor.appUserId()).isEqualTo(userId);
        assertThat(actor.telegramUserId()).isEqualTo(123456789L);
    }

    @Test
    void allowsNullTelegramUserId() {
        UUID userId = UUID.randomUUID();
        AuthenticatedActor actor = new AuthenticatedActor(userId, null);

        assertThat(actor.appUserId()).isEqualTo(userId);
        assertThat(actor.telegramUserId()).isNull();
    }

    @Test
    void rejectsNullAppUserId() {
        assertThatThrownBy(() -> new AuthenticatedActor(null, 123L))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("appUserId");
    }
}
