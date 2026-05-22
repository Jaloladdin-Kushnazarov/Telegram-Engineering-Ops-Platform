# INCIDENT / TASK Workflow Template Runbook (Phase 190)

> **Phase 190.** Operator uchun INCIDENT va TASK work item turlari uchun
> workflow definition'larini mavjud tenant-config admin API orqali qo'lda
> yaratish bo'yicha namuna retseptlari. Hech qanday yangi backend kod,
> schema migration, yoki bootstrap auto-seed shipping qilinmadi —
> faqat operator-runnable `curl` template'lar.

---

## 1. Maqsad va auditoriya

**Maqsad.** Real jamoa qo'lida turgan tenant'da BUG'dan tashqari ikkita
work item turi uchun (INCIDENT va TASK) workflow definition + status'lar +
transition rule'larini hujjat asosida yaratish. Bu runbook **template'larni**
beradi — hard-coded product truth emas. Operator template'larni o'z
operatsion modeliga moslab tahrir qilishi mumkin.

**Auditoriya.**
- **Tenant admin operator** — admin JWT bilan tenant'ni boshqaradi.
- **Backend owner** — yangi tenant onboarding paytida operatorga yo'l
  ko'rsatuvchi.
- **SRE** — incident response loop'ini Telegram bilan integratsiyalashtirmoqchi
  bo'lgan.

**Phase 190 doirasi.** Bu phase WorkItem domain'iga admin write surface
(owner / priority / severity update) qo'shdi. INCIDENT/TASK workflow
auto-seed **ataylab kiritilmadi** — sabablari §3 da.

---

## 2. Phase 190 va undan oldingi holat

**Mavjud bo'lganlar (Phase 190 dan oldin):**

- `WorkItemType` enum **BUG / INCIDENT / TASK** ni qo'llaydi — domain
  darajasida hech qanday cheklov yo'q.
- `BootstrapAdminInitializer` (Phase 156) **faqat BUG** workflow'ni
  idempotent seed qiladi ("MVP Bug Flow":
  `BUGS → PROCESSING → TESTING → FIXED`, `TESTING → BUGS` return,
  `FIXED → BUGS` reopen).
- Admin tenant-config API (`/api/admin/tenant-config/workflow-definitions`)
  **ixtiyoriy work_item_type** (BUG / INCIDENT / TASK) uchun workflow
  yaratishni qo'llaydi — `TenantConfigWriteFacade.ALLOWED_WORK_ITEM_TYPES`
  uchchalasini ham ruxsat etadi.
- `IntakeApplicationService` har qanday tenant uchun typeCode bo'yicha
  **aktiv** workflow'ni avtomatik tanlaydi (`findActiveWorkflowDefinitionsByType`).
