# Observability Runbook (Phase 189)

> **Phase 189.** Bu runbook v1 production'da operator nimani kuzatishi
> kerakligini bir joyga to'playdi: Phase 189 da qo'shilgan Micrometer
> counter'lar, Phase 185 va Phase 189 audit qatorlari, bounded log
> shape'lari, delivery observability HTTP endpoint'lari va minimal
> alert tavsiyalari. Yangi HTTP endpoint, yangi schema, yoki yangi
> Java behavior qo'shilmadi — faqat metric increment'lar va denial
> audit yo'llari.

---

## 1. Maqsad va auditoriya

**Maqsad.** Real jamoa qo'liga berilgan production deployment ustida
operator kuzatish strategiyasini hujjatlashtirish:

- nimani metric sifatida `/actuator/metrics` orqali ko'rish mumkin;
- nimani audit qatori sifatida DB'dan o'qish mumkin;
- nimani bounded log liniyalarida grep qilish mumkin;
- nimaga alert qo'yish kerak.

**Auditoriya.** Production-ni boshqaruvchi **operator** / SRE va
backend ownerlari. Phase 187 deployment baseline'i va Phase 188
hardening checklisti bilan tanish bo'lish kutiladi.

**Phase 189 nima qo'shdi.**

1. Uchta Micrometer counter:
   `engops.telegram.send.attempts`,
   `engops.telegram.card.refresh.outcomes`,
   `engops.telegram.callback.execution.outcomes`.
2. `ADMIN_AUTH_DENIED` audit qatori — **Phase 189 da joriy etildi**
   (fail-soft, `REQUIRES_NEW`).
3. `TELEGRAM_WEBHOOK_REJECTED` audit qatori — **Phase 189 da ataylab
   JORIY ETILMADI (deferred).** Sabab: `audit_event.entity_id`
   schema'da `NOT NULL` va loyihada schema-safe nil UUID convention
   mavjud emas. Rejection signal'i faqat bounded structured log
   line orqali qoladi (shakl §6.3 da). Kelajakda joriy etish uchun
   yo schema migration kerak (entity_id'ni nullable qilish yoki
   alohida `webhook_reject_log` jadval), yo loyihada explicit nil
   UUID convention qarori qabul qilinishi kerak.
4. Ushbu observability runbook (Uzbek).

---

## 2. Observability mental model

```
   App container (Phase 187)
        │
        ├──► structured logs (stdout, bounded)
        │       └── docker compose logs / log shipper / cloud logs
        │
        ├──► Micrometer metrics  (in-process registry)
        │       └── /actuator/metrics  (HTTP, Phase 188 da torayaytirilgan)
        │              └── (ixtiyoriy) Prometheus scrape → Grafana panel
        │
        ├──► append-only audit_event jadvali  (PostgreSQL)
        │       └── psql / SQL query (BOOTSTRAP_COMPLETED, STATUS_TRANSITION,
        │              TELEGRAM_CALLBACK_DENIED, ADMIN_AUTH_DENIED, …)
        │
        └──► telegram_delivery_attempt jadvali (PostgreSQL)
                └── /api/admin/delivery-observability/{summary,details,…}
                       (admin JWT bilan)
```

**Invariantlar:**

- **PostgreSQL — source of truth.** Audit qator, work item tarixi,
  delivery attempt — barchasi shu yerda saqlanadi. Metrics va log
  yordamchi signal.
- **Telegram xabarlar — projection.** Telegram tomonda nima
  ko'rinishi nima sodir bo'lganligini aniqlamaydi.
- **Low-cardinality.** Metric tag'lar bounded enum/string'lar bilan
  cheklangan — tenantId, workItemId, userId, chatId, messageId,
  callbackQueryId, token, exception message HECH QACHON tag bo'lmaydi.
- **Fail-soft.** Audit yozish va metric increment biznes xulq-atvorga
  ta'sir qilmaydi. Ular yon-ta'sir, asosiy yo'lni bloklamaydi.

---

## 3. Metrics catalog (Phase 189)

Quyidagi uchta counter Phase 189 dan boshlab in-process Micrometer
registry'da mavjud. Ular Spring Boot Actuator orqali standart
`/actuator/metrics/{name}` JSON sirti orqali olinadi. Yangi
dependency qo'shilmadi (Micrometer transitive starter-actuator
orqali kelgan).

### 3.1 `engops.telegram.send.attempts`

