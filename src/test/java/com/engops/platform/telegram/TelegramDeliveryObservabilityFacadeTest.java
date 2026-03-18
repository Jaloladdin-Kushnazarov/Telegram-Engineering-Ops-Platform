package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * TelegramDeliveryObservabilityFacade testlari.
 *
 * Tekshiruvlar:
 * - ikkala facade'ga delegatsiya qiladi
 * - tenantId/workItemId/historyLimit to'g'ri uzatiladi
 * - composed view to'g'ri qaytaradi
 * - null tenantId rad etiladi
 * - null workItemId rad etiladi
 * - historyLimit validatsiyasi historyFacade'ga delegatsiya qilinadi
 * - empty snapshot + empty history = valid view
 */
class TelegramDeliveryObservabilityFacadeTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID WORK_ITEM_ID = UUID.randomUUID();
    private static final Instant FIXED_TIME = Instant.parse("2026-03-18T10:00:00Z");

    private final TelegramDeliveryMetricsFacade metricsFacade =
            mock(TelegramDeliveryMetricsFacade.class);
    private final TelegramDeliveryAttemptHistoryFacade historyFacade =
            mock(TelegramDeliveryAttemptHistoryFacade.class);
    private final TelegramDeliveryObservabilityFacade facade =
            new TelegramDeliveryObservabilityFacade(metricsFacade, historyFacade);

    @Test
    void delegatesToBothFacadesAndReturnsCombinedView() {
        TelegramDeliveryMetricsSnapshot snapshot = TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, WORK_ITEM_ID, TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED, null, true);
        List<TelegramDeliveryAttempt> attempts = List.of(buildAttempt());

        when(metricsFacade.getDeliveryMetrics(TENANT_ID, WORK_ITEM_ID)).thenReturn(snapshot);
        when(historyFacade.getRecentAttempts(TENANT_ID, WORK_ITEM_ID, 10)).thenReturn(attempts);

        TelegramDeliveryObservabilityView view =
                facade.getObservabilityView(TENANT_ID, WORK_ITEM_ID, 10);

        assertThat(view.latestMetrics()).isSameAs(snapshot);
        assertThat(view.recentAttempts()).hasSize(1);
        verify(metricsFacade).getDeliveryMetrics(TENANT_ID, WORK_ITEM_ID);
        verify(historyFacade).getRecentAttempts(TENANT_ID, WORK_ITEM_ID, 10);
        verifyNoMoreInteractions(metricsFacade, historyFacade);
    }

    @Test
    void passesHistoryLimitCorrectly() {
        TelegramDeliveryMetricsSnapshot snapshot =
                TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WORK_ITEM_ID);
        when(metricsFacade.getDeliveryMetrics(TENANT_ID, WORK_ITEM_ID)).thenReturn(snapshot);
        when(historyFacade.getRecentAttempts(TENANT_ID, WORK_ITEM_ID, 25)).thenReturn(List.of());

        facade.getObservabilityView(TENANT_ID, WORK_ITEM_ID, 25);

        verify(historyFacade).getRecentAttempts(TENANT_ID, WORK_ITEM_ID, 25);
    }

    @Test
    void emptySnapshotAndEmptyHistoryReturnValidView() {
        TelegramDeliveryMetricsSnapshot emptySnapshot =
                TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WORK_ITEM_ID);
        when(metricsFacade.getDeliveryMetrics(TENANT_ID, WORK_ITEM_ID)).thenReturn(emptySnapshot);
        when(historyFacade.getRecentAttempts(TENANT_ID, WORK_ITEM_ID, 5)).thenReturn(List.of());

        TelegramDeliveryObservabilityView view =
                facade.getObservabilityView(TENANT_ID, WORK_ITEM_ID, 5);

        assertThat(view.latestMetrics().isEmpty()).isTrue();
        assertThat(view.recentAttempts()).isEmpty();
    }

    @Test
    void nullTenantIdRejected() {
        assertThatThrownBy(() -> facade.getObservabilityView(null, WORK_ITEM_ID, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId null");

        verifyNoInteractions(metricsFacade, historyFacade);
    }

    @Test
    void nullWorkItemIdRejected() {
        assertThatThrownBy(() -> facade.getObservabilityView(TENANT_ID, null, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemId null");

        verifyNoInteractions(metricsFacade, historyFacade);
    }

    @Test
    void invalidHistoryLimitPropagatedFromHistoryFacade() {
        TelegramDeliveryMetricsSnapshot snapshot =
                TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WORK_ITEM_ID);
        when(metricsFacade.getDeliveryMetrics(TENANT_ID, WORK_ITEM_ID)).thenReturn(snapshot);
        when(historyFacade.getRecentAttempts(TENANT_ID, WORK_ITEM_ID, 0))
                .thenThrow(new IllegalArgumentException("limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        assertThatThrownBy(() -> facade.getObservabilityView(TENANT_ID, WORK_ITEM_ID, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void viewRecentAttemptsListIsDefensivelyCopied() {
        TelegramDeliveryMetricsSnapshot snapshot =
                TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WORK_ITEM_ID);
        TelegramDeliveryAttempt attempt = buildAttempt();
        List<TelegramDeliveryAttempt> mutableList = new java.util.ArrayList<>(List.of(attempt));

        when(metricsFacade.getDeliveryMetrics(TENANT_ID, WORK_ITEM_ID)).thenReturn(snapshot);
        when(historyFacade.getRecentAttempts(TENANT_ID, WORK_ITEM_ID, 10)).thenReturn(mutableList);

        TelegramDeliveryObservabilityView view =
                facade.getObservabilityView(TENANT_ID, WORK_ITEM_ID, 10);

        assertThat(view.recentAttempts()).hasSize(1);
        mutableList.clear();
        assertThat(view.recentAttempts()).hasSize(1);
    }

    private TelegramDeliveryAttempt buildAttempt() {
        return TelegramDeliveryAttempt.reconstruct(
                UUID.randomUUID(), FIXED_TIME,
                TENANT_ID, WORK_ITEM_ID,
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                UUID.randomUUID(), 42L,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                99001L, null, null);
    }
}
