package com.engops.platform.intake;

import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.identity.model.AppUser;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.telegram.TelegramAcknowledgeCallbackResult;
import com.engops.platform.telegram.TelegramCallbackAcknowledgementService;
import com.engops.platform.telegram.TelegramCallbackChatRequest;
import com.engops.platform.telegram.TelegramCallbackMessageRequest;
import com.engops.platform.telegram.TelegramCallbackQueryRequest;
import com.engops.platform.telegram.TelegramCallbackUserRequest;
import com.engops.platform.workflow.WorkflowTransitionService;
import com.engops.platform.workitem.OperationalAuthorizationService;
import com.engops.platform.workitem.WorkItemQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 173 — {@link TelegramCallbackActionExecutionService} unit testlari.
 *
 * <p>Orchestrator har bir denial yo'lida workflow transition'ni umuman
 * chaqirmasligi va happy-path'da aniq argumentlar bilan bir marta
 * chaqirilishini isbotlaydi.</p>
 */
class TelegramCallbackActionExecutionServiceTest {

    private static final Long TELEGRAM_USER_ID = 123456789L;
    private static final UUID APP_USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORK_ITEM_ID = UUID.fromString("7c3b2a4d-1234-4abc-9def-0123456789ab");

    private final IdentityQueryService identityQueryService = mock(IdentityQueryService.class);
    private final WorkItemQueryService workItemQueryService = mock(WorkItemQueryService.class);
    private final OperationalAuthorizationService operationalAuthorizationService =
            mock(OperationalAuthorizationService.class);
    private final WorkflowTransitionService workflowTransitionService =
            mock(WorkflowTransitionService.class);
    private final TelegramCallbackAcknowledgementService acknowledgementService =
            mock(TelegramCallbackAcknowledgementService.class);

    private final TelegramCallbackActionExecutionService executionService =
            new TelegramCallbackActionExecutionService(
                    identityQueryService,
                    workItemQueryService,
                    operationalAuthorizationService,
                    workflowTransitionService,
                    acknowledgementService);

    private TelegramCallbackQueryRequest cb() {
        return new TelegramCallbackQueryRequest(
                "cb-id",
                new TelegramCallbackUserRequest(TELEGRAM_USER_ID),
                new TelegramCallbackMessageRequest(555L, new TelegramCallbackChatRequest(-1001234567890L)),
                WORK_ITEM_ID + ":START_PROCESSING");
    }

    private TelegramCallbackQueryRequest cbWithoutFrom() {
        return new TelegramCallbackQueryRequest(
                "cb-id",
                null,
                new TelegramCallbackMessageRequest(555L, new TelegramCallbackChatRequest(-1001234567890L)),
                WORK_ITEM_ID + ":START_PROCESSING");
    }

    private AppUser appUser() {
        return new AppUser(APP_USER_ID, TELEGRAM_USER_ID, "Test User");
    }

    private void stubHappyPathUpToTransition() {
        when(identityQueryService.findUserByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.of(appUser()));
        when(workItemQueryService.findTenantIdByWorkItemId(WORK_ITEM_ID))
                .thenReturn(Optional.of(TENANT_ID));
        when(identityQueryService.hasActiveMembership(TENANT_ID, APP_USER_ID))
                .thenReturn(true);
        // authorizeTransition default — does nothing (no throw).
    }

    // --- Identity resolution ---

