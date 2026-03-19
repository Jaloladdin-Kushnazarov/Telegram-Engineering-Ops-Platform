package com.engops.platform.admin;

import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.workitem.WorkItemQueryService;
import com.engops.platform.workitem.model.UpdateType;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import com.engops.platform.workitem.model.WorkItemUpdate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WorkItemDetailsFacade unit testlari.
 *
 * Tekshiruvlar:
 * - success path: work item + update history qaytariladi
 * - not-found: ResourceNotFoundException
 * - null tenantId: IllegalArgumentException
 * - blank workItemCode: IllegalArgumentException
 */
class WorkItemDetailsFacadeTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORKFLOW_DEF_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID AUTHOR_USER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String WORK_ITEM_CODE = "BUG-1";

    private final WorkItemQueryService queryService = mock(WorkItemQueryService.class);
    private final WorkItemDetailsFacade facade = new WorkItemDetailsFacade(queryService);

    @Test
    void returnsWorkItemWithUpdates() {
        WorkItem workItem = new WorkItem(
                TENANT_ID, WORK_ITEM_CODE, WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Login xato", "BUGS", null);

        WorkItemUpdate update = new WorkItemUpdate(
                TENANT_ID, workItem.getId(), AUTHOR_USER_ID,
                UpdateType.COMMENT, "Tekshirilmoqda");

        when(queryService.findByTenantAndCode(TENANT_ID, WORK_ITEM_CODE))
                .thenReturn(Optional.of(workItem));
        when(queryService.listUpdates(TENANT_ID, workItem.getId()))
                .thenReturn(List.of(update));

        WorkItemDetailsFacade.WorkItemDetailsView result =
                facade.getDetails(TENANT_ID, WORK_ITEM_CODE);

        assertThat(result.workItem()).isSameAs(workItem);
        assertThat(result.updates()).hasSize(1);
        assertThat(result.updates().get(0)).isSameAs(update);
    }

    @Test
    void returnsEmptyUpdatesWhenNone() {
        WorkItem workItem = new WorkItem(
                TENANT_ID, WORK_ITEM_CODE, WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Login xato", "BUGS", null);

        when(queryService.findByTenantAndCode(TENANT_ID, WORK_ITEM_CODE))
                .thenReturn(Optional.of(workItem));
        when(queryService.listUpdates(TENANT_ID, workItem.getId()))
                .thenReturn(List.of());

        WorkItemDetailsFacade.WorkItemDetailsView result =
                facade.getDetails(TENANT_ID, WORK_ITEM_CODE);

        assertThat(result.workItem()).isSameAs(workItem);
        assertThat(result.updates()).isEmpty();
    }

    @Test
    void throwsResourceNotFoundWhenWorkItemMissing() {
        when(queryService.findByTenantAndCode(TENANT_ID, "NONEXISTENT-99"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, "NONEXISTENT-99"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void throwsIllegalArgumentWhenTenantIdNull() {
        assertThatThrownBy(() -> facade.getDetails(null, WORK_ITEM_CODE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void throwsIllegalArgumentWhenWorkItemCodeNull() {
        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemCode");
    }

    @Test
    void throwsIllegalArgumentWhenWorkItemCodeBlank() {
        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemCode");
    }
}
