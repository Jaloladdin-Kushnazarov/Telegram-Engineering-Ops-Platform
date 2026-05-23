-- =====================================================================
-- Phase 199: TENANT_ONBOARD permission catalog + ADMIN role binding
-- =====================================================================
-- Yangi global ruxsat: tenant onboarding (POST /api/admin/tenants) uchun.
-- Bu permission "global" tabiatga ega — onboarding actor'i hali yangi
-- tenantning a'zosi emas, shu sababli per-tenant authorize() ishlamaydi.
-- Yangi OperationalAuthorizationService.authorizeGlobal(actor, permission)
-- shu yorliqni har bir actor uchun barcha aktiv membership'lar
-- kesishishida tekshiradi.
--
-- Permission ID: a0000000-0000-0000-0000-00000000000e (V2 a-prefixed +
-- keyingi raqam). Role-permission binding ID: c0000000-0000-0000-0000-00000000001d
-- (V6 c-prefixed + keyingi raqam).
-- =====================================================================

INSERT INTO permission (id, code, description) VALUES
    ('a0000000-0000-0000-0000-00000000000e', 'TENANT_ONBOARD',
     'Yangi tenant yaratish va boshlang''ich workflow''larini seed qilish');

-- ADMIN role'iga (V2 b0000000-0000-0000-0000-000000000001) TENANT_ONBOARD
-- ruxsatini biriktirish.
INSERT INTO role_permission (id, role_id, permission_id) VALUES
    ('c0000000-0000-0000-0000-00000000001d',
     'b0000000-0000-0000-0000-000000000001',
     'a0000000-0000-0000-0000-00000000000e');
