package com.engops.platform.dev;

import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipRoleBinding;
import com.engops.platform.identity.model.Role;
import com.engops.platform.identity.repository.AppUserRepository;
import com.engops.platform.identity.repository.MembershipRepository;
import com.engops.platform.identity.repository.MembershipRoleBindingRepository;
import com.engops.platform.identity.repository.RoleRepository;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.repository.TenantRepository;
import com.engops.platform.tenantconfig.repository.WorkflowDefinitionRepository;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import com.engops.platform.workitem.repository.WorkItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phase 211 — DEV PROFILE bootstrap seed.
 *
 * <p>Faqat {@code app.security.dev-mode.enabled=true} bo'lganda bean
 * yaratiladi. Production'da property o'rnatilmagan — bean yo'q, seed
 * bajarilmaydi.</p>
 *
 * <p><strong>Idempotent:</strong> Bootstrap admin UUID
 * ({@link #BOOTSTRAP_ADMIN_USER_ID}) bo'yicha tekshiriladi. AppUser allaqachon
 * mavjud bo'lsa, seed butunlay o'tkazib yuboriladi (log INFO + return).
 * Birinchi marta yoki database drop'dan keyin clean ravishda re-seed.</p>
 *
 * <p><strong>Seed mazmuni:</strong></p>
 * <ul>
 *   <li>1 ta AppUser (fixed UUID, telegram_user_id = 100000001)</li>
 *   <li>1 ta Tenant (fixed UUID, slug = "demo")</li>
 *   <li>1 ta ACTIVE Membership</li>
 *   <li>1 ta ADMIN role binding (V2'da seeded ADMIN role)</li>
 *   <li>3 ta WorkflowDefinition (BUG / INCIDENT / TASK uchun bittadan)</li>
 *   <li>10 ta WorkItem — D5 spec bo'yicha (4 BUG / 3 INCIDENT / 3 TASK)</li>
 * </ul>
 *
 * <p><strong>Audit bypass (D15):</strong> Bootstrap operatsiyalari audit
 * event yozmaydi. Sabab — bootstrap actor-driven emas, infrastructure setup.
 * {@code audit_event.actor_user_id} NULL accepted, lekin operatsion semantik
 * nuqtai nazaridan "system seed" audit trail'iga aralashtirmaslik to'g'ri.</p>
 *
 * <p><strong>Workflow templates bypass:</strong> TenantOnboardingService
 * Phase 198 WorkflowTemplateCatalog'idan to'liq workflow + statuses +
 * transitions seed qiladi. Demo uchun minimal WorkflowDefinition yetarli —
 * WorkItem.current_status_code plain VARCHAR, workflow_status'ga FK yo'q.
 * Status transition demo'da kerak emas (read-only dashboard).</p>
 *
 * <h3>Demo'ni ishga tushirish</h3>
 * <pre>
 *   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
 * </pre>
 * yoki:
 * <pre>
 *   SPRING_PROFILES_ACTIVE=dev java -jar target/*.jar
 * </pre>
 * Keyin browser'da:
 * <ol>
 *   <li>{@code http://localhost:8080/web/login} — "Get dev token" tugmasi
 *       avtomatik ko'rinadi (JS dev mode'ni detect qiladi)</li>
 *   <li>Tugmani bosing — JWT olinadi, localStorage'ga saqlanadi, tenant ham
 *       saqlanadi va /web/dashboard'ga redirect</li>
 *   <li>Dashboard real chart bilan render — 10 work item bo'yicha analytics</li>
 *   <li>"Work items" sahifasiga o'ting — 10-row jadval severity tag'lar bilan</li>
 * </ol>
 *
 * <h3>Production posture</h3>
 * <p>Property o'rnatilmagan → bean yo'q → seed bajarilmaydi → ZERO production
 * o'zgarishi. SecurityConfig'dagi {@code /api/dev/**} permitAll matcher
 * faqat dev mode'da yaratilgan controller orqali endpoint surface beradi —
 * controller yo'q bo'lsa, matcher hech narsani himoyalamaydi (Spring MVC 404).</p>
 */
@Component
@ConditionalOnProperty(name = "app.security.dev-mode.enabled", havingValue = "true")
public class DevBootstrapInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevBootstrapInitializer.class);

    // ========== Stable UUID sentinels (bookmarkable URLs) ==========
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
    private final WorkItemRepository workItemRepository;

    public DevBootstrapInitializer(AppUserRepository appUserRepository,
                                    TenantRepository tenantRepository,
                                    MembershipRepository membershipRepository,
                                    MembershipRoleBindingRepository membershipRoleBindingRepository,
                                    RoleRepository roleRepository,
                                    WorkflowDefinitionRepository workflowDefinitionRepository,
                                    WorkItemRepository workItemRepository) {
        this.appUserRepository = appUserRepository;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.membershipRoleBindingRepository = membershipRoleBindingRepository;
        this.roleRepository = roleRepository;
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.workItemRepository = workItemRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (appUserRepository.findById(BOOTSTRAP_ADMIN_USER_ID).isPresent()) {
            log.info("Dev bootstrap: admin user mavjud, seed o'tkazib yuborildi ({})",
                    BOOTSTRAP_ADMIN_USER_ID);
            return;
        }

        // ADMIN role'ni avval tekshiramiz. Production'da V2 migration uni
        // seed qiladi va bu lookup hech qachon bo'sh qaytmaydi. Test
        // profile'da H2 + Flyway off — ADMIN row yo'q. Graceful skip:
        // bootstrap startup'ni fail qilmaydi, ApplicationContext yuklanadi,
        // testlar ADMIN role'ni qo'lda seed qilib initializer.run(null) ni
        // qayta chaqirishi mumkin.
        Role adminRole = roleRepository.findByCode(ADMIN_ROLE_CODE).orElse(null);
        if (adminRole == null) {
            log.warn("Dev bootstrap: ADMIN role global katalog'da topilmadi "
                    + "(Flyway disabled?). Seed o'tkazib yuborildi.");
            return;
        }

        AppUser admin = seedAdminUser();
        Tenant tenant = seedTenant();
        Membership membership = seedMembership(tenant, admin);
        seedRoleBinding(membership, adminRole);
        WorkflowDefinition wfBug = seedWorkflowDefinition(tenant,
                BOOTSTRAP_WORKFLOW_BUG_ID, "Demo Bug Workflow", WorkItemType.BUG);
        WorkflowDefinition wfIncident = seedWorkflowDefinition(tenant,
                BOOTSTRAP_WORKFLOW_INCIDENT_ID, "Demo Incident Workflow", WorkItemType.INCIDENT);
        WorkflowDefinition wfTask = seedWorkflowDefinition(tenant,
                BOOTSTRAP_WORKFLOW_TASK_ID, "Demo Task Workflow", WorkItemType.TASK);
        seedWorkItems(tenant, admin, wfBug, wfIncident, wfTask);

        log.info("Dev bootstrap to'liq: admin={}, tenant={}, work items={}",
                admin.getId(), tenant.getId(), DEMO_WORK_ITEM_COUNT);
    }

    // ========== Seed steps ==========

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
        // Fixed UUID — workflow_definition_id WorkItem FK
        // uchun barqaror referans. Constructor BaseEntity(UUID) bilan
        // o'rnatish uchun reflection emas, manual id set kerak — buning
        // o'rniga JPA save'dan keyin id pre-set qilamiz emas — Tenant +
        // AppUser kabi yondashuv yo'q (WorkflowDefinition uchun (UUID, ...)
        // ctor yo'q). Shuning uchun id'ni @PrePersist'da o'rnatib bo'lmaydi,
        // entityni save qilib id ni qaytarib olib WorkItem.workflowDefinitionId
        // sifatida ishlatamiz.
        WorkflowDefinition wf = new WorkflowDefinition(tenant.getId(), name, type.name());
        wf.setDescription("Phase 211 dev bootstrap demo workflow");
        WorkflowDefinition saved = workflowDefinitionRepository.save(wf);
        // Fixed id constant'lar test stability uchun — ishlatilmaydi bu yerda,
        // chunki yangi UUID auto-generated. Test fix qilingan UUID'ni emas,
        // tenant + name lookup bo'yicha tekshiradi.
        return saved;
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
}
