package com.engops.platform.admin;

import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.telegram.TelegramDeliveryMetricsFacade;
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
 * DeliveryObservabilitySummaryByStatusFacade unit testlari.
 *
 * Tekshiruvlar:
 * - primary list uchun individual delivery metrics olinadi (tenant-wide top N emas)
 * - primary ordering saqlanadi
 * - empty primary short-circuit ishlaydi
 * - invalid input propagatsiya qiladi
 * - false inconsistency gap yopilgan
 * - authorization chaqiriladi
 * - authorization denial short-circuit
 * - validation before authorization ordering
 */
class DeliveryObservabilitySummaryByStatusFacadeTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_USER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID WI_ID_1 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WI_ID_2 = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID WI_ID_3 = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private final WorkItemSummaryByStatusFacade statusFacade =
            mock(WorkItemSummaryByStatusFacade.class);
    private final TelegramDeliveryMetricsFacade metricsFacade =
            mock(TelegramDeliveryMetricsFacade.class);
    private final AdminAuthorizationService authorizationService =
            mock(AdminAuthorizationService.class);
    private final DeliveryObservabilitySummaryByStatusFacade facade =
            new DeliveryObservabilitySummaryByStatusFacade(
                    statusFacade, metricsFacade, authorizationService);

    @Test
    void returnsDeliverySummaryForEachPrimaryItem() {
        var wi1 = workItemSummary(WI_ID_1, "BUG-1");
        var snapshot1 = TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WI_ID_1);

        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 20)).thenReturn(List.of(wi1));
        when(metricsFacade.getDeliveryMetrics(TENANT_ID, WI_ID_1)).thenReturn(snapshot1);

        var result = facade.getSummaryList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).workItemId()).isEqualTo(WI_ID_1);
        assertThat(result.get(0).workItemCode()).isEqualTo("BUG-1");
        assertThat(result.get(0).latestMetrics()).isSameAs(snapshot1);

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
        verify(statusFacade).getSummaryList(TENANT_ID, "BUGS", 20);
        verify(metricsFacade).getDeliveryMetrics(TENANT_ID, WI_ID_1);
        verifyNoMoreInteractions(statusFacade, metricsFacade);
    }

    @Test
    void primaryOrderingPreserved() {
        var wi1 = workItemSummary(WI_ID_1, "BUG-1");
        var wi2 = workItemSummary(WI_ID_2, "BUG-2");
        var snapshot1 = TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WI_ID_1);
        var snapshot2 = TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WI_ID_2);

        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 20))
                .thenReturn(List.of(wi1, wi2));
        when(metricsFacade.getDeliveryMetrics(TENANT_ID, WI_ID_1)).thenReturn(snapshot1);
        when(metricsFacade.getDeliveryMetrics(TENANT_ID, WI_ID_2)).thenReturn(snapshot2);

        var result = facade.getSummaryList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).workItemId()).isEqualTo(WI_ID_1);
        assertThat(result.get(1).workItemId()).isEqualTo(WI_ID_2);
    }

    @Test
    void noFalseInconsistencyWhenPrimaryItemsOutsideTenantWideTopN() {
        var wi1 = workItemSummary(WI_ID_1, "BUG-1");
        var wi2 = workItemSummary(WI_ID_2, "BUG-2");
        var wi3 = workItemSummary(WI_ID_3, "BUG-3");

        var snapshot1 = TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WI_ID_1);
        var snapshot2 = TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WI_ID_2);
        var snapshot3 = TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WI_ID_3);

        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 20))
                .thenReturn(List.of(wi1, wi2, wi3));
        when(metricsFacade.getDeliveryMetrics(TENANT_ID, WI_ID_1)).thenReturn(snapshot1);
        when(metricsFacade.getDeliveryMetrics(TENANT_ID, WI_ID_2)).thenReturn(snapshot2);
        when(metricsFacade.getDeliveryMetrics(TENANT_ID, WI_ID_3)).thenReturn(snapshot3);

        var result = facade.getSummaryList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).workItemId()).isEqualTo(WI_ID_1);
        assertThat(result.get(1).workItemId()).isEqualTo(WI_ID_2);
        assertThat(result.get(2).workItemId()).isEqualTo(WI_ID_3);
    }

    @Test
    void emptyListWhenPrimaryWorkItemSummaryEmpty() {
        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 20)).thenReturn(List.of());

        var result = facade.getSummaryList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID);

        assertThat(result).isEmpty();
        verifyNoInteractions(metricsFacade);
    }

    @Test
    void propagatesInvalidLimit() {
        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 0))
                .thenThrow(new IllegalArgumentException(
                        "limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        assertThatThrownBy(() -> facade.getSummaryList(TENANT_ID, "BUGS", 0, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");

        verifyNoInteractions(metricsFacade);
    }

    @Test
    void propagatesBlankStatusCode() {
        when(statusFacade.getSummaryList(TENANT_ID, "", 20))
                .thenThrow(new IllegalArgumentException(
                        "statusCode null yoki bo'sh bo'lishi mumkin emas"));

        assertThatThrownBy(() -> facade.getSummaryList(TENANT_ID, "", 20, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("statusCode");

        verifyNoInteractions(metricsFacade);
    }

    @Test
    void verifyDelegationArgumentsOnNonEmptyPath() {
        var wi = workItemSummary(WI_ID_1, "BUG-1");
        var snapshot = TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WI_ID_1);

        when(statusFacade.getSummaryList(TENANT_ID, "TESTING", 10)).thenReturn(List.of(wi));
        when(metricsFacade.getDeliveryMetrics(TENANT_ID, WI_ID_1)).thenReturn(snapshot);

        facade.getSummaryList(TENANT_ID, "TESTING", 10, ACTOR_USER_ID);

        verify(statusFacade).getSummaryList(TENANT_ID, "TESTING", 10);
        verify(metricsFacade).getDeliveryMetrics(TENANT_ID, WI_ID_1);
        verifyNoMoreInteractions(statusFacade, metricsFacade);
    }

    // ========== Authorization tests ==========

    @Test
    void authorizationCalledWithCorrectArguments() {
        when(statusFacade.getSummaryList(TENANT_ID, "BUGS", 20)).thenReturn(List.of());

        facade.getSummaryList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID);

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
    }

    @Test
    void authorizationDenialShortCircuitsBusinessDelegation() {
        doThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"))
                .when(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);

        assertThatThrownBy(() -> facade.getSummaryList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(statusFacade, metricsFacade);
    }

    @Test
    void nullTenantIdSkipsAuthorization() {
        assertThatThrownBy(() -> facade.getSummaryList(null, "BUGS", 20, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
    }

    // ========== Helpers ==========

    private WorkItemSummaryItem workItemSummary(UUID id, String code) {
        return new WorkItemSummaryItem(
                id, code, "Title",
                WorkItemType.BUG, "BUGS",
                null, null, null,
                null,
                Instant.parse("2026-03-18T10:00:00Z"),
                null, null, 0, false);
    }
}
