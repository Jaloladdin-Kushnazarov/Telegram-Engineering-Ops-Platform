# Production Hardening Runbook (Phase 188)

> **Phase 188.** Bu runbook Phase 187 deployment baseline'i tepasiga
> qo'shiladigan production security / ops hardening checklist'i. Yangi
> feature, yangi endpoint, yangi schema yo'q — faqat prod profile
> tightening, operator-level checklist va verifikatsiya retseptlari.

---

## 1. Maqsad va auditoriya

**Maqsad.** Phase 187'da paketlangan va deployable bo'lgan platformani
real jamoa qo'liga berishdan oldin operator bajarishi shart bo'lgan
**security + ops hardening** qadamlarini bir joyga to'plash. Bu
runbook qo'shimcha runtime xulq-atvor o'zgartirmaydi; faqat shu
hozir mavjud bo'lgan imkoniyatlarni xavfsiz konfiguratsiya bilan
ishlatish bo'yicha qadamlar va tekshiruvlarni beradi.

**Auditoriya.**
- Production'ga ilovani chiqaruvchi **operator** / SRE.
- **Backend owner** — secret rotation va audit verification uchun.
- **Deployment engineer** — reverse proxy, env va Docker Compose
  tomonidan kelgan masalalar uchun.

**Phase 188 nima qildi.**
1. `application-prod.properties` da actuator exposure'ni
   `health,info,metrics`'gacha toraytirdi (flyway endpoint prod'da
   yopildi).
2. `/actuator/health` javobida detail'lar yashirildi.
3. Bu (yangi) hardening runbook va alohida **backup-restore-runbook.md**
   yozildi.

Hech qanday Java kod, schema, security config, yoki Docker artifact
o'zgartirilmagan.

---

## 2. Production hardening mental model

```
   Internet / Telegram
        ↓ HTTPS 443
   Reverse proxy / TLS termination  (operator-owned: nginx / Caddy /
                                     Traefik / ALB)
        ↓ internal HTTP 8080  (docker bridge — never public)
   engops-platform-app
        ↔ PostgreSQL              (faqat docker network)
        → api.telegram.org         (HTTPS 443 outbound)
        → JWT issuer / JWKS        (HTTPS 443, agar configured)
```

Quyidagi invariantlarni o'zgarmas deb hisoblang:

- **Reverse proxy operator mas'uliyatida.** Phase 187 compose unga
  konteyner qo'shmaydi. TLS sertifikati, HTTP/2, rate limiting —
  operator tomonidan.
- **App faqat 8080'ni internal docker network'da ochadi.** Public
  trafik faqat reverse proxy orqali keladi.
- **PostgreSQL hech qachon publicly bind bo'lmaydi.**
  `docker-compose.prod.yml` overlay base'dagi `5432:5432` host port
  mapping'ni o'chiradi (`ports: []`).
- **Backend + DB — source of truth.** Telegram xabarlar projection.
  Telegram message id, chat id, username — hech qachon authoritative
  identifier emas.
- **Telegram callback message ko'rinishi authorization belgisi emas.**
  Har bir callback uchun server-side membership + permission tekshiruvi
  bajariladi (Phase 173/175).

---

## 3. Secret inventory

Quyidagi sirlar (secrets) production'da mavjud bo'lishi shart. Hech
biri kod, runbook, README, screenshot, yoki public chat'da
ko'rinmasligi kerak.