- `TelegramActionAssembler` (Phase 173 vintage) **faqat BUG** uchun
  inline action'larni render qiladi. INCIDENT/TASK uchun bo'sh action
  list qaytariladi (valid holat — card text matn ko'rinadi, lekin
  inline tugma yo'q).

**Phase 190 nima qildi:**

- `OperationalAuthorizationService.authorizeUpdate(...)` /
  `authorizeAssignOwner(...)` qo'shildi.
- `WorkItemCommandService.updatePriority(...)` / `updateSeverity(...)`
  qo'shildi.
- 3 ta admin POST endpoint:
  `POST /api/admin/work-items/{workItemId}/owner` /
  `POST /api/admin/work-items/{workItemId}/priority` /
  `POST /api/admin/work-items/{workItemId}/severity`.
- Audit eventType'lar: `OWNER_ASSIGNED` (mavjud), `PRIORITY_CHANGED`,
  `SEVERITY_CHANGED` (Phase 190 da yangi).
- Shu runbook (operator template'lari).

**Phase 190 nima qilmadi (ataylab):**

- INCIDENT/TASK **auto-seed YO'Q.** `BootstrapAdminInitializer` va
  `BootstrapWorkflowProperties` saqlanadi (faqat BUG).
- Telegram card rendering INCIDENT/TASK uchun **o'zgartirilmadi**.
- `TelegramActionAssembler` INCIDENT/TASK action'lari **qo'shilmadi**.
- Schema migration **YO'Q.**

---

## 3. Nima uchun auto-seed emas, template

`BootstrapAdminInitializer` da INCIDENT va TASK uchun ham
default workflow seed qo'shish texnik jihatdan mumkin edi. Lekin ataylab
**rad etildi** — sabablari:

1. **CLAUDE.md "Do not invent a generic BPM/workflow engine".** INCIDENT
   va TASK uchun "to'g'ri" workflow shape (qaysi statuslar, qaysi
   transitionlar) loyihada **frozen** decision sifatida belgilanmagan.
   BUG uchun shape CLAUDE.md ichida explicit yozilgan
   (`BUGS → PROCESSING → TESTING → FIXED`); INCIDENT/TASK uchun yo'q.
   Birinchi tenant operatori uchun "default" deb seed qilingan har qanday
   shape kelajakdagi tenant'lar uchun **migration og'irligini** keltirib
   chiqaradi (har bir tenant alohida o'z statuslarini tahrir qilishi
   kerak bo'ladi).
2. **Multi-tenant haqiqat.** Bitta jamoa "MITIGATING" qadamini xohlaydi,
   boshqa jamoa "TRIAGING"ni xohlaydi. Hard-coded seed bu farqlarni
   bostiradi.
3. **Idempotensiya xavfsizroq.** Operator runbook'ga ko'ra qo'lda
   yaratgan workflow keyingi version'larda code ozgarishidan ozod
   qoladi — Flyway migration'iga bog'liq emas. Operator istalgan vaqt
   workflow shape'ini admin API orqali boshqaradi.

Phase 190 yo'li: operator template'larga ega; auto-seed YO'Q;
admin API orqali har qanday shape yaratish mumkin.

---

## 4. Boshlanishdan oldin

Quyidagilar sozlangan bo'lishi shart (Phase 187 deployment runbook'iga
ko'ra):

- Tenant `<TENANT_ID>` bootstrap orqali yaratilgan
  (`BootstrapAdminInitializer` + V6 default role-permission seed).
- Admin actor `<ACTOR_USER_ID>` `TENANT_CONFIG_WRITE` permission'iga ega
  bo'lgan ADMIN role'ga biriktirilgan.
- Admin JWT `<JWT>` mavjud (`sub` claim = `<ACTOR_USER_ID>`).
- Reverse proxy `<BASE_URL>` (masalan `https://engops.example.com`) ostida
  Spring Boot app port 8080'da ishlayapti.

Placeholder shartlari:

| Placeholder | Ma'nosi |
| --- | --- |
| `<BASE_URL>` | HTTPS endpoint, masalan `https://engops.example.com` |
| `<JWT>` | Admin Bearer token (`sub = <ACTOR_USER_ID>`) |
| `<TENANT_ID>` | Tenant identifikatori (UUID) |
| `<DEFINITION_ID>` | Yangi yaratilgan workflow definition ID (response'dan olinadi) |
| `<STATUS_*_ID>` | Yangi yaratilgan status'lar ID'lari |

---

## 5. INCIDENT workflow template

Tavsiya etilgan shape:

```
OPEN (initial)
  → INVESTIGATING
  → MITIGATING
  → RESOLVED (terminal)
RESOLVED → OPEN  (reopen)
```

Bu **bitta** namuna — operator o'z modeliga moslab statuslarni
qo'shishi yoki olib tashlashi mumkin (masalan `TRIAGING`, `ESCALATED`,
`POST_MORTEM`).

### 5.1 Workflow definition yaratish

```bash
curl -fsS -X POST \
  "<BASE_URL>/api/admin/tenant-config/workflow-definitions?tenantId=<TENANT_ID>" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Default Incident Flow",
    "workItemType": "INCIDENT",
    "description": "MVP incident response loop"
  }'
```

Response (201 Created) ichidan `definitionId`'ni saqlang —
quyida `<DEFINITION_ID>` sifatida ishlatamiz.

### 5.2 Status'larni qo'shish

Statuslar tartib raqami (`orderIndex`) bo'yicha render qilinadi. Initial
faqat bittasida `true`, terminal — workflow yopuvchisida `true`.

```bash
# OPEN — initial
curl -fsS -X POST \
  "<BASE_URL>/api/admin/tenant-config/workflow-definitions/<DEFINITION_ID>/statuses?tenantId=<TENANT_ID>" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"name":"OPEN","orderIndex":0,"initial":true,"terminal":false}'

# INVESTIGATING
curl -fsS -X POST \
  "<BASE_URL>/api/admin/tenant-config/workflow-definitions/<DEFINITION_ID>/statuses?tenantId=<TENANT_ID>" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"name":"INVESTIGATING","orderIndex":1,"initial":false,"terminal":false}'

# MITIGATING
curl -fsS -X POST \
  "<BASE_URL>/api/admin/tenant-config/workflow-definitions/<DEFINITION_ID>/statuses?tenantId=<TENANT_ID>" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"name":"MITIGATING","orderIndex":2,"initial":false,"terminal":false}'

# RESOLVED — terminal
curl -fsS -X POST \
  "<BASE_URL>/api/admin/tenant-config/workflow-definitions/<DEFINITION_ID>/statuses?tenantId=<TENANT_ID>" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"name":"RESOLVED","orderIndex":3,"initial":false,"terminal":true}'
```

Har bir response'dan `statusId`ni saqlang
(`<STATUS_OPEN_ID>`, `<STATUS_INVESTIGATING_ID>`,
`<STATUS_MITIGATING_ID>`, `<STATUS_RESOLVED_ID>`).

### 5.3 Transition rule'larni qo'shish

```bash
# OPEN → INVESTIGATING
curl -fsS -X POST \
  "<BASE_URL>/api/admin/tenant-config/workflow-definitions/<DEFINITION_ID>/transition-rules?tenantId=<TENANT_ID>" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"fromStatusId":"<STATUS_OPEN_ID>","toStatusId":"<STATUS_INVESTIGATING_ID>"}'

# INVESTIGATING → MITIGATING
curl -fsS -X POST \
  "<BASE_URL>/api/admin/tenant-config/workflow-definitions/<DEFINITION_ID>/transition-rules?tenantId=<TENANT_ID>" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"fromStatusId":"<STATUS_INVESTIGATING_ID>","toStatusId":"<STATUS_MITIGATING_ID>"}'

# MITIGATING → RESOLVED
curl -fsS -X POST \
  "<BASE_URL>/api/admin/tenant-config/workflow-definitions/<DEFINITION_ID>/transition-rules?tenantId=<TENANT_ID>" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"fromStatusId":"<STATUS_MITIGATING_ID>","toStatusId":"<STATUS_RESOLVED_ID>"}'

# RESOLVED → OPEN (reopen)
curl -fsS -X POST \
  "<BASE_URL>/api/admin/tenant-config/workflow-definitions/<DEFINITION_ID>/transition-rules?tenantId=<TENANT_ID>" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"fromStatusId":"<STATUS_RESOLVED_ID>","toStatusId":"<STATUS_OPEN_ID>"}'
```

### 5.4 Workflow'ni aktiv qilish (agar default bo'lmasa)

`createWorkflowDefinition` natijasi `active=true` qaytarsa, bu qadam
o'tkaziladi. Aks holda:

```bash
curl -fsS -X POST \
  "<BASE_URL>/api/admin/tenant-config/workflow-definitions/<DEFINITION_ID>/activate?tenantId=<TENANT_ID>" \
  -H "Authorization: Bearer <JWT>"
```

### 5.5 Tasdiqlash

```bash
curl -fsS \
  "<BASE_URL>/api/admin/tenant-config/workflow-definitions/<DEFINITION_ID>?tenantId=<TENANT_ID>" \
  -H "Authorization: Bearer <JWT>"
```

`statuses` arrayda 4 ta status va `transitionRules` arrayda 4 ta rule
ko'rinishi kerak. `workItemType` = `"INCIDENT"`, `active` = `true`.

---

## 6. TASK workflow template

Tavsiya etilgan shape (eng oddiy 3-status loop):

```
OPEN (initial)
  → IN_PROGRESS
  → DONE (terminal)
DONE → OPEN  (reopen)
```

### 6.1 Workflow definition yaratish

```bash
curl -fsS -X POST \
  "<BASE_URL>/api/admin/tenant-config/workflow-definitions?tenantId=<TENANT_ID>" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Default Task Flow",
    "workItemType": "TASK",
    "description": "MVP task tracking loop"
  }'
```

### 6.2 Status'larni qo'shish

```bash
# OPEN — initial
curl -fsS -X POST \
  "<BASE_URL>/api/admin/tenant-config/workflow-definitions/<DEFINITION_ID>/statuses?tenantId=<TENANT_ID>" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"name":"OPEN","orderIndex":0,"initial":true,"terminal":false}'

# IN_PROGRESS
curl -fsS -X POST \
  "<BASE_URL>/api/admin/tenant-config/workflow-definitions/<DEFINITION_ID>/statuses?tenantId=<TENANT_ID>" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"name":"IN_PROGRESS","orderIndex":1,"initial":false,"terminal":false}'

# DONE — terminal
curl -fsS -X POST \
  "<BASE_URL>/api/admin/tenant-config/workflow-definitions/<DEFINITION_ID>/statuses?tenantId=<TENANT_ID>" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"name":"DONE","orderIndex":2,"initial":false,"terminal":true}'
```

### 6.3 Transition rule'larni qo'shish

```bash
# OPEN → IN_PROGRESS
curl -fsS -X POST \
  "<BASE_URL>/api/admin/tenant-config/workflow-definitions/<DEFINITION_ID>/transition-rules?tenantId=<TENANT_ID>" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"fromStatusId":"<STATUS_OPEN_ID>","toStatusId":"<STATUS_IN_PROGRESS_ID>"}'

# IN_PROGRESS → DONE
curl -fsS -X POST \
  "<BASE_URL>/api/admin/tenant-config/workflow-definitions/<DEFINITION_ID>/transition-rules?tenantId=<TENANT_ID>" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"fromStatusId":"<STATUS_IN_PROGRESS_ID>","toStatusId":"<STATUS_DONE_ID>"}'

# DONE → OPEN (reopen)
curl -fsS -X POST \
  "<BASE_URL>/api/admin/tenant-config/workflow-definitions/<DEFINITION_ID>/transition-rules?tenantId=<TENANT_ID>" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"fromStatusId":"<STATUS_DONE_ID>","toStatusId":"<STATUS_OPEN_ID>"}'
```

---

## 7. Intake bilan tekshirish

INCIDENT yoki TASK workflow yaratilgandan keyin, intake endpoint'i
yangi workflow'ni avtomatik tanlaydi (`findActiveWorkflowDefinitionsByType`
deterministic 1→use semantikasi).

```bash
curl -fsS -X POST \
  "<BASE_URL>/api/intake/work-items" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "<TENANT_ID>",
    "typeCode": "INCIDENT",
    "title": "Test incident — login latency surge",
    "description": "Test intake; remove or mark resolved after smoke.",
    "actionSource": "MANUAL"
  }'
```

Response'da `currentStatusCode` = `"OPEN"`, `workflowDefinitionId` = yangi
yaratilgan definition ID. Agar workflow definition tenant'da bir nechta
aktiv bo'lsa, `AMBIGUOUS_WORKFLOW` 422 qaytariladi — operator
`workflowDefinitionId` parametrini explicit yuborishi kerak.

---

## 8. Workflow transition'ni Phase 190 admin write surface bilan birga sinash

Yaratilgan INCIDENT work item uchun:

```bash
# Owner tayinlash (Phase 190)
curl -fsS -X POST \
  "<BASE_URL>/api/admin/work-items/<WORK_ITEM_ID>/owner" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"<TENANT_ID>","ownerUserId":"<OWNER_ID>"}'

# Severity belgilash (Phase 190)
curl -fsS -X POST \
  "<BASE_URL>/api/admin/work-items/<WORK_ITEM_ID>/severity" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"<TENANT_ID>","severityCode":"HIGH"}'

# Status o'zgartirish
curl -fsS -X POST \
  "<BASE_URL>/api/work-items/<WORK_ITEM_ID>/transitions" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId":"<TENANT_ID>",
    "targetStatusCode":"INVESTIGATING",
    "actionSource":"MANUAL"
  }'
```

Audit qatorlari `OWNER_ASSIGNED`, `SEVERITY_CHANGED`,
`STATUS_TRANSITION` sifatida yoziladi.

---

## 9. Telegram chegaralari

INCIDENT va TASK work item'lari yaratilganda routing prepared bo'lsa,
Telegram card matn ko'rinishida yuboriladi
(`<typeLabel> | <code>\n<title>\nStatus: <status>`). Inline action
tugmalari (Start Processing / Send to Testing / …) **YO'Q** — Phase 173
`TelegramActionAssembler` ataylab faqat BUG uchun action'larni render
qiladi. Operator transitionlarni admin API yoki workflow transition
endpoint orqali qiladi (Telegram callback path ataylab faqat BUG MVP
flow uchun ishlatiladi).

Bu cheklov **biznes qaror** — INCIDENT/TASK uchun callback path
keyingi alohida phase nomzodi.

---

## 10. Idempotensiya va xatolarni boshqarish

- Bir xil ism bilan workflow definition yaratishga harakat —
  `DUPLICATE_WORKFLOW_NAME` 422.
- Mavjud status nomi qayta qo'shilsa — `DUPLICATE_STATUS` 422.
- Bir xil from/to bilan transition rule qayta qo'shilsa —
  `DUPLICATE_TRANSITION_RULE` 422.
- Operator skript'ni qayta ishga tushirsa, mavjud qatorlar yaratilmaydi —
  faqat yetishmaganlari qo'shilishi uchun avval `GET ... /workflow-definitions/<DEFINITION_ID>`
  bilan snapshot olib, farqni manual hisoblang. Yoki shunchaki yangi
  workflow nomini ishlating.

---

## 11. Mavjud runbook'lar bilan aloqa

- [First-Admin Bootstrap Runbook](first-admin-bootstrap-runbook.md) —
  birinchi tenant + admin user + ADMIN role binding qanday yaratiladi.
- [Demo Smoke Runbook](demo-smoke-runbook.md) — end-to-end intake va
  workflow transition smoke yo'li (BUG MVP).
- [MVP Completion Runbook](mvp-completion-runbook.md) — demo va
  production checklist.
- [Production Deployment Runbook](production-deployment-runbook.md) —
  Dockerfile + docker-compose.prod.yml ostida deploy.
- [Production Hardening Runbook](production-hardening-runbook.md) —
  actuator scoping, secret rotation, log redaction.
- [Observability Runbook](observability-runbook.md) — Phase 189
  Micrometer counter'lar + ADMIN_AUTH_DENIED audit + webhook reject log.

---

## 12. Kelajakdagi phase nomzodlari (ma'lumot uchun)

- **INCIDENT/TASK uchun TelegramActionAssembler action'lari** —
  ehtimoliy alohida phase. Card text'iga teginish kerak emas (Phase 179
  edit-first NOT_MODIFIED/EDITED branch'iga ta'sir).
- **Intake request priority/severity/owner ixtiyoriy field'lari** —
  Phase 190 da ataylab DEFERRED. Alohida tasdiqlangan mini-phase bo'lsa
  qo'shiladi. Hozircha operator intake'dan keyin alohida POST
  `/api/admin/work-items/{id}/{owner|priority|severity}` chaqiradi.
- **Card text'da priority/severity/owner ko'rsatish** — ehtimoliy
  alohida phase; edit-first/NOT_MODIFIED matn-hash semantikasini qayta
  validatsiya qilishni talab qiladi.

Ushbu nomzodlardan birortasi ham Phase 190 ichida emas.
