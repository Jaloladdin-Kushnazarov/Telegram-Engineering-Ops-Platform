package com.engops.platform.admin;

import com.engops.platform.sharedkernel.exception.AccessDeniedException;
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
 * WorkItemSupportDetailsByStatusFacade unit testlari.
 *
 * Tekshiruvlar:
 * - combined details list primary filtered list'dan qaytariladi
 * - primary ordering saqlanadi
 * - har bir primary item uchun exact workItemCode bilan delegation
 * - bo'sh primary list short-circuit qiladi — downstream facade chaqirilmaydi
 * - invalid limit propagatsiya qiladi
 * - null tenantId propagatsiya qiladi
 * - blank statusCode propagatsiya qiladi
 * - pozitsion zipping yo'q — per-item facade call
 * - to'g'ridan-to'g'ri repository ishlatilmaydi
 */
class WorkItemSupportDetailsByStatusFacadeTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WI_ID_1 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WI_ID_2 = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID WORKFLOW_DEF_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID ACTOR_USER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private final WorkItemSummaryByStatusFacade statusFacade =
            mock(WorkItemSummaryByStatusFacade.class);
    private final WorkItemSupportDetailsFacade supportDetailsFacade =
            mock(WorkItemSupportDetailsFacade.class);
    private final AdminAuthorizationService authorizationService =
            mock(AdminAuthorizationService.class);
    private final WorkItemSupportDetailsByStatusFacade facade =
            new WorkItemSupportDetailsByStatusFacade(statusFacade, supportDetailsFacade, authorizationService);

    @Test
    void returnsCombinedDetailedListFromPrimaryFilteredList() {
        var wi = workItemSummary(WI_ID_1, "BUG-1");
        var detailsView = supportDetailsView("BUG-1");

        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 20)).thenReturn(List.of(wi));
        when(supportDetailsFacade.getDetails(TENANT_ID, "BUG-1", 10)).thenReturn(detailsView);

        var result = facade.getDetailsList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isSameAs(detailsView);

        verify(statusFacade).getSummaryList(TENANT_ID, "BUGS", 20);
        verify(supportDetailsFacade).getDetails(TENANT_ID, "BUG-1", 10);
        verifyNoMoreInteractions(statusFacade, supportDetailsFacade);
    }

    @Test
    void preservesPrimaryOrdering() {
        var wi1 = workItemSummary(WI_ID_1, "BUG-1");
        var wi2 = workItemSummary(WI_ID_2, "BUG-2");
        var details1 = supportDetailsView("BUG-1");
        var details2 = supportDetailsView("BUG-2");

        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 20)).thenReturn(List.of(wi1, wi2));
        when(supportDetailsFacade.getDetails(TENANT_ID, "BUG-1", 10)).thenReturn(details1);
        when(supportDetailsFacade.getDetails(TENANT_ID, "BUG-2", 10)).thenReturn(details2);

        var result = facade.getDetailsList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID);

        assertThat(result).hasSize(2);
        // Primary ordering preserved: BUG-1 first, BUG-2 second
        assertThat(result.get(0).workItemDetails().workItem().getWorkItemCode()).isEqualTo("BUG-1");
        assertThat(result.get(1).workItemDetails().workItem().getWorkItemCode()).isEqualTo("BUG-2");
    }

    @Test
    void delegatesUsingExactResolvedWorkItemCodeValues() {
        var wi1 = workItemSummary(WI_ID_1, "BUG-1");
        var wi2 = workItemSummary(WI_ID_2, "INCIDENT-5");
        var details1 = supportDetailsView("BUG-1");
        var details2 = supportDetailsView("INCIDENT-5");

        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 20)).thenReturn(List.of(wi1, wi2));
        when(supportDetailsFacade.getDetails(TENANT_ID, "BUG-1", 10)).thenReturn(details1);
        when(supportDetailsFacade.getDetails(TENANT_ID, "INCIDENT-5", 10)).thenReturn(details2);

        facade.getDetailsList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID);

        // Exact workItemCode values are used in delegation
        verify(supportDetailsFacade).getDetails(TENANT_ID, "BUG-1", 10);
        verify(supportDetailsFacade).getDetails(TENANT_ID, "INCIDENT-5", 10);
        verifyNoMoreInteractions(supportDetailsFacade);
    }

    @Test
    void emptyPrimaryListShortCircuitsAndSkipsDownstreamCalls() {
        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 20)).thenReturn(List.of());

        var result = facade.getDetailsList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID);

        assertThat(result).isEmpty();
        verify(statusFacade).getSummaryList(TENANT_ID, "BUGS", 20);
        verifyNoInteractions(supportDetailsFacade);
    }

    @Test
    void propagatesInvalidLimit() {
        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 0))
                .thenThrow(new IllegalArgumentException(
                        "limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        assertThatThrownBy(() -> facade.getDetailsList(TENANT_ID, "BUGS", 0, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");

        verifyNoInteractions(supportDetailsFacade);
    }

    @Test
    void propagatesNullTenantId() {
        when(statusFacade.getSummaryList(null, "BUGS", 20))
                .thenThrow(new IllegalArgumentException("tenantId null bo'lishi mumkin emas"));

        assertThatThrownBy(() -> facade.getDetailsList(null, "BUGS", 20, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(supportDetailsFacade);
    }

    @Test
    void propagatesBlankStatusCode() {
        when(statusFacade.getSummaryList(TENANT_ID, "", 20))
                .thenThrow(new IllegalArgumentException(
                        "statusCode null yoki bo'sh bo'lishi mumkin emas"));

        assertThatThrownBy(() -> facade.getDetailsList(TENANT_ID, "", 20, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("statusCode");

        verifyNoInteractions(supportDetailsFacade);
    }

    @Test
    void usesDefaultHistoryLimitForAllItems() {
        var wi1 = workItemSummary(WI_ID_1, "BUG-1");
        var wi2 = workItemSummary(WI_ID_2, "BUG-2");
        var details1 = supportDetailsView("BUG-1");
        var details2 = supportDetailsView("BUG-2");

        when(statusFacade.getSummaryList(TENANT_ID, "TESTING", 5)).thenReturn(List.of(wi1, wi2));
        when(supportDetailsFacade.getDetails(TENANT_ID, "BUG-1", 10)).thenReturn(details1);
        when(supportDetailsFacade.getDetails(TENANT_ID, "BUG-2", 10)).thenReturn(details2);

        facade.getDetailsList(TENANT_ID, "TESTING", 5, ACTOR_USER_ID);

        // DEFAULT_HISTORY_LIMIT = 10 consistently used for all items
        verify(supportDetailsFacade).getDetails(TENANT_ID, "BUG-1", 10);
        verify(supportDetailsFacade).getDetails(TENANT_ID, "BUG-2", 10);
        verifyNoMoreInteractions(supportDetailsFacade);
    }

    @Test
    void verifyDelegationArguments() {
        when(statusFacade.getSummaryList(TENANT_ID, "TESTING", 5)).thenReturn(List.of());

        facade.getDetailsList(TENANT_ID, "TESTING", 5, ACTOR_USER_ID);

        verify(statusFacade).getSummaryList(TENANT_ID, "TESTING", 5);
        verifyNoMoreInteractions(statusFacade);
        verifyNoInteractions(supportDetailsFacade);
    }

    @Test
    void noPositionalZippingAssumptions() {
        // Per-item delegation ensures no positional zipping —
        // each item is resolved independently by workItemCode
        var wi1 = workItemSummary(WI_ID_1, "BUG-1");
        var wi2 = workItemSummary(WI_ID_2, "BUG-2");
        var details1 = supportDetailsView("BUG-1");
        var details2 = supportDetailsView("BUG-2");

        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 20)).thenReturn(List.of(wi1, wi2));
        when(supportDetailsFacade.getDetails(TENANT_ID, "BUG-1", 10)).thenReturn(details1);
        when(supportDetailsFacade.getDetails(TENANT_ID, "BUG-2", 10)).thenReturn(details2);

        var result = facade.getDetailsList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID);

        // Identity consistency: workItemCode in result matches delegation argument
        assertThat(result.get(0).workItemDetails().workItem().getWorkItemCode()).isEqualTo("BUG-1");
        assertThat(result.get(1).workItemDetails().workItem().getWorkItemCode()).isEqualTo("BUG-2");
    }

    @Test
    void combinedResultPreservesCrossSectionIdentityConsistency() {
        // Bu test fixture'ning semantik realizmini isbotlaydi:
        // har bir combined view ichida workItemDetails va observabilityDetails
        // bir xil workItemId va workItemCode ishlatishi kerak
        var wi1 = workItemSummary(WI_ID_1, "BUG-1");
        var wi2 = workItemSummary(WI_ID_2, "BUG-2");
        var details1 = supportDetailsView("BUG-1");
        var details2 = supportDetailsView("BUG-2");

        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 20)).thenReturn(List.of(wi1, wi2));
        when(supportDetailsFacade.getDetails(TENANT_ID, "BUG-1", 10)).thenReturn(details1);
        when(supportDetailsFacade.getDetails(TENANT_ID, "BUG-2", 10)).thenReturn(details2);

        var result = facade.getDetailsList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID);

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

        verify(statusFacade).getSummaryList(TENANT_ID, "BUGS", 20);
        verify(supportDetailsFacade).getDetails(TENANT_ID, "BUG-1", 10);
        verify(supportDetailsFacade).getDetails(TENANT_ID, "BUG-2", 10);
        verifyNoMoreInteractions(statusFacade, supportDetailsFacade);
    }

    // ========== Authorization testlari ==========

    @Test
    void authorizationCalledWithCorrectArguments() {
        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 20)).thenReturn(List.of());

        facade.getDetailsList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID);

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
    }

    @Test
    void authorizationDenialShortCircuitsBusinessDelegation() {
        doThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"))
                .when(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);

        assertThatThrownBy(() -> facade.getDetailsList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(statusFacade, supportDetailsFacade);
    }

    @Test
    void nullTenantIdSkipsAuthorization() {
        assertThatThrownBy(() -> facade.getDetailsList(null, "BUGS", 20, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(authorizationService);
    }

    // ========== Helpers ==========

    private WorkItemSummaryItem workItemSummary(UUID id, String code) {
        return new WorkItemSummaryItem(
                id, code, "Title " + code,
                WorkItemType.BUG, "BUGS",
                null, null, null,
                Instant.parse("2026-03-18T10:00:00Z"),
                null, null, 0, false);
    }

    /**
     * Semantik jihatdan izchil fixture yaratadi:
     * WorkItem entity'ning haqiqiy generated id'si ikkala section uchun ishlatiladi.
     *
     * Oldingi versiya workItemId parametrini alohida qabul qilib,
     * observabilityView uchun shu parametrni, lekin WorkItem entity uchun
     * BaseEntity.randomUUID() generatsiya qilgan boshqa id'ni ishlatardi.
     * Bu cross-section id inconsistency'ni yashirar edi.
     */
    private WorkItemSupportDetailsFacade.WorkItemSupportDetailsView supportDetailsView(String code) {

        WorkItem workItem = new WorkItem(
                TENANT_ID, code, WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Title " + code, "BUGS", null);

        // WorkItem entity'ning haqiqiy id'sini canonical source sifatida ishlatish
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
