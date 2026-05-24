-- =====================================================================
-- Phase 215: 4-tier RBAC schema kengaytmasi (PURELY ADDITIVE)
-- =====================================================================
-- Yangi rollar va ruxsatlar qo'shiladi:
--   PLATFORM_OWNER — platforma darajasidagi superuser (tenant'larni
--                    yaratish, suspend qilish, o'chirish, list qilish)
--   TENANT_OWNER   — tenant egasi (TENANT_ADMIN huquqlari + danger
--                    zone: tenant delete, billing, ownership transfer)
--
-- Yangi jadval:
--   app_user_role_binding — tenant'siz, platform-level role binding.
--     Membership jadvali tenant_id NOT NULL talab qiladi, lekin
--     PLATFORM_OWNER hech qaysi tenantning a'zosi emas. Shu sababli
--     alohida tenantless binding mexanizmi qo'shildi (membership
--     schema'siga ta'sir qilmasdan).
--
-- MUHIM: Bu migration FAQAT QO'SHIMCHA — hech qanday DELETE/UPDATE
-- statement yo'q. Mavjud ADMIN role'iga biriktirilgan TENANT_ONBOARD
-- ruxsati saqlanadi (Phase 216 V10 atomik ravishda olib tashlaydi
-- va DevBootstrapInitializer'ni yangilaydi).
--
-- Sentinel UUID davom etishi (oldingi migration'lardan):
--   Role IDs:        b0000000-0000-0000-0000-000000000001..6
--   Permission IDs:  a0000000-0000-0000-0000-000000000001..0015
--   role_perm IDs:   c0000000-0000-0000-0000-000000000001..0032
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) Yangi jadval: app_user_role_binding
-- ---------------------------------------------------------------------
-- Tenant'siz, platform-level rol biriktirish. PLATFORM_OWNER bu jadval
-- orqali biriktiriladi. Membership jadvali tenant_id NOT NULL bo'lib
-- qoladi — schema invariant'ini buzmaslik uchun yangi jadval.
CREATE TABLE app_user_role_binding (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES app_user(id),
    role_id     UUID        NOT NULL REFERENCES role(id),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (user_id, role_id)
);

CREATE INDEX idx_app_user_role_binding_user_id
    ON app_user_role_binding(user_id);

-- ---------------------------------------------------------------------
-- 2) Yangi rollar: PLATFORM_OWNER va TENANT_OWNER
-- ---------------------------------------------------------------------
INSERT INTO role (id, code, name, description, system_role, active) VALUES
    ('b0000000-0000-0000-0000-000000000005', 'PLATFORM_OWNER',
     'Platform Owner', 'Barcha tenantlarni boshqarish, platform sozlamalari',
     TRUE, TRUE),
    ('b0000000-0000-0000-0000-000000000006', 'TENANT_OWNER',
     'Tenant Owner',
     'Tenant egasi — danger zone (delete, billing, ownership transfer)',
     TRUE, TRUE);

-- ---------------------------------------------------------------------
-- 3) Yangi ruxsatlar (7 ta)
-- ---------------------------------------------------------------------
INSERT INTO permission (id, code, description) VALUES
    ('a0000000-0000-0000-0000-00000000000f', 'PLATFORM_TENANT_LIST',
     'Platforma darajasida barcha tenantlarni ko''rish'),
    ('a0000000-0000-0000-0000-000000000010', 'PLATFORM_TENANT_SUSPEND',
     'Tenantni vaqtincha to''xtatish'),
    ('a0000000-0000-0000-0000-000000000011', 'PLATFORM_TENANT_DELETE',
     'Tenantni butunlay o''chirish (danger zone)'),
    ('a0000000-0000-0000-0000-000000000012', 'PLATFORM_OWNER_GRANT',
     'Boshqa foydalanuvchilarga PLATFORM_OWNER role berish'),
    ('a0000000-0000-0000-0000-000000000013', 'TENANT_DELETE',
     'O''z tenantini o''chirish (danger zone)'),
    ('a0000000-0000-0000-0000-000000000014', 'TENANT_BILLING_MANAGE',
     'Tenant billing va subscription sozlamalari'),
    ('a0000000-0000-0000-0000-000000000015', 'TENANT_OWNERSHIP_TRANSFER',
     'Tenant ownership''ni boshqa foydalanuvchiga ko''chirish');

