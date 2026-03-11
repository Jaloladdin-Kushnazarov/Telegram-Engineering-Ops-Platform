package com.engops.platform.workitem;

import com.engops.platform.audit.AuditService;
import com.engops.platform.audit.model.AuditEvent;
import com.engops.platform.workitem.model.UpdateType;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import com.engops.platform.workitem.model.WorkItemUpdate;
import com.engops.platform.workitem.repository.WorkItemRepository;
import com.engops.platform.workitem.repository.WorkItemUpdateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WorkItemCommandService unit testlari.
 */
@ExtendWith(MockitoExtension.class)
class WorkItemCommandServiceTest {

    @Mock private WorkItemRepository workItemRepository;
    @Mock private WorkItemUpdateRepository workItemUpdateRepository;
    @Mock private WorkItemCodeGenerator codeGenerator;
    @Mock private AuditService auditService;

    @InjectMocks
    private WorkItemCommandService commandService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID workflowDefId = UUID.randomUUID();

    @Test
    void workItemYaratish() {
        when(codeGenerator.generate(tenantId, WorkItemType.BUG)).thenReturn("BUG-1");
        when(workItemRepository.save(any(WorkItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.recordEvent(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AuditEvent(tenantId, "WORK_ITEM", UUID.randomUUID(), "CREATED", userId));

        WorkItem result = commandService.create(tenantId, WorkItemType.BUG, workflowDefId,
                "Login sahifada xato", "BUGS", userId);

        assertThat(result.getWorkItemCode()).isEqualTo("BUG-1");
        assertThat(result.getTitle()).isEqualTo("Login sahifada xato");
        assertThat(result.getCurrentStatusCode()).isEqualTo("BUGS");
        assertThat(result.getTypeCode()).isEqualTo(WorkItemType.BUG);

        verify(auditService).recordEvent(eq(tenantId), eq("WORK_ITEM"), any(),
                eq("CREATED"), eq(userId), eq(null), eq("BUG-1"));
    }

    @Test
    void ownerTayinlash() {
        UUID workItemId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();

        WorkItem existing = new WorkItem(tenantId, "BUG-1", WorkItemType.BUG,
                workflowDefId, "Test", "BUGS", userId);

        when(workItemRepository.findByTenantIdAndId(tenantId, workItemId))
                .thenReturn(Optional.of(existing));
        when(workItemRepository.save(any(WorkItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workItemUpdateRepository.save(any(WorkItemUpdate.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.recordEvent(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AuditEvent(tenantId, "WORK_ITEM", workItemId, "OWNER_ASSIGNED", userId));

        WorkItem result = commandService.assignOwner(tenantId, workItemId, ownerUserId, userId);

        assertThat(result.getCurrentOwnerUserId()).isEqualTo(ownerUserId);
        verify(auditService).recordEvent(eq(tenantId), eq("WORK_ITEM"), eq(workItemId),
                eq("OWNER_ASSIGNED"), eq(userId), eq(null), eq(ownerUserId.toString()));
    }

    @Test
    void yangilanishQoshish() {
        UUID workItemId = UUID.randomUUID();
        WorkItem existing = new WorkItem(tenantId, "BUG-1", WorkItemType.BUG,
                workflowDefId, "Test", "BUGS", userId);

        when(workItemRepository.findByTenantIdAndId(tenantId, workItemId))
                .thenReturn(Optional.of(existing));
        when(workItemUpdateRepository.save(any(WorkItemUpdate.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkItemUpdate update = commandService.addUpdate(tenantId, workItemId, userId,
                UpdateType.COMMENT, "Bug qayta namoyon bo'ldi");

        assertThat(update.getBody()).isEqualTo("Bug qayta namoyon bo'ldi");
        assertThat(update.getUpdateTypeCode()).isEqualTo(UpdateType.COMMENT);
    }
}
