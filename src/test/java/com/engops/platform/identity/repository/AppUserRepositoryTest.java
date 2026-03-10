package com.engops.platform.identity.repository;

import com.engops.platform.identity.model.AppUser;
import com.engops.platform.infrastructure.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AppUser repository testlari.
 * telegram_user_id bo'yicha qidiruv va unikal cheklovni tekshiradi.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class AppUserRepositoryTest {

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void telegramUserIdBoYichaTopish() {
        AppUser user = new AppUser(12345L, "Test User");
        appUserRepository.save(user);

        Optional<AppUser> found = appUserRepository.findByTelegramUserId(12345L);

        assertThat(found).isPresent();
        assertThat(found.get().getTelegramUserId()).isEqualTo(12345L);
        assertThat(found.get().getDisplayName()).isEqualTo("Test User");
    }

    @Test
    void mavjudEmasTelegramIdBilanTopilmasligi() {
        Optional<AppUser> found = appUserRepository.findByTelegramUserId(99999L);

        assertThat(found).isEmpty();
    }

    @Test
    void telegramUserIdMavjudliginiTekshirish() {
        AppUser user = new AppUser(11111L, "User One");
        appUserRepository.save(user);

        assertThat(appUserRepository.existsByTelegramUserId(11111L)).isTrue();
        assertThat(appUserRepository.existsByTelegramUserId(22222L)).isFalse();
    }
}
