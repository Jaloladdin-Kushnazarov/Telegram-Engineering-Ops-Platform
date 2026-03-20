package com.engops.platform.admin;

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
 * WorkItemSupportSummaryByStatusFacade unit testlari.
 */
class WorkItemSupportSummaryByStatusFacadeTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WI_ID_1 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WI_ID_2 = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final WorkItemSummaryByStatusFacade statusFacade =
            mock(WorkItemSummaryByStatusFacade.class);
    private final DeliveryObservabilitySummaryFacade deliveryFacade =
            mock(DeliveryObservabilitySummaryFacade.class);
    private final WorkItemSupportSummaryByStatusFacade facade =
            new WorkItemSupportSummaryByStatusFacade(statusFacade, deliveryFacade);

    @Test
    void returnsComposedListFromBothFacades() {
        var wi = workItemSummary(WI_ID_1, "BUG-1");
        var del = deliverySummary(WI_ID_1, "BUG-1");

        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 20)).thenReturn(List.of(wi));
        when(deliveryFacade.getSummaryList(TENANT_ID, 20)).thenReturn(List.of(del));

        var result = facade.getSummaryList(TENANT_ID, "BUGS", 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).workItem()).isSameAs(wi);
        assertThat(result.get(0).deliveryObservability()).isSameAs(del);

        // Non-empty path da ikkala facade aynan to'g'ri argumentlar bilan chaqirilganini isbotlash
        verify(statusFacade).getSummaryList(TENANT_ID, "BUGS", 20);
        verify(deliveryFacade).getSummaryList(TENANT_ID, 20);
        verifyNoMoreInteractions(statusFacade, deliveryFacade);
    }

    @Test
    void composesByWorkItemIdNotByPosition() {
        var wi1 = workItemSummary(WI_ID_1, "BUG-1");
        var wi2 = workItemSummary(WI_ID_2, "BUG-2");
        // Delivery ro'yxat ATAYLAB teskari tartibda
        var del2 = deliverySummary(WI_ID_2, "BUG-2");
        var del1 = deliverySummary(WI_ID_1, "BUG-1");

        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 20)).thenReturn(List.of(wi1, wi2));
        when(deliveryFacade.getSummaryList(TENANT_ID, 20)).thenReturn(List.of(del2, del1));

        var result = facade.getSummaryList(TENANT_ID, "BUGS", 20);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).workItem().workItemId()).isEqualTo(WI_ID_1);
        assertThat(result.get(0).deliveryObservability().workItemId()).isEqualTo(WI_ID_1);
        assertThat(result.get(1).workItem().workItemId()).isEqualTo(WI_ID_2);
        assertThat(result.get(1).deliveryObservability().workItemId()).isEqualTo(WI_ID_2);
    }

    @Test
    void emptyListWhenWorkItemSummaryEmpty() {
        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 20)).thenReturn(List.of());

        var result = facade.getSummaryList(TENANT_ID, "BUGS", 20);

        assertThat(result).isEmpty();
        verifyNoInteractions(deliveryFacade);
    }

    @Test
    void propagatesInvalidLimit() {
        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 0))
                .thenThrow(new IllegalArgumentException(
                        "limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        assertThatThrownBy(() -> facade.getSummaryList(TENANT_ID, "BUGS", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");

        verifyNoInteractions(deliveryFacade);
    }

    @Test
    void propagatesNullTenantId() {
        when(statusFacade.getSummaryList(null, "BUGS", 20))
                .thenThrow(new IllegalArgumentException("tenantId null bo'lishi mumkin emas"));

        assertThatThrownBy(() -> facade.getSummaryList(null, "BUGS", 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(deliveryFacade);
    }

    @Test
    void propagatesBlankStatusCode() {
        when(statusFacade.getSummaryList(TENANT_ID, "", 20))
                .thenThrow(new IllegalArgumentException(
                        "statusCode null yoki bo'sh bo'lishi mumkin emas"));

        assertThatThrownBy(() -> facade.getSummaryList(TENANT_ID, "", 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("statusCode");

        verifyNoInteractions(deliveryFacade);
    }

    @Test
    void failsFastWhenDeliverySummaryMissingForWorkItem() {
        var wi = workItemSummary(WI_ID_1, "BUG-1");

        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 20)).thenReturn(List.of(wi));
        when(deliveryFacade.getSummaryList(TENANT_ID, 20)).thenReturn(List.of());

        assertThatThrownBy(() -> facade.getSummaryList(TENANT_ID, "BUGS", 20))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("workItemId=" + WI_ID_1);
    }

    @Test
    void verifyDelegationArguments() {
        when(statusFacade.getSummaryList(TENANT_ID, "TESTING", 5)).thenReturn(List.of());

        facade.getSummaryList(TENANT_ID, "TESTING", 5);

        verify(statusFacade).getSummaryList(TENANT_ID, "TESTING", 5);
        verifyNoMoreInteractions(statusFacade);
        verifyNoInteractions(deliveryFacade);
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

    private DeliveryObservabilitySummaryItem deliverySummary(UUID id, String code) {
        return new DeliveryObservabilitySummaryItem(
                id, code, "Title",
                WorkItemType.BUG, "BUGS",
                TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, id));
    }
}
