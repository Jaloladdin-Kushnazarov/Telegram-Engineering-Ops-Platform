package com.engops.platform.admin;

import com.engops.platform.workitem.WorkItemQueryService;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WorkItemSummaryFacade unit testlari.
 *
 * Tekshiruvlar:
 * - kompakt mapping to'g'riligi
 * - limit siyosati (1..50)
 * - bo'sh ro'yxat valid natija
 * - null tenantId rejected
 * - noto'g'ri limit rejected
 */
class WorkItemSummaryFacadeTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORKFLOW_DEF_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OWNER_USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private final WorkItemQueryService queryService = mock(WorkItemQueryService.class);
    private final WorkItemSummaryFacade facade = new WorkItemSummaryFacade(queryService);

    @Test
    void returnsCompactMappedList() {
        WorkItem wi = new WorkItem(
                TENANT_ID, "BUG-1", WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Login xato", "BUGS", null);
        wi.setPriorityCode("HIGH");
        wi.setSeverityCode("CRITICAL");
        wi.assignOwner(OWNER_USER_ID);

        when(queryService.listActiveByTenant(TENANT_ID, 20)).thenReturn(List.of(wi));

        List<WorkItemSummaryItem> result = facade.getSummaryList(TENANT_ID, 20);

        assertThat(result).hasSize(1);
        WorkItemSummaryItem item = result.get(0);
        assertThat(item.workItemId()).isEqualTo(wi.getId());
        assertThat(item.workItemCode()).isEqualTo("BUG-1");
        assertThat(item.title()).isEqualTo("Login xato");
        assertThat(item.typeCode()).isEqualTo(WorkItemType.BUG);
        assertThat(item.currentStatusCode()).isEqualTo("BUGS");
        assertThat(item.priorityCode()).isEqualTo("HIGH");
        assertThat(item.severityCode()).isEqualTo("CRITICAL");
        assertThat(item.currentOwnerUserId()).isEqualTo(OWNER_USER_ID);
        assertThat(item.openedAt()).isNotNull();
        assertThat(item.reopenedCount()).isZero();
        assertThat(item.archived()).isFalse();
    }

    @Test
    void respectsLimitThroughCappedQueryPath() {
        WorkItem wi1 = new WorkItem(TENANT_ID, "BUG-1", WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Bug 1", "BUGS", null);
        WorkItem wi2 = new WorkItem(TENANT_ID, "BUG-2", WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Bug 2", "BUGS", null);

        when(queryService.listActiveByTenant(TENANT_ID, 5)).thenReturn(List.of(wi1, wi2));

        List<WorkItemSummaryItem> result = facade.getSummaryList(TENANT_ID, 5);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).workItemCode()).isEqualTo("BUG-1");
        assertThat(result.get(1).workItemCode()).isEqualTo("BUG-2");
    }

    @Test
    void returnsEmptyListWhenNoActiveItems() {
        when(queryService.listActiveByTenant(TENANT_ID, 20)).thenReturn(List.of());

        List<WorkItemSummaryItem> result = facade.getSummaryList(TENANT_ID, 20);

        assertThat(result).isEmpty();
    }

    @Test
    void throwsWhenTenantIdNull() {
        assertThatThrownBy(() -> facade.getSummaryList(null, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void throwsWhenLimitZero() {
        assertThatThrownBy(() -> facade.getSummaryList(TENANT_ID, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void throwsWhenLimitExceedsMax() {
        assertThatThrownBy(() -> facade.getSummaryList(TENANT_ID, 51))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void mapsNullableFieldsCorrectly() {
        WorkItem wi = new WorkItem(
                TENANT_ID, "TASK-1", WorkItemType.TASK,
                WORKFLOW_DEF_ID, "Simple task", "OPEN", null);
        // priorityCode, severityCode, currentOwnerUserId — all null

        when(queryService.listActiveByTenant(TENANT_ID, 10)).thenReturn(List.of(wi));

        List<WorkItemSummaryItem> result = facade.getSummaryList(TENANT_ID, 10);

        assertThat(result).hasSize(1);
        WorkItemSummaryItem item = result.get(0);
        assertThat(item.priorityCode()).isNull();
        assertThat(item.severityCode()).isNull();
        assertThat(item.currentOwnerUserId()).isNull();
        assertThat(item.lastTransitionAt()).isNull();
        assertThat(item.resolvedAt()).isNull();
    }
}
