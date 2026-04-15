package com.engops.platform.admin;

import com.engops.platform.sharedkernel.exception.AccessDeniedException;
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
 * - authorization chaqiriladi
 * - authorization denial short-circuit
 * - validation before authorization ordering
 */
class DeliveryObservabilityDetailsByIdFacadeTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_USER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID WORKFLOW_DEF_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String WORK_ITEM_CODE = "BUG-1";

    private final WorkItemQueryService queryService = mock(WorkItemQueryService.class);
    private final TelegramDeliveryObservabilityDetailsFacade codeBasedFacade =
            mock(TelegramDeliveryObservabilityDetailsFacade.class);
    private final AdminAuthorizationService authorizationService =
            mock(AdminAuthorizationService.class);
    private final DeliveryObservabilityDetailsByIdFacade facade =
            new DeliveryObservabilityDetailsByIdFacade(
                    queryService, codeBasedFacade, authorizationService);

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

        var result = facade.getDetails(TENANT_ID, actualId, 10, ACTOR_USER_ID);

        assertThat(result).isSameAs(expectedView);

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
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

        facade.getDetails(TENANT_ID, actualId, 30, ACTOR_USER_ID);

        verify(codeBasedFacade).getDetails(TENANT_ID, WORK_ITEM_CODE, 30);
    }

    @Test
    void throwsResourceNotFoundAndSkipsDownstreamWhenWorkItemMissing() {
        UUID unknownId = UUID.fromString("99999999-9999-9999-9999-aaaaaaaaaaaa");
        when(queryService.findByTenantAndId(TENANT_ID, unknownId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, unknownId, 10, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(codeBasedFacade);
    }

    @Test
    void throwsIllegalArgumentWhenTenantIdNullAndSkipsAll() {
        assertThatThrownBy(() -> facade.getDetails(null, UUID.randomUUID(), 10, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(authorizationService, queryService, codeBasedFacade);
    }

    @Test
    void throwsIllegalArgumentWhenWorkItemIdNullAndSkipsAll() {
        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, null, 10, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemId");

        verifyNoInteractions(authorizationService, queryService, codeBasedFacade);
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

        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, workItem.getId(), 0, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("historyLimit");
    }

    // ========== Authorization tests ==========

    @Test
    void authorizationDenialShortCircuitsBusinessDelegation() {
        doThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"))
                .when(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);

        assertThatThrownBy(() -> facade.getDetails(
                TENANT_ID, UUID.randomUUID(), 10, ACTOR_USER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(queryService, codeBasedFacade);
    }

    @Test
    void nullTenantIdSkipsAuthorization() {
        assertThatThrownBy(() -> facade.getDetails(null, UUID.randomUUID(), 10, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
    }

    @Test
    void nullWorkItemIdSkipsAuthorization() {
        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, null, 10, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
    }
}
