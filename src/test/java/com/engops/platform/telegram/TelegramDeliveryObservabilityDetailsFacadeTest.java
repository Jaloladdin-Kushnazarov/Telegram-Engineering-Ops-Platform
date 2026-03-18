package com.engops.platform.telegram;

import com.engops.platform.workitem.WorkItemQueryService;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * TelegramDeliveryObservabilityDetailsFacade testlari.
 *
 * Tekshiruvlar:
 * - work item resolve + observability delegation + enriched view composition
 * - work item metadata to'g'ri qaytariladi
 * - observability data to'g'ri qaytariladi
 * - null tenantId rad etiladi
 * - null/blank workItemCode rad etiladi
 * - topilmagan workItemCode uchun IllegalArgumentException
 * - invalid historyLimit propagatsiya qilinadi
 * - mavjud work item + bo'sh observability data = valid view
 */
class TelegramDeliveryObservabilityDetailsFacadeTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID WORK_ITEM_ID = UUID.randomUUID();
    private static final String WORK_ITEM_CODE = "BUG-1";

    private final WorkItemQueryService workItemQueryService =
            mock(WorkItemQueryService.class);
    private final TelegramDeliveryObservabilityFacade observabilityFacade =
            mock(TelegramDeliveryObservabilityFacade.class);
    private final TelegramDeliveryObservabilityDetailsFacade facade =
            new TelegramDeliveryObservabilityDetailsFacade(workItemQueryService, observabilityFacade);

    @Test
    void resolvesWorkItemAndReturnsEnrichedDetailsView() {
        WorkItem workItem = buildWorkItem();
        TelegramDeliveryObservabilityView observability = buildObservabilityView(false);

        when(workItemQueryService.findByTenantAndCode(TENANT_ID, WORK_ITEM_CODE))
                .thenReturn(Optional.of(workItem));
        when(observabilityFacade.getObservabilityView(TENANT_ID, WORK_ITEM_ID, 10))
                .thenReturn(observability);

        TelegramDeliveryObservabilityDetailsView details =
                facade.getDetails(TENANT_ID, WORK_ITEM_CODE, 10);

        assertThat(details.workItemId()).isEqualTo(WORK_ITEM_ID);
        assertThat(details.workItemCode()).isEqualTo(WORK_ITEM_CODE);
        assertThat(details.title()).isEqualTo("Login xato");
        assertThat(details.typeCode()).isEqualTo(WorkItemType.BUG);
        assertThat(details.currentStatusCode()).isEqualTo("BUGS");
        assertThat(details.latestMetrics()).isSameAs(observability.latestMetrics());
        assertThat(details.recentAttempts()).hasSize(1);

        verify(workItemQueryService).findByTenantAndCode(TENANT_ID, WORK_ITEM_CODE);
        verify(observabilityFacade).getObservabilityView(TENANT_ID, WORK_ITEM_ID, 10);
        verifyNoMoreInteractions(workItemQueryService, observabilityFacade);
    }

    @Test
    void workItemMetadataIncludedCorrectly() {
        WorkItem workItem = buildWorkItem();
        TelegramDeliveryObservabilityView observability = buildObservabilityView(true);

        when(workItemQueryService.findByTenantAndCode(TENANT_ID, WORK_ITEM_CODE))
                .thenReturn(Optional.of(workItem));
        when(observabilityFacade.getObservabilityView(TENANT_ID, WORK_ITEM_ID, 5))
                .thenReturn(observability);

        TelegramDeliveryObservabilityDetailsView details =
                facade.getDetails(TENANT_ID, WORK_ITEM_CODE, 5);

        assertThat(details.workItemId()).isEqualTo(WORK_ITEM_ID);
        assertThat(details.workItemCode()).isEqualTo(WORK_ITEM_CODE);
        assertThat(details.title()).isEqualTo("Login xato");
        assertThat(details.typeCode()).isEqualTo(WorkItemType.BUG);
        assertThat(details.currentStatusCode()).isEqualTo("BUGS");
    }

    @Test
    void existingWorkItemWithEmptyObservabilityReturnsValidView() {
        WorkItem workItem = buildWorkItem();
        TelegramDeliveryObservabilityView emptyObservability = buildObservabilityView(true);

        when(workItemQueryService.findByTenantAndCode(TENANT_ID, WORK_ITEM_CODE))
                .thenReturn(Optional.of(workItem));
        when(observabilityFacade.getObservabilityView(TENANT_ID, WORK_ITEM_ID, 5))
                .thenReturn(emptyObservability);

        TelegramDeliveryObservabilityDetailsView details =
                facade.getDetails(TENANT_ID, WORK_ITEM_CODE, 5);

        assertThat(details.latestMetrics().isEmpty()).isTrue();
        assertThat(details.recentAttempts()).isEmpty();
        assertThat(details.workItemId()).isEqualTo(WORK_ITEM_ID);
    }

    @Test
    void nullTenantIdRejected() {
        assertThatThrownBy(() -> facade.getDetails(null, WORK_ITEM_CODE, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId null");

        verifyNoInteractions(workItemQueryService, observabilityFacade);
    }

    @Test
    void nullWorkItemCodeRejected() {
        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, null, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemCode null");

        verifyNoInteractions(workItemQueryService, observabilityFacade);
    }

    @Test
    void blankWorkItemCodeRejected() {
        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, "  ", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemCode null");

        verifyNoInteractions(workItemQueryService, observabilityFacade);
    }

    @Test
    void workItemNotFoundThrowsIllegalArgument() {
        when(workItemQueryService.findByTenantAndCode(TENANT_ID, "NONEXISTENT-99"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, "NONEXISTENT-99", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WorkItem topilmadi")
                .hasMessageContaining("NONEXISTENT-99");

        verify(workItemQueryService).findByTenantAndCode(TENANT_ID, "NONEXISTENT-99");
        verifyNoInteractions(observabilityFacade);
    }

    @Test
    void invalidHistoryLimitPropagatedFromObservabilityFacade() {
        WorkItem workItem = buildWorkItem();
        when(workItemQueryService.findByTenantAndCode(TENANT_ID, WORK_ITEM_CODE))
                .thenReturn(Optional.of(workItem));
        when(observabilityFacade.getObservabilityView(TENANT_ID, WORK_ITEM_ID, 0))
                .thenThrow(new IllegalArgumentException("limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, WORK_ITEM_CODE, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    private WorkItem buildWorkItem() {
        WorkItem workItem = new WorkItem(
                TENANT_ID, WORK_ITEM_CODE, WorkItemType.BUG,
                UUID.randomUUID(), "Login xato", "BUGS", null);
        WorkItem spy = spy(workItem);
        doReturn(WORK_ITEM_ID).when(spy).getId();
        return spy;
    }

    private TelegramDeliveryObservabilityView buildObservabilityView(boolean empty) {
        TelegramDeliveryMetricsSnapshot snapshot;
        List<TelegramDeliveryAttempt> attempts;

        if (empty) {
            snapshot = TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WORK_ITEM_ID);
            attempts = List.of();
        } else {
            snapshot = TelegramDeliveryMetricsSnapshot.of(
                    TENANT_ID, WORK_ITEM_ID,
                    TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                    TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                    null, true);
            attempts = List.of(TelegramDeliveryAttempt.reconstruct(
                    UUID.randomUUID(), Instant.parse("2026-03-18T10:00:00Z"),
                    TENANT_ID, WORK_ITEM_ID,
                    TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                    UUID.randomUUID(), 42L,
                    TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                    99001L, null, null));
        }

        return new TelegramDeliveryObservabilityView(snapshot, attempts);
    }
}
