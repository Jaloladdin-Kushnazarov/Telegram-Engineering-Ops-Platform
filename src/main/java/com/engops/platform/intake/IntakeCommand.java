package com.engops.platform.intake;

import com.engops.platform.workitem.model.WorkItemType;

import java.util.UUID;

/**
 * Intake application command — yangi work item yaratish uchun kiruvchi buyruq.
 *
 * Bu controller DTO emas, application-level command object.
 * Tashqi adapterlar (Telegram, REST, integration) shu object orqali intake layer'ga murojaat qiladi.
 *
 * workflowDefinitionId va initialStatusCode ixtiyoriy:
 * - workflowDefinitionId berilmasa — tenant va typeCode bo'yicha active workflow avtomatik topiladi
 * - initialStatusCode berilmasa — workflow definition'dagi initial status avtomatik aniqlanadi
 */
public class IntakeCommand {

    private final UUID tenantId;
    private final WorkItemType typeCode;
    private final String title;
    private final String description;
    private final UUID workflowDefinitionId;
    private final String initialStatusCode;
    private final UUID createdByUserId;
    private final String actionSource;

    private IntakeCommand(Builder builder) {
        this.tenantId = builder.tenantId;
        this.typeCode = builder.typeCode;
        this.title = builder.title;
        this.description = builder.description;
        this.workflowDefinitionId = builder.workflowDefinitionId;
        this.initialStatusCode = builder.initialStatusCode;
        this.createdByUserId = builder.createdByUserId;
        this.actionSource = builder.actionSource;
    }

    public UUID getTenantId() { return tenantId; }
    public WorkItemType getTypeCode() { return typeCode; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public UUID getWorkflowDefinitionId() { return workflowDefinitionId; }
    public String getInitialStatusCode() { return initialStatusCode; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public String getActionSource() { return actionSource; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID tenantId;
        private WorkItemType typeCode;
        private String title;
        private String description;
        private UUID workflowDefinitionId;
        private String initialStatusCode;
        private UUID createdByUserId;
        private String actionSource;

        public Builder tenantId(UUID tenantId) { this.tenantId = tenantId; return this; }
        public Builder typeCode(WorkItemType typeCode) { this.typeCode = typeCode; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder workflowDefinitionId(UUID id) { this.workflowDefinitionId = id; return this; }
        public Builder initialStatusCode(String code) { this.initialStatusCode = code; return this; }
        public Builder createdByUserId(UUID id) { this.createdByUserId = id; return this; }
        public Builder actionSource(String source) { this.actionSource = source; return this; }

        public IntakeCommand build() {
            return new IntakeCommand(this);
        }
    }
}
