# Backup / Restore Runbook (Phase 188)

> **Phase 188.** Single-VM Docker Compose deployment uchun PostgreSQL
> backup va restore bo'yicha minimum viable operator runbook.
> Avtomatlashtirish script'lari, cron fayllari yoki yangi konteyner
> qo'shilmaydi — faqat operator-runnable retseptlar va checklist.

---

## 1. Maqsad va auditoriya

**Maqsad.** Production'ga deploy bo'lgan platforma uchun PostgreSQL
ma'lumotlarining xavfsiz backup'ini olish, off-host saqlash va
falokat holatida restore qilish bo'yicha qadamlarni hujjatlashtirish.

**Auditoriya.**
- Production-ni boshqaruvchi **operator** / SRE.
- **Backend owner** — schema o'zgaradigan deploy'dan oldin backup
  olish uchun.
- **Incident responder** — falokat yuz berganda restore drill'ni
  bajaradigan kishi.

**Hujjatlash doirasi.** Phase 187 da hosil bo'lgan
`docker-compose.yml` + `docker-compose.prod.yml` overlay
deployment'i. Kubernetes/Helm, multi-region replication, va PITR
(point-in-time recovery) v1 dan tashqari va alohida phase nomzodi.

---

## 2. Backup mental model

```
   engops-postgres volume (engops-pgdata)
        ↓ pg_dump  (logical, schema + data)
   .dump fayl       (host filesystem'da, encrypted)
        ↓ off-host upload  (S3 / GCS / SFTP / external NAS)
   restore drill
        ↓ staging verification
   production fallback ready
```

**Invariantlar:**

- **Database — source of truth.** Tenant config, identity, work
  item, audit, workflow definition, telegram delivery attempt —
  hammasi shu yerda saqlanadi. Telegram xabarlar projection
  (Phase 158/164/179 invariant).
- **Backup mahalliy diskdan tashqarida ham saqlanishi shart.**
  VM yo'qolsa, mahalliy backup ham yo'qoladi.
- **Backup faylda sezgir operational data bor** (audit log,
  membership, role binding, work item tarixi). Off-host saqlash
  shifrlangan bo'lishi shart.
- **Restore drill production'dan tashqari muhitda bajariladi.**
  Productionga to'g'ridan-to'g'ri restore — oxirgi chora.

---

## 3. Backup tarkibida nima bo'lishi shart

`pg_dump` butun `engops` ma'lumotlar bazasini logical formatda
saqlaydi. Quyidagi mantiqiy bloklar ichida:

- **Flyway schema state** — `flyway_schema_history` jadvali, joriy
  schema versiyasi (V1–V6 Phase 141 dan).
- **Tenant config** — `tenant`, `app_user`, `workflow_definition`,
  `workflow_status`, `workflow_transition_rule`, `telegram_chat_binding`,
  `telegram_topic_binding`, `routing_rule`.
- **Identity** — `app_user`, `membership`, `role`, `permission`,
  `membership_role_binding`, `role_permission`.
- **Work items va workflow** — `work_item`, `work_item_counter`,
  `work_item_update`, `work_item_transition`.
- **Audit event** — `audit_event` (BOOTSTRAP_COMPLETED,
  STATUS_TRANSITION, va Phase 185 TELEGRAM_CALLBACK_DENIED).
