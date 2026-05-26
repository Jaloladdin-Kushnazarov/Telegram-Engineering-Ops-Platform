package com.engops.platform.dev;

import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.model.AppUserRoleBinding;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipRoleBinding;
import com.engops.platform.identity.model.Role;
import com.engops.platform.identity.repository.AppUserRepository;
import com.engops.platform.identity.repository.AppUserRoleBindingRepository;
import com.engops.platform.identity.repository.MembershipRepository;
import com.engops.platform.identity.repository.MembershipRoleBindingRepository;
import com.engops.platform.identity.repository.RoleRepository;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.model.WorkflowStatus;
import com.engops.platform.tenantconfig.repository.TenantRepository;
import com.engops.platform.tenantconfig.repository.WorkflowDefinitionRepository;
import com.engops.platform.tenantconfig.repository.WorkflowStatusRepository;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemCounter;
import com.engops.platform.workitem.model.WorkItemType;
import com.engops.platform.workitem.repository.WorkItemCounterRepository;
import com.engops.platform.workitem.repository.WorkItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 211 — DEV PROFILE bootstrap seed.
 * Phase 216 — per-step idempotency + seedPlatformOwners() property-driven seed.
 *
 * <p>Faqat {@code app.security.dev-mode.enabled=true} bo'lganda bean
 * yaratiladi. Production'da property o'rnatilmagan — bean yo'q, seed
 * bajarilmaydi.</p>
 *
 * <p><strong>Phase 216 atomic cutover:</strong> ADMIN role'idan
 * TENANT_ONBOARD V10 migration'da olib tashlanadi va platform-level
 * tenant yaratish faqat {@code PLATFORM_OWNER} role bilan kelgan
 * {@link AppUserRoleBinding} orqali ishlaydi. {@link #seedPlatformOwners()}
 * metodi bootstrap admin'ga shu binding'ni yaratadi (yangi property
 * {@code app.security.bootstrap.platform-owner-telegram-ids} orqali).</p>
 *
 * <p><strong>Idempotency model (Phase 216 refactor):</strong> har bir
 * seed metod o'zining state'ini tekshirib skip qilishi mumkin. Avval
 * "admin user mavjud bo'lsa hamma narsani skip" pattern'i bor edi —
 * bu yangi seedPlatformOwners() chaqirilmasligiga olib kelardi. Endi
 * har metod alohida idempotent.</p>
 *
 * <h3>Demo'ni ishga tushirish</h3>
 * <pre>
 *   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
 * </pre>
 *
 * Startup log namunasi:
 * <pre>
 *   Dev bootstrap: starting seed...
 *   Dev bootstrap: admin user yaratildi user_id=00000000-...
 *   ...
 *   Dev bootstrap: user 00000000-... (telegram 100000001) PLATFORM_OWNER granted
 *   Dev bootstrap: seedPlatformOwners — 1 new bindings
 *   Dev bootstrap: seed complete
 * </pre>
 *
 * <h3>Production posture</h3>
 * <p>Property o'rnatilmagan → bean yo'q → seed bajarilmaydi → ZERO
 * production o'zgarishi.</p>
 */
@Component
@ConditionalOnProperty(name = "app.security.dev-mode.enabled", havingValue = "true")
public class DevBootstrapInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevBootstrapInitializer.class);

    // ========== Stable UUID sentinels ==========
    public static final UUID BOOTSTRAP_ADMIN_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID BOOTSTRAP_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID BOOTSTRAP_WORKFLOW_BUG_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    static final UUID BOOTSTRAP_WORKFLOW_INCIDENT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    static final UUID BOOTSTRAP_WORKFLOW_TASK_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000b3");

    /** Phase 215 V9 — PLATFORM_OWNER role sentinel UUID. */
    static final UUID PLATFORM_OWNER_ROLE_ID =
            UUID.fromString("b0000000-0000-0000-0000-000000000005");

    static final long BOOTSTRAP_ADMIN_TELEGRAM_USER_ID = 100_000_001L;
    static final String BOOTSTRAP_TENANT_SLUG = "demo";
    static final String ADMIN_ROLE_CODE = "ADMIN";

    static final int DEMO_WORK_ITEM_COUNT = 10;

    private final AppUserRepository appUserRepository;
    private final TenantRepository tenantRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipRoleBindingRepository membershipRoleBindingRepository;
    private final RoleRepository roleRepository;
    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemCounterRepository workItemCounterRepository;
    private final AppUserRoleBindingRepository appUserRoleBindingRepository;
    private final String platformOwnerTelegramIdsRaw;

    public DevBootstrapInitializer(AppUserRepository appUserRepository,
                                    TenantRepository tenantRepository,
                                    MembershipRepository membershipRepository,
                                    MembershipRoleBindingRepository membershipRoleBindingRepository,
                                    RoleRepository roleRepository,
                                    WorkflowDefinitionRepository workflowDefinitionRepository,
                                    WorkflowStatusRepository workflowStatusRepository,
                                    WorkItemRepository workItemRepository,
                                    WorkItemCounterRepository workItemCounterRepository,
                                    AppUserRoleBindingRepository appUserRoleBindingRepository,
                                    @Value("${app.security.bootstrap.platform-owner-telegram-ids:}")
                                    String platformOwnerTelegramIdsRaw) {
        this.appUserRepository = appUserRepository;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.membershipRoleBindingRepository = membershipRoleBindingRepository;
        this.roleRepository = roleRepository;
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.workflowStatusRepository = workflowStatusRepository;
        this.workItemRepository = workItemRepository;
        this.workItemCounterRepository = workItemCounterRepository;
        this.appUserRoleBindingRepository = appUserRoleBindingRepository;
        this.platformOwnerTelegramIdsRaw = platformOwnerTelegramIdsRaw;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Dev bootstrap: starting seed...");

        // ADMIN role lookup — production'da V2 migration uni seed qiladi.
        // Test profile'da Flyway off — test'lar uni qo'lda yaratadi.
        // Agar yo'q bo'lsa, faqat tenant/admin/membership/role-binding/
        // workflows seed'ini skip qilamiz — seedPlatformOwners hali ham
        // ishlab ko'rilishi mumkin (faqat AppUser mavjud bo'lsa).
        Role adminRole = roleRepository.findByCode(ADMIN_ROLE_CODE).orElse(null);
        if (adminRole == null) {
            log.warn("Dev bootstrap: ADMIN role topilmadi (Flyway disabled?). "
                    + "Tenant/admin/workflows seed o'tkazib yuborildi.");
        } else {
            seedAdminAndDemoTenant(adminRole);
        }

        // Phase 221 — oldingi bootstrap runlari (bu Phase'dan oldin) demo
        // workflow'larni status'siz yaratgan bo'lishi mumkin. Mavjud-DB repair
        // yo'li: status'larni backfill qiladi (idempotent).
        repairDemoWorkflowStatuses();

        // Phase 222 — oldingi runlar work_item_counter'ni yaratmagan bo'lishi
        // mumkin (demo item'lar aniq kod bilan kiritilgan). Backfill (idempotent).
        repairDemoWorkItemCounters();

        // Platform owner seed — alohida idempotent, ADMIN role'dan mustaqil.
        // Phase 216 yangi yo'l: PLATFORM_OWNER role V9 migration'da seed
        // qilinadi va bootstrap admin (yoki property'da ko'rsatilgan boshqa
        // foydalanuvchilar) AppUserRoleBinding orqali biriktiriladi.
        seedPlatformOwners();

        log.info("Dev bootstrap: seed complete");
    }

    // ========== Seed steps (per-step idempotent) ==========

    /**
     * Bootstrap admin + Demo tenant + membership + role binding + workflow
     * definitions + 10 work items. Admin user fixed UUID bo'yicha early
     * return — barchasi atomik (Phase 211 semantikasi).
     */
    private void seedAdminAndDemoTenant(Role adminRole) {
        if (appUserRepository.findById(BOOTSTRAP_ADMIN_USER_ID).isPresent()) {
            log.info("Dev bootstrap: admin user mavjud, demo seed o'tkazib yuborildi ({})",
                    BOOTSTRAP_ADMIN_USER_ID);
            return;
        }

        AppUser admin = seedAdminUser();
        Tenant tenant = seedTenant();
        Membership membership = seedMembership(tenant, admin);
        seedRoleBinding(membership, adminRole);
        Map<WorkItemType, List<DemoStatusSpec>> statusSpecs = demoStatusSpecs();
        WorkflowDefinition wfBug = seedWorkflowDefinition(tenant,
                BOOTSTRAP_WORKFLOW_BUG_ID, "Demo Bug Workflow", WorkItemType.BUG);
        seedWorkflowStatusesIfMissing(wfBug, statusSpecs.get(WorkItemType.BUG));
        WorkflowDefinition wfIncident = seedWorkflowDefinition(tenant,
                BOOTSTRAP_WORKFLOW_INCIDENT_ID, "Demo Incident Workflow", WorkItemType.INCIDENT);
        seedWorkflowStatusesIfMissing(wfIncident, statusSpecs.get(WorkItemType.INCIDENT));
        WorkflowDefinition wfTask = seedWorkflowDefinition(tenant,
                BOOTSTRAP_WORKFLOW_TASK_ID, "Demo Task Workflow", WorkItemType.TASK);
        seedWorkflowStatusesIfMissing(wfTask, statusSpecs.get(WorkItemType.TASK));
        seedWorkItems(tenant, admin, wfBug, wfIncident, wfTask);

        // Phase 222 — work_item_counter'ni oldindan band qilingan demo
        // kodlardan o'tkazib qo'yamiz (aks holda generator BUG-1 ni qayta
        // beradi va UNIQUE constraint buziladi).
        List<DemoWorkItemSpec> specs = demoSpecs();
        for (WorkItemType type : List.of(WorkItemType.BUG, WorkItemType.INCIDENT, WorkItemType.TASK)) {
            seedOrAdvanceWorkItemCounter(tenant.getId(), type, specs);
        }

        log.info("Dev bootstrap: admin={}, tenant={}, work items={}",
                admin.getId(), tenant.getId(), DEMO_WORK_ITEM_COUNT);
    }

    private AppUser seedAdminUser() {
        AppUser admin = new AppUser(BOOTSTRAP_ADMIN_USER_ID,
                BOOTSTRAP_ADMIN_TELEGRAM_USER_ID, "Demo Admin");
        admin.setUsername("demo_admin");
        return appUserRepository.save(admin);
    }

    private Tenant seedTenant() {
        Tenant tenant = new Tenant(BOOTSTRAP_TENANT_ID, "Demo Tenant", BOOTSTRAP_TENANT_SLUG);
        tenant.setTimezone("Asia/Tashkent");
        return tenantRepository.save(tenant);
    }

    private Membership seedMembership(Tenant tenant, AppUser admin) {
        Membership membership = new Membership(tenant.getId(), admin.getId());
        return membershipRepository.save(membership);
    }

    private void seedRoleBinding(Membership membership, Role adminRole) {
        MembershipRoleBinding binding = new MembershipRoleBinding(membership, adminRole);
        membershipRoleBindingRepository.save(binding);
    }

    private WorkflowDefinition seedWorkflowDefinition(Tenant tenant, UUID id,
                                                       String name, WorkItemType type) {
        WorkflowDefinition wf = new WorkflowDefinition(tenant.getId(), name, type.name());
        wf.setDescription("Phase 211 dev bootstrap demo workflow");
        return workflowDefinitionRepository.save(wf);
    }

    private void seedWorkItems(Tenant tenant, AppUser admin,
                                WorkflowDefinition wfBug,
                                WorkflowDefinition wfIncident,
                                WorkflowDefinition wfTask) {
        List<DemoWorkItemSpec> specs = demoSpecs();
        List<WorkItem> items = new ArrayList<>(specs.size());
        for (DemoWorkItemSpec spec : specs) {
            UUID wfId = switch (spec.type()) {
                case BUG -> wfBug.getId();
                case INCIDENT -> wfIncident.getId();
                case TASK -> wfTask.getId();
            };
            WorkItem item = new WorkItem(tenant.getId(), spec.code(), spec.type(),
                    wfId, spec.title(), spec.status(), admin.getId());
            item.setSeverityCode(spec.severity());
            items.add(item);
        }
        workItemRepository.saveAll(items);
    }

    /**
     * Phase 216 — property orqali ko'rsatilgan telegram_user_id'lar uchun
     * {@link AppUserRoleBinding}({@code PLATFORM_OWNER}) seed qiladi.
     *
     * <p><strong>Idempotent:</strong>
     * {@link AppUserRoleBindingRepository#existsByUserIdAndRoleId} tekshiruvi
     * orqali takror seed bo'lmaydi. Birinchi runda binding yaratiladi,
     * keyingi runlarda skip.</p>
     *
     * <p><strong>Graceful skip:</strong></p>
     * <ul>
     *   <li>Property bo'sh → log INFO + return (hech kim seed qilinmaydi)</li>
     *   <li>Format xato (non-numeric) → log WARN + skip shu item</li>
     *   <li>AppUser telegram_user_id bo'yicha topilmadi → log WARN + skip
     *       (admin hali yaratilmagan, masalan)</li>
     * </ul>
     */
    void seedPlatformOwners() {
        if (platformOwnerTelegramIdsRaw == null || platformOwnerTelegramIdsRaw.isBlank()) {
            log.info("Dev bootstrap: platform-owner-telegram-ids bo'sh, seedPlatformOwners skip");
            return;
        }

        String[] ids = platformOwnerTelegramIdsRaw.split(",");
        int granted = 0;
        for (String idStr : ids) {
            String trimmed = idStr.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            long telegramId;
            try {
                telegramId = Long.parseLong(trimmed);
            } catch (NumberFormatException ex) {
                log.warn("Dev bootstrap: invalid telegram id '{}', skip", trimmed);
                continue;
            }
            Optional<AppUser> userOpt = appUserRepository.findByTelegramUserId(telegramId);
            if (userOpt.isEmpty()) {
                log.warn("Dev bootstrap: telegram_user_id={} uchun AppUser topilmadi, "
                        + "PLATFORM_OWNER seed skip", telegramId);
                continue;
            }
            AppUser user = userOpt.get();
            if (appUserRoleBindingRepository.existsByUserIdAndRoleId(
                    user.getId(), PLATFORM_OWNER_ROLE_ID)) {
                log.info("Dev bootstrap: user {} (telegram {}) allaqachon "
                        + "PLATFORM_OWNER, skip", user.getId(), telegramId);
                continue;
            }
            AppUserRoleBinding binding = new AppUserRoleBinding(
                    user.getId(), PLATFORM_OWNER_ROLE_ID);
            appUserRoleBindingRepository.save(binding);
            granted++;
            log.info("Dev bootstrap: user {} (telegram {}) PLATFORM_OWNER granted",
                    user.getId(), telegramId);
        }
        log.info("Dev bootstrap: seedPlatformOwners — {} new bindings", granted);
    }

    /**
     * Demo workflow uchun status'larni seed qiladi (idempotent).
     * Har bir status nomi uchun {@code existsByWorkflowDefinition_IdAndName}
     * pre-check qilinadi — mavjud bo'lsa skip, yo'q bo'lsa qo'shadi.
     *
     * <p>Bu metod ikki yo'ldan chaqiriladi:</p>
     * <ul>
     *   <li>Fresh-DB path: {@link #seedAdminAndDemoTenant} ichida har bir
     *       {@code seedWorkflowDefinition} chaqiruvidan keyin.</li>
     *   <li>Existing-DB repair path: {@link #repairDemoWorkflowStatuses} orqali,
     *       oldingi runlarda status'siz yaratilgan workflow'larni puchqalash uchun.</li>
     * </ul>
     */
    private void seedWorkflowStatusesIfMissing(WorkflowDefinition workflow,
                                               List<DemoStatusSpec> specs) {
        List<WorkflowStatus> toInsert = specs.stream()
                .filter(s -> !workflowStatusRepository
                        .existsByWorkflowDefinition_IdAndName(workflow.getId(), s.name()))
                .map(s -> new WorkflowStatus(workflow, s.name(), s.order(),
                        s.initial(), s.terminal()))
                .toList();
        if (!toInsert.isEmpty()) {
            workflowStatusRepository.saveAll(toInsert);
        }
        log.info("Dev bootstrap: '{}' workflow uchun {} ta status qo'shildi",
                workflow.getName(), toInsert.size());
    }

    /**
     * Phase 221 — mavjud-DB repair: oldingi bootstrap runlari demo
     * workflow'larni status'siz yaratgan bo'lsa, status'larni backfill qiladi.
     * Idempotent — {@link #seedWorkflowStatusesIfMissing} pre-check qiladi.
     *
     * <p>Demo tenant yo'q bo'lsa (masalan ADMIN role topilmagan fresh test)
     * darhol qaytadi. Workflow topilmasa WARN log — bu yerda yaratilmaydi,
     * u {@link #seedAdminAndDemoTenant}'ning vazifasi.</p>
     */
    private void repairDemoWorkflowStatuses() {
        if (tenantRepository.findById(BOOTSTRAP_TENANT_ID).isEmpty()) {
            return;
        }
        Map<WorkItemType, List<DemoStatusSpec>> statusSpecs = demoStatusSpecs();
        for (WorkItemType type : List.of(WorkItemType.BUG, WorkItemType.INCIDENT, WorkItemType.TASK)) {
            Optional<WorkflowDefinition> workflow = workflowDefinitionRepository
                    .findByTenantIdAndWorkItemType(BOOTSTRAP_TENANT_ID, type.name());
            if (workflow.isPresent()) {
                seedWorkflowStatusesIfMissing(workflow.get(), statusSpecs.get(type));
            } else {
                log.warn("Dev bootstrap: {} demo workflow topilmadi — status backfill skip", type);
            }
        }
        log.info("Dev bootstrap: repairDemoWorkflowStatuses tugadi");
    }

    /**
     * Demo tenant uchun {@link WorkItemCounter}'ni seed qiladi va oldindan band
     * qilingan {@link #demoSpecs()} kodlaridan o'tkazib qo'yadi. Idempotent —
     * mavjud counter {@link WorkItemCounter#advanceTo(long)} orqali faqat
     * oldinga qarab siljiydi.
     *
     * <p>{@code minimumNextValue} hisoblash qoidasi: generator
     * ({@link WorkItemCodeGenerator#generate}) {@code typeCode.name() + "-"}
     * prefiksini ishlatadi. Faqat shu prefiks bilan boshlangan demo kodlar
     * to'qnashuvga sabab bo'ladi — boshqa prefiksdagi kodlar (masalan INCIDENT
     * uchun "INC-") generatordan kelmaydi, shuning uchun hisobga olinmaydi.</p>
     *
     * <p>Ikki yo'ldan chaqiriladi: fresh-DB ({@link #seedAdminAndDemoTenant},
     * {@link #seedWorkItems}'dan keyin) va existing-DB repair
     * ({@link #repairDemoWorkItemCounters}).</p>
     */
    private void seedOrAdvanceWorkItemCounter(UUID tenantId, WorkItemType type,
                                              List<DemoWorkItemSpec> specs) {
        String prefix = type.name() + "-";
        long minimumNextValue = specs.stream()
                .filter(s -> s.type() == type && s.code().startsWith(prefix))
                .mapToLong(s -> Long.parseLong(s.code().substring(prefix.length())))
                .max()
                .orElse(0L) + 1L;

        WorkItemCounter counter = workItemCounterRepository
                .findByTenantIdAndTypeCode(tenantId, type)
                .orElseGet(() -> workItemCounterRepository.save(
                        new WorkItemCounter(tenantId, type)));
        counter.advanceTo(minimumNextValue);
        workItemCounterRepository.save(counter);

        log.info("Dev bootstrap: '{}' counter nextValue >= {} (tenant={})",
                type, minimumNextValue, tenantId);
    }

    /**
     * Phase 222 — mavjud-DB repair: oldingi bootstrap runlari
     * {@link WorkItemCounter}'larni umuman yaratmasdan demo work item'larni
     * oldindan band qilingan kodlar bilan kiritgan. Bu yerda counter'larni
     * yaratamiz/oshiramiz (idempotent).
     *
     * <p>Demo tenant yo'q bo'lsa darhol qaytadi.</p>
     */
    private void repairDemoWorkItemCounters() {
        if (tenantRepository.findById(BOOTSTRAP_TENANT_ID).isEmpty()) {
            return;
        }
        List<DemoWorkItemSpec> specs = demoSpecs();
        for (WorkItemType type : List.of(WorkItemType.BUG, WorkItemType.INCIDENT, WorkItemType.TASK)) {
            seedOrAdvanceWorkItemCounter(BOOTSTRAP_TENANT_ID, type, specs);
        }
        log.info("Dev bootstrap: repairDemoWorkItemCounters tugadi");
    }

    /**
     * Demo workflow status spetsifikatsiyalari (type bo'yicha). Status nomlari
     * {@link #demoSpecs()} ishlatadigan {@code current_status_code}'lar bilan
     * mos — shu sababli mavjud 10 demo work item orphan-status bo'lib qolmaydi.
     */
    private static Map<WorkItemType, List<DemoStatusSpec>> demoStatusSpecs() {
        return Map.of(
                WorkItemType.BUG, List.of(
                        new DemoStatusSpec("REPORTED", 0, true, false),
                        new DemoStatusSpec("IN_PROGRESS", 1, false, false),
                        new DemoStatusSpec("RESOLVED", 2, false, false),
                        new DemoStatusSpec("CLOSED", 3, false, true)),
                WorkItemType.INCIDENT, List.of(
                        new DemoStatusSpec("REPORTED", 0, true, false),
                        new DemoStatusSpec("IN_PROGRESS", 1, false, false),
                        new DemoStatusSpec("RESOLVED", 2, false, true)),
                WorkItemType.TASK, List.of(
                        new DemoStatusSpec("REPORTED", 0, true, false),
                        new DemoStatusSpec("IN_PROGRESS", 1, false, false),
                        new DemoStatusSpec("DONE", 2, false, true)));
    }

    private static List<DemoWorkItemSpec> demoSpecs() {
        return List.of(
                new DemoWorkItemSpec("BUG-1", WorkItemType.BUG, "CRITICAL", "IN_PROGRESS",
                        "Login page returns 500 for OAuth users"),
                new DemoWorkItemSpec("INC-2", WorkItemType.INCIDENT, "HIGH", "IN_PROGRESS",
                        "Production database connection pool exhausted"),
                new DemoWorkItemSpec("BUG-3", WorkItemType.BUG, "HIGH", "REPORTED",
                        "CSV export drops UTF-8 characters in titles"),
                new DemoWorkItemSpec("TASK-4", WorkItemType.TASK, "MEDIUM", "IN_PROGRESS",
                        "Migrate workflow templates to V7 schema"),
                new DemoWorkItemSpec("INC-5", WorkItemType.INCIDENT, "HIGH", "RESOLVED",
                        "Webhook delivery delayed by >30s for 2 hours"),
                new DemoWorkItemSpec("BUG-6", WorkItemType.BUG, "MEDIUM", "REPORTED",
                        "Severity dropdown allows duplicate codes per tenant"),
                new DemoWorkItemSpec("TASK-7", WorkItemType.TASK, "MEDIUM", "IN_PROGRESS",
                        "Backfill ownerDisplayName for legacy work items"),
                new DemoWorkItemSpec("INC-8", WorkItemType.INCIDENT, "MEDIUM", "RESOLVED",
                        "Metric tag cardinality spike from tenant-slug logging"),
                new DemoWorkItemSpec("BUG-9", WorkItemType.BUG, "LOW", "CLOSED",
                        "HTMX fragment leaves <html> wrapper on swap"),
                new DemoWorkItemSpec("TASK-10", WorkItemType.TASK, "LOW", "REPORTED",
                        "Document analytics-runbook §7 SQL equivalents"));
    }

    private record DemoWorkItemSpec(String code, WorkItemType type,
                                    String severity, String status, String title) {
    }

    /** Phase 221 — demo workflow status seed spetsifikatsiyasi. */
    private record DemoStatusSpec(String name, int order, boolean initial, boolean terminal) {
    }
}