| Secret nomi                          | Manba                                          | Kim ishlatadi                                              |
| ------------------------------------ | ---------------------------------------------- | ---------------------------------------------------------- |
| `POSTGRES_PASSWORD`                  | env / secret manager                           | Postgres container init va keyingi authentication          |
| `DATABASE_PASSWORD`                  | env / secret manager (qiymati `POSTGRES_PASSWORD` bilan teng) | App JDBC connection                                       |
| `TELEGRAM_BOT_TOKEN`                 | env / secret manager (BotFather'dan)           | `HttpTelegramOutboundGateway` sendMessage / editMessageText / answerCallbackQuery |
| `TELEGRAM_WEBHOOK_SECRET_TOKEN`      | env / secret manager (operator generate)       | Webhook header constant-time match (Phase 171)              |
| `APP_SECURITY_JWT_HMAC_SECRET`       | env / secret manager                           | JWT decoder HMAC rejimida; mavjud bo'lsa boshqa rejimlar yopiq |
| `APP_SECURITY_JWT_ISSUER_URI` *yoki* `APP_SECURITY_JWT_JWK_SET_URI` | env (manba boshqa OIDC provider) | JWT decoder OIDC/JWKS rejimida                              |
| Bootstrap admin qiymatlari (first run) | env (faqat birinchi run)                    | `BootstrapAdminInitializer` (Phase 143/156)                |

**Qoidalar:**

- Hech qachon git'ga commit qilmang. Hatto stage qilish ham xato.
- README, runbook, issue tracker, chat, screenshot — hech qaerga
  yozmang. Placeholder ishlating.
- `.env` fayl ishlatilsa `chmod 600` shart va host'ning ownership'i
  app deploy qiluvchi foydalanuvchiga (yoki root'ga).
- Production'da managed secret store (Vault, AWS Secrets Manager,
  GCP Secret Manager, K8s Secret) afzal. `.env` faqat kichik
  single-VM deployment uchun maqbul.
- Bootstrap admin qiymatlari ham sezgir — `APP_BOOTSTRAP_ADMIN_TELEGRAM_USER_ID`
  va `APP_BOOTSTRAP_ADMIN_APP_USER_ID` adminni identifikatsiya
  qiladi. Birinchi run muvaffaqiyatli bo'lgach
  `APP_BOOTSTRAP_ADMIN_ENABLED=false` qiling.

---

## 4. Secret rotation checklist

Sirlarni vaqt o'tib (oylik / yarim yillik / leak shubhasi paydo
bo'lganda) rotate qilish kerak.

### 4.1 PostgreSQL password rotation

- O'zgarish: `POSTGRES_PASSWORD` va `DATABASE_PASSWORD` (qiymatlari
  o'zaro teng bo'lishi shart).
- Qadamlar:
  1. Live psql exec orqali yangi rol parolini o'rnatish:
     ```
     docker compose -f docker-compose.yml -f docker-compose.prod.yml \
       exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
         -c "ALTER ROLE \"$POSTGRES_USER\" WITH PASSWORD '<new>';"
     ```
  2. `.env` ichidagi `POSTGRES_PASSWORD` va `DATABASE_PASSWORD`'ni
     yangilang.
  3. App container'ni restart qiling (Postgres restart shart emas):
     `docker compose ... up -d --force-recreate app`.
- Restart kerakmi: app YES; postgres YO'Q.
- Verifikatsiya: `curl -fsS http://127.0.0.1:8080/actuator/health`
  qaytarsin `UP`; app log'da JDBC authentication xatosi yo'qligini
  tekshiring.

### 4.2 Telegram bot token rotation (BotFather)

- O'zgarish: `TELEGRAM_BOT_TOKEN`.
- Qadamlar:
  1. BotFather chatida `/revoke` (yoki `/token` — UI variantiga
     qarab) orqali eski tokenni bekor qilib, yangi token oling.
  2. `.env` ichidagi `TELEGRAM_BOT_TOKEN`'ni yangilang.
  3. App container'ni restart qiling.
  4. Webhook'ni yangi token bilan qayta register qiling
     (`setWebhook` — quyidagi §5 ga qarang).
- Restart kerakmi: app YES.
- Verifikatsiya: `getWebhookInfo` 200 OK qaytarsin va `url` to'g'ri
  bo'lsin; bitta test transition Telegram'da yangi card paydo
  qilsin.

### 4.3 Telegram webhook secret token rotation

- O'zgarish: `TELEGRAM_WEBHOOK_SECRET_TOKEN`.
- Qadamlar:
  1. Yangi sirni generate qiling: `openssl rand -hex 32`.
  2. `.env` ichidagi `TELEGRAM_WEBHOOK_SECRET_TOKEN`'ni yangilang.
  3. App container'ni restart qiling.
  4. `setWebhook` ni yangi `secret_token` bilan qayta chaqiring.
- Restart kerakmi: app YES.
- Verifikatsiya: noto'g'ri header'li POST 401 qaytarsin; haqiqiy
  callback 200 qaytarsin va transition ishlasin.

### 4.4 JWT HMAC secret rotation

