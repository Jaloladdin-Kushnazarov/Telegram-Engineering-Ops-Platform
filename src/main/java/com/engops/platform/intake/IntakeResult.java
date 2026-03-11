package com.engops.platform.intake;

import java.util.UUID;

/**
 * Intake natijasi — yaratilgan work item haqida structured javob.
 * Controller yoki adapter shu natijani o'z formatiga moslashtiradi.
 */
public class IntakeResult {

    private final UUID workItemId;
    private final String workItemCode;
    private final String currentStatusCode;
    private final UUID workflowDefinitionId;
    private final UUID tenantId;

    public IntakeResult(UUID workItemId, String workItemCode, String currentStatusCode,
                        UUID workflowDefinitionId, UUID tenantId) {
        this.workItemId = workItemId;
        this.workItemCode = workItemCode;
        this.currentStatusCode = currentStatusCode;
        this.workflowDefinitionId = workflowDefinitionId;
        this.tenantId = tenantId;
    }

    public UUID getWorkItemId() { return workItemId; }
    public String getWorkItemCode() { return workItemCode; }
    public String getCurrentStatusCode() { return currentStatusCode; }
    public UUID getWorkflowDefinitionId() { return workflowDefinitionId; }
    public UUID getTenantId() { return tenantId; }
}
