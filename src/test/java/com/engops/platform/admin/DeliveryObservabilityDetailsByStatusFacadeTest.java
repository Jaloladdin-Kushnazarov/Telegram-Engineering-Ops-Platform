package com.engops.platform.admin;

import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.telegram.TelegramDeliveryMetricsSnapshot;
import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsFacade;
import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsView;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * DeliveryObservabilityDetailsByStatusFacade unit testlari.
 *
 * Tekshiruvlar:
 * - primary list uchun individual delivery details olinadi
 * - primary ordering saqlanadi
 * - empty primary short-circuit ishlaydi
 * - DEFAULT_HISTORY_LIMIT to'g'ri uzatiladi
 * - invalid input propagatsiya qiladi
 * - authorization chaqiriladi
 * - authorization denial short-circuit
 * - validation before authorization ordering
 */
class DeliveryObservabilityDetailsByStatusFacadeTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_USER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID WI_ID_1 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WI_ID_2 = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID WI_ID_3 = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private final WorkItemSummaryByStatusFacade statusFacade =
            mock(WorkItemSummaryByStatusFacade.class);
    private final TelegramDeliveryObservabilityDetailsFacade detailsFacade =
            mock(TelegramDeliveryObservabilityDetailsFacade.class);
    private final AdminAuthorizationService authorizationService =
            mock(AdminAuthorizationService.class);
    private final DeliveryObservabilityDetailsByStatusFacade facade =
            new DeliveryObservabilityDetailsByStatusFacade(
                    statusFacade, detailsFacade, authorizationService);

    @Test
    void returnsDeliveryDetailsForEachPrimaryItem() {
        var wi1 = workItemSummary(WI_ID_1, "BUG-1");
        var details1 = detailsView(WI_ID_1, "BUG-1");

        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 20)).thenReturn(List.of(wi1));
        when(detailsFacade.getDetails(TENANT_ID, "BUG-1",
                DeliveryObservabilityDetailsByStatusFacade.DEFAULT_HISTORY_LIMIT))
                .thenReturn(details1);

        var result = facade.getDetailsList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isSameAs(details1);
        assertThat(result.get(0).workItemId()).isEqualTo(WI_ID_1);
        assertThat(result.get(0).workItemCode()).isEqualTo("BUG-1");

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
        verify(statusFacade).getSummaryList(TENANT_ID, "BUGS", 20);
        verify(detailsFacade).getDetails(TENANT_ID, "BUG-1",
                DeliveryObservabilityDetailsByStatusFacade.DEFAULT_HISTORY_LIMIT);
        verifyNoMoreInteractions(statusFacade, detailsFacade);
    }

    @Test
    void primaryOrderingPreserved() {
        var wi1 = workItemSummary(WI_ID_1, "BUG-1");
        var wi2 = workItemSummary(WI_ID_2, "BUG-2");
        var details1 = detailsView(WI_ID_1, "BUG-1");
        var details2 = detailsView(WI_ID_2, "BUG-2");

        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 20))
                .thenReturn(List.of(wi1, wi2));
        when(detailsFacade.getDetails(TENANT_ID, "BUG-1",
                DeliveryObservabilityDetailsByStatusFacade.DEFAULT_HISTORY_LIMIT))
                .thenReturn(details1);
        when(detailsFacade.getDetails(TENANT_ID, "BUG-2",
                DeliveryObservabilityDetailsByStatusFacade.DEFAULT_HISTORY_LIMIT))
                .thenReturn(details2);

        var result = facade.getDetailsList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).workItemId()).isEqualTo(WI_ID_1);
        assertThat(result.get(1).workItemId()).isEqualTo(WI_ID_2);
    }

    @Test
    void multipleItemsAllReceiveIndividualDetailsCalls() {
        var wi1 = workItemSummary(WI_ID_1, "BUG-1");
        var wi2 = workItemSummary(WI_ID_2, "BUG-2");
        var wi3 = workItemSummary(WI_ID_3, "BUG-3");

        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 20))
                .thenReturn(List.of(wi1, wi2, wi3));
        when(detailsFacade.getDetails(TENANT_ID, "BUG-1", 10)).thenReturn(detailsView(WI_ID_1, "BUG-1"));
        when(detailsFacade.getDetails(TENANT_ID, "BUG-2", 10)).thenReturn(detailsView(WI_ID_2, "BUG-2"));
        when(detailsFacade.getDetails(TENANT_ID, "BUG-3", 10)).thenReturn(detailsView(WI_ID_3, "BUG-3"));

        var result = facade.getDetailsList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).workItemId()).isEqualTo(WI_ID_1);
        assertThat(result.get(1).workItemId()).isEqualTo(WI_ID_2);
        assertThat(result.get(2).workItemId()).isEqualTo(WI_ID_3);

        verify(detailsFacade).getDetails(TENANT_ID, "BUG-1", 10);
        verify(detailsFacade).getDetails(TENANT_ID, "BUG-2", 10);
        verify(detailsFacade).getDetails(TENANT_ID, "BUG-3", 10);
    }

    @Test
    void emptyListWhenPrimaryWorkItemSummaryEmpty() {
        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 20)).thenReturn(List.of());

        var result = facade.getDetailsList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID);

        assertThat(result).isEmpty();
        verifyNoInteractions(detailsFacade);
    }

    @Test
    void defaultHistoryLimitIsUsedForDetailsCalls() {
        var wi1 = workItemSummary(WI_ID_1, "BUG-1");
        var details1 = detailsView(WI_ID_1, "BUG-1");

        when(statusFacade.getSummaryList(TENANT_ID, "PROCESSING", 5)).thenReturn(List.of(wi1));
        when(detailsFacade.getDetails(TENANT_ID, "BUG-1", 10)).thenReturn(details1);

        facade.getDetailsList(TENANT_ID, "PROCESSING", 5, ACTOR_USER_ID);

        verify(detailsFacade).getDetails(TENANT_ID, "BUG-1", 10);
    }

    @Test
    void propagatesInvalidLimit() {
        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 0))
                .thenThrow(new IllegalArgumentException(
                        "limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        assertThatThrownBy(() -> facade.getDetailsList(TENANT_ID, "BUGS", 0, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");

        verifyNoInteractions(detailsFacade);
    }

    @Test
    void propagatesBlankStatusCode() {
        when(statusFacade.getSummaryList(TENANT_ID, "", 20))
                .thenThrow(new IllegalArgumentException(
                        "statusCode null yoki bo'sh bo'lishi mumkin emas"));

        assertThatThrownBy(() -> facade.getDetailsList(TENANT_ID, "", 20, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("statusCode");

        verifyNoInteractions(detailsFacade);
    }

    @Test
    void verifyDelegationArgumentsOnNonEmptyPath() {
        var wi = workItemSummary(WI_ID_1, "BUG-1");
        var details = detailsView(WI_ID_1, "BUG-1");

        when(statusFacade.getSummaryList(TENANT_ID, "TESTING", 10)).thenReturn(List.of(wi));
        when(detailsFacade.getDetails(TENANT_ID, "BUG-1", 10)).thenReturn(details);

        facade.getDetailsList(TENANT_ID, "TESTING", 10, ACTOR_USER_ID);

        verify(statusFacade).getSummaryList(TENANT_ID, "TESTING", 10);
        verify(detailsFacade).getDetails(TENANT_ID, "BUG-1", 10);
        verifyNoMoreInteractions(statusFacade, detailsFacade);
    }

    // ========== Authorization tests ==========

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

        verifyNoInteractions(statusFacade, detailsFacade);
    }

    @Test
    void nullTenantIdSkipsAuthorization() {
        assertThatThrownBy(() -> facade.getDetailsList(null, "BUGS", 20, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
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

    private TelegramDeliveryObservabilityDetailsView detailsView(UUID workItemId, String code) {
        return new TelegramDeliveryObservabilityDetailsView(
                workItemId, code, "Title",
                WorkItemType.BUG, "BUGS",
                TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, workItemId),
                List.of());
    }
}
