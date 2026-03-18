package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TelegramDeliveryMetricsFacade testlari.
 *
 * Tekshiruvlar:
 * - happy-path: queryService'ga delegatsiya va snapshot qaytarish
 * - empty snapshot qaytaradi agar ma'lumot yo'q bo'lsa
 * - tenantId va workItemId to'g'ri uzatiladi
 * - null tenantId rad etiladi
 * - null workItemId rad etiladi
 */
class TelegramDeliveryMetricsFacadeTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID WORK_ITEM_ID = UUID.randomUUID();

    private final TelegramDeliveryMetricsQueryService queryService =
            mock(TelegramDeliveryMetricsQueryService.class);
    private final TelegramDeliveryMetricsFacade facade =
            new TelegramDeliveryMetricsFacade(queryService);

    @Test
    void delegatesToQueryServiceAndReturnsSnapshot() {
        TelegramDeliveryMetricsSnapshot expectedSnapshot = TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, WORK_ITEM_ID, TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                null, true);

        when(queryService.getSnapshot(any(TelegramDeliveryMetricsQuery.class)))
                .thenReturn(expectedSnapshot);

        TelegramDeliveryMetricsSnapshot result = facade.getDeliveryMetrics(TENANT_ID, WORK_ITEM_ID);

        assertThat(result).isSameAs(expectedSnapshot);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isEmpty()).isFalse();
    }

    @Test
    void emptySnapshotReturnedWhenNoData() {
        TelegramDeliveryMetricsSnapshot emptySnapshot =
                TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WORK_ITEM_ID);

        when(queryService.getSnapshot(any(TelegramDeliveryMetricsQuery.class)))
                .thenReturn(emptySnapshot);

        TelegramDeliveryMetricsSnapshot result = facade.getDeliveryMetrics(TENANT_ID, WORK_ITEM_ID);

        assertThat(result).isSameAs(emptySnapshot);
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void scopePassedCorrectlyToQueryService() {
        when(queryService.getSnapshot(any(TelegramDeliveryMetricsQuery.class)))
                .thenReturn(TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WORK_ITEM_ID));

        facade.getDeliveryMetrics(TENANT_ID, WORK_ITEM_ID);

        ArgumentCaptor<TelegramDeliveryMetricsQuery> captor =
                ArgumentCaptor.forClass(TelegramDeliveryMetricsQuery.class);
        verify(queryService).getSnapshot(captor.capture());

        TelegramDeliveryMetricsQuery capturedQuery = captor.getValue();
        assertThat(capturedQuery.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(capturedQuery.getWorkItemId()).isEqualTo(WORK_ITEM_ID);
        verifyNoMoreInteractions(queryService);
    }

    @Test
    void nullTenantIdRejected() {
        assertThatThrownBy(() -> facade.getDeliveryMetrics(null, WORK_ITEM_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId null");
    }

    @Test
    void nullWorkItemIdRejected() {
        assertThatThrownBy(() -> facade.getDeliveryMetrics(TENANT_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemId null");
    }
}
