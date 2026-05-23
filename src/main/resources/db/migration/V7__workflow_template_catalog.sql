-- =====================================================================
-- Phase 198: Workflow Template Catalog (global, tenant-agnostic)
-- =====================================================================
-- Global system catalog of workflow templates. Tenant'lar uchun
-- "shablon" sifatida ko'rsatma. Phase 199 onboarding endpoint shu
-- katalogdan o'qib har bir yangi tenant uchun workflow_definition +
-- workflow_status + workflow_transition_rule qatorlarini yaratadi.
--
-- Tenant_id ustuni YO'Q — bu jadval butun tizim uchun bitta katalog.
-- BootstrapAdminInitializer (Phase 156) shu phase'da tegmaydi —
-- bootstrap workflow seed mantiq'i o'z holicha saqlanadi.
-- =====================================================================

CREATE TABLE workflow_template (
    id              UUID         PRIMARY KEY,
    code            VARCHAR(50)  NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,
    description     VARCHAR(1000),
    work_item_type  VARCHAR(50)  NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_workflow_template_work_item_type
        CHECK (work_item_type IN ('BUG', 'INCIDENT', 'TASK'))
);

CREATE TABLE workflow_template_status (
    id              UUID         PRIMARY KEY,
    template_id     UUID         NOT NULL REFERENCES workflow_template(id) ON DELETE CASCADE,
    status_code     VARCHAR(50)  NOT NULL,
    display_name    VARCHAR(200) NOT NULL,
    is_initial      BOOLEAN      NOT NULL DEFAULT FALSE,
    status_order    INT          NOT NULL,
    UNIQUE (template_id, status_code)
);

CREATE INDEX idx_workflow_template_status_template
    ON workflow_template_status(template_id);

-- Eslatma: from_status_code va to_status_code workflow_template_status.status_code
-- ga ishora qiladi, lekin DB darajasida FK constraint YO'Q (V2 workflow_transition_rule
-- shabloniga o'xshamaydi, chunki bu jadvalda status_code STRING orqali ishlatiladi,
-- status_id UUID emas). Composite UNIQUE constraint orqali (template_id + from + to)
-- duplikatlar oldini oladi; from/to mavjudligini Phase 199 JPA tomonida insert
-- vaqtida validatsiya qiladi.
CREATE TABLE workflow_template_transition (
    id                UUID         PRIMARY KEY,
    template_id       UUID         NOT NULL REFERENCES workflow_template(id) ON DELETE CASCADE,
    from_status_code  VARCHAR(50)  NOT NULL,
    to_status_code    VARCHAR(50)  NOT NULL,
    action_label      VARCHAR(100) NOT NULL,
    UNIQUE (template_id, from_status_code, to_status_code)
);

CREATE INDEX idx_workflow_template_transition_template
    ON workflow_template_transition(template_id);

-- =====================================================================
-- SEED DATA: 4 ta tizim shabloni (BUG_MINIMAL, BUG_FULL, INCIDENT_BASIC, TASK_BASIC)
-- =====================================================================

-- ---------------------------------------------------------------------
-- BUG_MINIMAL — mavjud MVP Bug Flow (BootstrapAdminInitializer bilan mos)
-- ---------------------------------------------------------------------
INSERT INTO workflow_template (id, code, name, description, work_item_type) VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-000000000001', 'BUG_MINIMAL',
     'Bug — Minimal lifecycle',
     'MVP bug flow: BUGS -> PROCESSING -> TESTING -> FIXED (with reopen). Mirrors the current bootstrap workflow.',
     'BUG');

