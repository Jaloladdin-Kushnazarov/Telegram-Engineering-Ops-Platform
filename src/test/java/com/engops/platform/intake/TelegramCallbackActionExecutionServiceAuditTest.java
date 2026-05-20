package com.engops.platform.intake;

import com.engops.platform.audit.AuditService;
import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.identity.model.AppUser;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.telegram.TelegramCallbackAcknowledgementService;
import com.engops.platform.telegram.TelegramCallbackChatRequest;
import com.engops.platform.telegram.TelegramCallbackMessageRequest;
import com.engops.platform.telegram.TelegramCallbackQueryRequest;
import com.engops.platform.telegram.TelegramCallbackUserRequest;
import com.engops.platform.workflow.WorkflowTransitionService;
import com.engops.platform.workitem.OperationalAuthorizationService;
import com.engops.platform.workitem.WorkItemQueryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
import static org.mockito.Mockito.when;

/**
 * Phase 185 — denial audit emission testlari.
 *
 * <p>Bu klass {@link TelegramCallbackActionExecutionService}'ning
 * mavjud outcome semantikasini takrorlamaydi (uni
 * {@code TelegramCallbackActionExecutionServiceTest} qoplaydi).
 * Bu yerda faqat Phase 185 da qo'shilgan
 * {@code TELEGRAM_CALLBACK_DENIED} audit yo'lining invariantlari
 * isbotlanadi:</p>
 * <ul>
 *   <li>Qaysi outcome'lar audit qatori yozadi va qaysilari yozmaydi.</li>
 *   <li>Audit payload tarkibi — faqat {@code outcome / actionCode /
 *       targetStatusCode}; raw callback_data, exception message, token
 *       hech qachon kirmaydi.</li>
 *   <li>Audit yozish {@link AuditService#recordEventInNewTransaction}
 *       (REQUIRES_NEW) orqali bajarilishi.</li>
 *   <li>Fail-soft kontrakti — audit yozish RuntimeException tashlasa,
 *       callback outcome o'zgarmaydi va acknowledgement baribir
 *       chaqiriladi.</li>
 * </ul>
 */
class TelegramCallbackActionExecutionServiceAuditTest {

    private static final Long TELEGRAM_USER_ID = 987654321L;
    private static final UUID APP_USER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WORK_ITEM_ID = UUID.fromString("8c4d3b2a-9876-4abc-9def-fedcba987654");

    private final IdentityQueryService identityQueryService = mock(IdentityQueryService.class);
    private final WorkItemQueryService workItemQueryService = mock(WorkItemQueryService.class);
    private final OperationalAuthorizationService operationalAuthorizationService =
            mock(OperationalAuthorizationService.class);
    private final WorkflowTransitionService workflowTransitionService =
            mock(WorkflowTransitionService.class);
    private final TelegramCallbackAcknowledgementService acknowledgementService =
            mock(TelegramCallbackAcknowledgementService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final TelegramCallbackActionExecutionService executionService =
            new TelegramCallbackActionExecutionService(
                    identityQueryService,
                    workItemQueryService,
                    operationalAuthorizationService,
                    workflowTransitionService,
                    acknowledgementService,
                    auditService,
                    meterRegistry);

    private TelegramCallbackQueryRequest cb() {
        return new TelegramCallbackQueryRequest(
                "cb-audit-id",
                new TelegramCallbackUserRequest(TELEGRAM_USER_ID),
                new TelegramCallbackMessageRequest(777L,
                        new TelegramCallbackChatRequest(-1009998887776L)),
                WORK_ITEM_ID + ":START_PROCESSING");
    }

    private TelegramCallbackQueryRequest cbWithoutFrom() {
        return new TelegramCallbackQueryRequest(
                "cb-audit-id",
                null,
                new TelegramCallbackMessageRequest(777L,
                        new TelegramCallbackChatRequest(-1009998887776L)),
                WORK_ITEM_ID + ":START_PROCESSING");
    }

    private AppUser appUser() {
        return new AppUser(APP_USER_ID, TELEGRAM_USER_ID, "Audit User");
    }

    private void stubResolvedActorTenantAndMembership() {
        when(identityQueryService.findUserByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.of(appUser()));
        when(workItemQueryService.findTenantIdByWorkItemId(WORK_ITEM_ID))
                .thenReturn(Optional.of(TENANT_ID));
        when(identityQueryService.hasActiveMembership(TENANT_ID, APP_USER_ID))
                .thenReturn(true);
    }

    private ArgumentCaptor<String> captureAuditPayload() {
        ArgumentCaptor<String> newValueCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService, times(1)).recordEventInNewTransaction(
                eq(TENANT_ID),
                eq("WORK_ITEM"),
                eq(WORK_ITEM_ID),
                eq("TELEGRAM_CALLBACK_DENIED"),
                eq(APP_USER_ID),
                eq("TELEGRAM_CALLBACK"),
                isNull(),
                newValueCaptor.capture());
        return newValueCaptor;
    }

