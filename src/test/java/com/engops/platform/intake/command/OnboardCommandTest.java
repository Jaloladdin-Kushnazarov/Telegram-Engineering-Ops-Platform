package com.engops.platform.intake.command;

import com.engops.platform.admin.TenantOnboardingCommand;
import com.engops.platform.admin.TenantOnboardingResult;
import com.engops.platform.admin.TenantOnboardingService;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.telegram.TelegramBotCommandContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 201 — {@link OnboardCommand} unit testlari. TenantOnboardingService
 * mock; argument parsing + reply formatting + exception konversiya'ni
 * tekshiradi.
 */
class OnboardCommandTest {

    private static final UUID ACTOR_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID NEW_TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID NEW_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID NEW_MEMBERSHIP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID WORKFLOW_ID_1 = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID WORKFLOW_ID_2 = UUID.fromString("66666666-6666-6666-6666-666666666666");

    private final TenantOnboardingService onboardingService = mock(TenantOnboardingService.class);
    private final OnboardCommand command = new OnboardCommand(onboardingService);

    private TelegramBotCommandContext context(String rawText) {
        return new TelegramBotCommandContext(
                ACTOR_ID, "Operator",
                UUID.randomUUID(), "ops",
                123456789L, -1001234567890L,
                "/onboard", List.of(), rawText);
    }

    private TenantOnboardingResult happyResult(List<String> templateCodes) {
        List<TenantOnboardingResult.WorkflowDefinitionSummary> summaries = new java.util.ArrayList<>();
        UUID[] ids = {WORKFLOW_ID_1, WORKFLOW_ID_2};
        for (int i = 0; i < templateCodes.size(); i++) {
            UUID id = i < ids.length ? ids[i] : UUID.randomUUID();
            summaries.add(new TenantOnboardingResult.WorkflowDefinitionSummary(
                    id, templateCodes.get(i), "BUG"));
        }
        return new TenantOnboardingResult(
                NEW_TENANT_ID, "acme", "Acme Corp",
                Instant.parse("2026-05-23T00:00:00Z"),
                NEW_USER_ID, NEW_MEMBERSHIP_ID, summaries);
    }

    // ========== Command identity ==========

    @Test
    void commandName_isOnboard() {
        assertThat(command.commandName()).isEqualTo("/onboard");
    }

    // ========== Happy paths ==========

    @Test
    void happyPath_singleTemplate_callsServiceWithParsedArgsAndReturnsSuccessReply() {
        when(onboardingService.onboard(any(TenantOnboardingCommand.class)))
                .thenReturn(happyResult(List.of("BUG_MINIMAL")));

        String reply = command.execute(context(
                "/onboard acme \"Acme Corp\" 123456789 \"Demo Admin\" BUG_MINIMAL"));

        assertThat(reply).contains("✅ Tenant yaratildi");
        assertThat(reply).contains("Slug: acme");
        assertThat(reply).contains("Tenant ID: " + NEW_TENANT_ID);
        assertThat(reply).contains("Admin user ID: " + NEW_USER_ID);
        assertThat(reply).contains("Workflows: 1 ta (BUG_MINIMAL)");

        ArgumentCaptor<TenantOnboardingCommand> captor =
                ArgumentCaptor.forClass(TenantOnboardingCommand.class);
        verify(onboardingService).onboard(captor.capture());
        TenantOnboardingCommand sent = captor.getValue();
        assertThat(sent.tenantSlug()).isEqualTo("acme");
        assertThat(sent.tenantName()).isEqualTo("Acme Corp");
        assertThat(sent.adminTelegramUserId()).isEqualTo(123456789L);
        assertThat(sent.adminDisplayName()).isEqualTo("Demo Admin");
        assertThat(sent.workflowTemplateCodes()).containsExactly("BUG_MINIMAL");
        assertThat(sent.tenantTimezone()).isNull();
        assertThat(sent.adminUsername()).isNull();
        assertThat(sent.actorUserId()).isEqualTo(ACTOR_ID);
    }

    @Test
    void happyPath_multipleTemplates_includesAllInReply() {
        when(onboardingService.onboard(any(TenantOnboardingCommand.class)))
                .thenReturn(happyResult(List.of("BUG_MINIMAL", "TASK_BASIC")));

        String reply = command.execute(context(
                "/onboard widgets \"Widgets Inc\" 987654321 \"Alice Wonder\" BUG_MINIMAL TASK_BASIC"));

        assertThat(reply).contains("Workflows: 2 ta (BUG_MINIMAL, TASK_BASIC)");
        ArgumentCaptor<TenantOnboardingCommand> captor =
                ArgumentCaptor.forClass(TenantOnboardingCommand.class);
        verify(onboardingService).onboard(captor.capture());
        assertThat(captor.getValue().workflowTemplateCodes())
                .containsExactly("BUG_MINIMAL", "TASK_BASIC");
    }

    @Test
    void replyAlwaysUnder4000Chars() {
        when(onboardingService.onboard(any(TenantOnboardingCommand.class)))
                .thenReturn(happyResult(List.of("BUG_MINIMAL", "TASK_BASIC")));

        String reply = command.execute(context(
                "/onboard acme \"Acme Corp\" 123 \"Demo Admin\" BUG_MINIMAL TASK_BASIC"));
        assertThat(reply.length()).isLessThan(4000);
    }

    // ========== Argument / parsing errors ==========

    @Test
    void tooFewArguments_returnsUsageReply() {
        String reply = command.execute(context("/onboard acme \"Acme Corp\" 123 \"Demo Admin\""));
        // 4 positional after command (missing template) → usage hint.
        assertThat(reply).contains("Foydalanish");
        verifyNoInteractions(onboardingService);
    }