INSERT INTO workflow_template_status (id, template_id, status_code, display_name, is_initial, status_order) VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000001', 'BUGS',       'Bugs',       TRUE,  1),
    ('bbbbbbbb-bbbb-bbbb-bbbb-000000000002', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000001', 'PROCESSING', 'Processing', FALSE, 2),
    ('bbbbbbbb-bbbb-bbbb-bbbb-000000000003', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000001', 'TESTING',    'Testing',    FALSE, 3),
    ('bbbbbbbb-bbbb-bbbb-bbbb-000000000004', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000001', 'FIXED',      'Fixed',      FALSE, 4);

INSERT INTO workflow_template_transition (id, template_id, from_status_code, to_status_code, action_label) VALUES
    ('cccccccc-cccc-cccc-cccc-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000001', 'BUGS',       'PROCESSING', 'Start'),
    ('cccccccc-cccc-cccc-cccc-000000000002', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000001', 'PROCESSING', 'TESTING',    'Mark Ready for Test'),
    ('cccccccc-cccc-cccc-cccc-000000000003', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000001', 'TESTING',    'FIXED',      'Mark Fixed'),
    ('cccccccc-cccc-cccc-cccc-000000000004', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000001', 'TESTING',    'BUGS',       'Reopen'),
    ('cccccccc-cccc-cccc-cccc-000000000005', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000001', 'FIXED',      'BUGS',       'Reopen');

-- ---------------------------------------------------------------------
-- BUG_FULL — kengaytirilgan bug lifecycle (triage → close + reopen)
-- ---------------------------------------------------------------------
INSERT INTO workflow_template (id, code, name, description, work_item_type) VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-000000000002', 'BUG_FULL',
     'Bug — Full lifecycle',
     'Expanded bug flow with triage, investigation, work, test, fixed and closed stages plus reopen.',
     'BUG');

INSERT INTO workflow_template_status (id, template_id, status_code, display_name, is_initial, status_order) VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-000000000005', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000002', 'TRIAGE',         'Triage',         TRUE,  1),
    ('bbbbbbbb-bbbb-bbbb-bbbb-000000000006', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000002', 'INVESTIGATING',  'Investigating',  FALSE, 2),
    ('bbbbbbbb-bbbb-bbbb-bbbb-000000000007', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000002', 'IN_PROGRESS',    'In Progress',    FALSE, 3),
    ('bbbbbbbb-bbbb-bbbb-bbbb-000000000008', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000002', 'READY_FOR_TEST', 'Ready for Test', FALSE, 4),
    ('bbbbbbbb-bbbb-bbbb-bbbb-000000000009', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000002', 'TESTING',        'Testing',        FALSE, 5),
    ('bbbbbbbb-bbbb-bbbb-bbbb-00000000000a', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000002', 'FIXED',          'Fixed',          FALSE, 6),
    ('bbbbbbbb-bbbb-bbbb-bbbb-00000000000b', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000002', 'CLOSED',         'Closed',         FALSE, 7);

INSERT INTO workflow_template_transition (id, template_id, from_status_code, to_status_code, action_label) VALUES
    ('cccccccc-cccc-cccc-cccc-000000000006', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000002', 'TRIAGE',         'INVESTIGATING',  'Begin Investigation'),
    ('cccccccc-cccc-cccc-cccc-000000000007', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000002', 'INVESTIGATING',  'IN_PROGRESS',    'Start Work'),
    ('cccccccc-cccc-cccc-cccc-000000000008', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000002', 'IN_PROGRESS',    'READY_FOR_TEST', 'Submit for Test'),
    ('cccccccc-cccc-cccc-cccc-000000000009', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000002', 'READY_FOR_TEST', 'TESTING',        'Begin Testing'),
    ('cccccccc-cccc-cccc-cccc-00000000000a', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000002', 'TESTING',        'FIXED',          'Mark Fixed'),
    ('cccccccc-cccc-cccc-cccc-00000000000b', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000002', 'FIXED',          'CLOSED',         'Close'),
    ('cccccccc-cccc-cccc-cccc-00000000000c', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000002', 'TESTING',        'IN_PROGRESS',    'Send Back'),
    ('cccccccc-cccc-cccc-cccc-00000000000d', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000002', 'CLOSED',         'TRIAGE',         'Reopen');

-- ---------------------------------------------------------------------
-- INCIDENT_BASIC — bazaviy incident lifecycle
-- ---------------------------------------------------------------------
INSERT INTO workflow_template (id, code, name, description, work_item_type) VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-000000000003', 'INCIDENT_BASIC',
     'Incident — Basic lifecycle',
     'Reported -> Investigating -> Mitigating -> Resolved -> Post-Mortem.',
     'INCIDENT');

INSERT INTO workflow_template_status (id, template_id, status_code, display_name, is_initial, status_order) VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-00000000000c', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000003', 'REPORTED',      'Reported',      TRUE,  1),
    ('bbbbbbbb-bbbb-bbbb-bbbb-00000000000d', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000003', 'INVESTIGATING', 'Investigating', FALSE, 2),
    ('bbbbbbbb-bbbb-bbbb-bbbb-00000000000e', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000003', 'MITIGATING',    'Mitigating',    FALSE, 3),
    ('bbbbbbbb-bbbb-bbbb-bbbb-00000000000f', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000003', 'RESOLVED',      'Resolved',      FALSE, 4),
    ('bbbbbbbb-bbbb-bbbb-bbbb-000000000010', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000003', 'POST_MORTEM',   'Post-Mortem',   FALSE, 5);

INSERT INTO workflow_template_transition (id, template_id, from_status_code, to_status_code, action_label) VALUES
    ('cccccccc-cccc-cccc-cccc-00000000000e', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000003', 'REPORTED',      'INVESTIGATING', 'Start Investigation'),
    ('cccccccc-cccc-cccc-cccc-00000000000f', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000003', 'INVESTIGATING', 'MITIGATING',    'Apply Mitigation'),
    ('cccccccc-cccc-cccc-cccc-000000000010', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000003', 'MITIGATING',    'RESOLVED',      'Mark Resolved'),
    ('cccccccc-cccc-cccc-cccc-000000000011', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000003', 'RESOLVED',      'POST_MORTEM',   'Open Post-Mortem');

-- ---------------------------------------------------------------------
-- TASK_BASIC — bazaviy task lifecycle
-- ---------------------------------------------------------------------
INSERT INTO workflow_template (id, code, name, description, work_item_type) VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-000000000004', 'TASK_BASIC',
     'Task — Basic lifecycle',
     'To Do -> In Progress -> Review -> Done (with request-changes loop).',
     'TASK');

INSERT INTO workflow_template_status (id, template_id, status_code, display_name, is_initial, status_order) VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-000000000011', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000004', 'TODO',        'To Do',       TRUE,  1),
    ('bbbbbbbb-bbbb-bbbb-bbbb-000000000012', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000004', 'IN_PROGRESS', 'In Progress', FALSE, 2),
    ('bbbbbbbb-bbbb-bbbb-bbbb-000000000013', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000004', 'REVIEW',      'Review',      FALSE, 3),
    ('bbbbbbbb-bbbb-bbbb-bbbb-000000000014', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000004', 'DONE',        'Done',        FALSE, 4);

INSERT INTO workflow_template_transition (id, template_id, from_status_code, to_status_code, action_label) VALUES
    ('cccccccc-cccc-cccc-cccc-000000000012', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000004', 'TODO',        'IN_PROGRESS', 'Start'),
    ('cccccccc-cccc-cccc-cccc-000000000013', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000004', 'IN_PROGRESS', 'REVIEW',      'Submit for Review'),
    ('cccccccc-cccc-cccc-cccc-000000000014', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000004', 'REVIEW',      'DONE',        'Approve'),
    ('cccccccc-cccc-cccc-cccc-000000000015', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000004', 'REVIEW',      'IN_PROGRESS', 'Request Changes');