    @Test
    void unknownTelegramUserReturnsUserNotFound() {
        when(identityQueryService.findUserByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.empty());

        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.USER_NOT_FOUND);
        verifyNoInteractions(workItemQueryService);
        verifyNoInteractions(operationalAuthorizationService);
        verifyNoInteractions(workflowTransitionService);
    }

    @Test
    void nullFromReturnsUserNotFound() {
        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cbWithoutFrom(), WORK_ITEM_ID, "START_PROCESSING");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.USER_NOT_FOUND);
        // findUserByTelegramUserId hech qachon chaqirilmasligi shart —
        // telegramUserId null bo'lsa orchestrator darhol qaytadi.
        verify(identityQueryService, never()).findUserByTelegramUserId(any());
        verifyNoInteractions(workItemQueryService);
        verifyNoInteractions(operationalAuthorizationService);
        verifyNoInteractions(workflowTransitionService);
    }

    @Test
    void nullCallbackQueryReturnsUserNotFound() {
        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(null, WORK_ITEM_ID, "START_PROCESSING");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.USER_NOT_FOUND);
        verifyNoInteractions(identityQueryService);
        verifyNoInteractions(workItemQueryService);
        verifyNoInteractions(operationalAuthorizationService);
        verifyNoInteractions(workflowTransitionService);
    }

    // --- Tenant resolution ---

    @Test
    void workItemMissingReturnsWorkItemNotFound() {
        when(identityQueryService.findUserByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.of(appUser()));
        when(workItemQueryService.findTenantIdByWorkItemId(WORK_ITEM_ID))
                .thenReturn(Optional.empty());

        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.WORK_ITEM_NOT_FOUND);
        verify(identityQueryService, never()).hasActiveMembership(any(), any());
        verifyNoInteractions(operationalAuthorizationService);
        verifyNoInteractions(workflowTransitionService);
    }

    // --- Membership ---

    @Test
    void inactiveMembershipReturnsNotAMember() {
        when(identityQueryService.findUserByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.of(appUser()));
        when(workItemQueryService.findTenantIdByWorkItemId(WORK_ITEM_ID))
                .thenReturn(Optional.of(TENANT_ID));
        when(identityQueryService.hasActiveMembership(TENANT_ID, APP_USER_ID))
                .thenReturn(false);

        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.NOT_A_MEMBER);
        verifyNoInteractions(operationalAuthorizationService);
        verifyNoInteractions(workflowTransitionService);
    }

    // --- Permission ---

    @Test
    void accessDeniedReturnsPermissionDenied() {
        stubHappyPathUpToTransition();
        doThrow(new AccessDeniedException("Bu operatsiya uchun WORK_ITEM_TRANSITION ruxsati talab qilinadi"))
                .when(operationalAuthorizationService)
                .authorizeTransition(TENANT_ID, APP_USER_ID);

        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.PERMISSION_DENIED);
        verifyNoInteractions(workflowTransitionService);
    }

    // --- Workflow execution failures ---

    @Test
    void invalidTransitionRuleReturnsInvalidTransition() {
        stubHappyPathUpToTransition();
        when(workflowTransitionService.transition(
                eq(TENANT_ID), eq(WORK_ITEM_ID), eq("PROCESSING"),
                eq(APP_USER_ID), eq("TELEGRAM_CALLBACK"), isNull()))
                .thenThrow(new BusinessRuleException("INVALID_TRANSITION",
                        "'FIXED' dan 'PROCESSING' ga o'tish ruxsat etilmagan"));

        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.INVALID_TRANSITION);
        verify(workflowTransitionService, times(1)).transition(
                any(), any(), anyString(), any(), anyString(), isNull());
    }

    @Test
    void sameStatusBusinessRuleAlsoYieldsInvalidTransition() {
        stubHappyPathUpToTransition();
        when(workflowTransitionService.transition(
                any(), any(), anyString(), any(), anyString(), isNull()))
                .thenThrow(new BusinessRuleException("SAME_STATUS",
                        "Work item allaqachon 'PROCESSING' holatida"));

        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.INVALID_TRANSITION);
    }

    @Test
    void workItemDisappearsBeforeTransitionReturnsWorkItemNotFound() {
        stubHappyPathUpToTransition();
        when(workflowTransitionService.transition(
                any(), any(), anyString(), any(), anyString(), isNull()))
                .thenThrow(new ResourceNotFoundException("WorkItem", WORK_ITEM_ID));

        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.WORK_ITEM_NOT_FOUND);
    }

    @Test
    void unexpectedRuntimeExceptionReturnsUnexpectedFailure() {
        stubHappyPathUpToTransition();
        when(workflowTransitionService.transition(
                any(), any(), anyString(), any(), anyString(), isNull()))
                .thenThrow(new RuntimeException("simulyatsiya qilingan kutilmagan xato"));

        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.UNEXPECTED_FAILURE);
    }

    // --- Happy path ---

    @Test
    void happyPathExecutesTransitionWithCorrectArguments() {
        stubHappyPathUpToTransition();

        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.EXECUTED);
        verify(workflowTransitionService, times(1)).transition(
                eq(TENANT_ID), eq(WORK_ITEM_ID), eq("PROCESSING"),
                eq(APP_USER_ID), eq("TELEGRAM_CALLBACK"), isNull());
    }

    // --- Action code mapping (boundary lock vs TelegramActionAssembler MVP rules) ---

    @ParameterizedTest
    @CsvSource({
            "START_PROCESSING, PROCESSING",
            "SEND_TO_TESTING,  TESTING",
            "MARK_FIXED,       FIXED",
            "RETURN_TO_BUGS,   BUGS",
            "REOPEN,           BUGS"
    })
    void targetStatusMappingCoversAllKnownActionCodes(String actionCode,
                                                       String expectedTargetStatus) {
        stubHappyPathUpToTransition();

        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cb(), WORK_ITEM_ID, actionCode);

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.EXECUTED);
        verify(workflowTransitionService, times(1)).transition(
                eq(TENANT_ID), eq(WORK_ITEM_ID), eq(expectedTargetStatus),
                eq(APP_USER_ID), eq("TELEGRAM_CALLBACK"), isNull());
    }

    @Test
    void unmappedActionCodeFromParserReturnsInvalidTransitionWithoutCallingWorkflow() {
        // Defensive guard: agar parser KNOWN_ACTION_CODES'da bo'lgan,
        // lekin ACTION_TO_TARGET_STATUS map'da bo'lmagan kod uzatib qo'ysa
        // (sinxron emas holatda), orchestrator transition'ni chaqirmaydi.
        stubHappyPathUpToTransition();

        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cb(), WORK_ITEM_ID, "UNMAPPED_BUT_ACCEPTED");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.INVALID_TRANSITION);
        verifyNoInteractions(workflowTransitionService);
    }

    @Test
    void noTransitionAttemptedForDenialPaths() {
        // Bitta umumiy assertion: USER_NOT_FOUND, WORK_ITEM_NOT_FOUND,
        // NOT_A_MEMBER, PERMISSION_DENIED — barchasi uchun
        // workflowTransitionService HECH QACHON chaqirilmasligini kafolatlaydi.

        // 1) USER_NOT_FOUND
        when(identityQueryService.findUserByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.empty());
        executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        // 2) WORK_ITEM_NOT_FOUND
        when(identityQueryService.findUserByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.of(appUser()));
        when(workItemQueryService.findTenantIdByWorkItemId(WORK_ITEM_ID))
                .thenReturn(Optional.empty());
        executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        // 3) NOT_A_MEMBER
        when(workItemQueryService.findTenantIdByWorkItemId(WORK_ITEM_ID))
                .thenReturn(Optional.of(TENANT_ID));
        when(identityQueryService.hasActiveMembership(TENANT_ID, APP_USER_ID))
                .thenReturn(false);
        executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        // 4) PERMISSION_DENIED
        when(identityQueryService.hasActiveMembership(TENANT_ID, APP_USER_ID))
                .thenReturn(true);
        doThrow(new AccessDeniedException("denied"))
                .when(operationalAuthorizationService).authorizeTransition(TENANT_ID, APP_USER_ID);
        executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        verifyNoInteractions(workflowTransitionService);
    }

    // ===========================================================
    // Phase 175 — acknowledgement wiring
    // ===========================================================

    @Test
    void happyPathAcknowledgesOnceWithExecutedText() {
        stubHappyPathUpToTransition();

        executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        verify(acknowledgementService, times(1))
                .acknowledge(eq("cb-id"), eq("Action applied."));
    }

    @Test
    void userNotFoundAcknowledgesAndDoesNotInvokeWorkflow() {
        when(identityQueryService.findUserByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.empty());

        executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        verify(acknowledgementService, times(1))
                .acknowledge(eq("cb-id"),
                        eq("Telegram user is not linked to a platform account."));
        verifyNoInteractions(workflowTransitionService);
    }

    @Test
    void workItemNotFoundAcknowledgesAndDoesNotInvokeWorkflow() {
        when(identityQueryService.findUserByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.of(appUser()));
        when(workItemQueryService.findTenantIdByWorkItemId(WORK_ITEM_ID))
                .thenReturn(Optional.empty());

        executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        verify(acknowledgementService, times(1))
                .acknowledge(eq("cb-id"), eq("Work item was not found."));
        verifyNoInteractions(workflowTransitionService);
    }

    @Test
    void notAMemberAcknowledgesAndDoesNotInvokeWorkflow() {
        when(identityQueryService.findUserByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.of(appUser()));
        when(workItemQueryService.findTenantIdByWorkItemId(WORK_ITEM_ID))
                .thenReturn(Optional.of(TENANT_ID));
        when(identityQueryService.hasActiveMembership(TENANT_ID, APP_USER_ID))
                .thenReturn(false);

        executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        verify(acknowledgementService, times(1))
                .acknowledge(eq("cb-id"),
                        eq("You are not an active member of this tenant."));
        verifyNoInteractions(workflowTransitionService);
    }

    @Test
    void permissionDeniedAcknowledgesAndDoesNotInvokeWorkflow() {
        stubHappyPathUpToTransition();
        doThrow(new AccessDeniedException("denied"))
                .when(operationalAuthorizationService).authorizeTransition(TENANT_ID, APP_USER_ID);

        executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        verify(acknowledgementService, times(1))
                .acknowledge(eq("cb-id"),
                        eq("You do not have permission to change this work item."));
        verifyNoInteractions(workflowTransitionService);
    }

    @Test
    void invalidTransitionAcknowledgesAndReturnsInvalidTransition() {
        stubHappyPathUpToTransition();
        when(workflowTransitionService.transition(
                any(), any(), anyString(), any(), anyString(), isNull()))
                .thenThrow(new BusinessRuleException("INVALID_TRANSITION", "not allowed"));

        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.INVALID_TRANSITION);
        verify(acknowledgementService, times(1))
                .acknowledge(eq("cb-id"),
                        eq("This action is no longer valid for the current status."));
    }

    @Test
    void unexpectedFailureAcknowledgesAndReturnsUnexpectedFailure() {
        stubHappyPathUpToTransition();
        when(workflowTransitionService.transition(
                any(), any(), anyString(), any(), anyString(), isNull()))
                .thenThrow(new RuntimeException("boom"));

        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.UNEXPECTED_FAILURE);
        verify(acknowledgementService, times(1))
                .acknowledge(eq("cb-id"),
                        eq("Could not process the action. Please try again later."));
    }

    @Test
    void unmappedActionCodeAcknowledgesAndDoesNotInvokeWorkflow() {
        stubHappyPathUpToTransition();

        executionService.execute(cb(), WORK_ITEM_ID, "UNMAPPED_BUT_ACCEPTED");

        verify(acknowledgementService, times(1))
                .acknowledge(eq("cb-id"),
                        eq("This action is no longer valid for the current status."));
        verifyNoInteractions(workflowTransitionService);
    }

    @Test
    void nullFromAcknowledgesUsingInboundCallbackQueryId() {
        // callback_query.from null bo'lsa orchestrator USER_NOT_FOUND
        // qaytaradi, lekin callback_query.id mavjud — acknowledgement
        // baribir yuborilishi shart.
        executionService.execute(cbWithoutFrom(), WORK_ITEM_ID, "START_PROCESSING");

        verify(acknowledgementService, times(1))
                .acknowledge(eq("cb-id"),
                        eq("Telegram user is not linked to a platform account."));
    }

    @Test
    void acknowledgementServiceExceptionDoesNotChangeReturnedOutcome() {
        // Defense-in-depth: service o'zining fail-soft kontrakti bilan
        // exception tashlamasligi shart, lekin agar tashlasa, orchestrator
        // outcome'i o'zgarmaydi.
        stubHappyPathUpToTransition();
        when(acknowledgementService.acknowledge(anyString(), anyString()))
                .thenThrow(new RuntimeException("simulated ack failure"));

        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.EXECUTED);
        // Workflow transition baribir bir marta chaqirilgan.
        verify(workflowTransitionService, times(1)).transition(
                eq(TENANT_ID), eq(WORK_ITEM_ID), eq("PROCESSING"),
                eq(APP_USER_ID), eq("TELEGRAM_CALLBACK"), isNull());
    }

    @Test
    void acknowledgementServiceFailureResultDoesNotChangeReturnedOutcome() {
        stubHappyPathUpToTransition();
        when(acknowledgementService.acknowledge(anyString(), anyString()))
                .thenReturn(TelegramAcknowledgeCallbackResult.failed(
                        com.engops.platform.telegram.TelegramGatewayError.NETWORK_ERROR,
                        "stubbed"));

        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.EXECUTED);
    }
}
