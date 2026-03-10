-- =====================================================================
-- Phase 2: Identity va Tenant Config modullari uchun jadvallar
-- =====================================================================

-- =====================================================================
-- TENANT-CONFIG MODULE
-- =====================================================================

-- Tenant jadvali — har bir tashkilot (tenant) uchun asosiy yozuv
CREATE TABLE tenant (
    id              UUID        PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(100) NOT NULL UNIQUE,
    timezone        VARCHAR(50)  NOT NULL DEFAULT 'UTC',
    status          VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0
);

-- =====================================================================
-- IDENTITY MODULE
-- =====================================================================

-- Platforma foydalanuvchisi — Telegram identity bilan bog'langan
CREATE TABLE app_user (
    id              UUID        PRIMARY KEY,
    telegram_user_id BIGINT     NOT NULL UNIQUE,
    username        VARCHAR(255),
    display_name    VARCHAR(255),
    status          VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_app_user_telegram_user_id ON app_user(telegram_user_id);

-- Rol — global katalog (tenant'ga bog'liq emas)
-- Rollar membership_role_binding orqali tenant a'zolariga tayinlanadi
CREATE TABLE role (
    id              UUID        PRIMARY KEY,
    code            VARCHAR(100) NOT NULL UNIQUE,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500),
    system_role     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0
);

-- Ruxsat (permission) — tizim darajasida aniqlanadi
CREATE TABLE permission (
    id              UUID        PRIMARY KEY,
    code            VARCHAR(100) NOT NULL UNIQUE,
    description     VARCHAR(500),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0
);

-- Rol va ruxsat bog'lanishi
CREATE TABLE role_permission (
    id              UUID        PRIMARY KEY,
    role_id         UUID        NOT NULL REFERENCES role(id),
    permission_id   UUID        NOT NULL REFERENCES permission(id),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (role_id, permission_id)
);

CREATE INDEX idx_role_permission_role_id ON role_permission(role_id);

-- A'zolik — foydalanuvchini tenantga bog'laydi
CREATE TABLE membership (
    id              UUID        PRIMARY KEY,
    tenant_id       UUID        NOT NULL REFERENCES tenant(id),
    user_id         UUID        NOT NULL REFERENCES app_user(id),
    status          VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, user_id)
);

CREATE INDEX idx_membership_tenant_id ON membership(tenant_id);
CREATE INDEX idx_membership_user_id ON membership(user_id);

-- A'zolik va rol bog'lanishi
CREATE TABLE membership_role_binding (
    id              UUID        PRIMARY KEY,
    membership_id   UUID        NOT NULL REFERENCES membership(id),
    role_id         UUID        NOT NULL REFERENCES role(id),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (membership_id, role_id)
);

CREATE INDEX idx_membership_role_binding_membership_id ON membership_role_binding(membership_id);

-- =====================================================================
-- TENANT-CONFIG MODULE (davomi)
-- =====================================================================

-- Workflow ta'rifi — tenant ichida yaratiladi
CREATE TABLE workflow_definition (
    id              UUID        PRIMARY KEY,
    tenant_id       UUID        NOT NULL REFERENCES tenant(id),
    name            VARCHAR(255) NOT NULL,
    work_item_type  VARCHAR(50)  NOT NULL,
    description     VARCHAR(1000),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, name)
);

CREATE INDEX idx_workflow_definition_tenant_id ON workflow_definition(tenant_id);

-- Workflow holati (status) — workflow_definition ichidagi holatlar
CREATE TABLE workflow_status (
    id                      UUID        PRIMARY KEY,
    workflow_definition_id  UUID        NOT NULL REFERENCES workflow_definition(id),
    name                    VARCHAR(100) NOT NULL,
    status_order            INT          NOT NULL DEFAULT 0,
    initial                 BOOLEAN      NOT NULL DEFAULT FALSE,
    terminal                BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (workflow_definition_id, name)
);

CREATE INDEX idx_workflow_status_definition_id ON workflow_status(workflow_definition_id);

-- Workflow holat o'tish qoidasi
CREATE TABLE workflow_transition_rule (
    id                      UUID        PRIMARY KEY,
    workflow_definition_id  UUID        NOT NULL REFERENCES workflow_definition(id),
    from_status_id          UUID        NOT NULL REFERENCES workflow_status(id),
    to_status_id            UUID        NOT NULL REFERENCES workflow_status(id),
    required_permission_id  UUID        REFERENCES permission(id),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (workflow_definition_id, from_status_id, to_status_id)
);

