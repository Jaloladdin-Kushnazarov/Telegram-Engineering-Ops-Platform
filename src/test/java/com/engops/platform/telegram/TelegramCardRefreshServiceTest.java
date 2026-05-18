package com.engops.platform.telegram;

import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.TelegramChatBinding;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 177 — {@link TelegramCardRefreshService} unit testlari.
 *
 * <p>Service'ning composition + fail-soft kontraktini isbotlaydi.
 * Production caller mavjud emas — bu testlar primitiv'larning birga
 * to'g'ri ishlashini tasdiqlaydi.</p>
 */
class TelegramCardRefreshServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORK_ITEM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CHAT_BINDING_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final long CHAT_ID = -1001234567890L;
    private static final Long MESSAGE_ID = 555L;

    private final TelegramDeliveryAttemptHistoryReadAccess historyReadAccess =
            mock(TelegramDeliveryAttemptHistoryReadAccess.class);
    private final TenantConfigQueryService tenantConfigQueryService =
            mock(TenantConfigQueryService.class);
    private final TelegramOutboundGateway gateway = mock(TelegramOutboundGateway.class);

    private final TelegramCardRefreshService service = new TelegramCardRefreshService(
            historyReadAccess, tenantConfigQueryService, gateway);

    private TelegramDeliveryAttempt deliveredAttempt(Long externalMessageId, UUID chatBindingId) {
        // reconstruct factory adapter pattern bilan bir xil — testlar uchun valid attempt.
        return TelegramDeliveryAttempt.reconstruct(
                UUID.randomUUID(),
                Instant.parse("2026-03-18T10:00:00Z"),
                TENANT_ID,
                WORK_ITEM_ID,
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                chatBindingId,
                42L,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                externalMessageId,
                null, null);
    }

    private TelegramChatBinding chatBindingWithId(long chatId) {
        TelegramChatBinding binding = mock(TelegramChatBinding.class);
        // Eager stubbing to ensure no UnfinishedStubbing leaks into the
        // enclosing when().thenReturn() chain.
        org.mockito.Mockito.doReturn(chatId).when(binding).getChatId();
        return binding;
    }

    // ---- input short-circuit ----

    @Test
    void nullTenantIdReturnsRejectedAndDoesNotInvokeGateway() {
        TelegramEditMessageTextResult result =
                service.refresh(null, WORK_ITEM_ID, "text", null);

        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.REJECTED);
        verifyNoInteractions(historyReadAccess, tenantConfigQueryService, gateway);
    }

    @Test
    void nullWorkItemIdReturnsRejectedAndDoesNotInvokeGateway() {
        TelegramEditMessageTextResult result =
                service.refresh(TENANT_ID, null, "text", null);

        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.REJECTED);
        verifyNoInteractions(historyReadAccess, tenantConfigQueryService, gateway);
    }

    @Test
    void blankTextReturnsRejectedAndDoesNotInvokeGateway() {
        TelegramEditMessageTextResult result =
                service.refresh(TENANT_ID, WORK_ITEM_ID, "  ", null);

        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.REJECTED);
        verifyNoInteractions(historyReadAccess, tenantConfigQueryService, gateway);
    }

    // ---- read access path ----

    @Test
    void noPriorDeliveredSendReturnsRejectedAndDoesNotInvokeGateway() {
        when(historyReadAccess.findLatestDeliveredSendMessage(TENANT_ID, WORK_ITEM_ID))
                .thenReturn(Optional.empty());

        TelegramEditMessageTextResult result =
                service.refresh(TENANT_ID, WORK_ITEM_ID, "text", null);

        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.REJECTED);
        verifyNoInteractions(tenantConfigQueryService, gateway);
    }

    @Test
    void priorAttemptWithNullExternalMessageIdReturnsRejected() {
        when(historyReadAccess.findLatestDeliveredSendMessage(TENANT_ID, WORK_ITEM_ID))
                .thenReturn(Optional.of(deliveredAttempt(null, CHAT_BINDING_ID)));

        TelegramEditMessageTextResult result =
                service.refresh(TENANT_ID, WORK_ITEM_ID, "text", null);

        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.REJECTED);
        verifyNoInteractions(tenantConfigQueryService, gateway);
    }

    @Test
    void readAccessExceptionMapsToFailedUnknown() {
        when(historyReadAccess.findLatestDeliveredSendMessage(TENANT_ID, WORK_ITEM_ID))
                .thenThrow(new RuntimeException("db down"));

        TelegramEditMessageTextResult result =
                service.refresh(TENANT_ID, WORK_ITEM_ID, "text", null);

        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.UNKNOWN_ERROR);
        verifyNoInteractions(tenantConfigQueryService, gateway);
    }

    // ---- chat binding resolution ----

    @Test
    void chatBindingMissingReturnsRejectedAndDoesNotInvokeGateway() {
        when(historyReadAccess.findLatestDeliveredSendMessage(TENANT_ID, WORK_ITEM_ID))
                .thenReturn(Optional.of(deliveredAttempt(MESSAGE_ID, CHAT_BINDING_ID)));
        when(tenantConfigQueryService.findChatBindingById(TENANT_ID, CHAT_BINDING_ID))
                .thenReturn(Optional.empty());

        TelegramEditMessageTextResult result =
                service.refresh(TENANT_ID, WORK_ITEM_ID, "text", null);

        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.REJECTED);
        verifyNoInteractions(gateway);
    }

    @Test
    void chatBindingLookupExceptionMapsToFailedUnknown() {
        when(historyReadAccess.findLatestDeliveredSendMessage(TENANT_ID, WORK_ITEM_ID))
                .thenReturn(Optional.of(deliveredAttempt(MESSAGE_ID, CHAT_BINDING_ID)));
        when(tenantConfigQueryService.findChatBindingById(TENANT_ID, CHAT_BINDING_ID))
                .thenThrow(new RuntimeException("db down"));

        TelegramEditMessageTextResult result =
                service.refresh(TENANT_ID, WORK_ITEM_ID, "text", null);

        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.UNKNOWN_ERROR);
        verifyNoInteractions(gateway);
    }

    // ---- gateway forwarding ----

    @Test
    void happyPathForwardsToGatewayWithResolvedChatIdAndMessageId() {
        when(historyReadAccess.findLatestDeliveredSendMessage(TENANT_ID, WORK_ITEM_ID))
                .thenReturn(Optional.of(deliveredAttempt(MESSAGE_ID, CHAT_BINDING_ID)));
        TelegramChatBinding _binding = chatBindingWithId(CHAT_ID);
        when(tenantConfigQueryService.findChatBindingById(TENANT_ID, CHAT_BINDING_ID))
                .thenReturn(Optional.of(_binding));
        when(gateway.editMessageText(any(TelegramEditMessageTextRequest.class)))
                .thenAnswer(invocation -> {
                    TelegramEditMessageTextRequest req = invocation.getArgument(0);
                    assertThat(req.chatId()).isEqualTo(CHAT_ID);
                    assertThat(req.messageId()).isEqualTo(MESSAGE_ID);
                    assertThat(req.text()).isEqualTo("Bug | BUG-1\nStatus: FIXED");
                    assertThat(req.keyboard()).isEmpty();
                    return TelegramEditMessageTextResult.success(MESSAGE_ID);
                });

        TelegramEditMessageTextResult result = service.refresh(
                TENANT_ID, WORK_ITEM_ID, "Bug | BUG-1\nStatus: FIXED", null);

        assertThat(result.isSuccess()).isTrue();
        verify(gateway).editMessageText(any(TelegramEditMessageTextRequest.class));
    }

    @Test
    void gatewayRejectedResultPropagatedAsIs() {
        when(historyReadAccess.findLatestDeliveredSendMessage(TENANT_ID, WORK_ITEM_ID))
                .thenReturn(Optional.of(deliveredAttempt(MESSAGE_ID, CHAT_BINDING_ID)));
        TelegramChatBinding _binding = chatBindingWithId(CHAT_ID);
        when(tenantConfigQueryService.findChatBindingById(TENANT_ID, CHAT_BINDING_ID))
                .thenReturn(Optional.of(_binding));
        when(gateway.editMessageText(any(TelegramEditMessageTextRequest.class)))
                .thenReturn(TelegramEditMessageTextResult.rejected(
                        TelegramGatewayError.INVALID_REQUEST, "stale"));

        TelegramEditMessageTextResult result = service.refresh(
                TENANT_ID, WORK_ITEM_ID, "text", null);

        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.REJECTED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.INVALID_REQUEST);
    }

    @Test
    void gatewayFailedResultPropagatedAsIs() {
        when(historyReadAccess.findLatestDeliveredSendMessage(TENANT_ID, WORK_ITEM_ID))
                .thenReturn(Optional.of(deliveredAttempt(MESSAGE_ID, CHAT_BINDING_ID)));
        TelegramChatBinding _binding = chatBindingWithId(CHAT_ID);
        when(tenantConfigQueryService.findChatBindingById(TENANT_ID, CHAT_BINDING_ID))
                .thenReturn(Optional.of(_binding));
        when(gateway.editMessageText(any(TelegramEditMessageTextRequest.class)))
                .thenReturn(TelegramEditMessageTextResult.failed(
                        TelegramGatewayError.NETWORK_ERROR, "timeout"));

        TelegramEditMessageTextResult result = service.refresh(
                TENANT_ID, WORK_ITEM_ID, "text", null);

        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.NETWORK_ERROR);
    }

    @Test
    void gatewayRuntimeExceptionMapsToFailedUnknown() {
        when(historyReadAccess.findLatestDeliveredSendMessage(TENANT_ID, WORK_ITEM_ID))
                .thenReturn(Optional.of(deliveredAttempt(MESSAGE_ID, CHAT_BINDING_ID)));
        TelegramChatBinding _binding = chatBindingWithId(CHAT_ID);
        when(tenantConfigQueryService.findChatBindingById(TENANT_ID, CHAT_BINDING_ID))
                .thenReturn(Optional.of(_binding));
        when(gateway.editMessageText(any(TelegramEditMessageTextRequest.class)))
                .thenThrow(new RuntimeException("boom"));

        TelegramEditMessageTextResult result = service.refresh(
                TENANT_ID, WORK_ITEM_ID, "text", null);

        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.UNKNOWN_ERROR);
    }

    @Test
    void gatewayNullResultMapsToFailedUnknown() {
        when(historyReadAccess.findLatestDeliveredSendMessage(TENANT_ID, WORK_ITEM_ID))
                .thenReturn(Optional.of(deliveredAttempt(MESSAGE_ID, CHAT_BINDING_ID)));
        TelegramChatBinding _binding = chatBindingWithId(CHAT_ID);
        when(tenantConfigQueryService.findChatBindingById(TENANT_ID, CHAT_BINDING_ID))
                .thenReturn(Optional.of(_binding));
        when(gateway.editMessageText(any(TelegramEditMessageTextRequest.class)))
                .thenReturn(null);

        TelegramEditMessageTextResult result = service.refresh(
                TENANT_ID, WORK_ITEM_ID, "text", null);

        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.UNKNOWN_ERROR);
    }

    @Test
    void keyboardForwardedToGateway() {
        when(historyReadAccess.findLatestDeliveredSendMessage(TENANT_ID, WORK_ITEM_ID))
                .thenReturn(Optional.of(deliveredAttempt(MESSAGE_ID, CHAT_BINDING_ID)));
        TelegramChatBinding _binding = chatBindingWithId(CHAT_ID);
        when(tenantConfigQueryService.findChatBindingById(TENANT_ID, CHAT_BINDING_ID))
                .thenReturn(Optional.of(_binding));

        var row = new TelegramInlineKeyboardRow(List.of(
                new TelegramInlineKeyboardButton("Mark Fixed", "uuid:MARK_FIXED")));

        when(gateway.editMessageText(any(TelegramEditMessageTextRequest.class)))
                .thenAnswer(invocation -> {
                    TelegramEditMessageTextRequest req = invocation.getArgument(0);
                    assertThat(req.hasKeyboard()).isTrue();
                    assertThat(req.keyboard()).hasSize(1);
                    return TelegramEditMessageTextResult.success(MESSAGE_ID);
                });

        TelegramEditMessageTextResult result = service.refresh(
                TENANT_ID, WORK_ITEM_ID, "text", List.of(row));

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void noGatewayCallWhenNoActiveCard() {
        when(historyReadAccess.findLatestDeliveredSendMessage(TENANT_ID, WORK_ITEM_ID))
                .thenReturn(Optional.empty());

        service.refresh(TENANT_ID, WORK_ITEM_ID, "text", null);

        verify(gateway, never()).editMessageText(any());
    }
}
