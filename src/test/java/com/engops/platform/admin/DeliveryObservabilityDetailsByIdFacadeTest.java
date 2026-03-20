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
import static org.mockito.Mockito.*;

/**
 * DeliveryObservabilityDetailsByIdFacade unit testlari.
 *
 * Tekshiruvlar:
 * - UUID -> code resolve -> code-based facade'ga delegation (verify bilan)
 * - resolved code aynan downstream facade'ga uzatiladi
 * - historyLimit to'g'ri uzatiladi
 * - work item topilmasa ResourceNotFoundException + downstream chaqirilMAYDI
 * - null tenantId / workItemId rejected + downstream chaqirilMAYDI
 * - invalid historyLimit propagatsiya qiladi
 */
class DeliveryObservabilityDetailsByIdFacadeTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORKFLOW_DEF_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String WORK_ITEM_CODE = "BUG-1";

    private final WorkItemQueryService queryService = mock(WorkItemQueryService.class);
    private final TelegramDeliveryObservabilityDetailsFacade codeBasedFacade =
            mock(TelegramDeliveryObservabilityDetailsFacade.class);
    private final DeliveryObservabilityDetailsByIdFacade facade =
            new DeliveryObservabilityDetailsByIdFacade(queryService, codeBasedFacade);

    @Test
    void resolvesWorkItemIdAndDelegatesWithExactResolvedCode() {
        WorkItem workItem = new WorkItem(
                TENANT_ID, WORK_ITEM_CODE, WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Login xato", "BUGS", null);
        UUID actualId = workItem.getId();

        TelegramDeliveryMetricsSnapshot snapshot =
                TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, actualId);
        var expectedView = new TelegramDeliveryObservabilityDetailsView(
                actualId, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                snapshot, List.of());

        when(queryService.findByTenantAndId(TENANT_ID, actualId))
                .thenReturn(Optional.of(workItem));
        when(codeBasedFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, 10))
                .thenReturn(expectedView);

        var result = facade.getDetails(TENANT_ID, actualId, 10);

        assertThat(result).isSameAs(expectedView);

        verify(queryService).findByTenantAndId(TENANT_ID, actualId);
        verify(codeBasedFacade).getDetails(TENANT_ID, WORK_ITEM_CODE, 10);
        verifyNoMoreInteractions(queryService, codeBasedFacade);
    }

    @Test
    void forwardsHistoryLimitCorrectly() {
        WorkItem workItem = new WorkItem(
                TENANT_ID, WORK_ITEM_CODE, WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Login xato", "BUGS", null);
        UUID actualId = workItem.getId();

        TelegramDeliveryMetricsSnapshot snapshot =
                TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, actualId);
        var expectedView = new TelegramDeliveryObservabilityDetailsView(
                actualId, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                snapshot, List.of());

        when(queryService.findByTenantAndId(TENANT_ID, actualId))
                .thenReturn(Optional.of(workItem));
        when(codeBasedFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, 30))
                .thenReturn(expectedView);

        facade.getDetails(TENANT_ID, actualId, 30);

        verify(codeBasedFacade).getDetails(TENANT_ID, WORK_ITEM_CODE, 30);
    }

    @Test
    void throwsResourceNotFoundAndSkipsDownstreamWhenWorkItemMissing() {
        UUID unknownId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        when(queryService.findByTenantAndId(TENANT_ID, unknownId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, unknownId, 10))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(codeBasedFacade);
    }

    @Test
    void throwsIllegalArgumentWhenTenantIdNullAndSkipsAll() {
        assertThatThrownBy(() -> facade.getDetails(null, UUID.randomUUID(), 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(queryService, codeBasedFacade);
    }

    @Test
    void throwsIllegalArgumentWhenWorkItemIdNullAndSkipsAll() {
        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, null, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemId");

        verifyNoInteractions(queryService, codeBasedFacade);
    }

    @Test
    void propagatesInvalidHistoryLimitFromCodeBasedFacade() {
        WorkItem workItem = new WorkItem(
                TENANT_ID, WORK_ITEM_CODE, WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Login xato", "BUGS", null);

        when(queryService.findByTenantAndId(TENANT_ID, workItem.getId()))
                .thenReturn(Optional.of(workItem));
        when(codeBasedFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, 0))
                .thenThrow(new IllegalArgumentException(
                        "historyLimit 1..50 oralig'ida bo'lishi kerak"));

        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, workItem.getId(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("historyLimit");
    }
}
