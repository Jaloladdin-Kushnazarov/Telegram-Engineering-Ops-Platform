-- Phase 77: Global role catalog write surface uchun kerakli sxema o'zgarishlar.
--
-- 1. role jadvaliga active ustun qo'shish (barcha mavjud rollar default aktiv)
-- 2. audit_event.tenant_id ni nullable qilish (global entity'lar uchun tenant bo'lmaydi)

ALTER TABLE role ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE audit_event ALTER COLUMN tenant_id DROP NOT NULL;
