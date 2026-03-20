package com.engops.platform.admin;

import com.engops.platform.telegram.TelegramDeliveryMetricsFacade;
import com.engops.platform.telegram.TelegramDeliveryMetricsSnapshot;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * DeliveryObservabilitySummaryByStatusFacade unit testlari.
 *
 * Tekshiruvlar:
 * - primary list uchun individual delivery metrics olinadi (tenant-wide top N emas)
 * - primary ordering saqlanadi
 * - empty primary short-circuit ishlaydi
 * - invalid input propagatsiya qiladi
 * - false inconsistency gap yopilgan
 */
class DeliveryObservabilitySummaryByStatusFacadeTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WI_ID_1 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WI_ID_2 = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID WI_ID_3 = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private final WorkItemSummaryByStatusFacade statusFacade =
            mock(WorkItemSummaryByStatusFacade.class);
    private final TelegramDeliveryMetricsFacade metricsFacade =
            mock(TelegramDeliveryMetricsFacade.class);
    private final DeliveryObservabilitySummaryByStatusFacade facade =
            new DeliveryObservabilitySummaryByStatusFacade(statusFacade, metricsFacade);

    @Test
    void returnsDeliverySummaryForEachPrimaryItem() {
        var wi1 = workItemSummary(WI_ID_1, "BUG-1");
        var snapshot1 = TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WI_ID_1);

        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 20)).thenReturn(List.of(wi1));
        when(metricsFacade.getDeliveryMetrics(TENANT_ID, WI_ID_1)).thenReturn(snapshot1);

        var result = facade.getSummaryList(TENANT_ID, "BUGS", 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).workItemId()).isEqualTo(WI_ID_1);
        assertThat(result.get(0).workItemCode()).isEqualTo("BUG-1");
        assertThat(result.get(0).latestMetrics()).isSameAs(snapshot1);

        verify(statusFacade).getSummaryList(TENANT_ID, "BUGS", 20);
        verify(metricsFacade).getDeliveryMetrics(TENANT_ID, WI_ID_1);
        verifyNoMoreInteractions(statusFacade, metricsFacade);
    }

    @Test
    void primaryOrderingPreserved() {
        var wi1 = workItemSummary(WI_ID_1, "BUG-1");
        var wi2 = workItemSummary(WI_ID_2, "BUG-2");
        var snapshot1 = TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WI_ID_1);
        var snapshot2 = TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WI_ID_2);

        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 20))
                .thenReturn(List.of(wi1, wi2));
        when(metricsFacade.getDeliveryMetrics(TENANT_ID, WI_ID_1)).thenReturn(snapshot1);
        when(metricsFacade.getDeliveryMetrics(TENANT_ID, WI_ID_2)).thenReturn(snapshot2);

        var result = facade.getSummaryList(TENANT_ID, "BUGS", 20);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).workItemId()).isEqualTo(WI_ID_1);
        assertThat(result.get(1).workItemId()).isEqualTo(WI_ID_2);
    }

    @Test
    void noFalseInconsistencyWhenPrimaryItemsOutsideTenantWideTopN() {
        // MUHIM: bu test oldingi semantic gap'ni isbotlaydi.
        // Senariy: 3 ta status-filtered work item bor.
        // Oldingi implementatsiya tenant-wide top N dan foydalanardi,
        // va agar shu 3 item tenant-wide top N ga kirmasa, false IllegalStateException berardi.
        // Yangi implementatsiya har bir primary item uchun individual metrics oladi,
        // shuning uchun bu muammo yo'q.

        var wi1 = workItemSummary(WI_ID_1, "BUG-1");
        var wi2 = workItemSummary(WI_ID_2, "BUG-2");
        var wi3 = workItemSummary(WI_ID_3, "BUG-3");

        var snapshot1 = TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WI_ID_1);
        var snapshot2 = TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WI_ID_2);
        var snapshot3 = TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WI_ID_3);

        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 20))
                .thenReturn(List.of(wi1, wi2, wi3));
        when(metricsFacade.getDeliveryMetrics(TENANT_ID, WI_ID_1)).thenReturn(snapshot1);
        when(metricsFacade.getDeliveryMetrics(TENANT_ID, WI_ID_2)).thenReturn(snapshot2);
        when(metricsFacade.getDeliveryMetrics(TENANT_ID, WI_ID_3)).thenReturn(snapshot3);

        // Hech qanday IllegalStateException bo'lmasligi kerak
        var result = facade.getSummaryList(TENANT_ID, "BUGS", 20);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).workItemId()).isEqualTo(WI_ID_1);
        assertThat(result.get(1).workItemId()).isEqualTo(WI_ID_2);
        assertThat(result.get(2).workItemId()).isEqualTo(WI_ID_3);
    }

    @Test
    void emptyListWhenPrimaryWorkItemSummaryEmpty() {
        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 20)).thenReturn(List.of());

        var result = facade.getSummaryList(TENANT_ID, "BUGS", 20);

        assertThat(result).isEmpty();
        verifyNoInteractions(metricsFacade);
    }

    @Test
    void propagatesInvalidLimit() {
        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 0))
                .thenThrow(new IllegalArgumentException(
                        "limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        assertThatThrownBy(() -> facade.getSummaryList(TENANT_ID, "BUGS", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");

        verifyNoInteractions(metricsFacade);
    }

    @Test
    void propagatesNullTenantId() {
        when(statusFacade.getSummaryList(null, "BUGS", 20))
                .thenThrow(new IllegalArgumentException("tenantId null bo'lishi mumkin emas"));

        assertThatThrownBy(() -> facade.getSummaryList(null, "BUGS", 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(metricsFacade);
    }

    @Test
    void propagatesBlankStatusCode() {
        when(statusFacade.getSummaryList(TENANT_ID, "", 20))
                .thenThrow(new IllegalArgumentException(
                        "statusCode null yoki bo'sh bo'lishi mumkin emas"));

        assertThatThrownBy(() -> facade.getSummaryList(TENANT_ID, "", 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("statusCode");

        verifyNoInteractions(metricsFacade);
    }

    @Test
    void verifyDelegationArgumentsOnNonEmptyPath() {
        var wi = workItemSummary(WI_ID_1, "BUG-1");
        var snapshot = TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WI_ID_1);

        when(statusFacade.getSummaryList(TENANT_ID, "TESTING", 10)).thenReturn(List.of(wi));
        when(metricsFacade.getDeliveryMetrics(TENANT_ID, WI_ID_1)).thenReturn(snapshot);

        facade.getSummaryList(TENANT_ID, "TESTING", 10);

        verify(statusFacade).getSummaryList(TENANT_ID, "TESTING", 10);
        verify(metricsFacade).getDeliveryMetrics(TENANT_ID, WI_ID_1);
        verifyNoMoreInteractions(statusFacade, metricsFacade);
    }

    // ========== Helpers ==========

    private WorkItemSummaryItem workItemSummary(UUID id, String code) {
        return new WorkItemSummaryItem(
                id, code, "Title",
                WorkItemType.BUG, "BUGS",
                null, null, null,
                Instant.parse("2026-03-18T10:00:00Z"),
                null, null, 0, false);
    }
}