CREATE INDEX idx_workflow_transition_rule_definition_id ON workflow_transition_rule(workflow_definition_id);

-- Telegram chat bog'lanishi — tenant chatni qaysi guruhga bog'lashi
CREATE TABLE telegram_chat_binding (
    id              UUID        PRIMARY KEY,
    tenant_id       UUID        NOT NULL REFERENCES tenant(id),
    chat_id         BIGINT       NOT NULL,
    chat_title      VARCHAR(500),
    binding_type    VARCHAR(50)  NOT NULL DEFAULT 'MAIN_GROUP',
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, chat_id)
);

CREATE INDEX idx_telegram_chat_binding_tenant_id ON telegram_chat_binding(tenant_id);

-- Telegram topic bog'lanishi — chat ichidagi topic
CREATE TABLE telegram_topic_binding (
    id                      UUID        PRIMARY KEY,
    chat_binding_id         UUID        NOT NULL REFERENCES telegram_chat_binding(id),
    topic_id                BIGINT       NOT NULL,
    topic_name              VARCHAR(500),
    purpose                 VARCHAR(100) NOT NULL,
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (chat_binding_id, topic_id)
);

CREATE INDEX idx_telegram_topic_binding_chat_binding_id ON telegram_topic_binding(chat_binding_id);

-- Yo'naltirish qoidasi — tenant uchun itemlarni qayerga yo'naltirish
CREATE TABLE routing_rule (
    id              UUID        PRIMARY KEY,
    tenant_id       UUID        NOT NULL REFERENCES tenant(id),
    name            VARCHAR(255) NOT NULL,
    work_item_type  VARCHAR(50)  NOT NULL,
    target_topic_binding_id UUID REFERENCES telegram_topic_binding(id),
    priority        INT          NOT NULL DEFAULT 0,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    condition_expression TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_routing_rule_tenant_id ON routing_rule(tenant_id);

-- =====================================================================
-- SEED DATA: Asosiy permission'lar
-- =====================================================================

INSERT INTO permission (id, code, description) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'WORK_ITEM_CREATE', 'Yangi work item yaratish'),
    ('a0000000-0000-0000-0000-000000000002', 'WORK_ITEM_VIEW', 'Work itemlarni ko''rish'),
    ('a0000000-0000-0000-0000-000000000003', 'WORK_ITEM_UPDATE', 'Work itemni yangilash'),
    ('a0000000-0000-0000-0000-000000000004', 'WORK_ITEM_TRANSITION', 'Work item holatini o''zgartirish'),
    ('a0000000-0000-0000-0000-000000000005', 'WORK_ITEM_ASSIGN', 'Work itemni tayinlash'),
    ('a0000000-0000-0000-0000-000000000006', 'TENANT_MANAGE', 'Tenant sozlamalarini boshqarish'),
    ('a0000000-0000-0000-0000-000000000007', 'MEMBER_MANAGE', 'A''zolarni boshqarish'),
    ('a0000000-0000-0000-0000-000000000008', 'ROLE_MANAGE', 'Rollarni boshqarish'),
    ('a0000000-0000-0000-0000-000000000009', 'WORKFLOW_MANAGE', 'Workflow sozlamalarini boshqarish'),
    ('a0000000-0000-0000-0000-00000000000a', 'ROUTING_MANAGE', 'Yo''naltirish qoidalarini boshqarish'),
    ('a0000000-0000-0000-0000-00000000000b', 'ANALYTICS_VIEW', 'Analitikani ko''rish');

-- =====================================================================
-- SEED DATA: Asosiy global rollar
-- =====================================================================

INSERT INTO role (id, code, name, description, system_role) VALUES
    ('b0000000-0000-0000-0000-000000000001', 'ADMIN', 'Administrator', 'Tenant administratori — to''liq boshqaruv huquqi', TRUE),
    ('b0000000-0000-0000-0000-000000000002', 'ENGINEER', 'Engineer', 'Muhandis — work item yaratish va boshqarish', TRUE),
    ('b0000000-0000-0000-0000-000000000003', 'TESTER', 'Tester', 'Tester — testlash va natijalarni qayd qilish', TRUE),
    ('b0000000-0000-0000-0000-000000000004', 'VIEWER', 'Viewer', 'Kuzatuvchi — faqat ko''rish huquqi', TRUE);
