package com.engops.platform.admin;

import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
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
 * WorkItemDetailsByIdFacade unit testlari.
 *
 * Tekshiruvlar:
 * - UUID -> code resolve -> code-based facade'ga delegation (verify bilan)
 * - resolved code aynan downstream facade'ga uzatiladi
 * - work item topilmasa ResourceNotFoundException + downstream chaqirilMAYDI
 * - null tenantId / workItemId rejected + downstream chaqirilMAYDI
 */
class WorkItemDetailsByIdFacadeTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORKFLOW_DEF_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String WORK_ITEM_CODE = "BUG-1";

    private final WorkItemQueryService queryService = mock(WorkItemQueryService.class);
    private final WorkItemDetailsFacade detailsFacade = mock(WorkItemDetailsFacade.class);
    private final WorkItemDetailsByIdFacade facade =
            new WorkItemDetailsByIdFacade(queryService, detailsFacade);

    @Test
    void resolvesWorkItemIdAndDelegatesWithExactResolvedCode() {
        WorkItem workItem = new WorkItem(
                TENANT_ID, WORK_ITEM_CODE, WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Login xato", "BUGS", null);
        UUID actualId = workItem.getId();

        var expectedView = new WorkItemDetailsFacade.WorkItemDetailsView(workItem, List.of());

        when(queryService.findByTenantAndId(TENANT_ID, actualId))
                .thenReturn(Optional.of(workItem));
        when(detailsFacade.getDetails(TENANT_ID, WORK_ITEM_CODE))
                .thenReturn(expectedView);

        var result = facade.getDetails(TENANT_ID, actualId);

        assertThat(result).isSameAs(expectedView);

        verify(queryService).findByTenantAndId(TENANT_ID, actualId);
        verify(detailsFacade).getDetails(TENANT_ID, WORK_ITEM_CODE);
        verifyNoMoreInteractions(queryService, detailsFacade);
    }

    @Test
    void throwsResourceNotFoundAndSkipsDownstreamWhenWorkItemMissing() {
        UUID unknownId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        when(queryService.findByTenantAndId(TENANT_ID, unknownId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, unknownId))
                .isInstanceOf(ResourceNotFoundException.class);

        // queryService chaqirilganini, lekin detailsFacade chaqirilMAGANini isbotlash
        verify(queryService).findByTenantAndId(TENANT_ID, unknownId);
        verifyNoMoreInteractions(queryService);
        verifyNoInteractions(detailsFacade);
    }

    @Test
    void throwsIllegalArgumentWhenTenantIdNullAndSkipsAll() {
        assertThatThrownBy(() -> facade.getDetails(null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(queryService, detailsFacade);
    }

    @Test
    void throwsIllegalArgumentWhenWorkItemIdNullAndSkipsAll() {
        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemId");

        verifyNoInteractions(queryService, detailsFacade);
    }
}
