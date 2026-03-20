package com.engops.platform.admin;

import com.engops.platform.workitem.WorkItemQueryService;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * WorkItemSummaryByOwnerFacade unit testlari.
 */
class WorkItemSummaryByOwnerFacadeTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OWNER_USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID WORKFLOW_DEF_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final WorkItemQueryService queryService = mock(WorkItemQueryService.class);
    private final WorkItemSummaryByOwnerFacade facade =
            new WorkItemSummaryByOwnerFacade(queryService);

    @Test
    void returnsMappedCompactList() {
        WorkItem wi = new WorkItem(
                TENANT_ID, "BUG-1", WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Login xato", "BUGS", null);
        wi.setPriorityCode("HIGH");
        wi.assignOwner(OWNER_USER_ID);

        when(queryService.listActiveByTenantAndOwner(TENANT_ID, OWNER_USER_ID, 20))
                .thenReturn(List.of(wi));

        List<WorkItemSummaryItem> result = facade.getSummaryList(TENANT_ID, OWNER_USER_ID, 20);

        assertThat(result).hasSize(1);
        WorkItemSummaryItem item = result.get(0);
        assertThat(item.workItemId()).isEqualTo(wi.getId());
        assertThat(item.workItemCode()).isEqualTo("BUG-1");
        assertThat(item.title()).isEqualTo("Login xato");
        assertThat(item.typeCode()).isEqualTo(WorkItemType.BUG);
        assertThat(item.currentStatusCode()).isEqualTo("BUGS");
        assertThat(item.priorityCode()).isEqualTo("HIGH");
        assertThat(item.currentOwnerUserId()).isEqualTo(OWNER_USER_ID);
    }

    @Test
    void respectsCappedQueryPath() {
        when(queryService.listActiveByTenantAndOwner(TENANT_ID, OWNER_USER_ID, 5))
                .thenReturn(List.of());

        facade.getSummaryList(TENANT_ID, OWNER_USER_ID, 5);

        verify(queryService).listActiveByTenantAndOwner(TENANT_ID, OWNER_USER_ID, 5);
        verifyNoMoreInteractions(queryService);
    }

    @Test
    void returnsEmptyListWhenNoMatches() {
        when(queryService.listActiveByTenantAndOwner(TENANT_ID, OWNER_USER_ID, 20))
                .thenReturn(List.of());

        List<WorkItemSummaryItem> result = facade.getSummaryList(TENANT_ID, OWNER_USER_ID, 20);

        assertThat(result).isEmpty();
    }

    @Test
    void rejectsNullTenantId() {
        assertThatThrownBy(() -> facade.getSummaryList(null, OWNER_USER_ID, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
        verifyNoInteractions(queryService);
    }

    @Test
    void rejectsNullOwnerUserId() {
        assertThatThrownBy(() -> facade.getSummaryList(TENANT_ID, null, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ownerUserId");
        verifyNoInteractions(queryService);
    }

    @Test
    void rejectsLimitZero() {
        assertThatThrownBy(() -> facade.getSummaryList(TENANT_ID, OWNER_USER_ID, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
        verifyNoInteractions(queryService);
    }

    @Test
    void rejectsLimitAboveMax() {
        assertThatThrownBy(() -> facade.getSummaryList(TENANT_ID, OWNER_USER_ID, 51))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
        verifyNoInteractions(queryService);
    }

    @Test
    void mapsNullableFieldsCorrectly() {
        WorkItem wi = new WorkItem(
                TENANT_ID, "TASK-1", WorkItemType.TASK,
                WORKFLOW_DEF_ID, "Simple task", "OPEN", null);

        when(queryService.listActiveByTenantAndOwner(TENANT_ID, OWNER_USER_ID, 10))
                .thenReturn(List.of(wi));

        List<WorkItemSummaryItem> result = facade.getSummaryList(TENANT_ID, OWNER_USER_ID, 10);

        assertThat(result).hasSize(1);
        WorkItemSummaryItem item = result.get(0);
        assertThat(item.priorityCode()).isNull();
        assertThat(item.severityCode()).isNull();
        assertThat(item.currentOwnerUserId()).isNull();
        assertThat(item.lastTransitionAt()).isNull();
        assertThat(item.resolvedAt()).isNull();
    }
}