-- ---------------------------------------------------------------------
-- 4) PLATFORM_OWNER role_permission bindings (5 ta)
-- ---------------------------------------------------------------------
-- PLATFORM_OWNER ruxsatlari:
--   TENANT_ONBOARD          (V8'dan, yangi binding shu yerda)
--   PLATFORM_TENANT_LIST    (V9 yangi)
--   PLATFORM_TENANT_SUSPEND (V9 yangi)
--   PLATFORM_TENANT_DELETE  (V9 yangi)
--   PLATFORM_OWNER_GRANT    (V9 yangi)
INSERT INTO role_permission (id, role_id, permission_id) VALUES
    ('c0000000-0000-0000-0000-00000000001e',
     'b0000000-0000-0000-0000-000000000005',
     'a0000000-0000-0000-0000-00000000000e'),  -- TENANT_ONBOARD
    ('c0000000-0000-0000-0000-00000000001f',
     'b0000000-0000-0000-0000-000000000005',
     'a0000000-0000-0000-0000-00000000000f'),  -- PLATFORM_TENANT_LIST
    ('c0000000-0000-0000-0000-000000000020',
     'b0000000-0000-0000-0000-000000000005',
     'a0000000-0000-0000-0000-000000000010'),  -- PLATFORM_TENANT_SUSPEND
    ('c0000000-0000-0000-0000-000000000021',
     'b0000000-0000-0000-0000-000000000005',
     'a0000000-0000-0000-0000-000000000011'),  -- PLATFORM_TENANT_DELETE
    ('c0000000-0000-0000-0000-000000000022',
     'b0000000-0000-0000-0000-000000000005',
     'a0000000-0000-0000-0000-000000000012'); -- PLATFORM_OWNER_GRANT

-- ---------------------------------------------------------------------
-- 5) TENANT_OWNER role_permission bindings (16 ta)
-- ---------------------------------------------------------------------
-- TENANT_OWNER — TENANT_ADMIN sirti + danger zone qo'shimchalari.
-- TENANT_ONBOARD ESHITILMAYDI (platform-level only).
--
-- Operational (5): WORK_ITEM_CREATE / VIEW / UPDATE / TRANSITION / ASSIGN
-- Catalog (5):     TENANT_MANAGE / MEMBER_MANAGE / ROLE_MANAGE /
--                  WORKFLOW_MANAGE / ROUTING_MANAGE
-- Analytics (1):   ANALYTICS_VIEW
-- Tenant cfg (2):  TENANT_CONFIG_READ / TENANT_CONFIG_WRITE
-- Danger (3):      TENANT_DELETE / TENANT_BILLING_MANAGE /
--                  TENANT_OWNERSHIP_TRANSFER  (V9 yangi)
INSERT INTO role_permission (id, role_id, permission_id) VALUES
    -- Operational permissions (V2 catalog)
    ('c0000000-0000-0000-0000-000000000023',
     'b0000000-0000-0000-0000-000000000006',
     'a0000000-0000-0000-0000-000000000001'),  -- WORK_ITEM_CREATE
    ('c0000000-0000-0000-0000-000000000024',
     'b0000000-0000-0000-0000-000000000006',
     'a0000000-0000-0000-0000-000000000002'),  -- WORK_ITEM_VIEW
    ('c0000000-0000-0000-0000-000000000025',
     'b0000000-0000-0000-0000-000000000006',
     'a0000000-0000-0000-0000-000000000003'),  -- WORK_ITEM_UPDATE
    ('c0000000-0000-0000-0000-000000000026',
     'b0000000-0000-0000-0000-000000000006',
     'a0000000-0000-0000-0000-000000000004'),  -- WORK_ITEM_TRANSITION
    ('c0000000-0000-0000-0000-000000000027',
     'b0000000-0000-0000-0000-000000000006',
     'a0000000-0000-0000-0000-000000000005'),  -- WORK_ITEM_ASSIGN
    -- Catalog management permissions (V2)
    ('c0000000-0000-0000-0000-000000000028',
     'b0000000-0000-0000-0000-000000000006',
     'a0000000-0000-0000-0000-000000000006'),  -- TENANT_MANAGE
    ('c0000000-0000-0000-0000-000000000029',
     'b0000000-0000-0000-0000-000000000006',
     'a0000000-0000-0000-0000-000000000007'),  -- MEMBER_MANAGE
    ('c0000000-0000-0000-0000-00000000002a',
     'b0000000-0000-0000-0000-000000000006',
     'a0000000-0000-0000-0000-000000000008'),  -- ROLE_MANAGE
    ('c0000000-0000-0000-0000-00000000002b',
     'b0000000-0000-0000-0000-000000000006',
     'a0000000-0000-0000-0000-000000000009'),  -- WORKFLOW_MANAGE
    ('c0000000-0000-0000-0000-00000000002c',
     'b0000000-0000-0000-0000-000000000006',
     'a0000000-0000-0000-0000-00000000000a'),  -- ROUTING_MANAGE
    -- Analytics + tenant config (V2/V6)
    ('c0000000-0000-0000-0000-00000000002d',
     'b0000000-0000-0000-0000-000000000006',
     'a0000000-0000-0000-0000-00000000000b'),  -- ANALYTICS_VIEW
    ('c0000000-0000-0000-0000-00000000002e',
     'b0000000-0000-0000-0000-000000000006',
     'a0000000-0000-0000-0000-00000000000c'),  -- TENANT_CONFIG_READ
    ('c0000000-0000-0000-0000-00000000002f',
     'b0000000-0000-0000-0000-000000000006',
     'a0000000-0000-0000-0000-00000000000d'),  -- TENANT_CONFIG_WRITE
    -- Danger zone (V9 yangi)
    ('c0000000-0000-0000-0000-000000000030',
     'b0000000-0000-0000-0000-000000000006',
     'a0000000-0000-0000-0000-000000000013'),  -- TENANT_DELETE
    ('c0000000-0000-0000-0000-000000000031',
     'b0000000-0000-0000-0000-000000000006',
     'a0000000-0000-0000-0000-000000000014'),  -- TENANT_BILLING_MANAGE
    ('c0000000-0000-0000-0000-000000000032',
     'b0000000-0000-0000-0000-000000000006',
     'a0000000-0000-0000-0000-000000000015'); -- TENANT_OWNERSHIP_TRANSFER