- O'zgarish: `APP_SECURITY_JWT_HMAC_SECRET`.
- Qadamlar:
  1. Yangi HMAC kalit generate qiling: `openssl rand -base64 48`.
  2. JWT issuer (tashqi) ham yangi kalit bilan token chiqarishni
     boshlasin — aks holda eski JWT'lar rad etiladi.
  3. `.env` ichida secret'ni yangilang.
  4. App restart.
- Restart kerakmi: app YES.
- Verifikatsiya: yangi JWT bilan `/api/admin/tenant-config/details`
  200 qaytarsin; eski JWT 401 qaytarsin.

### 4.5 JWT issuer / JWKS key rotation

- O'zgarish: identity provider tomondan JWKS key ID rotation; app
  tomondan env vars o'zgarmaydi (issuer-uri yoki jwk-set-uri o'sha
  URL'ga ishora qiladi).
- Qadamlar: provider tomondan kalit rotation o'tkazilgach, app
  birinchi 401 holatda JWKS'ni qayta cache qiladi. Tezroq
  amalga oshirish uchun app container'ni restart qilish mumkin.
- Restart kerakmi: ixtiyoriy.
- Verifikatsiya: yangi kid'li token 200; eski kid'li token 401
  bo'ladi.

---

## 5. Telegram webhook hardening va verifikatsiya

To'liq detali
[`telegram-outbound-gateway-runbook.md` §12.3](telegram-outbound-gateway-runbook.md)
ichida.

**Register (qayta register qilish):**

```
curl -sS -X POST \
  "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/setWebhook" \
  -H "Content-Type: application/json" \
  -d '{
        "url": "https://<your.domain>/api/telegram/webhook",
        "secret_token": "<TELEGRAM_WEBHOOK_SECRET_TOKEN>",
        "allowed_updates": ["callback_query"]
      }'
```

**Verifikatsiya:**

```
curl -sS "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/getWebhookInfo"
```

Quyidagilarni tekshiring:

- `url` siz kutgan HTTPS URL'ga teng.
- `has_custom_certificate` — `false` (publicly trusted cert
  ishlatilgan).
- `pending_update_count` keyingi bir necha sekundda `0` ga tushadi.
- `last_error_message` mavjud bo'lmasligi yoki keyingi
  callback'dan keyin tozalanishi kerak.

**Rollback / delete:**

```
curl -sS -X POST \
  "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/deleteWebhook?drop_pending_updates=true"
```

**Response semantikasi (Phase 171/173/175/185):**

| Holat | Response va xulq-atvor |
| --- | --- |
| `X-Telegram-Bot-Api-Secret-Token` yo'q yoki noto'g'ri | `401 UNAUTHORIZED` + envelope. Service umuman chaqirilmaydi. |
| Valid secret + non-callback update | `200 OK`. Business action yo'q (parser ham, workflow ham chaqirilmaydi). |
| Valid secret + parser tomonidan ignored / malformed / unknown callback | `200 OK`. Bounded parser log; workflow execution yo'q; audit qatori yozilmaydi. |
| Valid secret + ACCEPTED callback, muvaffaqiyatli bajariladi | `200 OK`. `WorkflowTransitionService` `STATUS_TRANSITION` audit qatorini yozadi. Phase 175 `answerCallbackQuery` toast — best-effort (fail-soft). |
| Valid secret + ACCEPTED callback, business / auth denial | `200 OK`. Phase 175 `answerCallbackQuery` toast — best-effort. Phase 185 `TELEGRAM_CALLBACK_DENIED` audit qatori faqat eligible denial outcomelar uchun (tenantId + actorUserId + workItemId mavjud bo'lganda) yoziladi. |

`200 OK` business outcome uchun atayin tanlangan — Telegram non-2xx
javoblarda retry qiladi va permanent error'larda loop'ga tushgan
bo'lardi.

**Webhook secret hech qachon log'ga yozilmaydi.** Phase 171
controller token qiymatini hech qaerga chiqarmaydi.

---

## 6. JWT production mode checklist

`SecurityConfig` (Phase 124–148) `JwtDecoder` bean'ni `@ConditionalOnExpression`
bilan yuklaydi (`HMAC`, `ISSUER_URI`, `JWK_SET_URI` rejimlari mutually
exclusive). Production'da quyidagilarni tekshiring:

- [ ] **Exactly one** decoder rejimi configured. Boshqalar bo'sh
      bo'lishi shart.
- [ ] **HMAC rejimi** controlled MVP deployment uchun maqbul: secret
      kuchli (≥ 48 bayt base64), `.env` yoki secret manager ichida,
      operator'dan tashqari hech kim ko'rmagan.
- [ ] **OIDC / JWKS rejim** real identity provider integratsiya
      bo'lganda afzal (Okta, Auth0, Keycloak, Cognito, va h.k.).
- [ ] JWT `sub` claim **AppUser.id** UUID qiymatiga teng. Boshqa
      attribute (email, username) authority emas.
- [ ] Permission DB'dan kelади — JWT roles/groups claim'ga
      tayanmang. `IdentityQueryService.resolvePermissionCodes(...)`
      yagona authority.
- [ ] Decoder yo'q (3'ta env bo'sh) holatda har bir `/api/**`
      so'rovi 401 qaytaradi — fail-closed (Phase 137 + 146 + 148
      invariant).