| Maydon | Qiymat |
| --- | --- |
| Counter nomi | `engops.telegram.send.attempts` |
| Increment joyi | `TelegramOutboundDispatchService.dispatch(...)` — har bir send attempt natijasidan keyin |
| Tag `outcome` | `DELIVERED` &#124; `REJECTED` &#124; `FAILED` &#124; `EXCEPTION` |
| Tag `error` | `NONE` (DELIVERED uchun) &#124; `INVALID_REQUEST` &#124; `RATE_LIMIT` &#124; `NETWORK_ERROR` &#124; `UNKNOWN_ERROR` &#124; `DISPATCH_NOT_SUPPORTED` |
| Tag `gateway` | `http` (HttpTelegramOutboundGateway) &#124; `stub` (StubTelegramOutboundGateway) &#124; `unknown` |
| Low-cardinality | Ha — uchala tag bounded enum/string. tenantId/workItemId/token tag'i YO'Q. |

**Nima anglatadi.** Har bir `gateway.execute(...)` chaqiruvi natijasi
bitta increment yozadi. Retry pipeline (Phase 168) `RATE_LIMIT` va
`NETWORK_ERROR` uchun bir necha urinish qiladi — har bir urinish
alohida increment beradi. Counter sanog'i shu sababdan
`telegram_delivery_attempt` jadvalidagi qatorlar soni bilan mos
keladi (1:1).

**Normal pattern.** `outcome=DELIVERED,error=NONE,gateway=http`
ko'pchilik trafikni egallaydi. Stub mode'da
`outcome=FAILED,error=UNKNOWN_ERROR,gateway=stub` dominant.

**Shubhali pattern.**

- `error=RATE_LIMIT` keskin oshib ketsa — Telegram quota cheklov,
  outbound trafik kamaytirish kerak.
- `error=NETWORK_ERROR` davomiy ko'paysa — outbound TCP/DNS
  muammosi yoki Telegram tomondan vaqtinchalik nosozlik.
- `outcome=EXCEPTION` umuman bo'lmasligi kerak — gateway
  contract'iga ko'ra kutilmagan exception. Bir nechta paydo bo'lsa,
  application log'ni darhol tekshirish.

### 3.2 `engops.telegram.card.refresh.outcomes`

| Maydon | Qiymat |
| --- | --- |
| Counter nomi | `engops.telegram.card.refresh.outcomes` |
| Increment joyi | `TelegramCardRefreshDispatchService.dispatch(...)` — har bir AFTER_COMMIT card refresh chaqiruvi uchun bir marta |
| Tag `outcome` | `OutcomeCategory` enum nomi: `SKIPPED_BAD_INPUT`, `RENDERER_THREW_SWALLOWED`, `EDITED`, `NOT_MODIFIED`, `EDIT_REJECTED_FALLBACK_SEND`, `EDIT_RATE_LIMIT_FALLBACK_SEND`, `EDIT_NETWORK_FALLBACK_SEND`, `EDIT_FAILED_FALLBACK_SEND`, `EDIT_NULL_RESULT_FALLBACK_SEND`, `REFRESH_THREW_FALLBACK_SEND` |
| Low-cardinality | Ha — outcome bitta bounded enum (10 ta qiymat). |

**Nima anglatadi.** Phase 179 edit-first / send-as-fallback
coordinator'i har AFTER_COMMIT card dispatch event'i uchun bitta
outcome kategoriyasini tanlaydi va shu kategoriyaga sanaqlanadi.
Bitta workflow transition = bitta increment.

