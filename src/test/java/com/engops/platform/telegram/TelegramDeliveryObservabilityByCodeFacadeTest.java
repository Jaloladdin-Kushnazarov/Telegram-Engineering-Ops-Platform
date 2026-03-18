package com.engops.platform.telegram;

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
 * TelegramDeliveryObservabilityByCodeFacade testlari.
 *
 * Tekshiruvlar:
 * - workItemCode resolve qilinadi va observabilityFacade'ga delegatsiya qilinadi
 * - tenantId/historyLimit to'g'ri uzatiladi
 * - null tenantId rad etiladi
 * - null workItemCode rad etiladi
 * - blank workItemCode rad etiladi
 * - topilmagan workItemCode uchun IllegalArgumentException
 * - invalid historyLimit observabilityFacade'dan propagatsiya qilinadi
 */
class TelegramDeliveryObservabilityByCodeFacadeTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID WORK_ITEM_ID = UUID.randomUUID();
    private static final String WORK_ITEM_CODE = "BUG-1";

    private final WorkItemQueryService workItemQueryService =
            mock(WorkItemQueryService.class);
    private final TelegramDeliveryObservabilityFacade observabilityFacade =
            mock(TelegramDeliveryObservabilityFacade.class);
    private final TelegramDeliveryObservabilityByCodeFacade facade =
            new TelegramDeliveryObservabilityByCodeFacade(workItemQueryService, observabilityFacade);

    @Test
    void resolvesCodeAndDelegatesToObservabilityFacade() {
        WorkItem workItem = buildWorkItem();
        TelegramDeliveryObservabilityView expectedView = buildEmptyView();

        when(workItemQueryService.findByTenantAndCode(TENANT_ID, WORK_ITEM_CODE))
                .thenReturn(Optional.of(workItem));
        when(observabilityFacade.getObservabilityView(TENANT_ID, WORK_ITEM_ID, 10))
                .thenReturn(expectedView);

        TelegramDeliveryObservabilityView result =
                facade.getObservabilityViewByCode(TENANT_ID, WORK_ITEM_CODE, 10);

        assertThat(result).isSameAs(expectedView);
        verify(workItemQueryService).findByTenantAndCode(TENANT_ID, WORK_ITEM_CODE);
        verify(observabilityFacade).getObservabilityView(TENANT_ID, WORK_ITEM_ID, 10);
        verifyNoMoreInteractions(workItemQueryService, observabilityFacade);
    }

    @Test
    void passesHistoryLimitCorrectly() {
        WorkItem workItem = buildWorkItem();
        TelegramDeliveryObservabilityView view = buildEmptyView();

        when(workItemQueryService.findByTenantAndCode(TENANT_ID, WORK_ITEM_CODE))
                .thenReturn(Optional.of(workItem));
        when(observabilityFacade.getObservabilityView(TENANT_ID, WORK_ITEM_ID, 30))
                .thenReturn(view);

        facade.getObservabilityViewByCode(TENANT_ID, WORK_ITEM_CODE, 30);

        verify(observabilityFacade).getObservabilityView(TENANT_ID, WORK_ITEM_ID, 30);
    }

    @Test
    void nullTenantIdRejected() {
        assertThatThrownBy(() -> facade.getObservabilityViewByCode(null, WORK_ITEM_CODE, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId null");

        verifyNoInteractions(workItemQueryService, observabilityFacade);
    }

    @Test
    void nullWorkItemCodeRejected() {
        assertThatThrownBy(() -> facade.getObservabilityViewByCode(TENANT_ID, null, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemCode null");

        verifyNoInteractions(workItemQueryService, observabilityFacade);
    }

    @Test
    void blankWorkItemCodeRejected() {
        assertThatThrownBy(() -> facade.getObservabilityViewByCode(TENANT_ID, "  ", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemCode null");

        verifyNoInteractions(workItemQueryService, observabilityFacade);
    }

    @Test
    void workItemNotFoundThrowsIllegalArgument() {
        when(workItemQueryService.findByTenantAndCode(TENANT_ID, "NONEXISTENT-99"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getObservabilityViewByCode(TENANT_ID, "NONEXISTENT-99", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WorkItem topilmadi")
                .hasMessageContaining("NONEXISTENT-99");

        verify(workItemQueryService).findByTenantAndCode(TENANT_ID, "NONEXISTENT-99");
        verifyNoInteractions(observabilityFacade);
    }

    @Test
    void invalidHistoryLimitPropagatedFromUnderlyingFacade() {
        WorkItem workItem = buildWorkItem();
        when(workItemQueryService.findByTenantAndCode(TENANT_ID, WORK_ITEM_CODE))
                .thenReturn(Optional.of(workItem));
        when(observabilityFacade.getObservabilityView(TENANT_ID, WORK_ITEM_ID, 0))
                .thenThrow(new IllegalArgumentException("limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        assertThatThrownBy(() -> facade.getObservabilityViewByCode(TENANT_ID, WORK_ITEM_CODE, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    private WorkItem buildWorkItem() {
        WorkItem workItem = new WorkItem(
                TENANT_ID, WORK_ITEM_CODE, WorkItemType.BUG,
                UUID.randomUUID(), "Test bug", "BUGS", null);
        // WorkItem extends BaseEntity which generates ID in constructor.
        // We need our test to use a known ID, so use reflection-free approach:
        // mock the getId() instead.
        WorkItem spy = spy(workItem);
        doReturn(WORK_ITEM_ID).when(spy).getId();
        return spy;
    }

    private TelegramDeliveryObservabilityView buildEmptyView() {
        return new TelegramDeliveryObservabilityView(
                TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WORK_ITEM_ID),
                List.of());
    }
}