---

## 7. Actuator hardening (Phase 188)

`application-prod.properties` Phase 188 dan boshlab quyidagi
override'ga ega:

```
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=never
```

**Nima o'zgardi:**

- Base `application.properties` `health,info,metrics,flyway`'ni ochiq
  qilgan edi. Prod'da `flyway` endpoint butunlay yo'q — schema
  metadata HTTP orqali olib bo'lmaydi.
- `/actuator/health` faqat umumiy `status` qaytaradi (`UP` / `DOWN`).
  Komponent-darajadagi diagnostika (DB ping, disk, va h.k.)
  yashirilgan.

**Verifikatsiya:**

```
curl -fsS http://127.0.0.1:8080/actuator/health
```

Kutilgan: `{"status":"UP"}` (komponent ro'yxati yo'q).

```
curl -i http://127.0.0.1:8080/actuator/flyway
```

Kutilgan: prod profile'da `404 Not Found` (yoki endpoint umuman
exposed emas; jami har holda HTTP javobi 2xx emas).

**Production'da actuator faqat ichki probelar uchun.** Reverse proxy
sozlamasida `/actuator/**` yo'lini external clients'ga forward
qilmang — faqat ichki health-check tooli (Kubernetes liveness, ALB
target group) uchun ochiq qoldiring.

---

## 8. Log redaction verification

Application bounded structured logging ishlatadi (Phase 158/160/161/
171/173/175/179/185). Hech bir secret log'ga tushmasligini production
deploy'idan keyin tasdiqlash uchun quyidagi grep retseptlari:

### 8.1 Bot token substring leak check

```
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  logs app | grep -F "$TELEGRAM_BOT_TOKEN"
```

Kutilgan: **zero matches**. `HttpTelegramOutboundGateway` (Phase 158)
har bir log message'da token sub-stringini `***` bilan almashtiradi.

### 8.2 Bot API URL leak check

```
docker compose ... logs app | grep -E "/bot[0-9]+:"
```

Kutilgan: **zero matches**. Bot token URL ichida ham yozilmaydi.

### 8.3 Webhook secret header leak check

```
docker compose ... logs app | grep -F "$TELEGRAM_WEBHOOK_SECRET_TOKEN"
```

Kutilgan: **zero matches**. Phase 171 controller `incoming` qiymatini
hech qaerga yozmaydi.

### 8.4 Raw callback_data leak check

```
docker compose ... logs app | grep -E "callback_data|<UUID>:START_PROCESSING"
```

Kutilgan: faqat metadata (`actionCode=START_PROCESSING` va
`workItemId=<uuid>` alohida-alohida), to'liq raw `<uuid>:<action>`
satr emas.

### 8.5 Exception message leak check

```
docker compose ... logs app | grep "exceptionType="
```

Kutilgan: faqat class simple name (masalan `RuntimeException`,
`AccessDeniedException`), exception message yo'q. Phase 160/161/179/185
da `ex.getMessage()` log'ga ataylab chiqarilmaydi.

Agar har qaysi grep'ning birortasi natija bersa, deploy production
shart emas — root cause'ni aniqlab tuzatish kerak.

---

## 9. Audit hardening verification

Production'ga deploy bo'lgach quyidagi audit qatorlar yozilganligini
tekshiring (psql exec orqali):

### 9.1 `BOOTSTRAP_COMPLETED`

```sql
SELECT occurred_at, action_source, new_value_json
  FROM audit_event
 WHERE event_type = 'BOOTSTRAP_COMPLETED'
 ORDER BY occurred_at DESC LIMIT 5;
```