**Normal pattern.** `outcome=EDITED` dominant (prior delivered card
mavjud bo'lganda); `outcome=EDIT_REJECTED_FALLBACK_SEND` birinchi
transition uchun (prior card yo'q). `NOT_MODIFIED` ham kutilgan
(yangi text mavjud bilan teng bo'lsa).

**Shubhali pattern.**

- `EDIT_RATE_LIMIT_FALLBACK_SEND` yoki
  `EDIT_NETWORK_FALLBACK_SEND` keskin oshsa — Telegram tomondan
  vaqtinchalik nosozlik.
- `REFRESH_THREW_FALLBACK_SEND` davomiy bo'lsa — bug yoki
  unexpected exception zanjirida.

### 3.3 `engops.telegram.callback.execution.outcomes`

| Maydon | Qiymat |
| --- | --- |
| Counter nomi | `engops.telegram.callback.execution.outcomes` |
| Increment joyi | `TelegramCallbackActionExecutionService.execute(...)` — har bir terminal `ExecutionOutcome` uchun bir marta |
| Tag `outcome` | `ExecutionOutcome` enum nomi: `EXECUTED`, `USER_NOT_FOUND`, `WORK_ITEM_NOT_FOUND`, `NOT_A_MEMBER`, `PERMISSION_DENIED`, `INVALID_TRANSITION`, `UNEXPECTED_FAILURE` |
| Low-cardinality | Ha — outcome bitta bounded enum (7 ta qiymat). |

**Nima anglatadi.** Parser tomonidan ACCEPTED deb belgilangan har
bir inbound callback uchun bitta terminal outcome'da bitta
increment. Parser tomonidan ignored / malformed / unknown
callback'lar bu counter'ga TUSHMAYDI — ular faqat
`TelegramCallbackQueryService` parser log'da qoladi.

**Normal pattern.** `outcome=EXECUTED` dominant.
`outcome=PERMISSION_DENIED` va `outcome=NOT_A_MEMBER` past
chastotali — operator xatosi yoki noto'g'ri Telegram identity'dan.

**Shubhali pattern.**

- `outcome=PERMISSION_DENIED` keskin spike — coordinated brute-force
  yoki misconfigured membership.
- `outcome=UNEXPECTED_FAILURE` umuman bo'lmasligi kerak — har biri
  application log'da `exceptionType=` bilan birga ko'rinadi.

---

## 4. Prometheus / Actuator misollar

### 4.1 Counter'ni Actuator orqali ko'rish

Phase 188 prod profile actuator exposure'ni `health,info,metrics`
gacha toraytirgan. `/actuator/metrics` ochiq qoldi.

```
curl -fsS http://127.0.0.1:8080/actuator/metrics/engops.telegram.send.attempts
```

Javob misol (skelet):

```json
{
  "name": "engops.telegram.send.attempts",
  "measurements": [{"statistic": "COUNT", "value": 42.0}],
  "availableTags": [
    {"tag": "outcome", "values": ["DELIVERED", "FAILED", "REJECTED"]},
    {"tag": "error",   "values": ["NONE", "NETWORK_ERROR", "INVALID_REQUEST"]},
    {"tag": "gateway", "values": ["http", "stub"]}
  ]
}
```

Filterlar bilan:

```
curl -fsS "http://127.0.0.1:8080/actuator/metrics/engops.telegram.send.attempts?tag=outcome:DELIVERED&tag=error:NONE"
```

### 4.2 Prometheus / PromQL (ixtiyoriy)

Operator scrape qiluvchi Prometheus o'rnatsa va `prometheus`
endpoint'ini qo'shsa (Phase 188 da prod uchun kiritilmagan —
keyingi tanlov), tipik PromQL misollari:

```
# DELIVERED tezligi (per second, 5 daqiqa window):
rate(engops_telegram_send_attempts_total{outcome="DELIVERED"}[5m])

# RATE_LIMIT yoki NETWORK_ERROR keskin oshishini aniqlash:
rate(engops_telegram_send_attempts_total{error=~"RATE_LIMIT|NETWORK_ERROR"}[5m])

# Callback denial nisbati:
sum by (outcome) (
  rate(engops_telegram_callback_execution_outcomes_total{
    outcome=~"PERMISSION_DENIED|NOT_A_MEMBER|INVALID_TRANSITION"
  }[5m])
)

# Card refresh fallback nisbati EDITED'ga nisbatan:
sum(rate(engops_telegram_card_refresh_outcomes_total{outcome!="EDITED"}[5m]))
  /
sum(rate(engops_telegram_card_refresh_outcomes_total[5m]))
```

> **Eslatma.** Phase 189 Prometheus registry'ni standart bilan
> ulamaydi. PromQL misollari faqat operator o'zining scrape
> infratuzilmasi mavjud bo'lganda ishlaydi. Prometheus
> dependency yoki configuration shipping qilinmadi.

---

## 5. Telegram delivery observability

Tafsilotlar
[`telegram-outbound-gateway-runbook.md`](telegram-outbound-gateway-runbook.md)
ichida. Bu yerda faqat asosiy aloqa nuqtalari:

- **`telegram_delivery_attempt` jadvali** append-only — har bir
  `SEND_NEW_MESSAGE` urinishi (DELIVERED, REJECTED, FAILED) bitta
  qator yozadi.
- **Phase 179 muvaffaqiyatli `EDIT_MESSAGE` urinishlari shu
  jadvalga YOZILMAYDI.** Coordinator metric va bounded log
  (yuqorida §3.2) yagona signal.
- **Admin HTTP endpoint'lar:**
  - `GET /api/admin/delivery-observability/summary?tenantId=...`
  - `GET /api/admin/delivery-observability/details?tenantId=...&workItemId=...`
  - `GET /api/admin/delivery-observability/summary/by-status?...`
  - `GET /api/admin/delivery-observability/summary/by-owner?...`
  - va h.k. (jami 7 ta GET)
- Admin JWT va `TENANT_CONFIG_READ` permission talab qilinadi.

`engops.telegram.send.attempts` counter'i bilan delivery_attempt
jadvali bitta-bittaga mos keladi — counter aggregate, jadval
per-row tarix.

---

## 6. Callback observability

Inbound Telegram callback ikkita signal qatlamiga ega:

### 6.1 Counter

`engops.telegram.callback.execution.outcomes` (yuqorida §3.3).
Tag = bounded `ExecutionOutcome`.

### 6.2 Audit qatorlari

| Event type                  | Phase | Manba                                                  |
| --------------------------- | ----- | ------------------------------------------------------ |
| `STATUS_TRANSITION`         | 122   | `WorkflowTransitionService` (muvaffaqiyatli EXECUTED)  |
| `TELEGRAM_CALLBACK_DENIED`  | 185   | `TelegramCallbackActionExecutionService` (NOT_A_MEMBER, PERMISSION_DENIED, INVALID_TRANSITION, UNEXPECTED_FAILURE) |
| `TELEGRAM_WEBHOOK_REJECTED` | 189   | **Ataylab DEFERRED — audit qatori YO'Q.** Bounded log only (shakl §6.3 da). Sabab va kelajakdagi yo'l §11 da. |

SQL misol — `TELEGRAM_CALLBACK_DENIED` so'nggi 20 ta qator:

```sql
SELECT occurred_at, tenant_id, actor_user_id, entity_id, new_value_json
  FROM audit_event
 WHERE event_type = 'TELEGRAM_CALLBACK_DENIED'
 ORDER BY occurred_at DESC LIMIT 20;
```

`new_value_json` faqat `outcome`, `actionCode`, `targetStatusCode`
maydonlariga ega — raw callback_data, token, exception message
yo'q.

### 6.3 Webhook rejection bounded log

Phase 189 `TelegramWebhookController` har 401 reject uchun
quyidagi shaklda log yozadi:

```
WARN  c.e.p.t.TelegramWebhookController -- Telegram webhook rejected reason=MISSING_HEADER hasCallbackQuery=false
```

`reason` qiymatlari: `MISSING_SECRET_CONFIG`, `MISSING_HEADER`,
`INVALID_HEADER`. Token qiymati (configured yoki incoming) hech
qachon yozilmaydi.

---

## 7. Admin API denial observability (Phase 189)

`AdminAuthorizationService` har bir denial yo'li uchun bitta audit
qator yozadi — fail-soft, `REQUIRES_NEW` tranzaksiyada.

| Maydon            | Qiymat                                                          |
| ----------------- | --------------------------------------------------------------- |
| `event_type`      | `ADMIN_AUTH_DENIED`                                             |
| `entity_type`     | `ADMIN_API`                                                     |
| `entity_id`       | `tenantId` (schema `NOT NULL` constraint'ini qondiradi)         |
| `tenant_id`       | `tenantId`                                                      |
| `actor_user_id`   | actor user id (MISSING_ACTOR yo'lida `null` bo'lishi mumkin)    |
| `action_source`   | `ADMIN_API`                                                     |
| `new_value_json`  | `{"permission":"...","reason":"MISSING_ACTOR\|PERMISSION_DENIED"}` |

Audit yozish xatosi 403 javobiga ta'sir qilmaydi. Exception
message audit payload'ga kirmaydi.

SQL misol:

```sql
SELECT occurred_at, tenant_id, actor_user_id, new_value_json
  FROM audit_event
 WHERE event_type = 'ADMIN_AUTH_DENIED'
 ORDER BY occurred_at DESC LIMIT 20;
```

**Phase 190 admin write success audit qatorlari.** Phase 190 admin
write endpoint'lari (`POST /api/admin/work-items/{id}/owner` /
`.../priority` / `.../severity`) har bir muvaffaqiyatli mutatsiya
uchun bitta audit qatorini biznes tranzaksiyasi ichida (`MANDATORY`
propagation, `REQUIRES_NEW` emas) yozadi:

| `event_type`       | Manba metod                                              |
| ------------------ | -------------------------------------------------------- |
| `OWNER_ASSIGNED`   | `WorkItemCommandService.assignOwner(...)`                |
| `PRIORITY_CHANGED` | `WorkItemCommandService.updatePriority(...)` (Phase 190) |
| `SEVERITY_CHANGED` | `WorkItemCommandService.updateSeverity(...)` (Phase 190) |

Har uchchalasi uchun `action_source = ADMIN_API`,
`entity_type = WORK_ITEM`, `entity_id = workItemId`. `new_value_json`
faqat bounded qiymatni saqlaydi (yangi owner UUID, yoki yangi
priority/severity kodi — `LOW` / `MEDIUM` / `HIGH` / `CRITICAL`).
Request body, JWT, exception message audit payload'iga **kirmaydi**.
Operator smoke retsepti
[`demo-smoke-runbook.md` §14](demo-smoke-runbook.md#14-phase-190-admin-write-smoke--owner--priority--severity)
da. Bu endpoint'lar uchun denial audit qatori joriy etilmagan —
denial yo'li mavjud `OperationalAuthorizationService` warn log liniyasi
orqali signal beradi (Phase 190 doirasidan tashqari).

---

## 8. Log grep playbook

Bounded log shape'lar barqaror; operator quyidagi naqshlardan
foydalanishi mumkin (token leak yo'qligi grep'lari hardening
runbook'da, [§8](production-hardening-runbook.md)).

```
# Edit-first muvaffaqiyatli
docker compose ... logs app | grep "outcome=EDITED"

# "Message is not modified" benign no-op
docker compose ... logs app | grep "outcome=NOT_MODIFIED"

# Edit rad etilgan, fallback send chaqirilgan
docker compose ... logs app | grep "EDIT_REJECTED_FALLBACK_SEND"

# Callback execution outcome (har bir terminal outcome)
docker compose ... logs app | grep "Telegram callback execute outcome="

# Audit fail-soft swallow (Phase 185 / Phase 189)
docker compose ... logs app | grep "audit swallowed"

# Webhook rejection (Phase 189 bounded log)
docker compose ... logs app | grep "Telegram webhook rejected reason="
```

**Token leak negative grep** (production hardening runbook §8 bilan
takrorlanmaslik uchun bu yerda qisqacha):

```
docker compose ... logs app | grep -F "$TELEGRAM_BOT_TOKEN"   # zero match kutiladi
docker compose ... logs app | grep -E "/bot[0-9]+:"           # zero match kutiladi
```

---

## 9. Minimal alert tavsiyalari

Operator alert tooli bo'lmasa ham, quyidagi shartlarni vaqti-vaqti
bilan grep yoki SQL bilan tekshiring. Prometheus + Alertmanager
mavjud bo'lsa, PromQL'da yozish mumkin.

| Sharoit | Signal | Tavsiya |
| --- | --- | --- |
| `engops.telegram.send.attempts{error="RATE_LIMIT"}` keskin spike | rate > base × 5 oxirgi 5 daqiqada | Outbound trafik kamaytirish; Telegram quota tekshirish |
| `engops.telegram.send.attempts{error="NETWORK_ERROR"}` davomiy | rate > 0 davomli 10 daqiqa | Network / DNS / firewall tekshirish |
| `engops.telegram.callback.execution.outcomes{outcome="UNEXPECTED_FAILURE"}` | har qanday nolga teng bo'lmagan qiymat | Application log darhol tekshirish |
| Webhook rejected log spike | `Telegram webhook rejected reason=INVALID_HEADER` davomli | Bot token / webhook secret leak ehtimoli; rotation |
| `ADMIN_AUTH_DENIED` keskin spike | so'nggi 1 soatda > base × 10 | Misconfigured membership yoki coordinated probing |
| `intake.work_items` ko'p, lekin `engops.telegram.send.attempts` past | intake-to-send nisbat dramatik tushgan | Routing rule / chat binding / topic binding nosozligi |

---

## 10. Known limitations

Phase 189 ataylab tor doirada. Quyidagi ishlar **qoldirildi**:

- **Custom dashboard yo'q.** Grafana dashboard JSON, Prometheus
  scrape config va alert rule'lari shipping qilinmagan — operator
  o'z infratuzilmasi bilan ulaydi.
- **Prometheus dependency yo'q.** `/actuator/prometheus` endpoint
  prod'da ochilmagan; faqat `/actuator/metrics` JSON sirti.
- **`EDIT_MESSAGE` attempt persistence yo'q.** Phase 179 da atayin
  omit qilingan; coordinator counter va log signal beradi.
- **`telegram_active_card` projection jadvali yo'q.** Phase 177
  read model `telegram_delivery_attempt`'dan derive qiladi.
- **Stale-card cleanup yo'q.** Server-side authorization javobgar.
- **Async / outbox / scheduler yo'q.** Sinxron AFTER_COMMIT.
- **`TELEGRAM_WEBHOOK_REJECTED` audit qatori — Phase 189 da
  ATAYLAB DEFERRED, qo'shilmadi.** Sabab: `audit_event.entity_id`
  schema'da `NOT NULL` va loyihada schema-safe nil UUID convention
  joriy emas. Kelajakda audit darajasidagi rejection trail'ni
  qo'shish uchun **ikkita yo'ldan biri** kerak:
  (a) schema migration (`entity_id` ustunini nullable qilish yoki
  webhook rejection uchun alohida append-only jadval qo'shish), yoki
  (b) loyiha doirasida explicit nil UUID convention qarori qabul
  qilinishi va hujjatlashtirilishi (masalan,
  `00000000-0000-0000-0000-000000000000` qiymati "no entity"
  semantikasini ifodalashi). Phase 189 hech qaysi yo'lni
  ataylab tanlamadi — operator qaroriga qoldirildi. Hozircha
  bounded log line (§6.3) yagona signal.

---

## 11. Verifikatsiya checklist

Phase 189 deploy'idan keyin operator quyidagilarni o'tkazadi:

- [ ] `./mvnw clean test` — barcha unit testlar yashil (1907+ test).
- [ ] `curl /actuator/metrics/engops.telegram.send.attempts` —
      counter mavjud va measurement qaytaradi.
- [ ] `curl /actuator/metrics/engops.telegram.card.refresh.outcomes`
      — counter mavjud.
- [ ] `curl /actuator/metrics/engops.telegram.callback.execution.outcomes`
      — counter mavjud.
- [ ] Bitta intake + bitta workflow transition orqali smoke run —
      counter sanog'i oshganligi `availableTags` bilan birga
      tasdiqlanadi.
- [ ] Atayin denial callback (operator
      `WORK_ITEM_TRANSITION` permission'siz) — `TELEGRAM_CALLBACK_DENIED`
      audit qatori yozildi.
- [ ] Atayin denial admin so'rovi (operator
      `TENANT_CONFIG_READ` permission'siz) — `ADMIN_AUTH_DENIED`
      audit qatori yozildi.
- [ ] Bot token / webhook secret application log'larga tushmadi
      (hardening runbook §8 grep retseptlari clean qaytarsin).

---

To'liq operator zanjirini ko'rish uchun
[`mvp-completion-runbook.md`](mvp-completion-runbook.md).
Production deployment retsepti
[`production-deployment-runbook.md`](production-deployment-runbook.md).
Hardening checklist
[`production-hardening-runbook.md`](production-hardening-runbook.md).
Backup / restore
[`backup-restore-runbook.md`](backup-restore-runbook.md).

Phase 192 dan keyingi keyingi-qadam yo'l-yo'rig'i so'nggi phase
review'idan kelishi kerak. Phase 192 holatida ushbu runbook Phase 189
observability signal'lari (uchta Micrometer counter, `ADMIN_AUTH_DENIED`
audit qatori, `TELEGRAM_WEBHOOK_REJECTED` bounded log) hamda Phase 190
admin-write success audit qatorlari (`OWNER_ASSIGNED`,
`PRIORITY_CHANGED`, `SEVERITY_CHANGED` — §7 da hujjatlangan) ni qamrab
oladi. `TELEGRAM_WEBHOOK_REJECTED` audit qatori hali ham ataylab
DEFERRED — bounded log only (§10 ga qarang).
