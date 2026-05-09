package com.engops.platform.admin.bootstrap;

import com.engops.platform.audit.AuditService;
import com.engops.platform.identity.IdentityCommandService;
import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipRoleBinding;
import com.engops.platform.identity.model.Role;
import com.engops.platform.tenantconfig.TenantConfigCommandService;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.model.WorkflowStatus;
import com.engops.platform.tenantconfig.model.WorkflowTransitionRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 143 — birinchi admin uchun property-driven first-run bootstrap.
 *
 * <p>Mavjud command/query service public API'larini ishlatib (admin facade'lar
 * EMAS, chunki ular {@code TENANT_CONFIG_WRITE} talab qiladi va bo'sh DB'da
 * deadlock yaratadi) idempotent ravishda quyidagilarni yaratadi:</p>
 * <ol>
 *   <li>Tenant — {@link BootstrapProperties#getTenantSlug() slug} bo'yicha
 *       lookup; mavjud bo'lsa qayta ishlatiladi</li>
 *   <li>AppUser — {@link BootstrapProperties#getAppUserId() appUserId} bo'yicha
 *       lookup; mavjud bo'lsa qayta ishlatiladi (UUID JWT {@code sub} bilan
 *       moslashishi shart)</li>
 *   <li>ACTIVE Membership — {@code (tenantId, userId)} bo'yicha lookup;
 *       mavjud bo'lsa qayta ishlatiladi</li>
 *   <li>Membership-Role binding — V2 seed'idan ADMIN role
 *       (code = {@value #ADMIN_ROLE_CODE}); allaqachon biriktirilgan bo'lsa
 *       qayta yaratilmaydi</li>
 * </ol>
 *
 * <p>Default {@code app.bootstrap.admin.enabled = false} — bootstrap atayin
 * yoqilishi shart. Yoqilganda barcha required field'lar mavjud bo'lishi shart;
 * yo'q bo'lsa fail-fast {@link IllegalStateException} application startup'ni
 * to'xtatadi.</p>
 *
 * <p>Idempotensiya: ikkinchi yoki keyingi boot'larda bootstrap allaqachon
 * mavjud rowlarni qayta ishlatadi va hech qanday {@code DUPLICATE_*} exception
 * tashlamaydi. Hech qanday yangi tenant/app_user/membership/role-binding row
 * yaratilmaydi.</p>
 *
 * <p>Audit: har bir muvaffaqiyatli enabled bootstrap run'i bitta
 * {@code BOOTSTRAP_COMPLETED} audit event'ini chiqaradi
 * ({@code aggregateType=TENANT}, {@code aggregateId=tenantId},
 * {@code actorUserId=appUserId}, {@code actionSource=BOOTSTRAP}). Bu idempotent
 * re-run'lar uchun ham amal qiladi — operator har bir bootstrap urinishi audit
 * trail'da ko'rinishi uchun (real production'da kim qachon bootstrap config'ni
 * faollashtirgani aniq bo'lishi muhim). Yangi rowlar yaratilmagan bo'lsa ham
 * BOOTSTRAP_COMPLETED yoziladi; faqat boshlang'ich create'larning
 * {@code TENANT/CREATED}, {@code APP_USER/CREATED}, va boshqa per-row event'lari
 * (mavjud command service'lar tomonidan emit qilinadi) takrorlanmaydi.</p>
 *
 * <p>Transaction: butun bootstrap bitta tranzaksiyada — qisman fail butun
 * operatsiyani rollback qiladi.</p>
 *
 * <p><strong>Architecture:</strong> bootstrap mavjud command service'lar
 * (Tenant{@link TenantConfigCommandService}, {@link IdentityCommandService})
 * o'zlari authorization tekshirmasligi faktidan foydalanadi — admin facade'lar
 * faqat HTTP boundary'da {@code authorizeWrite} chaqiradi. Repository
 * to'g'ridan-to'g'ri ishlatilmaydi (modular monolith boundary'lari saqlanadi).</p>
 *
 * <p><strong>Phase 156 — default MVP Bug workflow seed:</strong> Admin
 * bootstrap muvaffaqiyatli o'tgandan keyin (tenant + admin user + membership
 * + ADMIN role binding ta'minlangan), {@link BootstrapWorkflowProperties#isEnabled()}
 * yoqilgan bo'lsa, aktiv tenant uchun MVP Bug Flow workflow definition'i
 * (4 status: BUGS / PROCESSING / TESTING / FIXED; 5 transition rule:
 * BUGS→PROCESSING, PROCESSING→TESTING, TESTING→FIXED, TESTING→BUGS,
 * FIXED→BUGS) idempotent yaratiladi. Mavjud workflow ({@code work_item_type=BUG})
 * qayta ishlatiladi va faqat yetishmagan status'lar / rule'lar to'ldiriladi.
 * Per-row create event'lar mavjud {@link TenantConfigCommandService} command
 * service'lari tomonidan {@code ADMIN_API} action source bilan emit qilinadi
 * (Phase 143 admin pattern bilan bir xil — bootstrap o'zining
 * {@code BOOTSTRAP_COMPLETED} event'i orqali tashqi shaklni saqlaydi).
 * Workflow seed disabled bo'lsa Phase 143 xulqi o'zgarmaydi (no-op).</p>
 */
@Configuration
@EnableConfigurationProperties({BootstrapProperties.class, BootstrapWorkflowProperties.class})
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    /** V2 seed'idan ADMIN role code. {@link IdentityQueryService#findRoleByCode(String)} bilan ishlatiladi. */
    static final String ADMIN_ROLE_CODE = "ADMIN";

    static final String AUDIT_AGGREGATE_TYPE = "TENANT";
    static final String AUDIT_ACTION = "BOOTSTRAP_COMPLETED";
    static final String AUDIT_ACTION_SOURCE = "BOOTSTRAP";

    /** Phase 156 — MVP Bug Flow workflow uchun work-item turi (CLAUDE.md va WorkItemType.BUG). */
    static final String BUG_WORK_ITEM_TYPE = "BUG";

    /** Phase 156 — MVP Bug Flow status nomlari va order. */
    static final String STATUS_BUGS = "BUGS";
    static final String STATUS_PROCESSING = "PROCESSING";
    static final String STATUS_TESTING = "TESTING";
    static final String STATUS_FIXED = "FIXED";

    private final BootstrapProperties properties;
    private final BootstrapWorkflowProperties workflowProperties;
    private final TenantConfigQueryService tenantConfigQueryService;
    private final TenantConfigCommandService tenantConfigCommandService;
    private final IdentityQueryService identityQueryService;
    private final IdentityCommandService identityCommandService;
    private final AuditService auditService;

    public BootstrapAdminInitializer(BootstrapProperties properties,
                                      BootstrapWorkflowProperties workflowProperties,
                                      TenantConfigQueryService tenantConfigQueryService,
                                      TenantConfigCommandService tenantConfigCommandService,
                                      IdentityQueryService identityQueryService,
                                      IdentityCommandService identityCommandService,
                                      AuditService auditService) {
        this.properties = properties;
        this.workflowProperties = workflowProperties;
        this.tenantConfigQueryService = tenantConfigQueryService;
        this.tenantConfigCommandService = tenantConfigCommandService;
        this.identityQueryService = identityQueryService;
        this.identityCommandService = identityCommandService;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.debug("Bootstrap admin disabled (app.bootstrap.admin.enabled=false) — skip");
            return;
        }

        validateRequiredProperties();
        // Phase 156 mini-fix: workflow seed yoqilgan bo'lsa, name fail-fast
        // tekshiruv — admin bootstrap mutatsiyalaridan oldin (rollback'siz
        // toza fail-fast). Disabled bo'lsa skipped.
        validateWorkflowSeedProperties();

        log.info("Bootstrap admin initialization started: tenantSlug={}, appUserId={}",
                properties.getTenantSlug(), properties.getAppUserId());

        // 1. Tenant: lookup by slug, create if absent.
        Tenant tenant = tenantConfigQueryService.findTenantBySlug(properties.getTenantSlug())
                .orElseGet(() -> tenantConfigCommandService.createTenant(
                        properties.getTenantName(),
                        properties.getTenantSlug(),
                        properties.getTenantTimezone()));

        // 2. AppUser: lookup by deterministic UUID, create if absent.
        AppUser user = identityQueryService.findUserById(properties.getAppUserId())
                .orElseGet(() -> identityCommandService.createAppUserWithId(
                        properties.getAppUserId(),
                        properties.getTelegramUserId(),
                        properties.getUsername(),
                        properties.getDisplayName()));

        // 3. Membership: lookup by (tenantId, userId), create+activate if absent.
        Membership membership = identityQueryService.findMembership(
                        tenant.getId(), user.getId())
                .orElseGet(() -> identityCommandService.createMembership(
                        tenant.getId(), user.getId()));
        // createMembership default = ACTIVE; mavjud membership SUSPENDED/REMOVED
        // bo'lsa bootstrap uni ataylab tegmaydi (operator alohida hal qiladi).

        // 4. ADMIN role binding: lookup, assign if absent.
        Role adminRole = identityQueryService.findRoleByCode(ADMIN_ROLE_CODE)
                .orElseThrow(() -> new IllegalStateException(
                        "Bootstrap fail: ADMIN role not found in role catalog "
                                + "(V2 seed buzilgan?)"));

        boolean alreadyHasAdmin = identityQueryService.getMembershipRoles(membership.getId())
                .stream()
                .map(MembershipRoleBinding::getRole)
                .anyMatch(r -> ADMIN_ROLE_CODE.equals(r.getCode()));

        if (!alreadyHasAdmin) {
            identityCommandService.assignRoleToMembership(
                    tenant.getId(), membership.getId(), adminRole.getId());
        }

        // Phase 156: optional default MVP Bug workflow seed (idempotent).
        // Same transaction — agar workflow seed exception tashlasa, butun
        // bootstrap rollback qilinadi (atomic admin + workflow shape).
        if (workflowProperties.isEnabled()) {
            seedDefaultBugWorkflow(tenant.getId());
        }

        // Audit: bitta BOOTSTRAP_COMPLETED event muvaffaqiyatli bootstrap'ni frame qiladi.
        // (Mavjud command service'lar har biri o'zining audit event'ini ham yozadi —
        // bu qo'shimcha event audit trail'da bootstrap operatsiyasini aniq belgilaydi.)
        auditService.recordEvent(
                tenant.getId(),
                AUDIT_AGGREGATE_TYPE,
                tenant.getId(),
                AUDIT_ACTION,
                properties.getAppUserId(),
                AUDIT_ACTION_SOURCE,
                null,
                properties.getTenantSlug());

        log.info("Bootstrap admin initialization completed: tenantId={}, appUserId={}, slug={}",
                tenant.getId(), properties.getAppUserId(), properties.getTenantSlug());
    }

    /**
     * Phase 156 — aktiv tenant uchun MVP Bug Flow workflow seed (idempotent).
     *
     * <p>Quyidagilarni ta'minlaydi:</p>
     * <ol>
     *   <li>{@code work_item_type = "BUG"} uchun workflow definition mavjud:
     *       yo'q bo'lsa {@link BootstrapWorkflowProperties#getName()} nomi
     *       bilan yaratiladi va aktiv qoldiriladi (default
     *       {@link WorkflowDefinition#isActive()} = true).</li>
     *   <li>4 ta status nomi mavjud: BUGS (initial, order=0), PROCESSING (order=1),
     *       TESTING (order=2), FIXED (terminal, order=3). Mavjud bo'lganlari
     *       qayta ishlatiladi (definition.statuses snapshot orqali).</li>
     *   <li>5 ta transition rule mavjud: BUGS→PROCESSING; PROCESSING→TESTING;
     *       TESTING→FIXED; TESTING→BUGS (return); FIXED→BUGS (reopen).
     *       CLAUDE.md "FIXED can be reopened" — eng aniq mantiqiy reopen target
     *       BUGS (triage). FIXED→PROCESSING ataylab seed qilinmaydi (operator
     *       agar talab qilsa keyin admin API orqali qo'shadi).</li>
     * </ol>
     *
     * <p>Idempotensiya: definition snapshot'idagi statuslar va transitionlar
     * map/set'larga o'qiladi; seed faqat yetishmaganlarini yaratadi. Repeat
     * runlar duplicate row yaratmaydi (DB UNIQUE constraint ham himoya beradi).</p>
     *
     * <p>Per-row audit event'lar mavjud {@link TenantConfigCommandService}
     * tomonidan {@code ADMIN_API} action source bilan yoziladi —
     * {@link BootstrapAdminInitializer} pattern'i bilan bir xil. Bootstrap
     * o'zining {@code BOOTSTRAP_COMPLETED} event'i operatsiyaning tashqi
     * shaklini saqlaydi.</p>
     */
    private void seedDefaultBugWorkflow(UUID tenantId) {
        log.info("Bootstrap workflow seed started: tenantId={}, workflowName={}",
                tenantId, workflowProperties.getName());

        // 1. Workflow definition: BUG turi bo'yicha mavjudini qayta ishlatadi
        //    yoki yangi yaratadi.
        WorkflowDefinition workflow = tenantConfigQueryService
                .findWorkflowDefinition(tenantId, BUG_WORK_ITEM_TYPE)
                .orElseGet(() -> tenantConfigCommandService.createWorkflowDefinition(
                        tenantId, workflowProperties.getName(), BUG_WORK_ITEM_TYPE, null));

        // 2. Statuslar — definition.getStatuses() snapshot'idan local map qurish
        //    va yetishmagan nomlarni yaratib map'ga qo'shish.
        Map<String, WorkflowStatus> statusByName = new HashMap<>();
        for (WorkflowStatus existing : workflow.getStatuses()) {
            statusByName.put(existing.getName(), existing);
        }
        ensureStatus(tenantId, workflow.getId(), statusByName, STATUS_BUGS, 0, true, false);
        ensureStatus(tenantId, workflow.getId(), statusByName, STATUS_PROCESSING, 1, false, false);
        ensureStatus(tenantId, workflow.getId(), statusByName, STATUS_TESTING, 2, false, false);
        ensureStatus(tenantId, workflow.getId(), statusByName, STATUS_FIXED, 3, false, true);

        // 3. Transitionlar — definition.getTransitionRules() snapshot'idan
        //    "from->to" key'lardan iborat set qurish va yetishmaganlarini
        //    yaratish. Bizning seed listidagi har bir pair noyob bo'lganligi
        //    sababli, snapshot'da yo'q bo'lganlar yangi yaratiladi.
        Set<String> existingTransitions = new HashSet<>();
        for (WorkflowTransitionRule rule : workflow.getTransitionRules()) {
            existingTransitions.add(transitionKey(rule.getFromStatus().getName(),
                    rule.getToStatus().getName()));
        }
        ensureTransition(tenantId, workflow.getId(), statusByName, existingTransitions,
                STATUS_BUGS, STATUS_PROCESSING);
        ensureTransition(tenantId, workflow.getId(), statusByName, existingTransitions,
                STATUS_PROCESSING, STATUS_TESTING);
        ensureTransition(tenantId, workflow.getId(), statusByName, existingTransitions,
                STATUS_TESTING, STATUS_FIXED);
        ensureTransition(tenantId, workflow.getId(), statusByName, existingTransitions,
                STATUS_TESTING, STATUS_BUGS);
        ensureTransition(tenantId, workflow.getId(), statusByName, existingTransitions,
                STATUS_FIXED, STATUS_BUGS);

        log.info("Bootstrap workflow seed completed: tenantId={}, workflowId={}",
                tenantId, workflow.getId());
    }

    private void ensureStatus(UUID tenantId, UUID workflowDefinitionId,
                               Map<String, WorkflowStatus> statusByName,
                               String name, int order, boolean initial, boolean terminal) {
        if (statusByName.containsKey(name)) {
            return;
        }
        WorkflowStatus created = tenantConfigCommandService.createWorkflowStatus(
                tenantId, workflowDefinitionId, name, order, initial, terminal);
        statusByName.put(name, created);
    }

    private void ensureTransition(UUID tenantId, UUID workflowDefinitionId,
                                   Map<String, WorkflowStatus> statusByName,
                                   Set<String> existingTransitions,
                                   String fromName, String toName) {
        if (existingTransitions.contains(transitionKey(fromName, toName))) {
            return;
        }
        WorkflowStatus from = statusByName.get(fromName);
        WorkflowStatus to = statusByName.get(toName);
        if (from == null || to == null) {
            // Defensive: ensureStatus oldidan chaqirilgan, statuslar to'liq
            // bo'lishi shart. Agar bu yo'l ishga tushsa — seed kontrakti buzilgan.
            throw new IllegalStateException(
                    "Bootstrap workflow seed: status '" + fromName + "' yoki '" + toName
                            + "' map'da topilmadi (ensureStatus oldindan chaqirilmagan?)");
        }
        tenantConfigCommandService.createWorkflowTransitionRule(
                tenantId, workflowDefinitionId, from.getId(), to.getId());
    }

    private static String transitionKey(String fromName, String toName) {
        return fromName + "->" + toName;
    }

    private void validateRequiredProperties() {
        requireText("app.bootstrap.admin.tenant-name", properties.getTenantName());
        requireText("app.bootstrap.admin.tenant-slug", properties.getTenantSlug());
        requireText("app.bootstrap.admin.display-name", properties.getDisplayName());
        if (properties.getAppUserId() == null) {
            throw new IllegalStateException(
                    "Bootstrap admin enabled but missing required property: "
                            + "app.bootstrap.admin.app-user-id (UUID matching JWT 'sub' claim)");
        }
        if (properties.getTelegramUserId() == null) {
            throw new IllegalStateException(
                    "Bootstrap admin enabled but missing required property: "
                            + "app.bootstrap.admin.telegram-user-id");
        }
    }

    /**
     * Phase 156 mini-fix — workflow seed yoqilgan bo'lsa, kerakli property'lar
     * bo'sh emasligini fail-fast tekshiradi. Disabled bo'lsa hech narsa qilmaydi
     * (Phase 156 default xulqi: name = "MVP Bug Flow" bo'lganda validatsiya
     * o'tadi va default qiymat saqlanadi).
     *
     * <p>Tekshiruv {@link #validateRequiredProperties()} dan keyin, lekin
     * birinchi tenant/user lookup'idan oldin bajariladi — invalid konfig
     * sharoitida hech qanday DB mutatsiyasi bo'lmaydi.</p>
     */
    private void validateWorkflowSeedProperties() {
        if (!workflowProperties.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(workflowProperties.getName())) {
            throw new IllegalStateException(
                    "Bootstrap workflow enabled but missing required property: "
                            + "app.bootstrap.workflow.name");
        }
    }

    private static void requireText(String propertyName, String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    "Bootstrap admin enabled but missing required property: " + propertyName);
        }
    }

    // Test access — paket-private getter'lar mavjud emas, lekin test'lar
    // konstruktor orqali to'g'ridan-to'g'ri inject qiladi va run()ni chaqiradi.
    @SuppressWarnings("unused")
    Optional<UUID> visibleAppUserIdForTest() {
        return Optional.ofNullable(properties.getAppUserId());
    }
}