    // ===========================================================
    // No-audit outcomes
    // ===========================================================

    @Test
    void userNotFoundDoesNotWriteAuditRow() {
        when(identityQueryService.findUserByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.empty());

        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.USER_NOT_FOUND);
        verify(auditService, never()).recordEventInNewTransaction(
                any(), anyString(), any(), anyString(), any(), anyString(), any(), any());
        // Acknowledgement baribir attempt qilinishi shart.
        verify(acknowledgementService, times(1))
                .acknowledge(eq("cb-audit-id"), anyString());
    }

    @Test
    void nullFromDoesNotWriteAuditRow() {
        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cbWithoutFrom(), WORK_ITEM_ID, "START_PROCESSING");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.USER_NOT_FOUND);
        verify(auditService, never()).recordEventInNewTransaction(
                any(), anyString(), any(), anyString(), any(), anyString(), any(), any());
        verify(acknowledgementService, times(1))
                .acknowledge(eq("cb-audit-id"), anyString());
    }

    @Test
    void workItemNotFoundDoesNotWriteAuditRow() {
        when(identityQueryService.findUserByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.of(appUser()));
        when(workItemQueryService.findTenantIdByWorkItemId(WORK_ITEM_ID))
                .thenReturn(Optional.empty());

        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.WORK_ITEM_NOT_FOUND);
        verify(auditService, never()).recordEventInNewTransaction(
                any(), anyString(), any(), anyString(), any(), anyString(), any(), any());
        verify(acknowledgementService, times(1))
                .acknowledge(eq("cb-audit-id"), anyString());
    }

    @Test
    void executedDoesNotWriteDeniedAuditRowFromThisService() {
        // WorkflowTransitionService o'z STATUS_TRANSITION audit'ini yozadi —
        // bu service EXECUTED yo'lida hech qanday qo'shimcha audit yozmaydi.
        stubResolvedActorTenantAndMembership();

        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.EXECUTED);
        verify(auditService, never()).recordEventInNewTransaction(
                any(), anyString(), any(), anyString(), any(), anyString(), any(), any());
    }

    @Test
    void workItemDisappearsDuringTransitionDoesNotWriteAuditRow() {
        // ResourceNotFoundException → outcome WORK_ITEM_NOT_FOUND.
        // Spec: WORK_ITEM_NOT_FOUND uchun audit yozilmaydi (qatorda
        // tenantId mavjud bo'lsa-da, outcome semantikasi bo'yicha
        // entity allaqachon yo'qolgan — audit chaqirilmaydi).
        stubResolvedActorTenantAndMembership();
        when(workflowTransitionService.transition(
                any(), any(), anyString(), any(), anyString(), isNull()))
                .thenThrow(new ResourceNotFoundException("WorkItem", WORK_ITEM_ID));

        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.WORK_ITEM_NOT_FOUND);
        verify(auditService, never()).recordEventInNewTransaction(
                any(), anyString(), any(), anyString(), any(), anyString(), any(), any());
    }

    // ===========================================================
    // Denial outcomes — audit ROW must be written
    // ===========================================================

    @Test
    void notAMemberWritesExactlyOneAuditRowWithBoundedPayload() {
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
        ArgumentCaptor<String> payload = captureAuditPayload();
        assertThat(payload.getValue())
                .contains("\"outcome\":\"NOT_A_MEMBER\"")
                .contains("\"actionCode\":\"START_PROCESSING\"")
                .contains("\"targetStatusCode\":null");
        assertThat(payload.getValue())
                .doesNotContain("cb-audit-id")
                .doesNotContain(WORK_ITEM_ID + ":START_PROCESSING")
                .doesNotContain("Audit User");
    }

    @Test
    void permissionDeniedWritesExactlyOneAuditRowWithBoundedPayload() {
        stubResolvedActorTenantAndMembership();
        doThrow(new AccessDeniedException("denied"))
                .when(operationalAuthorizationService)
                .authorizeTransition(TENANT_ID, APP_USER_ID);

        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.PERMISSION_DENIED);
        ArgumentCaptor<String> payload = captureAuditPayload();
        assertThat(payload.getValue())
                .contains("\"outcome\":\"PERMISSION_DENIED\"")
                .contains("\"actionCode\":\"START_PROCESSING\"")
                .contains("\"targetStatusCode\":null");
    }

    @Test
    void invalidTransitionFromBusinessRuleWritesAuditRowWithTargetStatus() {
        stubResolvedActorTenantAndMembership();
        when(workflowTransitionService.transition(
                eq(TENANT_ID), eq(WORK_ITEM_ID), eq("PROCESSING"),
                eq(APP_USER_ID), eq("TELEGRAM_CALLBACK"), isNull()))
                .thenThrow(new BusinessRuleException("INVALID_TRANSITION",
                        "rule violated"));

        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.INVALID_TRANSITION);
        ArgumentCaptor<String> payload = captureAuditPayload();
        assertThat(payload.getValue())
                .contains("\"outcome\":\"INVALID_TRANSITION\"")
                .contains("\"actionCode\":\"START_PROCESSING\"")
                .contains("\"targetStatusCode\":\"PROCESSING\"");
        // Exception message ham, raw callback_data ham audit payload'ga
        // kirmaganligi tasdiqlanadi.
        assertThat(payload.getValue())
                .doesNotContain("rule violated")
                .doesNotContain("cb-audit-id");
    }

    @Test
    void invalidTransitionFromUnmappedActionWritesAuditRowWithoutTargetStatus() {
        // Parser ACCEPTED qaytargan, lekin ACTION_TO_TARGET_STATUS map'da
        // yo'q — orchestrator INVALID_TRANSITION sifatida belgilab,
        // workflow transition'ni umuman chaqirmaydi. Audit baribir
        // yozilishi shart, targetStatusCode esa null bo'lib qoladi.
        stubResolvedActorTenantAndMembership();

        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cb(), WORK_ITEM_ID, "UNMAPPED_BUT_ACCEPTED");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.INVALID_TRANSITION);
        ArgumentCaptor<String> payload = captureAuditPayload();
        assertThat(payload.getValue())
                .contains("\"outcome\":\"INVALID_TRANSITION\"")
                .contains("\"actionCode\":\"UNMAPPED_BUT_ACCEPTED\"")
                .contains("\"targetStatusCode\":null");
    }

    @Test
    void unexpectedFailureWritesAuditRowAndDoesNotLeakExceptionMessage() {
        stubResolvedActorTenantAndMembership();
        when(workflowTransitionService.transition(
                any(), any(), anyString(), any(), anyString(), isNull()))
                .thenThrow(new RuntimeException("sensitive secret token leaked into msg"));

        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.UNEXPECTED_FAILURE);
        ArgumentCaptor<String> payload = captureAuditPayload();
        assertThat(payload.getValue())
                .contains("\"outcome\":\"UNEXPECTED_FAILURE\"")
                .contains("\"actionCode\":\"START_PROCESSING\"")
                .contains("\"targetStatusCode\":\"PROCESSING\"");
        // Exception message hech qachon audit payload'ga kirmasligi shart.
        assertThat(payload.getValue())
                .doesNotContain("sensitive secret token leaked into msg")
                .doesNotContain("secret")
                .doesNotContain("RuntimeException");
    }

    // ===========================================================
    // Fail-soft contract
    // ===========================================================

    @Test
    void auditWriteFailureDoesNotChangeOutcomeAndAcknowledgementStillInvoked() {
        stubResolvedActorTenantAndMembership();
        doThrow(new AccessDeniedException("denied"))
                .when(operationalAuthorizationService)
                .authorizeTransition(TENANT_ID, APP_USER_ID);
        doThrow(new RuntimeException("simulated audit persistence failure"))
                .when(auditService).recordEventInNewTransaction(
                        any(), anyString(), any(), anyString(), any(), anyString(), any(), any());

        TelegramCallbackActionExecutionService.ExecutionOutcome outcome =
                executionService.execute(cb(), WORK_ITEM_ID, "START_PROCESSING");

        // Outcome o'zgarmaydi va exception controller'ga propagate
        // qilinmaydi.
        assertThat(outcome)
                .isEqualTo(TelegramCallbackActionExecutionService.ExecutionOutcome.PERMISSION_DENIED);
        // Audit chaqirildi (va exception tashladi).
        verify(auditService, times(1)).recordEventInNewTransaction(
                any(), anyString(), any(), anyString(), any(), anyString(), any(), any());
        // Acknowledgement audit muvaffaqiyatidan qat'iy nazar yuborilishi shart.
        verify(acknowledgementService, times(1))
                .acknowledge(eq("cb-audit-id"),
                        eq("You do not have permission to change this work item."));
    }
}
