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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;
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
 */
@Configuration
@EnableConfigurationProperties(BootstrapProperties.class)
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    /** V2 seed'idan ADMIN role code. {@link IdentityQueryService#findRoleByCode(String)} bilan ishlatiladi. */
    static final String ADMIN_ROLE_CODE = "ADMIN";

    static final String AUDIT_AGGREGATE_TYPE = "TENANT";
    static final String AUDIT_ACTION = "BOOTSTRAP_COMPLETED";
    static final String AUDIT_ACTION_SOURCE = "BOOTSTRAP";

    private final BootstrapProperties properties;
    private final TenantConfigQueryService tenantConfigQueryService;
    private final TenantConfigCommandService tenantConfigCommandService;
    private final IdentityQueryService identityQueryService;
    private final IdentityCommandService identityCommandService;
    private final AuditService auditService;

    public BootstrapAdminInitializer(BootstrapProperties properties,
                                      TenantConfigQueryService tenantConfigQueryService,
                                      TenantConfigCommandService tenantConfigCommandService,
                                      IdentityQueryService identityQueryService,
                                      IdentityCommandService identityCommandService,
                                      AuditService auditService) {
        this.properties = properties;
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
