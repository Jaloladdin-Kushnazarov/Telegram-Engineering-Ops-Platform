package com.engops.platform.intake;

import com.engops.platform.audit.AuditService;
import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipStatus;
import com.engops.platform.identity.repository.MembershipRepository;
import com.engops.platform.telegram.TelegramBotCommand;
import com.engops.platform.telegram.TelegramBotCommandContext;
import com.engops.platform.telegram.TelegramBotCommandRegistry;
import com.engops.platform.telegram.TelegramCallbackChatRequest;
import com.engops.platform.telegram.TelegramCallbackUserRequest;
import com.engops.platform.telegram.TelegramGatewayResult;
import com.engops.platform.telegram.TelegramMessageRequest;
import com.engops.platform.telegram.TelegramOutboundGateway;
import com.engops.platform.telegram.TelegramUpdateRequest;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 200 — {@link TelegramBotCommandService} unit testlari.
 *
 * Hamma collaborator'lar (registry, identity, repository, tenant, gateway,
 * audit) mock. Service'ning routing + audit invariantlarini tekshiradi.
 */
class TelegramBotCommandServiceTest {

    private static final long TELEGRAM_USER_ID = 123456789L;
    private static final long CHAT_ID = -1001234567890L;
    private static final UUID APP_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final TelegramBotCommandRegistry registry = mock(TelegramBotCommandRegistry.class);
    private final IdentityQueryService identityQueryService = mock(IdentityQueryService.class);
    private final MembershipRepository membershipRepository = mock(MembershipRepository.class);
    private final TenantConfigQueryService tenantConfigQueryService = mock(TenantConfigQueryService.class);
    private final TelegramOutboundGateway outboundGateway = mock(TelegramOutboundGateway.class);
    private final AuditService auditService = mock(AuditService.class);

    private final TelegramBotCommandService service = new TelegramBotCommandService(
            registry, identityQueryService, membershipRepository,
            tenantConfigQueryService, outboundGateway, auditService);

    private AppUser appUser;
    private Tenant tenant;
    private TelegramBotCommand helpCommand;

    @BeforeEach
    void setUp() {
        appUser = mock(AppUser.class);
        when(appUser.getId()).thenReturn(APP_USER_ID);
        when(appUser.getDisplayName()).thenReturn("Demo Admin");

        tenant = mock(Tenant.class);
        when(tenant.getId()).thenReturn(TENANT_ID);
        when(tenant.getSlug()).thenReturn("acme");

        helpCommand = mock(TelegramBotCommand.class);
        when(helpCommand.commandName()).thenReturn("/help");
        when(helpCommand.execute(any(TelegramBotCommandContext.class))).thenReturn("Mavjud buyruqlar: ...");

        when(outboundGateway.sendBotReply(anyLong(), anyString()))
                .thenReturn(TelegramGatewayResult.success(42L));
    }

    private TelegramUpdateRequest update(String text) {
        TelegramCallbackUserRequest from = new TelegramCallbackUserRequest(TELEGRAM_USER_ID);
        TelegramCallbackChatRequest chat = new TelegramCallbackChatRequest(CHAT_ID);
        TelegramMessageRequest message = new TelegramMessageRequest(from, chat, text);
        return new TelegramUpdateRequest(1L, null, message);
    }

    private Membership activeMembership() {
        Membership m = new Membership(TENANT_ID, APP_USER_ID);
        m.setStatus(MembershipStatus.ACTIVE);
        return m;
    }

    // ========== Happy paths ==========

    @Test
    void knownCommand_validActor_validMembership_executesAndSendsReplyAndAudits() {
        when(registry.findByName("/help")).thenReturn(Optional.of(helpCommand));
        when(identityQueryService.findUserByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.of(appUser));
        when(membershipRepository.findByUserId(APP_USER_ID))
                .thenReturn(List.of(activeMembership()));
        when(tenantConfigQueryService.findTenantById(TENANT_ID))
                .thenReturn(Optional.of(tenant));

        service.handle(update("/help"));

        verify(helpCommand).execute(any(TelegramBotCommandContext.class));
        verify(outboundGateway).sendBotReply(eq(CHAT_ID), eq("Mavjud buyruqlar: ..."));
        verify(auditService).recordEventInNewTransaction(
                eq(TENANT_ID), eq("APP_USER"), eq(APP_USER_ID),
                eq("TELEGRAM_BOT_COMMAND_EXECUTED"), eq(APP_USER_ID),
                eq("TELEGRAM_COMMAND"), eq(null), anyString());
    }