- **Telegram delivery attempt** — `telegram_delivery_attempt`
  append-only tarix (sendMessage outcome'lari).

**Backup'da BO'LMAYDI:**

- Application env vars (DATABASE_PASSWORD, TELEGRAM_BOT_TOKEN,
  webhook secret, JWT secret). Bu sirlar alohida secret manager /
  `.env` ichida saqlanadi va o'ziga xos rotation strategy'siga ega
  ([`production-hardening-runbook.md` §4](production-hardening-runbook.md)).
- Docker volume metadata (`engops-pgdata` mount point) — backup
  qayta tiklanganda yangi mount point yaratiladi.
- `.env` fayl yoki har qanday secret. **Hech qachon** backup
  hujjatiga env-secret yozmang.

---

## 4. Backup methods

### 4.1 Logical backup — `pg_dump` (recommended for v1)

Eng oddiy va portable. Bitta tenant DB uchun yetarli. Format:
`custom` (sotiladigan, kompressed, `pg_restore` bilan flexible).

Foyda: schema + data + roles + sequences hammasi bitta faylda;
restore strategiyasi keng (jadval ichida tanlash, ma'lumot
filtratsiyasi).

Cheklov: snapshot vaqtidagi consistency (long-running tranzaksiyalar
bo'lsa locking ehtimoli); juda katta DB uchun (≥ 50 GB) sekin.

### 4.2 `pg_dumpall` — cluster-wide backup

Faqat agar **bir necha DB / global role** mavjud bo'lsa kerak. Bu
loyihada faqat bitta `engops` DB ishlatiladi, shuning uchun
`pg_dumpall` zarur emas. Lekin Postgres rolinin parolini ham backup
qilish kerak bo'lsa (`pg_dump` faqat data + schema oladi),
`pg_dumpall --globals-only` ishlatilishi mumkin.

### 4.3 Managed PostgreSQL snapshot

Cloud managed Postgres ishlatilsa (AWS RDS, GCP Cloud SQL, DigitalOcean
managed DB, va h.k.) — provider snapshot tooli birinchi tanlov.
Operator faqat retention va off-region copy'ni configure qiladi.
Logical `pg_dump` ham qo'shimcha quvvat (multi-cloud portability).

### 4.4 Volume-level backup — diqqat

Docker volume (`engops-pgdata`) ni to'g'ridan-to'g'ri arxivlash —
faqat Postgres **to'xtatilgan** holatda xavfsiz. Live volume copy
crash-recovery'siz "torn" data berishi mumkin.

```
docker compose -f docker-compose.yml -f docker-compose.prod.yml stop postgres
docker run --rm -v engops-pgdata:/data -v "$(pwd)":/backup \
  alpine:3 tar czf /backup/pgdata-$(date +%Y%m%d-%H%M%S).tgz -C /data .
docker compose -f docker-compose.yml -f docker-compose.prod.yml start postgres
```

Bu hujjat **uchun asosiy strategiya emas**. v1 da `pg_dump` afzal.

---

## 5. Recommended v1 backup policy

Bitta jamoa, single-VM deployment uchun amaliy siyosat:

- **Frekvensiya:** kechki (off-peak) `pg_dump` har kuni.
- **Retention:** 7 ta kunlik + 4 ta haftalik + 3 ta oylik
  (oddiy GFS rotation).
- **Encryption:** off-host upload'dan oldin AES-256 (masalan
  `gpg --symmetric --cipher-algo AES256` yoki `openssl enc -aes-256-cbc`).
- **Off-host storage:** S3 / GCS / SFTP / external NAS. Hech qachon
  shu VM ichida qoldirmang.
- **Restore drill:** kamida oyiga bir marta staging environment'ga
  restore qilib, smoke verification.
- **Schema o'zgaradigan deploy'dan oldin** majburiy ad-hoc backup
  (Phase 141 dan keyin migration qo'shilmagan, lekin Phase 190+
  da yangi V7+ qo'shilishi mumkin).
- **Manual DB maintenance'dan oldin** majburiy ad-hoc backup
  (masalan `VACUUM FULL` yoki katta DELETE).

---

## 6. Docker Compose `pg_dump` command

Phase 187 compose overlay'i Postgres'ni faqat docker network'da
chiqaradi. Backup `docker compose exec` orqali olinadi.

### 6.1 Logical backup (custom format)

```
TIMESTAMP=$(date -u +%Y%m%dT%H%M%SZ)
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  exec -T postgres pg_dump \
    --username="$POSTGRES_USER" \
    --dbname="$POSTGRES_DB" \
    --format=custom \
    --no-owner \
    --no-privileges \
    --compress=9 \
  > "./backups/engops-${TIMESTAMP}.dump"
```

Eslatmalar:

- `-T` flag `docker compose exec` da TTY allocation'ni o'chiradi
  (binary stdout uchun shart).
- `--no-owner` va `--no-privileges` portability uchun (restore
  paytida boshqa role bilan import qilish mumkin).
- `--compress=9` maksimal kompressiya; CPU narxi katta DB uchun
  o'rta bo'lishi mumkin (4–6 ham ko'p hollarda yetarli).
- `./backups/` katalog operator tomondan yaratilgan, `chmod 700`,
  egasi deploy user'i.

### 6.2 Encrypt before off-host upload

```
gpg --symmetric --cipher-algo AES256 \
  --output "./backups/engops-${TIMESTAMP}.dump.gpg" \
  "./backups/engops-${TIMESTAMP}.dump"
shred -u "./backups/engops-${TIMESTAMP}.dump"
```

Yoki off-host upload tooli (rclone, awscli) o'zining server-side
encryption'ini ishlatish mumkin.

### 6.3 Off-host upload misol (rclone bilan)

```
rclone copy "./backups/engops-${TIMESTAMP}.dump.gpg" \
  my-encrypted-bucket:engops-backups/$(date -u +%Y/%m)/
```

`rclone` config va remote secret manager ichida.

---

## 7. Restore drill

**Hech qachon birinchi qadam — production'ga to'g'ridan-to'g'ri
restore qilish bo'lmasin.** Quyidagi xavfsiz sequence shart.

### 7.1 Tayyorgarlik

1. Encrypted backup faylni decrypt qiling:
   ```
   gpg --decrypt --output ./restore-work/engops.dump \
     ./backups/engops-${TIMESTAMP}.dump.gpg
   ```
2. Staging muhit yarating — alohida VM yoki localhost ichida
   alohida Postgres konteyner (boshqa volume, boshqa port).
3. App'ni production manbai bilan emas, restore qilingan staging
   DB bilan ishga tushirishga tayyorlaning (alohida `.env`).

### 7.2 Restore sequence

1. **App'ni to'xtating** (yoki staging mode'ga o'tkazing):
   ```
   docker compose -f docker-compose.yml -f docker-compose.prod.yml stop app
   ```
2. **Bo'sh DB yarating:**
   ```
   docker compose -f docker-compose.yml -f docker-compose.prod.yml \
     exec postgres psql -U "$POSTGRES_USER" -d postgres \
       -c "CREATE DATABASE engops_restore_${TIMESTAMP};"
   ```
3. **Dump faylni restore qiling:**
   ```
   docker compose -f docker-compose.yml -f docker-compose.prod.yml \
     exec -T postgres pg_restore \
       --username="$POSTGRES_USER" \
       --dbname="engops_restore_${TIMESTAMP}" \
       --no-owner \
       --no-privileges \
     < ./restore-work/engops.dump
   ```
4. **App'ni isolated DB ga ishora qilib qayta tushiring:**
   `DATABASE_URL=jdbc:postgresql://postgres:5432/engops_restore_<TIMESTAMP>`
   bilan vaqtinchalik compose override yoki alohida `.env`.

### 7.3 Verification

- [ ] `curl -fsS http://127.0.0.1:8080/actuator/health` →
      `UP` (Phase 188 dan keyin detail'siz `status` only).
- [ ] App log'da Flyway `Successfully applied 6 migrations` (yoki
      keyingi version) yoki `Schema is up to date` ko'rsatsin.
- [ ] `GET /api/admin/tenant-config/details` → tenant ro'yxati to'g'ri.
- [ ] `GET /api/admin/work-items/details/by-id?...` — backup paytidagi
      qaysi work item identifikatorlari mavjud bo'lsa, o'shalarni
      qaytarsin.
- [ ] Audit event sanog'i:
      ```sql
      SELECT event_type, COUNT(*)
        FROM audit_event
       GROUP BY event_type
       ORDER BY 1;
      ```
      Sanog'i backup oldidagi qiymatga teng yoki yaqin bo'lsin.

### 7.4 Cutover qarori

Verifikatsiya green bo'lsa va operator productionni shu restore
holatiga o'tkazishni xohlasa:

1. Production app'ni to'xtatish.
2. Production DB'ni `engops_production_old_${TIMESTAMP}` ga
   rename qilish (audit forensiya uchun).
3. Restore qilingan DB'ni `engops` deb rename qilish.
4. App'ni qayta tushirish.
5. `setWebhook` qayta chaqirish (agar host yoki secret o'zgarsa).
6. `mvp-completion-runbook.md` §5 smoke checklisti bilan
   verifikatsiya.

---

## 8. Pre-deployment backup checklist

Quyidagi vaziyatlarda **majburiy** ad-hoc backup oling (yuqoridagi
§6.1 retsepti bilan):

- [ ] Yangi Flyway migration qo'shilgan deploy. Phase 141 dan beri
      migration qo'shilmadi, lekin Phase 190+ buni o'zgartirishi
      mumkin.
- [ ] Major deploy (Spring Boot version yangilanish, JVM o'zgarish,
      katta refactor).
- [ ] DB access'ga ta'sir qiluvchi secret rotation (POSTGRES_PASSWORD,
      DATABASE_PASSWORD).
- [ ] Qo'lda DB maintenance: `VACUUM FULL`, katta `DELETE`, indeks
      qayta yaratish.
- [ ] Birinchi production deploy. Bootstrap muvaffaqiyatli bo'lgach
      darhol backup oling — birinchi clean baseline.

---

## 9. Disaster recovery checklist

Falokat yuz bersa (DB corruption, VM yo'qolish, data loss):

1. **Incident vaqtini aniqlang.** Telegram audit + application
   log'lar last good state'ni topishga yordam beradi.
2. **Mos backup tanlang** — incident'dan oldingi eng so'nggi.
3. **Isolated muhitga restore qiling** (§7).
4. **Verifikatsiya** — tenant, work item, audit, delivery attempt
   sanog'i kutilgan qiymatga teng.
5. **Cutover qaror** — operator va backend owner birgalikda.
6. **`setWebhook` qayta chaqirish** — agar host yoki webhook secret
   o'zgargan bo'lsa.
7. **Demo smoke subset'ni bajarish** —
   [`demo-smoke-runbook.md` §7–§9](demo-smoke-runbook.md) yoki
   [`mvp-completion-runbook.md` §5](mvp-completion-runbook.md)
   checklisti.

---

## 10. Backup uchun security checklist

- [ ] Backup faylda audit event, membership, role binding, va boshqa
      sezgir operational data bor. **Encrypt at rest** majburiy.
- [ ] Backup fayl host filesystem'da `chmod 600`, egasi deploy
      user'i (yoki root).
- [ ] Public bucket (S3 default `public-read`, GCS public access)
      ga **hech qachon** yuklamang.
- [ ] git'ga commit qilmang. `.dockerignore` allaqachon `*.dump`
      naqshini chiqaradi, lekin operator atayin `git add` qilmasin.
- [ ] Issue tracker / chat / Slack / Telegram'ga SQL dump'lar
      yopishtirmang. Hatto qisqartirilgan diagnostik to'g'rilash uchun
      ham.
- [ ] Backup yo'qolsa yoki shubhali kirish bo'lsa — bot token,
      webhook secret, DB password'ni
      [`production-hardening-runbook.md` §4](production-hardening-runbook.md)
      bo'yicha rotate qiling.

---

## 11. Known limitations

Phase 188 ataylab tor doirada — operator-runnable retseptlar va
checklist. Quyidagilar **yo'q**:

- App ichida built-in backup scheduler yo'q. Cron / systemd timer /
  K8s CronJob — operator tomondan.
- Point-in-time recovery (PITR) `pg_basebackup` + WAL archive
  o'rnatilmagan. v1 da `pg_dump` yetarli; PITR — keyingi product
  scope.
- Kubernetes / Helm-native backup tooli yo'q. v1 single-VM.
- Multi-region replication yo'q. Single zone deployment.
- Automated restore drill yo'q. Operator har oy qo'lda bajarishi
  kerak.
- Avtomatlashtirilgan smoke verification scripti yo'q. Restore'dan
  keyin §7.3 checklist'ini operator qo'lda yuradi.

---

## 12. Quick commands appendix

Operator tezda ishlatish uchun yig'ma.

**Backup:**

```
TIMESTAMP=$(date -u +%Y%m%dT%H%M%SZ)
mkdir -p ./backups && chmod 700 ./backups
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  exec -T postgres pg_dump \
    --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
    --format=custom --no-owner --no-privileges --compress=9 \
  > "./backups/engops-${TIMESTAMP}.dump"
```

**Encrypt + remove plaintext:**

```
gpg --symmetric --cipher-algo AES256 \
  --output "./backups/engops-${TIMESTAMP}.dump.gpg" \
  "./backups/engops-${TIMESTAMP}.dump"
shred -u "./backups/engops-${TIMESTAMP}.dump"
```

**List local backups:**

```
ls -lh ./backups/*.dump.gpg
```

**Restore (isolated DB):**

```
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  exec postgres psql -U "$POSTGRES_USER" -d postgres \
    -c "CREATE DATABASE engops_restore_${TIMESTAMP};"
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  exec -T postgres pg_restore \
    --username="$POSTGRES_USER" \
    --dbname="engops_restore_${TIMESTAMP}" \
    --no-owner --no-privileges \
  < ./restore-work/engops.dump
```

**Verify schema version (Flyway):**

```
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  exec postgres psql -U "$POSTGRES_USER" -d "engops_restore_${TIMESTAMP}" \
    -c "SELECT version, description, success, installed_on
          FROM flyway_schema_history
         ORDER BY installed_rank;"
```

**Verify audit count:**

```
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  exec postgres psql -U "$POSTGRES_USER" -d "engops_restore_${TIMESTAMP}" \
    -c "SELECT event_type, COUNT(*) FROM audit_event
        GROUP BY event_type ORDER BY 1;"
```

**Cleanup local plaintext after restore drill:**

```
shred -u ./restore-work/engops.dump
rmdir ./restore-work
```

Restore drilldan keyin staging DB'ni o'chirish:

```
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  exec postgres psql -U "$POSTGRES_USER" -d postgres \
    -c "DROP DATABASE engops_restore_${TIMESTAMP};"
```

---

To'liq deploy zanjirini ko'rish uchun
[`production-deployment-runbook.md`](production-deployment-runbook.md).
Production hardening checklist:
[`production-hardening-runbook.md`](production-hardening-runbook.md).
Single operator entry point — yana
[`mvp-completion-runbook.md`](mvp-completion-runbook.md).