Bitta qator bo'lishi shart (birinchi muvaffaqiyatli bootstrap).

### 9.2 `STATUS_TRANSITION`

```sql
SELECT occurred_at, actor_user_id, entity_id, action_source
  FROM audit_event
 WHERE event_type = 'STATUS_TRANSITION'
 ORDER BY occurred_at DESC LIMIT 10;
```

Har bir muvaffaqiyatli workflow transition (admin HTTP yoki Telegram
callback) bitta qator yozadi.

### 9.3 `TELEGRAM_CALLBACK_DENIED` (Phase 185)

```sql
SELECT occurred_at, actor_user_id, entity_id, new_value_json
  FROM audit_event
 WHERE event_type = 'TELEGRAM_CALLBACK_DENIED'
 ORDER BY occurred_at DESC LIMIT 20;
```

Operator harakat qilib ko'rib (rad etilgan callback) bitta qator
hosil qilishi mumkin. `new_value_json` faqat
`outcome/actionCode/targetStatusCode` tarkibida bo'lishi shart —
raw callback_data, exception message yoki token yo'q.

### 9.4 Hali joriy bo'lmagan audit yo'llari

Quyidagilar Phase 189 nomzodi sifatida ataylab qoldirilgan:

- Admin API authorization denial uchun audit qator yo'q (hozir faqat
  log).
- Webhook secret 401 rejection uchun audit qator yo'q (hozir faqat
  log).

Phase 189'gacha bu yo'llar uchun log'ni `WARN` darajada grep qiling.

---

## 10. Production deploy checklist

To'liq deployment retsepti
[`production-deployment-runbook.md`](production-deployment-runbook.md)
ichida. Bu yerda faqat oxirgi hardening qadamlar.

- [ ] Hamma kerakli env vars `.env` yoki secret manager ichida.
- [ ] Sirlar rotate qilingan, hech kim qarashga ulgurganligini
      aniqlash imkoni yo'q bo'lsa ham eski qiymatlar bekor
      qilingan.
- [ ] Bootstrap **faqat birinchi run** uchun yoqilgan
      (`APP_BOOTSTRAP_ADMIN_ENABLED=true`), keyingi deploy'da
      `false` ga o'tkazilgan.
- [ ] Webhook `setWebhook` orqali register qilingan; `getWebhookInfo`
      `url` va `pending_update_count=0` ko'rsatadi.
- [ ] `/actuator/health` `UP`.
- [ ] [`mvp-completion-runbook.md`](mvp-completion-runbook.md) §5
      smoke checklisti to'liq yashil.
- [ ] §8 dagi grep retseptlari hech qanday secret leak topmadi.
- [ ] §9 dagi audit qatorlar mavjud (kamida `BOOTSTRAP_COMPLETED`
      va bitta `STATUS_TRANSITION`).

---

## 11. Known limitations va keyingi phase'lar

Phase 188 ataylab tor doirada. Quyidagi bo'shliqlar keyingi
bounded phase'lar uchun ochiq qoldirildi:

- **Backup / restore.** [`backup-restore-runbook.md`](backup-restore-runbook.md)
  Phase 188 doirasida yoziladi.
- **Micrometer counter'lar.** Delivery attempt outcome, callback
  outcome, va card refresh outcome uchun counter'lar Phase 189
  nomzodi. Hozircha alert log'da grep orqali.
- **Admin API denial audit + webhook secret rejection audit.**
  Phase 189 nomzodi — log'dan audit qatoriga ko'tarish.
- **Priority / severity / owner intake va admin write API.**
  Phase 190 nomzodi. Schema (V3) allaqachon column'larni qo'llaydi;
  faqat HTTP surface yo'q.
- **INCIDENT / TASK workflow template / bootstrap seed.** Phase 190
  nomzodi. Hozir faqat BUG workflow seed qilinadi (Phase 156).
- **Web admin UI, Kubernetes/Helm, multi-region HA, SSO/SAML, AI
  triage, BI dashboard** — v1 dan tashqari, keyingi product
  scope.

Phase 189 boshlanishidan oldin Phase 188 deploy'dan o'tkazilsin va
yuqoridagi 8.x grep retseptlari clean qaytarsin. Faqat shu yerda
production-usable v1 «hardening» qismi yopiladi.