    @Test
    void unmatchedQuote_returnsTokenizerError() {
        String reply = command.execute(context("/onboard acme \"Unclosed"));
        assertThat(reply).contains("Argumentlarda xatolik");
        verifyNoInteractions(onboardingService);
    }

    @Test
    void invalidTelegramUserId_nonNumeric_returnsFriendlyError() {
        String reply = command.execute(context(
                "/onboard acme \"Acme\" not-a-number \"Demo\" BUG_MINIMAL"));
        assertThat(reply).contains("Telegram user id raqam bo'lishi shart");
        verifyNoInteractions(onboardingService);
    }

    @Test
    void negativeTelegramUserId_returnsFriendlyError() {
        String reply = command.execute(context(
                "/onboard acme \"Acme\" -5 \"Demo\" BUG_MINIMAL"));
        assertThat(reply).contains("musbat raqam");
        verifyNoInteractions(onboardingService);
    }

    @Test
    void zeroTelegramUserId_returnsFriendlyError() {
        String reply = command.execute(context(
                "/onboard acme \"Acme\" 0 \"Demo\" BUG_MINIMAL"));
        assertThat(reply).contains("musbat raqam");
        verifyNoInteractions(onboardingService);
    }

    // ========== Service exception conversion ==========

    @Test
    void slugTaken_returnsLocalizedReply() {
        when(onboardingService.onboard(any(TenantOnboardingCommand.class)))
                .thenThrow(new BusinessRuleException("SLUG_TAKEN", "exists"));

        String reply = command.execute(context(
                "/onboard acme \"Acme\" 123 \"Demo\" BUG_MINIMAL"));
        assertThat(reply).contains("Bu slug allaqachon band: 'acme'");
    }

    @Test
    void unknownWorkflowTemplate_returnsLocalizedReply() {
        when(onboardingService.onboard(any(TenantOnboardingCommand.class)))
                .thenThrow(new BusinessRuleException(
                        "UNKNOWN_WORKFLOW_TEMPLATE", "Noma'lum workflow shablon kodi"));

        String reply = command.execute(context(
                "/onboard acme \"Acme\" 123 \"Demo\" BOGUS_TEMPLATE"));
        assertThat(reply).contains("Noma'lum workflow shabloni: 'BOGUS_TEMPLATE'");
    }

    @Test
    void invalidSlug_returnsLocalizedReply() {
        when(onboardingService.onboard(any(TenantOnboardingCommand.class)))
                .thenThrow(new BusinessRuleException("INVALID_SLUG", "regex fail"));

        String reply = command.execute(context(
                "/onboard BAD-SLUG \"Acme\" 123 \"Demo\" BUG_MINIMAL"));
        assertThat(reply).contains("Slug noto'g'ri");
        assertThat(reply).contains("'BAD-SLUG'");
    }

    @Test
    void noTemplatesRequested_returnsLocalizedReply() {
        // Service throws after our 5-token gate is satisfied (e.g. all blank
        // codes pre-validated by service).
        when(onboardingService.onboard(any(TenantOnboardingCommand.class)))
                .thenThrow(new BusinessRuleException(
                        "NO_TEMPLATES_REQUESTED", "kamida 1 ta"));

        String reply = command.execute(context(
                "/onboard acme \"Acme\" 123 \"Demo\" BUG_MINIMAL"));
        assertThat(reply).contains("Kamida 1 ta workflow shabloni");
    }

    @Test
    void accessDenied_returnsLocalizedReply_noLeak() {
        when(onboardingService.onboard(any(TenantOnboardingCommand.class)))
                .thenThrow(new AccessDeniedException("Bu operatsiya uchun TENANT_ONBOARD ruxsati"));

        String reply = command.execute(context(
                "/onboard acme \"Acme\" 123 \"Demo\" BUG_MINIMAL"));
        assertThat(reply).isEqualTo(OnboardCommand.REPLY_ACCESS_DENIED);
    }

    @Test
    void unexpectedRuntimeException_returnsFallbackReply() {
        when(onboardingService.onboard(any(TenantOnboardingCommand.class)))
                .thenThrow(new RuntimeException("simulated DB outage"));

        String reply = command.execute(context(
                "/onboard acme \"Acme\" 123 \"Demo\" BUG_MINIMAL"));
        assertThat(reply).isEqualTo(OnboardCommand.REPLY_UNEXPECTED);
        // PII / exception message must NOT leak into reply.
        assertThat(reply).doesNotContain("simulated DB outage");
    }

    @Test
    void unknownBusinessRuleCode_returnsFallbackTemplate() {
        when(onboardingService.onboard(any(TenantOnboardingCommand.class)))
                .thenThrow(new BusinessRuleException("WEIRD_NEW_CODE", "msg"));

        String reply = command.execute(context(
                "/onboard acme \"Acme\" 123 \"Demo\" BUG_MINIMAL"));
        assertThat(reply).contains("Onboarding xatolik: WEIRD_NEW_CODE");
    }

    // ========== Audit invariant (Phase 201 D13: NO audit from OnboardCommand) ==========

    @Test
    void errorReply_doesNotEchoRawText() {
        when(onboardingService.onboard(any(TenantOnboardingCommand.class)))
                .thenThrow(new RuntimeException("payload-with-secret-token"));

        String reply = command.execute(context(
                "/onboard acme \"My secret tenant name with secrets\" 123 \"Demo\" BUG_MINIMAL"));
        // rawText must never echo into the reply.
        assertThat(reply).doesNotContain("My secret tenant name with secrets");
        assertThat(reply).doesNotContain("payload-with-secret-token");
    }
}
