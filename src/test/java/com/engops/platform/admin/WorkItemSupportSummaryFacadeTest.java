package com.engops.platform.admin;

import com.engops.platform.telegram.TelegramDeliveryMetricsSnapshot;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WorkItemSupportSummaryFacade unit testlari.
 *
 * Tekshiruvlar:
 * - composed list ikkala facade natijasini o'z ichiga oladi
 * - composition workItemId bo'yicha, pozitsiya bo'yicha emas
 * - bo'sh ro'yxat valid natija
 * - invalid limit propagatsiya qiladi
 * - null tenantId propagatsiya qiladi
 * - delivery summary topilmasa IllegalStateException
 * - compact section'lar details bilan expand qilinmaydi
 */
class WorkItemSupportSummaryFacadeTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WI_ID_1 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WI_ID_2 = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final WorkItemSummaryFacade workItemSummaryFacade =
            mock(WorkItemSummaryFacade.class);
    private final DeliveryObservabilitySummaryFacade deliverySummaryFacade =
            mock(DeliveryObservabilitySummaryFacade.class);
    private final WorkItemSupportSummaryFacade facade =
            new WorkItemSupportSummaryFacade(workItemSummaryFacade, deliverySummaryFacade);

    @Test
    void returnsComposedListFromBothFacades() {
        WorkItemSummaryItem wi1 = workItemSummary(WI_ID_1, "BUG-1", "Login xato");
        DeliveryObservabilitySummaryItem del1 = deliverySummary(WI_ID_1, "BUG-1", "Login xato");

        when(workItemSummaryFacade.getSummaryList(TENANT_ID, 20))
                .thenReturn(List.of(wi1));
        when(deliverySummaryFacade.getSummaryList(TENANT_ID, 20))
                .thenReturn(List.of(del1));

        List<WorkItemSupportSummaryItem> result = facade.getSummaryList(TENANT_ID, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).workItem()).isSameAs(wi1);
        assertThat(result.get(0).deliveryObservability()).isSameAs(del1);
    }

    @Test
    void composesByWorkItemIdNotByPosition() {
        // Work item ro'yxat: WI_ID_1 birinchi, WI_ID_2 ikkinchi
        WorkItemSummaryItem wi1 = workItemSummary(WI_ID_1, "BUG-1", "Bug 1");
        WorkItemSummaryItem wi2 = workItemSummary(WI_ID_2, "BUG-2", "Bug 2");

        // Delivery ro'yxat: ATAYLAB teskari tartibda — WI_ID_2 birinchi, WI_ID_1 ikkinchi
        DeliveryObservabilitySummaryItem del2 = deliverySummary(WI_ID_2, "BUG-2", "Bug 2");
        DeliveryObservabilitySummaryItem del1 = deliverySummary(WI_ID_1, "BUG-1", "Bug 1");

        when(workItemSummaryFacade.getSummaryList(TENANT_ID, 20))
                .thenReturn(List.of(wi1, wi2));
        when(deliverySummaryFacade.getSummaryList(TENANT_ID, 20))
                .thenReturn(List.of(del2, del1)); // teskari tartib

        List<WorkItemSupportSummaryItem> result = facade.getSummaryList(TENANT_ID, 20);

        assertThat(result).hasSize(2);
        // Birinchi item: WI_ID_1 work item + WI_ID_1 delivery (pozitsiya emas, workItemId bo'yicha)
        assertThat(result.get(0).workItem().workItemId()).isEqualTo(WI_ID_1);
        assertThat(result.get(0).deliveryObservability().workItemId()).isEqualTo(WI_ID_1);
        // Ikkinchi item: WI_ID_2 work item + WI_ID_2 delivery
        assertThat(result.get(1).workItem().workItemId()).isEqualTo(WI_ID_2);
        assertThat(result.get(1).deliveryObservability().workItemId()).isEqualTo(WI_ID_2);
    }

    @Test
    void emptyListWhenBothFacadesReturnEmpty() {
        when(workItemSummaryFacade.getSummaryList(TENANT_ID, 20))
                .thenReturn(List.of());

        List<WorkItemSupportSummaryItem> result = facade.getSummaryList(TENANT_ID, 20);

        assertThat(result).isEmpty();
    }

    @Test
    void propagatesIllegalArgumentForInvalidLimit() {
        when(workItemSummaryFacade.getSummaryList(TENANT_ID, 0))
                .thenThrow(new IllegalArgumentException(
                        "limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        assertThatThrownBy(() -> facade.getSummaryList(TENANT_ID, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void propagatesIllegalArgumentForNullTenantId() {
        when(workItemSummaryFacade.getSummaryList(null, 20))
                .thenThrow(new IllegalArgumentException("tenantId null bo'lishi mumkin emas"));

        assertThatThrownBy(() -> facade.getSummaryList(null, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void failsFastWhenDeliverySummaryMissingForWorkItem() {
        WorkItemSummaryItem wi1 = workItemSummary(WI_ID_1, "BUG-1", "Login xato");

        when(workItemSummaryFacade.getSummaryList(TENANT_ID, 20))
                .thenReturn(List.of(wi1));
        when(deliverySummaryFacade.getSummaryList(TENANT_ID, 20))
                .thenReturn(List.of()); // delivery summary yo'q — inconsistency

        assertThatThrownBy(() -> facade.getSummaryList(TENANT_ID, 20))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("workItemId=" + WI_ID_1);
    }

    @Test
    void preservesCompactSectionsWithoutDetailsExpansion() {
        WorkItemSummaryItem wi1 = workItemSummary(WI_ID_1, "BUG-1", "Login xato");
        DeliveryObservabilitySummaryItem del1 = deliverySummary(WI_ID_1, "BUG-1", "Login xato");

        when(workItemSummaryFacade.getSummaryList(TENANT_ID, 10))
                .thenReturn(List.of(wi1));
        when(deliverySummaryFacade.getSummaryList(TENANT_ID, 10))
                .thenReturn(List.of(del1));

        List<WorkItemSupportSummaryItem> result = facade.getSummaryList(TENANT_ID, 10);

        assertThat(result).hasSize(1);
        // Work item section kompakt — description, updates yo'q
        WorkItemSummaryItem workItem = result.get(0).workItem();
        assertThat(workItem.workItemCode()).isEqualTo("BUG-1");
        assertThat(workItem.priorityCode()).isNull(); // nullable field
        // Delivery section kompakt — recentAttempts yo'q
        DeliveryObservabilitySummaryItem delivery = result.get(0).deliveryObservability();
        assertThat(delivery.latestMetrics()).isNotNull();
        assertThat(delivery.latestMetrics().isEmpty()).isTrue(); // empty snapshot
    }

    // ========== Yordamchi metodlar ==========

    private WorkItemSummaryItem workItemSummary(UUID id, String code, String title) {
        return new WorkItemSummaryItem(
                id, code, title,
                WorkItemType.BUG, "BUGS",
                null, null, null,
                Instant.parse("2026-03-18T10:00:00Z"),
                null, null, 0, false);
    }

    private DeliveryObservabilitySummaryItem deliverySummary(UUID id, String code, String title) {
        return new DeliveryObservabilitySummaryItem(
                id, code, title,
                WorkItemType.BUG, "BUGS",
                TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, id));
    }
}
