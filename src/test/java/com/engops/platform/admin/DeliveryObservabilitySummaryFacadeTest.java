package com.engops.platform.admin;

import com.engops.platform.telegram.TelegramDeliveryMetricsFacade;
import com.engops.platform.telegram.TelegramDeliveryMetricsSnapshot;
import com.engops.platform.telegram.TelegramDeliveryOperation;
import com.engops.platform.telegram.TelegramDeliveryResult;
import com.engops.platform.workitem.WorkItemQueryService;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * DeliveryObservabilitySummaryFacade testlari.
 *
 * Tekshiruvlar:
 * - tenant-scoped work item list + per-item metrics composition
 * - limit to'g'ri qo'llanadi
 * - bo'sh ro'yxat valid natija
 * - null tenantId rad etiladi
 * - invalid limit rad etiladi
 */
class DeliveryObservabilitySummaryFacadeTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID WORK_ITEM_ID_1 = UUID.randomUUID();
    private static final UUID WORK_ITEM_ID_2 = UUID.randomUUID();

    private final WorkItemQueryService workItemQueryService =
            mock(WorkItemQueryService.class);
    private final TelegramDeliveryMetricsFacade metricsFacade =
            mock(TelegramDeliveryMetricsFacade.class);
    private final DeliveryObservabilitySummaryFacade facade =
            new DeliveryObservabilitySummaryFacade(workItemQueryService, metricsFacade);

    @Test
    void returnsComposedSummaryList() {
        WorkItem wi1 = buildWorkItem(WORK_ITEM_ID_1, "BUG-1", "Login xato");
        WorkItem wi2 = buildWorkItem(WORK_ITEM_ID_2, "BUG-2", "Timeout xato");

        TelegramDeliveryMetricsSnapshot snap1 = TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, WORK_ITEM_ID_1,
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED, null, true);
        TelegramDeliveryMetricsSnapshot snap2 = TelegramDeliveryMetricsSnapshot.empty(
                TENANT_ID, WORK_ITEM_ID_2);

        when(workItemQueryService.listActiveByTenant(TENANT_ID, 20))
                .thenReturn(List.of(wi1, wi2));
        when(metricsFacade.getDeliveryMetrics(TENANT_ID, WORK_ITEM_ID_1)).thenReturn(snap1);
        when(metricsFacade.getDeliveryMetrics(TENANT_ID, WORK_ITEM_ID_2)).thenReturn(snap2);

        List<DeliveryObservabilitySummaryItem> result = facade.getSummaryList(TENANT_ID, 20);

        assertThat(result).hasSize(2);

        assertThat(result.get(0).workItemId()).isEqualTo(WORK_ITEM_ID_1);
        assertThat(result.get(0).workItemCode()).isEqualTo("BUG-1");
        assertThat(result.get(0).title()).isEqualTo("Login xato");
        assertThat(result.get(0).latestMetrics().isSuccess()).isTrue();

        assertThat(result.get(1).workItemId()).isEqualTo(WORK_ITEM_ID_2);
        assertThat(result.get(1).latestMetrics().isEmpty()).isTrue();

        verify(workItemQueryService).listActiveByTenant(TENANT_ID, 20);
        verify(metricsFacade).getDeliveryMetrics(TENANT_ID, WORK_ITEM_ID_1);
        verify(metricsFacade).getDeliveryMetrics(TENANT_ID, WORK_ITEM_ID_2);
    }

    @Test
    void respectsLimit() {
        WorkItem wi1 = buildWorkItem(WORK_ITEM_ID_1, "BUG-1", "First");
        WorkItem wi2 = buildWorkItem(WORK_ITEM_ID_2, "BUG-2", "Second");

        TelegramDeliveryMetricsSnapshot snap1 = TelegramDeliveryMetricsSnapshot.empty(
                TENANT_ID, WORK_ITEM_ID_1);

        when(workItemQueryService.listActiveByTenant(TENANT_ID, 1))
                .thenReturn(List.of(wi1));
        when(metricsFacade.getDeliveryMetrics(TENANT_ID, WORK_ITEM_ID_1)).thenReturn(snap1);

        List<DeliveryObservabilitySummaryItem> result = facade.getSummaryList(TENANT_ID, 1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).workItemCode()).isEqualTo("BUG-1");

        verify(workItemQueryService).listActiveByTenant(TENANT_ID, 1);
        verify(metricsFacade).getDeliveryMetrics(TENANT_ID, WORK_ITEM_ID_1);
        verify(metricsFacade, never()).getDeliveryMetrics(TENANT_ID, WORK_ITEM_ID_2);
    }

    @Test
    void emptyListWhenNoActiveWorkItems() {
        when(workItemQueryService.listActiveByTenant(TENANT_ID, 20)).thenReturn(List.of());

        List<DeliveryObservabilitySummaryItem> result = facade.getSummaryList(TENANT_ID, 20);

        assertThat(result).isEmpty();
        verifyNoInteractions(metricsFacade);
    }

    @Test
    void nullTenantIdRejected() {
        assertThatThrownBy(() -> facade.getSummaryList(null, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId null");

        verifyNoInteractions(workItemQueryService, metricsFacade);
    }

    @Test
    void limitZeroRejected() {
        assertThatThrownBy(() -> facade.getSummaryList(TENANT_ID, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");

        verifyNoInteractions(workItemQueryService);
    }

    @Test
    void limitAboveMaxRejected() {
        assertThatThrownBy(() -> facade.getSummaryList(
                TENANT_ID, DeliveryObservabilitySummaryFacade.MAX_LIMIT + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");

        verifyNoInteractions(workItemQueryService);
    }

    private WorkItem buildWorkItem(UUID workItemId, String code, String title) {
        WorkItem workItem = new WorkItem(
                TENANT_ID, code, WorkItemType.BUG,
                UUID.randomUUID(), title, "BUGS", null);
        WorkItem spied = spy(workItem);
        doReturn(workItemId).when(spied).getId();
        return spied;
    }
}
