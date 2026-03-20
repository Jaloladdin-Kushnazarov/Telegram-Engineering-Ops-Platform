package com.engops.platform.admin;

import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.telegram.TelegramDeliveryMetricsSnapshot;
import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsFacade;
import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsView;
import com.engops.platform.workitem.WorkItemQueryService;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WorkItemSupportDetailsByIdFacade unit testlari.
 *
 * Tekshiruvlar:
 * - UUID -> code resolve -> ikkala facade'ga delegation
 * - historyLimit to'g'ri uzatiladi
 * - work item topilmasa ResourceNotFoundException
 * - null tenantId / workItemId rejected
 * - invalid historyLimit propagatsiya qiladi
 */
class WorkItemSupportDetailsByIdFacadeTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORK_ITEM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WORKFLOW_DEF_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String WORK_ITEM_CODE = "BUG-1";

    private final WorkItemQueryService queryService = mock(WorkItemQueryService.class);
    private final WorkItemDetailsFacade detailsFacade = mock(WorkItemDetailsFacade.class);
    private final TelegramDeliveryObservabilityDetailsFacade observabilityFacade =
            mock(TelegramDeliveryObservabilityDetailsFacade.class);
    private final WorkItemSupportDetailsByIdFacade facade =
            new WorkItemSupportDetailsByIdFacade(queryService, detailsFacade, observabilityFacade);

    @Test
    void resolvesWorkItemIdAndDelegatesToBothFacades() {
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

        when(queryService.findByTenantAndId(TENANT_ID, workItem.getId()))
                .thenReturn(Optional.of(workItem));
        when(detailsFacade.getDetails(TENANT_ID, WORK_ITEM_CODE))
                .thenReturn(workItemView);
        when(observabilityFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, 10))
                .thenReturn(observabilityView);

        var result = facade.getDetails(TENANT_ID, workItem.getId(), 10);

        assertThat(result.workItemDetails()).isSameAs(workItemView);
        assertThat(result.observabilityDetails()).isSameAs(observabilityView);
    }

    @Test
    void forwardsHistoryLimitCorrectly() {
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

        when(queryService.findByTenantAndId(TENANT_ID, workItem.getId()))
                .thenReturn(Optional.of(workItem));
        when(detailsFacade.getDetails(TENANT_ID, WORK_ITEM_CODE))
                .thenReturn(workItemView);
        when(observabilityFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, 30))
                .thenReturn(observabilityView);

        var result = facade.getDetails(TENANT_ID, workItem.getId(), 30);

        assertThat(result.observabilityDetails()).isSameAs(observabilityView);
    }

    @Test
    void throwsResourceNotFoundWhenWorkItemMissing() {
        when(queryService.findByTenantAndId(TENANT_ID, WORK_ITEM_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, WORK_ITEM_ID, 10))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void throwsIllegalArgumentWhenTenantIdNull() {
        assertThatThrownBy(() -> facade.getDetails(null, WORK_ITEM_ID, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void throwsIllegalArgumentWhenWorkItemIdNull() {
        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, null, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemId");
    }

    @Test
    void propagatesInvalidHistoryLimitFromObservabilityFacade() {
        WorkItem workItem = new WorkItem(
                TENANT_ID, WORK_ITEM_CODE, WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Login xato", "BUGS", null);

        var workItemView = new WorkItemDetailsFacade.WorkItemDetailsView(workItem, List.of());

        when(queryService.findByTenantAndId(TENANT_ID, workItem.getId()))
                .thenReturn(Optional.of(workItem));
        when(detailsFacade.getDetails(TENANT_ID, WORK_ITEM_CODE))
                .thenReturn(workItemView);
        when(observabilityFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, 0))
                .thenThrow(new IllegalArgumentException(
                        "historyLimit 1..50 oralig'ida bo'lishi kerak"));

        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, workItem.getId(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("historyLimit");
    }
}
