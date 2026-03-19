package com.engops.platform.workitem;

import com.engops.platform.workitem.model.UpdateType;
import com.engops.platform.workitem.model.WorkItemUpdate;
import com.engops.platform.workitem.repository.WorkItemRepository;
import com.engops.platform.workitem.repository.WorkItemUpdateRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * WorkItemQueryService unit testlari.
 *
 * listUpdates metodining deterministic ordered repository
 * metodiga delegatsiyasini isbotlaydi.
 */
class WorkItemQueryServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORK_ITEM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final WorkItemRepository workItemRepository = mock(WorkItemRepository.class);
    private final WorkItemUpdateRepository updateRepository = mock(WorkItemUpdateRepository.class);
    private final WorkItemQueryService queryService =
            new WorkItemQueryService(workItemRepository, updateRepository);

    @Test
    void listUpdates_delegatesToOrderedRepositoryMethod() {
        WorkItemUpdate update = new WorkItemUpdate(
                TENANT_ID, WORK_ITEM_ID, null, UpdateType.COMMENT, "Test");

        when(updateRepository.findByTenantIdAndWorkItemIdOrderByCreatedAtAscIdAsc(
                TENANT_ID, WORK_ITEM_ID))
                .thenReturn(List.of(update));

        List<WorkItemUpdate> result = queryService.listUpdates(TENANT_ID, WORK_ITEM_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isSameAs(update);

        // Ordered metod chaqirilganini isbotlash
        verify(updateRepository).findByTenantIdAndWorkItemIdOrderByCreatedAtAscIdAsc(
                TENANT_ID, WORK_ITEM_ID);

        // Tartibsiz metod CHAQIRILMAGANINI isbotlash
        verify(updateRepository, never()).findByTenantIdAndWorkItemId(any(), any());
    }
}
