package com.engops.platform.identity.auth;

import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 218a — TelegramLoginService unit testlari.
 *
 * <p>Verifier, AppUserRepository, TokenIssuer mocklanadi.
 * Service'ning oqim mantig'i tekshiriladi.</p>
 */
@ExtendWith(MockitoExtension.class)
class TelegramLoginServiceTest {

    @Mock
    private TelegramLoginVerifier verifier;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private TelegramLoginTokenIssuer tokenIssuer;

    @Mock
    private ObjectProvider<TelegramLoginTokenIssuer> tokenIssuerProvider;

    private TelegramLoginService service;

    @BeforeEach
    void setUp() {
        service = new TelegramLoginService(verifier, appUserRepository, tokenIssuerProvider);
    }

    private static TelegramLoginPayload payload(Long id, String firstName,
                                                 String lastName, String username) {
        return new TelegramLoginPayload(id, firstName, lastName, username, null,
                Instant.now().getEpochSecond(), "deadbeef");
    }

    @Test
    void authenticate_disabled_throwsException() {
        when(verifier.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.authenticate(payload(1L, "X", null, null)))
                .isInstanceOf(TelegramLoginException.class)
                .hasMessageContaining("sozlanmagan");

        verify(appUserRepository, never()).findByTelegramUserId(anyLong());
    }

    @Test
    void authenticate_nullPayload_throwsException() {
        when(verifier.isEnabled()).thenReturn(true);

        assertThatThrownBy(() -> service.authenticate(null))
                .isInstanceOf(TelegramLoginException.class)
                .hasMessageContaining("majburiy");
    }

    @Test
    void authenticate_nullId_throwsException() {
        when(verifier.isEnabled()).thenReturn(true);

        assertThatThrownBy(() ->
                service.authenticate(payload(null, "X", null, null)))
                .isInstanceOf(TelegramLoginException.class)
                .hasMessageContaining("majburiy");
    }

    @Test
    void authenticate_tokenIssuerMissing_throwsException() {
        when(verifier.isEnabled()).thenReturn(true);
        when(tokenIssuerProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.authenticate(payload(1L, "X", null, null)))
                .isInstanceOf(TelegramLoginException.class)
                .hasMessageContaining("sozlanmagan");
    }

    @Test
    void authenticate_invalidHash_throwsException() {
        when(verifier.isEnabled()).thenReturn(true);
        when(tokenIssuerProvider.getIfAvailable()).thenReturn(tokenIssuer);
        when(verifier.verify(any(TelegramLoginPayload.class))).thenReturn(false);

        assertThatThrownBy(() ->
                service.authenticate(payload(1L, "X", null, null)))
                .isInstanceOf(TelegramLoginException.class)
                .hasMessageContaining("noto'g'ri");

        verify(appUserRepository, never()).findByTelegramUserId(anyLong());
        verify(tokenIssuer, never()).issueToken(any(UUID.class), anyLong());
    }

    @Test
    void authenticate_validHash_existingUser_issuesToken() {
        UUID userId = UUID.randomUUID();
        AppUser existing = new AppUser(userId, 1L, "Existing");
        when(verifier.isEnabled()).thenReturn(true);
        when(tokenIssuerProvider.getIfAvailable()).thenReturn(tokenIssuer);
        when(verifier.verify(any(TelegramLoginPayload.class))).thenReturn(true);
        when(appUserRepository.findByTelegramUserId(1L)).thenReturn(Optional.of(existing));
        when(tokenIssuer.issueToken(userId, 1L)).thenReturn("jwt-token");

        String token = service.authenticate(payload(1L, "X", null, null));

        assertThat(token).isEqualTo("jwt-token");
        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    @Test
    void authenticate_validHash_newUser_createsAndIssues() {
        when(verifier.isEnabled()).thenReturn(true);
        when(tokenIssuerProvider.getIfAvailable()).thenReturn(tokenIssuer);
        when(verifier.verify(any(TelegramLoginPayload.class))).thenReturn(true);
        when(appUserRepository.findByTelegramUserId(2L)).thenReturn(Optional.empty());
        when(appUserRepository.save(any(AppUser.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(tokenIssuer.issueToken(any(UUID.class), anyLong())).thenReturn("jwt-new");

        String token = service.authenticate(payload(2L, "NewUser", null, null));

        assertThat(token).isEqualTo("jwt-new");
        verify(appUserRepository, times(1)).save(any(AppUser.class));
    }

    @Test
    void authenticate_newUser_persistsDisplayName_fromFirstName() {
        when(verifier.isEnabled()).thenReturn(true);
        when(tokenIssuerProvider.getIfAvailable()).thenReturn(tokenIssuer);
        when(verifier.verify(any(TelegramLoginPayload.class))).thenReturn(true);
        when(appUserRepository.findByTelegramUserId(3L)).thenReturn(Optional.empty());
        when(appUserRepository.save(any(AppUser.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(tokenIssuer.issueToken(any(UUID.class), anyLong())).thenReturn("t");

        service.authenticate(payload(3L, "Davron", null, null));

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(captor.capture());
        assertThat(captor.getValue().getDisplayName()).isEqualTo("Davron");
        assertThat(captor.getValue().getTelegramUserId()).isEqualTo(3L);
    }

    @Test
    void authenticate_newUser_concatenatesLastName() {
        when(verifier.isEnabled()).thenReturn(true);
        when(tokenIssuerProvider.getIfAvailable()).thenReturn(tokenIssuer);
        when(verifier.verify(any(TelegramLoginPayload.class))).thenReturn(true);
        when(appUserRepository.findByTelegramUserId(4L)).thenReturn(Optional.empty());
        when(appUserRepository.save(any(AppUser.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(tokenIssuer.issueToken(any(UUID.class), anyLong())).thenReturn("t");

        service.authenticate(payload(4L, "Davron", "Yusupov", null));

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(captor.capture());
        assertThat(captor.getValue().getDisplayName()).isEqualTo("Davron Yusupov");
    }

    @Test
    void authenticate_newUser_persistsUsername() {
        when(verifier.isEnabled()).thenReturn(true);
        when(tokenIssuerProvider.getIfAvailable()).thenReturn(tokenIssuer);
        when(verifier.verify(any(TelegramLoginPayload.class))).thenReturn(true);
        when(appUserRepository.findByTelegramUserId(5L)).thenReturn(Optional.empty());
        when(appUserRepository.save(any(AppUser.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(tokenIssuer.issueToken(any(UUID.class), anyLong())).thenReturn("t");

        service.authenticate(payload(5L, "X", null, "x_user"));

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("x_user");
    }

    @Test
    void authenticate_newUser_emptyFirstName_fallbackToUser() {
        when(verifier.isEnabled()).thenReturn(true);
        when(tokenIssuerProvider.getIfAvailable()).thenReturn(tokenIssuer);
        when(verifier.verify(any(TelegramLoginPayload.class))).thenReturn(true);
        when(appUserRepository.findByTelegramUserId(6L)).thenReturn(Optional.empty());
        when(appUserRepository.save(any(AppUser.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(tokenIssuer.issueToken(any(UUID.class), anyLong())).thenReturn("t");

        service.authenticate(payload(6L, null, null, null));

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(captor.capture());
        assertThat(captor.getValue().getDisplayName()).isEqualTo("User");
    }
}
