-- =====================================================================
-- Phase 3: WorkItem, Workflow Transition, Audit modullari
-- =====================================================================

-- =====================================================================
-- WORKITEM MODULE
-- =====================================================================

-- Asosiy operatsion yozuv — bug, incident yoki task
CREATE TABLE work_item (
    id                      UUID        PRIMARY KEY,
    tenant_id               UUID        NOT NULL REFERENCES tenant(id),
    work_item_code          VARCHAR(50)  NOT NULL,
    type_code               VARCHAR(50)  NOT NULL,
    workflow_definition_id  UUID        NOT NULL REFERENCES workflow_definition(id),
    title                   VARCHAR(500) NOT NULL,
    description             TEXT,
    environment_code        VARCHAR(50),
    source_service          VARCHAR(255),
    current_status_code     VARCHAR(100) NOT NULL,
    current_owner_user_id   UUID        REFERENCES app_user(id),
    priority_code           VARCHAR(50),
    severity_code           VARCHAR(50),
    correlation_key         VARCHAR(255),
    opened_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    last_transition_at      TIMESTAMP WITH TIME ZONE,
    resolved_at             TIMESTAMP WITH TIME ZONE,
    reopened_count          INT          NOT NULL DEFAULT 0,
    created_by_user_id      UUID        REFERENCES app_user(id),
    updated_by_user_id      UUID        REFERENCES app_user(id),
    is_archived             BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version                 BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, work_item_code)
);

CREATE INDEX idx_work_item_tenant_id ON work_item(tenant_id);
CREATE INDEX idx_work_item_tenant_status ON work_item(tenant_id, current_status_code);
CREATE INDEX idx_work_item_tenant_type ON work_item(tenant_id, type_code);
CREATE INDEX idx_work_item_owner ON work_item(current_owner_user_id);
CREATE INDEX idx_work_item_correlation ON work_item(correlation_key) WHERE correlation_key IS NOT NULL;

-- Tizimli yangilanish — work item'ga qo'shilgan izoh yoki o'zgarish
CREATE TABLE work_item_update (
    id                  UUID        PRIMARY KEY,
    tenant_id           UUID        NOT NULL REFERENCES tenant(id),
    work_item_id        UUID        NOT NULL REFERENCES work_item(id),
    author_user_id      UUID        REFERENCES app_user(id),
    update_type_code    VARCHAR(50)  NOT NULL,
    body                TEXT,
    visibility_code     VARCHAR(50)  NOT NULL DEFAULT 'INTERNAL',
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_work_item_update_work_item ON work_item_update(work_item_id);
CREATE INDEX idx_work_item_update_tenant ON work_item_update(tenant_id);

-- =====================================================================
-- WORKFLOW MODULE
-- =====================================================================

-- Status o'tish tarixi — har bir holat o'zgarishi qayd qilinadi
CREATE TABLE work_item_transition (
    id                  UUID        PRIMARY KEY,
    tenant_id           UUID        NOT NULL REFERENCES tenant(id),
    work_item_id        UUID        NOT NULL REFERENCES work_item(id),
    from_status_code    VARCHAR(100) NOT NULL,
    to_status_code      VARCHAR(100) NOT NULL,
    actor_user_id       UUID        REFERENCES app_user(id),
    action_source       VARCHAR(50)  NOT NULL DEFAULT 'MANUAL',
    transition_reason   TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_work_item_transition_work_item ON work_item_transition(work_item_id);
CREATE INDEX idx_work_item_transition_tenant ON work_item_transition(tenant_id);

-- =====================================================================
-- AUDIT MODULE
-- =====================================================================

-- Audit event — biznes uchun muhim o'zgarishlar qayd qilinadi (append-only)
CREATE TABLE audit_event (
    id                  UUID        PRIMARY KEY,
    tenant_id           UUID        NOT NULL REFERENCES tenant(id),
    entity_type         VARCHAR(100) NOT NULL,
    entity_id           UUID        NOT NULL,
    event_type          VARCHAR(100) NOT NULL,
    actor_user_id       UUID,
    action_source       VARCHAR(50),
    old_value_json      TEXT,
    new_value_json      TEXT,
    correlation_id      VARCHAR(255),
    occurred_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_event_tenant ON audit_event(tenant_id);
CREATE INDEX idx_audit_event_entity ON audit_event(entity_type, entity_id);
CREATE INDEX idx_audit_event_occurred ON audit_event(occurred_at);
