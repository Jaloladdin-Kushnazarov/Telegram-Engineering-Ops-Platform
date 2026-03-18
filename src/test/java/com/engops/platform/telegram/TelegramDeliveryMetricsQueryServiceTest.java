package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * TelegramDeliveryMetricsQueryService testlari.
 *
 * Tekshiruvlar:
 * - empty snapshot qaytaradi agar attempt topilmasa
 * - happy-path: DELIVERED attempt -> success snapshot
 * - REJECTED attempt -> rejected snapshot
 * - FAILED attempt -> failed snapshot
 * - tenant/workItem scope to'g'ri uzatiladi
 * - assembler'ga delegatsiya qiladi, o'zi hisoblash qilmaydi
 * - null query rad etiladi
 */
class TelegramDeliveryMetricsQueryServiceTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-03-18T10:00:00Z");
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID WORK_ITEM_ID = UUID.randomUUID();

    private final TelegramDeliveryMetricsReadAccess readAccess =
            mock(TelegramDeliveryMetricsReadAccess.class);
    private final TelegramDeliveryMetricsAssembler assembler =
            new TelegramDeliveryMetricsAssembler();
    private final TelegramDeliveryMetricsQueryService queryService =
            new TelegramDeliveryMetricsQueryService(readAccess, assembler);

    @Test
    void emptySnapshotReturnedWhenNoAttemptFound() {
        when(readAccess.findLatestAttempt(TENANT_ID, WORK_ITEM_ID))
                .thenReturn(Optional.empty());

        TelegramDeliveryMetricsQuery query = new TelegramDeliveryMetricsQuery(TENANT_ID, WORK_ITEM_ID);
        TelegramDeliveryMetricsSnapshot snapshot = queryService.getSnapshot(query);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.isEmpty()).isTrue();
        assertThat(snapshot.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(snapshot.getWorkItemId()).isEqualTo(WORK_ITEM_ID);
        assertThat(snapshot.isSuccess()).isFalse();
        assertThat(snapshot.isRejected()).isFalse();
        assertThat(snapshot.isFailed()).isFalse();
    }

    @Test
    void deliveredAttemptMappedToSnapshot() {
        TelegramDeliveryCommand command = buildCommand(TENANT_ID, WORK_ITEM_ID);
        TelegramDeliveryResult result = TelegramDeliveryResult.success(command, 77001L);
        TelegramDeliveryAttempt attempt = TelegramDeliveryAttempt.of(command, result, FIXED_TIME);

        when(readAccess.findLatestAttempt(TENANT_ID, WORK_ITEM_ID))
                .thenReturn(Optional.of(attempt));

        TelegramDeliveryMetricsQuery query = new TelegramDeliveryMetricsQuery(TENANT_ID, WORK_ITEM_ID);
        TelegramDeliveryMetricsSnapshot snapshot = queryService.getSnapshot(query);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.isEmpty()).isFalse();
        assertThat(snapshot.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(snapshot.getWorkItemId()).isEqualTo(WORK_ITEM_ID);
        assertThat(snapshot.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED);
        assertThat(snapshot.isSuccess()).isTrue();
        assertThat(snapshot.hasExternalMessageId()).isTrue();
    }

    @Test
    void rejectedAttemptMappedToSnapshot() {
        TelegramDeliveryCommand command = buildCommand(TENANT_ID, WORK_ITEM_ID);
        TelegramDeliveryResult result = TelegramDeliveryResult.rejected(
                command, "INVALID_CHAT", "Chat not found");
        TelegramDeliveryAttempt attempt = TelegramDeliveryAttempt.of(command, result, FIXED_TIME);

        when(readAccess.findLatestAttempt(TENANT_ID, WORK_ITEM_ID))
                .thenReturn(Optional.of(attempt));

        TelegramDeliveryMetricsQuery query = new TelegramDeliveryMetricsQuery(TENANT_ID, WORK_ITEM_ID);
        TelegramDeliveryMetricsSnapshot snapshot = queryService.getSnapshot(query);

        assertThat(snapshot.isEmpty()).isFalse();
        assertThat(snapshot.isRejected()).isTrue();
        assertThat(snapshot.getFailureCode()).isEqualTo("INVALID_CHAT");
    }

    @Test
    void failedAttemptMappedToSnapshot() {
        TelegramDeliveryCommand command = buildCommand(TENANT_ID, WORK_ITEM_ID);
        TelegramDeliveryResult result = TelegramDeliveryResult.failed(
                command, "NETWORK_ERROR", "Connection timeout");
        TelegramDeliveryAttempt attempt = TelegramDeliveryAttempt.of(command, result, FIXED_TIME);

        when(readAccess.findLatestAttempt(TENANT_ID, WORK_ITEM_ID))
                .thenReturn(Optional.of(attempt));

        TelegramDeliveryMetricsQuery query = new TelegramDeliveryMetricsQuery(TENANT_ID, WORK_ITEM_ID);
        TelegramDeliveryMetricsSnapshot snapshot = queryService.getSnapshot(query);

        assertThat(snapshot.isEmpty()).isFalse();
        assertThat(snapshot.isFailed()).isTrue();
        assertThat(snapshot.isSuccess()).isFalse();
        assertThat(snapshot.isRejected()).isFalse();
        assertThat(snapshot.getFailureCode()).isEqualTo("NETWORK_ERROR");
    }

    @Test
    void scopePassedCorrectlyToReadAccess() {
        when(readAccess.findLatestAttempt(any(), any())).thenReturn(Optional.empty());

        TelegramDeliveryMetricsQuery query = new TelegramDeliveryMetricsQuery(TENANT_ID, WORK_ITEM_ID);
        queryService.getSnapshot(query);

        verify(readAccess).findLatestAttempt(TENANT_ID, WORK_ITEM_ID);
        verifyNoMoreInteractions(readAccess);
    }

    @Test
    void delegatesToAssemblerInsteadOfDuplicatingLogic() {
        TelegramDeliveryMetricsAssembler spyAssembler = spy(new TelegramDeliveryMetricsAssembler());
        TelegramDeliveryMetricsQueryService serviceWithSpy =
                new TelegramDeliveryMetricsQueryService(readAccess, spyAssembler);

        TelegramDeliveryCommand command = buildCommand(TENANT_ID, WORK_ITEM_ID);
        TelegramDeliveryResult result = TelegramDeliveryResult.success(command, 55001L);
        TelegramDeliveryAttempt attempt = TelegramDeliveryAttempt.of(command, result, FIXED_TIME);

        when(readAccess.findLatestAttempt(TENANT_ID, WORK_ITEM_ID))
                .thenReturn(Optional.of(attempt));

        TelegramDeliveryMetricsQuery query = new TelegramDeliveryMetricsQuery(TENANT_ID, WORK_ITEM_ID);
        serviceWithSpy.getSnapshot(query);

        verify(spyAssembler).assemble(attempt);
    }

    @Test
    void nullQueryRejected() {
        assertThatThrownBy(() -> queryService.getSnapshot(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query null");
    }

    private TelegramDeliveryCommand buildCommand(UUID tenantId, UUID workItemId) {
        return new TelegramDeliveryCommand(
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                tenantId, workItemId,
                UUID.randomUUID(), 42L,
                "Bug | BUG-1\n[BUG-1] Test\nStatus: BUGS",
                List.of());
    }
}