    @Test
    void commandWithArguments_argumentsParsedAndPassedInContext() {
        when(registry.findByName("/help")).thenReturn(Optional.of(helpCommand));
        when(identityQueryService.findUserByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.of(appUser));
        when(membershipRepository.findByUserId(APP_USER_ID))
                .thenReturn(List.of(activeMembership()));
        when(tenantConfigQueryService.findTenantById(TENANT_ID))
                .thenReturn(Optional.of(tenant));

        service.handle(update("/help foo bar baz"));

        ArgumentCaptor<TelegramBotCommandContext> ctx = ArgumentCaptor.forClass(TelegramBotCommandContext.class);
        verify(helpCommand).execute(ctx.capture());
        assertThat(ctx.getValue().arguments()).containsExactly("foo", "bar", "baz");
        assertThat(ctx.getValue().rawText()).isEqualTo("/help foo bar baz");
    }

    @Test
    void multipleMemberships_firstActiveMembershipChosen() {
        UUID otherTenantId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        Membership suspended = new Membership(otherTenantId, APP_USER_ID);
        suspended.setStatus(MembershipStatus.SUSPENDED);
        Membership active = activeMembership();
        when(registry.findByName("/help")).thenReturn(Optional.of(helpCommand));
        when(identityQueryService.findUserByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.of(appUser));
        when(membershipRepository.findByUserId(APP_USER_ID))
                .thenReturn(List.of(suspended, active));
        when(tenantConfigQueryService.findTenantById(TENANT_ID))
                .thenReturn(Optional.of(tenant));

        service.handle(update("/help"));

        ArgumentCaptor<TelegramBotCommandContext> ctx = ArgumentCaptor.forClass(TelegramBotCommandContext.class);
        verify(helpCommand).execute(ctx.capture());
        assertThat(ctx.getValue().tenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void tenantSlugResolvedAndPassedInContext() {
        when(registry.findByName("/help")).thenReturn(Optional.of(helpCommand));
        when(identityQueryService.findUserByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.of(appUser));
        when(membershipRepository.findByUserId(APP_USER_ID))
                .thenReturn(List.of(activeMembership()));
        when(tenantConfigQueryService.findTenantById(TENANT_ID))
                .thenReturn(Optional.of(tenant));

        service.handle(update("/help"));

        ArgumentCaptor<TelegramBotCommandContext> ctx = ArgumentCaptor.forClass(TelegramBotCommandContext.class);
        verify(helpCommand).execute(ctx.capture());
        assertThat(ctx.getValue().tenantSlug()).isEqualTo("acme");
        assertThat(ctx.getValue().actorDisplayName()).isEqualTo("Demo Admin");
    }

    @Test
    void auditPayloadContainsCommandAndTenantId_butNotArgumentsOrRawText() {
        when(registry.findByName("/help")).thenReturn(Optional.of(helpCommand));
        when(identityQueryService.findUserByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.of(appUser));
        when(membershipRepository.findByUserId(APP_USER_ID))
                .thenReturn(List.of(activeMembership()));
        when(tenantConfigQueryService.findTenantById(TENANT_ID))
                .thenReturn(Optional.of(tenant));

        service.handle(update("/help some-secret-token-from-user"));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(auditService).recordEventInNewTransaction(
                any(), any(), any(), any(), any(), any(), any(), payload.capture());
        String json = payload.getValue();
        assertThat(json).contains("\"command\":\"/help\"");
        assertThat(json).contains("\"tenantId\":\"" + TENANT_ID + "\"");
        assertThat(json).doesNotContain("some-secret-token-from-user");
        assertThat(json).doesNotContain("rawText");
        assertThat(json).doesNotContain("arguments");
    }

    // ========== Sad paths ==========

    @Test
    void unknownCommand_sendsPoliteReply_andDoesNotAudit() {
        when(registry.findByName("/foo")).thenReturn(Optional.empty());

        service.handle(update("/foo"));

        verify(outboundGateway).sendBotReply(eq(CHAT_ID),
                eq(TelegramBotCommandService.REPLY_UNKNOWN_COMMAND));
        verifyNoInteractions(identityQueryService);
        verifyNoInteractions(membershipRepository);
        verifyNoInteractions(auditService);
    }

    @Test
    void knownCommand_unknownAppUser_sendsNotRegisteredReply_andNoAudit() {
        when(registry.findByName("/help")).thenReturn(Optional.of(helpCommand));
        when(identityQueryService.findUserByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.empty());

        service.handle(update("/help"));

        verify(outboundGateway).sendBotReply(eq(CHAT_ID),
                eq(TelegramBotCommandService.REPLY_NOT_REGISTERED));
        verifyNoInteractions(membershipRepository);
        verify(auditService, never()).recordEventInNewTransaction(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void knownCommand_appUserWithoutActiveMembership_sendsNotRegisteredReply_andAuditsNotRegistered() {
        when(registry.findByName("/help")).thenReturn(Optional.of(helpCommand));
        when(identityQueryService.findUserByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.of(appUser));
        when(membershipRepository.findByUserId(APP_USER_ID))
                .thenReturn(new ArrayList<>()); // empty memberships

        service.handle(update("/help"));

        verify(outboundGateway).sendBotReply(eq(CHAT_ID),
                eq(TelegramBotCommandService.REPLY_NOT_REGISTERED));
        verify(auditService).recordEventInNewTransaction(
                eq(null), eq("APP_USER"), eq(APP_USER_ID),
                eq("TELEGRAM_BOT_COMMAND_NOT_REGISTERED"),
                eq(APP_USER_ID), eq("TELEGRAM_COMMAND"), eq(null), anyString());
    }

    @Test
    void emptyOrNonSlashText_doesNothing() {
        TelegramCallbackUserRequest from = new TelegramCallbackUserRequest(TELEGRAM_USER_ID);
        TelegramCallbackChatRequest chat = new TelegramCallbackChatRequest(CHAT_ID);

        service.handle(new TelegramUpdateRequest(1L, null,
                new TelegramMessageRequest(from, chat, "hello world")));
        service.handle(new TelegramUpdateRequest(2L, null,
                new TelegramMessageRequest(from, chat, "")));
        service.handle(new TelegramUpdateRequest(3L, null,
                new TelegramMessageRequest(from, chat, null)));
        service.handle(new TelegramUpdateRequest(4L, null, null));
        service.handle(null);

        verifyNoInteractions(registry);
        verifyNoInteractions(outboundGateway);
        verifyNoInteractions(auditService);
    }

    @Test
    void missingChatOrFrom_doesNothing() {
        TelegramCallbackUserRequest from = new TelegramCallbackUserRequest(TELEGRAM_USER_ID);
        TelegramCallbackChatRequest chat = new TelegramCallbackChatRequest(CHAT_ID);

        // chat missing
        service.handle(new TelegramUpdateRequest(1L, null,
                new TelegramMessageRequest(from, null, "/help")));
        // from missing
        service.handle(new TelegramUpdateRequest(2L, null,
                new TelegramMessageRequest(null, chat, "/help")));
        // chat.id missing
        service.handle(new TelegramUpdateRequest(3L, null,
                new TelegramMessageRequest(from, new TelegramCallbackChatRequest(null), "/help")));

        verifyNoInteractions(registry);
        verifyNoInteractions(outboundGateway);
    }

    @Test
    void caseInsensitiveCommandLookup_routesCorrectly() {
        when(registry.findByName("/Help")).thenReturn(Optional.of(helpCommand));
        when(identityQueryService.findUserByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.of(appUser));
        when(membershipRepository.findByUserId(APP_USER_ID))
                .thenReturn(List.of(activeMembership()));
        when(tenantConfigQueryService.findTenantById(TENANT_ID))
                .thenReturn(Optional.of(tenant));

        service.handle(update("/Help"));

        verify(helpCommand).execute(any(TelegramBotCommandContext.class));
    }

    @Test
    void commandReturnsBlankReply_fallbackPlaceholderSent() {
        when(helpCommand.execute(any(TelegramBotCommandContext.class))).thenReturn("");
        when(registry.findByName("/help")).thenReturn(Optional.of(helpCommand));
        when(identityQueryService.findUserByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.of(appUser));
        when(membershipRepository.findByUserId(APP_USER_ID))
                .thenReturn(List.of(activeMembership()));
        when(tenantConfigQueryService.findTenantById(TENANT_ID))
                .thenReturn(Optional.of(tenant));

        service.handle(update("/help"));

        verify(outboundGateway).sendBotReply(eq(CHAT_ID), anyString());
    }
}
