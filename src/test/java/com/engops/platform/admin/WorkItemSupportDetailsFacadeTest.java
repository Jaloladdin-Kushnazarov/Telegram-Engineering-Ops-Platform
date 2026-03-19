package com.engops.platform.admin;

import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.telegram.TelegramDeliveryMetricsSnapshot;
import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsFacade;
import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsView;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WorkItemSupportDetailsFacade unit testlari.
 *
 * Tekshiruvlar:
 * - composed result ikkala facade natijasini o'z ichiga oladi
 * - work item not found propagatsiya qiladi
 * - invalid input propagatsiya qiladi
 * - historyLimit to'g'ri uzatiladi
 */
class WorkItemSupportDetailsFacadeTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORK_ITEM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WORKFLOW_DEF_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String WORK_ITEM_CODE = "BUG-1";

    private final WorkItemDetailsFacade workItemDetailsFacade = mock(WorkItemDetailsFacade.class);
    private final TelegramDeliveryObservabilityDetailsFacade observabilityFacade =
            mock(TelegramDeliveryObservabilityDetailsFacade.class);
    private final WorkItemSupportDetailsFacade facade =
            new WorkItemSupportDetailsFacade(workItemDetailsFacade, observabilityFacade);

    @Test
    void returnsComposedResultFromBothFacades() {
        WorkItem workItem = new WorkItem(
                TENANT_ID, WORK_ITEM_CODE, WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Login xato", "BUGS", null);

        var workItemView = new WorkItemDetailsFacade.WorkItemDetailsView(workItem, List.of());

        TelegramDeliveryMetricsSnapshot snapshot =
                TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WORK_ITEM_ID);
        var observabilityView = new TelegramDeliveryObservabilityDetailsView(
                WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                snapshot, List.of());

        when(workItemDetailsFacade.getDetails(TENANT_ID, WORK_ITEM_CODE))
                .thenReturn(workItemView);
        when(observabilityFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, 10))
                .thenReturn(observabilityView);

        WorkItemSupportDetailsFacade.WorkItemSupportDetailsView result =
                facade.getDetails(TENANT_ID, WORK_ITEM_CODE, 10);

        assertThat(result.workItemDetails()).isSameAs(workItemView);
        assertThat(result.observabilityDetails()).isSameAs(observabilityView);
    }

    @Test
    void forwardsHistoryLimitToObservabilityFacade() {
        WorkItem workItem = new WorkItem(
                TENANT_ID, WORK_ITEM_CODE, WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Login xato", "BUGS", null);

        var workItemView = new WorkItemDetailsFacade.WorkItemDetailsView(workItem, List.of());

        TelegramDeliveryMetricsSnapshot snapshot =
                TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WORK_ITEM_ID);
        var observabilityView = new TelegramDeliveryObservabilityDetailsView(
                WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                snapshot, List.of());

        when(workItemDetailsFacade.getDetails(TENANT_ID, WORK_ITEM_CODE))
                .thenReturn(workItemView);
        when(observabilityFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, 25))
                .thenReturn(observabilityView);

        WorkItemSupportDetailsFacade.WorkItemSupportDetailsView result =
                facade.getDetails(TENANT_ID, WORK_ITEM_CODE, 25);

        assertThat(result.observabilityDetails()).isSameAs(observabilityView);
    }

    @Test
    void propagatesResourceNotFoundFromWorkItemFacade() {
        when(workItemDetailsFacade.getDetails(TENANT_ID, "NONEXISTENT-99"))
                .thenThrow(new ResourceNotFoundException("WorkItem", "NONEXISTENT-99"));

        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, "NONEXISTENT-99", 10))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void propagatesIllegalArgumentFromWorkItemFacade() {
        when(workItemDetailsFacade.getDetails(TENANT_ID, ""))
                .thenThrow(new IllegalArgumentException(
                        "workItemCode null yoki bo'sh bo'lishi mumkin emas"));

        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, "", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemCode");
    }

    @Test
    void propagatesIllegalArgumentFromObservabilityFacade() {
        WorkItem workItem = new WorkItem(
                TENANT_ID, WORK_ITEM_CODE, WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Login xato", "BUGS", null);

        var workItemView = new WorkItemDetailsFacade.WorkItemDetailsView(workItem, List.of());
        when(workItemDetailsFacade.getDetails(TENANT_ID, WORK_ITEM_CODE))
                .thenReturn(workItemView);
        when(observabilityFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, 0))
                .thenThrow(new IllegalArgumentException(
                        "historyLimit 1..50 oralig'ida bo'lishi kerak"));

        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, WORK_ITEM_CODE, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("historyLimit");
    }
}
