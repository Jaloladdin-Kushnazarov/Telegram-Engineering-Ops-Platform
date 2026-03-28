package com.engops.platform.admin;

import com.engops.platform.telegram.TelegramDeliveryMetricsSnapshot;
import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsView;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * WorkItemSupportDetailsByOwnerFacade unit testlari.
 *
 * Tekshiruvlar:
 * - combined details list primary filtered list'dan qaytariladi
 * - primary ordering saqlanadi
 * - har bir primary item uchun exact workItemCode bilan delegation
 * - bo'sh primary list short-circuit qiladi — downstream facade chaqirilmaydi
 * - invalid limit propagatsiya qiladi
 * - null tenantId propagatsiya qiladi
 * - null ownerUserId propagatsiya qiladi
 * - DEFAULT_HISTORY_LIMIT = 10 barcha item'lar uchun ishlatiladi
 * - combined result cross-section identity consistency saqlanadi
 * - pozitsion zipping yo'q — per-item facade call
 * - to'g'ridan-to'g'ri repository ishlatilmaydi
 */
class WorkItemSupportDetailsByOwnerFacadeTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OWNER_USER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID WI_ID_1 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WI_ID_2 = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID WORKFLOW_DEF_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private final WorkItemSummaryByOwnerFacade ownerFacade =
            mock(WorkItemSummaryByOwnerFacade.class);
    private final WorkItemSupportDetailsFacade supportDetailsFacade =
            mock(WorkItemSupportDetailsFacade.class);
    private final WorkItemSupportDetailsByOwnerFacade facade =
            new WorkItemSupportDetailsByOwnerFacade(ownerFacade, supportDetailsFacade);

    @Test
    void returnsCombinedDetailedListFromPrimaryFilteredList() {
        var wi = workItemSummary(WI_ID_1, "BUG-1");
        var detailsView = supportDetailsView("BUG-1");

        when(ownerFacade.getSummaryList(TENANT_ID, OWNER_USER_ID, 20)).thenReturn(List.of(wi));
        when(supportDetailsFacade.getDetails(TENANT_ID, "BUG-1", 10)).thenReturn(detailsView);

        var result = facade.getDetailsList(TENANT_ID, OWNER_USER_ID, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isSameAs(detailsView);

        verify(ownerFacade).getSummaryList(TENANT_ID, OWNER_USER_ID, 20);
        verify(supportDetailsFacade).getDetails(TENANT_ID, "BUG-1", 10);
        verifyNoMoreInteractions(ownerFacade, supportDetailsFacade);
    }

    @Test
    void preservesPrimaryOrdering() {
        var wi1 = workItemSummary(WI_ID_1, "BUG-1");
        var wi2 = workItemSummary(WI_ID_2, "BUG-2");
        var details1 = supportDetailsView("BUG-1");
        var details2 = supportDetailsView("BUG-2");

        when(ownerFacade.getSummaryList(TENANT_ID, OWNER_USER_ID, 20)).thenReturn(List.of(wi1, wi2));
        when(supportDetailsFacade.getDetails(TENANT_ID, "BUG-1", 10)).thenReturn(details1);
        when(supportDetailsFacade.getDetails(TENANT_ID, "BUG-2", 10)).thenReturn(details2);

        var result = facade.getDetailsList(TENANT_ID, OWNER_USER_ID, 20);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).workItemDetails().workItem().getWorkItemCode()).isEqualTo("BUG-1");
        assertThat(result.get(1).workItemDetails().workItem().getWorkItemCode()).isEqualTo("BUG-2");
    }

    @Test
    void delegatesUsingExactResolvedWorkItemCodeValues() {
        var wi1 = workItemSummary(WI_ID_1, "BUG-1");
        var wi2 = workItemSummary(WI_ID_2, "INCIDENT-5");
        var details1 = supportDetailsView("BUG-1");
        var details2 = supportDetailsView("INCIDENT-5");

        when(ownerFacade.getSummaryList(TENANT_ID, OWNER_USER_ID, 20)).thenReturn(List.of(wi1, wi2));
        when(supportDetailsFacade.getDetails(TENANT_ID, "BUG-1", 10)).thenReturn(details1);
        when(supportDetailsFacade.getDetails(TENANT_ID, "INCIDENT-5", 10)).thenReturn(details2);

        facade.getDetailsList(TENANT_ID, OWNER_USER_ID, 20);

        verify(supportDetailsFacade).getDetails(TENANT_ID, "BUG-1", 10);
        verify(supportDetailsFacade).getDetails(TENANT_ID, "INCIDENT-5", 10);
        verifyNoMoreInteractions(supportDetailsFacade);
    }

    @Test
    void emptyPrimaryListShortCircuitsAndSkipsDownstreamCalls() {
        when(ownerFacade.getSummaryList(TENANT_ID, OWNER_USER_ID, 20)).thenReturn(List.of());

        var result = facade.getDetailsList(TENANT_ID, OWNER_USER_ID, 20);

        assertThat(result).isEmpty();
        verify(ownerFacade).getSummaryList(TENANT_ID, OWNER_USER_ID, 20);
        verifyNoInteractions(supportDetailsFacade);
    }

    @Test
    void propagatesInvalidLimit() {
        when(ownerFacade.getSummaryList(TENANT_ID, OWNER_USER_ID, 0))
                .thenThrow(new IllegalArgumentException(
                        "limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        assertThatThrownBy(() -> facade.getDetailsList(TENANT_ID, OWNER_USER_ID, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");

        verifyNoInteractions(supportDetailsFacade);
    }

    @Test
    void propagatesNullTenantId() {
        when(ownerFacade.getSummaryList(null, OWNER_USER_ID, 20))
                .thenThrow(new IllegalArgumentException("tenantId null bo'lishi mumkin emas"));

        assertThatThrownBy(() -> facade.getDetailsList(null, OWNER_USER_ID, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(supportDetailsFacade);
    }

    @Test
    void propagatesNullOwnerUserId() {
        when(ownerFacade.getSummaryList(TENANT_ID, null, 20))
                .thenThrow(new IllegalArgumentException("ownerUserId null bo'lishi mumkin emas"));

        assertThatThrownBy(() -> facade.getDetailsList(TENANT_ID, null, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ownerUserId");

        verifyNoInteractions(supportDetailsFacade);
    }

    @Test
    void usesDefaultHistoryLimitForAllItems() {
        var wi1 = workItemSummary(WI_ID_1, "BUG-1");
        var wi2 = workItemSummary(WI_ID_2, "BUG-2");
        var details1 = supportDetailsView("BUG-1");
        var details2 = supportDetailsView("BUG-2");

        when(ownerFacade.getSummaryList(TENANT_ID, OWNER_USER_ID, 5)).thenReturn(List.of(wi1, wi2));
        when(supportDetailsFacade.getDetails(TENANT_ID, "BUG-1", 10)).thenReturn(details1);
        when(supportDetailsFacade.getDetails(TENANT_ID, "BUG-2", 10)).thenReturn(details2);

        facade.getDetailsList(TENANT_ID, OWNER_USER_ID, 5);

        verify(supportDetailsFacade).getDetails(TENANT_ID, "BUG-1", 10);
        verify(supportDetailsFacade).getDetails(TENANT_ID, "BUG-2", 10);
        verifyNoMoreInteractions(supportDetailsFacade);
    }

    @Test
    void verifyDelegationArguments() {
        when(ownerFacade.getSummaryList(TENANT_ID, OWNER_USER_ID, 5)).thenReturn(List.of());

        facade.getDetailsList(TENANT_ID, OWNER_USER_ID, 5);

        verify(ownerFacade).getSummaryList(TENANT_ID, OWNER_USER_ID, 5);
        verifyNoMoreInteractions(ownerFacade);
        verifyNoInteractions(supportDetailsFacade);
    }

    @Test
    void combinedResultPreservesCrossSectionIdentityConsistency() {
        var wi1 = workItemSummary(WI_ID_1, "BUG-1");
        var wi2 = workItemSummary(WI_ID_2, "BUG-2");
        var details1 = supportDetailsView("BUG-1");
        var details2 = supportDetailsView("BUG-2");

        when(ownerFacade.getSummaryList(TENANT_ID, OWNER_USER_ID, 20)).thenReturn(List.of(wi1, wi2));
        when(supportDetailsFacade.getDetails(TENANT_ID, "BUG-1", 10)).thenReturn(details1);
        when(supportDetailsFacade.getDetails(TENANT_ID, "BUG-2", 10)).thenReturn(details2);

        var result = facade.getDetailsList(TENANT_ID, OWNER_USER_ID, 20);

        assertThat(result).hasSize(2);

        // Item 1: workItemId consistent across both sections
        UUID item1WorkItemId = result.get(0).workItemDetails().workItem().getId();
        assertThat(result.get(0).observabilityDetails().workItemId()).isEqualTo(item1WorkItemId);
        // Item 1: workItemCode consistent across both sections
        String item1Code = result.get(0).workItemDetails().workItem().getWorkItemCode();
        assertThat(result.get(0).observabilityDetails().workItemCode()).isEqualTo(item1Code);
        assertThat(item1Code).isEqualTo("BUG-1");

        // Item 2: workItemId consistent across both sections
        UUID item2WorkItemId = result.get(1).workItemDetails().workItem().getId();
        assertThat(result.get(1).observabilityDetails().workItemId()).isEqualTo(item2WorkItemId);
        // Item 2: workItemCode consistent across both sections
        String item2Code = result.get(1).workItemDetails().workItem().getWorkItemCode();
        assertThat(result.get(1).observabilityDetails().workItemCode()).isEqualTo(item2Code);
        assertThat(item2Code).isEqualTo("BUG-2");

        // Two items have different workItemIds
        assertThat(item1WorkItemId).isNotEqualTo(item2WorkItemId);

        verify(ownerFacade).getSummaryList(TENANT_ID, OWNER_USER_ID, 20);
        verify(supportDetailsFacade).getDetails(TENANT_ID, "BUG-1", 10);
        verify(supportDetailsFacade).getDetails(TENANT_ID, "BUG-2", 10);
        verifyNoMoreInteractions(ownerFacade, supportDetailsFacade);
    }

    // ========== Helpers ==========

    private WorkItemSummaryItem workItemSummary(UUID id, String code) {
        return new WorkItemSummaryItem(
                id, code, "Title " + code,
                WorkItemType.BUG, "BUGS",
                null, null, OWNER_USER_ID,
                Instant.parse("2026-03-18T10:00:00Z"),
                null, null, 0, false);
    }

    /**
     * Semantik jihatdan izchil fixture yaratadi:
     * WorkItem entity'ning haqiqiy generated id'si ikkala section uchun ishlatiladi.
     *
     * Canonical id = workItem.getId() — tashqaridan alohida UUID berilmaydi.
     * Bu cross-section id inconsistency'ni oldini oladi.
     */
    private WorkItemSupportDetailsFacade.WorkItemSupportDetailsView supportDetailsView(String code) {

        WorkItem workItem = new WorkItem(
                TENANT_ID, code, WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Title " + code, "BUGS", null);

        UUID consistentId = workItem.getId();

        var workItemView = new WorkItemDetailsFacade.WorkItemDetailsView(workItem, List.of());

        TelegramDeliveryMetricsSnapshot snapshot =
                TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, consistentId);
        var observabilityView = new TelegramDeliveryObservabilityDetailsView(
                consistentId, code, "Title " + code,
                WorkItemType.BUG, "BUGS",
                snapshot, List.of());

        return new WorkItemSupportDetailsFacade.WorkItemSupportDetailsView(
                workItemView, observabilityView);
    }
}
