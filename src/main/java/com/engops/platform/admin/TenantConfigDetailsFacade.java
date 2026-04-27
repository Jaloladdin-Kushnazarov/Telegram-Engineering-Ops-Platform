package com.engops.platform.admin;

import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipRoleBinding;
import com.engops.platform.identity.model.Permission;
import com.engops.platform.identity.model.Role;
import com.engops.platform.identity.model.RolePermission;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.model.RoutingRule;
import com.engops.platform.tenantconfig.model.TelegramChatBinding;
import com.engops.platform.tenantconfig.model.TelegramTopicBinding;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.model.WorkflowStatus;
import com.engops.platform.tenantconfig.model.WorkflowTransitionRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Tenant konfiguratsiyasi uchun compact details read facade.
 *
 * Admin/support caller'lar uchun tenant'ning operatsion konfiguratsiya holatini
 * bitta chaqiruvda qaytaradi:
 * - tenant metadata
 * - membership summary (total + active count)
 * - workflow summary (total + active count)
 * - routing summary (total + active count)
 * - telegram summary (active chat binding + topic binding count)
 *
 * Delegation:
 * (tenantId)
 *   -> TenantConfigQueryService (tenant, workflow, routing, telegram)
 *   -> IdentityQueryService (membership)
 *   -> TenantConfigDetailsView
 *
 * Muhim:
 * - Faqat mavjud public query service'lar orqali ishlaydi
 * - Repository bypass yo'q
 * - Biznes logika yo'q — faqat thin orchestration va count aggregation
 * - Tenant topilmasa ResourceNotFoundException (404)
 * - Tenant-scoped
 * - Read-only tranzaksiya
 * - Stateless
 */
@Service
@Transactional(readOnly = true)
public class TenantConfigDetailsFacade {

    private final TenantConfigQueryService tenantConfigQueryService;
    private final IdentityQueryService identityQueryService;
    private final AdminAuthorizationService authorizationService;

    public TenantConfigDetailsFacade(TenantConfigQueryService tenantConfigQueryService,
                                      IdentityQueryService identityQueryService,
                                      AdminAuthorizationService authorizationService) {
        this.tenantConfigQueryService = tenantConfigQueryService;
        this.identityQueryService = identityQueryService;
        this.authorizationService = authorizationService;
    }

    /**
     * Tenant konfiguratsiyasining compact details view'ini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @return compact tenant config details
     * @throws IllegalArgumentException agar tenantId null bo'lsa
     * @throws ResourceNotFoundException agar tenant topilmasa
     */
    public TenantConfigDetailsView getDetails(UUID tenantId, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeRead(tenantId, actorUserId);

        Tenant tenant = tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        int totalMembershipCount = identityQueryService.listAllMembers(tenantId).size();
        int activeMembershipCount = identityQueryService.listActiveMembers(tenantId).size();

        int totalWorkflowDefinitionCount = tenantConfigQueryService
                .listAllWorkflowDefinitions(tenantId).size();
        int activeWorkflowDefinitionCount = tenantConfigQueryService
                .listActiveWorkflowDefinitions(tenantId).size();

        int totalRoutingRuleCount = tenantConfigQueryService
                .listAllRoutingRules(tenantId).size();
        int activeRoutingRuleCount = tenantConfigQueryService
                .listActiveRoutingRules(tenantId).size();

        List<TelegramChatBinding> activeChatBindings = tenantConfigQueryService
                .listActiveChatBindings(tenantId);
        int activeChatBindingCount = activeChatBindings.size();

        int activeTopicBindingCount = activeChatBindings.stream()
                .mapToInt(cb -> tenantConfigQueryService
                        .listActiveTopicBindings(cb.getId()).size())
                .sum();

        return new TenantConfigDetailsView(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getTimezone(),
                tenant.getStatus().name(),
                tenant.getCreatedAt(),
                totalMembershipCount,
                activeMembershipCount,
                totalWorkflowDefinitionCount,
                activeWorkflowDefinitionCount,
                totalRoutingRuleCount,
                activeRoutingRuleCount,
                activeChatBindingCount,
                activeTopicBindingCount);
    }

    /**
     * Global rol katalogini compact ro'yxat sifatida qaytaradi.
     *
     * Rollar GLOBAL — tenantga tegishli emas. tenantId endpoint-family
     * izchilligi va admin kontekst validatsiyasi uchun tekshiriladi.
     *
     * Ordering: code ASC -> id ASC
     *
     * @param tenantId admin kontekst tenant identifikatori
     * @return global rol katalogi ro'yxati
     * @throws IllegalArgumentException agar tenantId null bo'lsa
     * @throws ResourceNotFoundException agar tenant topilmasa
     */
    public RoleListView getRoles(UUID tenantId, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeRead(tenantId, actorUserId);

        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        List<Role> roles = identityQueryService.listAllRoles();

        List<RoleItemView> items = roles.stream()
                .sorted(Comparator.comparing(Role::getCode)
                        .thenComparing(Role::getId))
                .map(r -> new RoleItemView(
                        r.getId(),
                        r.getCode(),
                        r.getName(),
                        r.getDescription(),
                        r.isSystemRole(),
                        r.getCreatedAt()))
                .toList();

        return new RoleListView(tenantId, items);
    }

    /**
     * Facade natija modeli — global rol katalogi ro'yxati.
     */
    public record RoleListView(
            UUID tenantId,
            List<RoleItemView> items) {}

    public record RoleItemView(
            UUID roleId,
            String code,
            String name,
            String description,
            boolean systemRole,
            Instant createdAt) {}

    /**
     * Berilgan global rol uchun biriktirilgan ruxsat (permission)
     * binding'larini katalog ko'rinishida qaytaradi.
     *
     * Rol va ruxsat ikkalasi ham GLOBAL — tenantga tegishli emas. tenantId
     * endpoint-family izchilligi va admin kontekst validatsiyasi uchun tekshiriladi.
     *
     * Validation-before-authorization ordering:
     * 1. tenantId null bo'lmasligi kerak
     * 2. roleId null bo'lmasligi kerak
     * 3. authorizeRead chaqiriladi
     * 4. tenant mavjudligi tekshiriladi
     * 5. role mavjudligi tekshiriladi (ID bo'yicha global katalogdan)
     * 6. role uchun permission binding'lar yig'iladi
     *
     * Ordering: code ASC -> id ASC
     *
     * Duplicate item bo'lmaydi — underlying (role_id, permission_id) UNIQUE
     * constraint kafolatlaydi.
     *
     * @param tenantId admin kontekst tenant identifikatori
     * @param roleId global rol identifikatori
     * @param actorUserId joriy actor identifikatori
     * @return rol header + biriktirilgan permission'lar ro'yxati
     * @throws IllegalArgumentException agar tenantId yoki roleId null bo'lsa
     * @throws ResourceNotFoundException agar tenant yoki rol topilmasa
     */
    public RolePermissionListView getRolePermissions(UUID tenantId, UUID roleId, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (roleId == null) {
            throw new IllegalArgumentException("roleId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeRead(tenantId, actorUserId);

        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        Role role = identityQueryService.findRoleById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));

        List<RolePermission> bindings = identityQueryService.findRolePermissions(roleId);

        List<PermissionItemView> items = bindings.stream()
                .map(RolePermission::getPermission)
                .sorted(Comparator.comparing(Permission::getCode)
                        .thenComparing(Permission::getId))
                .map(p -> new PermissionItemView(
                        p.getId(),
                        p.getCode(),
                        p.getDescription(),
                        p.getCreatedAt()))
                .toList();

        return new RolePermissionListView(
                tenantId,
                role.getId(),
                role.getCode(),
                role.getName(),
                items);
    }

    /**
     * Facade natija modeli — rol uchun biriktirilgan permission ro'yxati.
     */
    public record RolePermissionListView(
            UUID tenantId,
            UUID roleId,
            String roleCode,
            String roleName,
            List<PermissionItemView> items) {}

    /**
     * Berilgan global ruxsat (permission) uchun unga biriktirilgan global rol'lar
     * ro'yxatini katalog ko'rinishida qaytaradi.
     *
     * Rol va ruxsat ikkalasi ham GLOBAL — tenantga tegishli emas. tenantId
     * endpoint-family izchilligi va admin kontekst validatsiyasi uchun tekshiriladi.
     *
     * Validation-before-authorization ordering:
     * 1. tenantId null bo'lmasligi kerak
     * 2. permissionId null bo'lmasligi kerak
     * 3. authorizeRead chaqiriladi
     * 4. tenant mavjudligi tekshiriladi
     * 5. permission mavjudligi tekshiriladi (ID bo'yicha global katalogdan)
     * 6. permission uchun rol binding'lar yig'iladi
     *
     * Ordering: code ASC -> id ASC
     *
     * Duplicate item bo'lmaydi — underlying (role_id, permission_id) UNIQUE
     * constraint kafolatlaydi.
     *
     * @param tenantId admin kontekst tenant identifikatori
     * @param permissionId global ruxsat identifikatori
     * @param actorUserId joriy actor identifikatori
     * @return permission header + biriktirilgan rol'lar ro'yxati
     * @throws IllegalArgumentException agar tenantId yoki permissionId null bo'lsa
     * @throws ResourceNotFoundException agar tenant yoki permission topilmasa
     */
    public PermissionRoleListView getPermissionRoles(UUID tenantId, UUID permissionId, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (permissionId == null) {
            throw new IllegalArgumentException("permissionId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeRead(tenantId, actorUserId);

        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        Permission permission = identityQueryService.findPermissionById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", permissionId));

        List<RolePermission> bindings = identityQueryService.findPermissionRoles(permissionId);

        List<RoleItemView> items = bindings.stream()
                .map(RolePermission::getRole)
                .sorted(Comparator.comparing(Role::getCode)
                        .thenComparing(Role::getId))
                .map(r -> new RoleItemView(
                        r.getId(),
                        r.getCode(),
                        r.getName(),
                        r.getDescription(),
                        r.isSystemRole(),
                        r.getCreatedAt()))
                .toList();

        return new PermissionRoleListView(
                tenantId,
                permission.getId(),
                permission.getCode(),
                items);
    }

    /**
     * Facade natija modeli — permission uchun biriktirilgan rol ro'yxati.
     */
    public record PermissionRoleListView(
            UUID tenantId,
            UUID permissionId,
            String permissionCode,
            List<RoleItemView> items) {}

    /**
     * Berilgan a'zolik (membership) uchun unga biriktirilgan global rol'lar
     * ro'yxatini qaytaradi.
     *
     * Validation-before-authorization ordering:
     * 1. tenantId null bo'lmasligi kerak
     * 2. membershipId null bo'lmasligi kerak
     * 3. authorizeRead chaqiriladi
     * 4. tenant mavjudligi tekshiriladi
     * 5. membership shu tenantga tegishli bo'lishi tekshiriladi (tenant-safe lookup —
     *    cross-tenant lookup natijasi NOT FOUND ko'rinishida qaytadi)
     * 6. membership uchun rol binding'lar yig'iladi
     *
     * Ordering: role code ASC -> role id ASC
     *
     * Duplicate item bo'lmaydi — underlying (membership_id, role_id) UNIQUE
     * constraint kafolatlaydi.
     *
     * @param tenantId admin kontekst tenant identifikatori
     * @param membershipId a'zolik identifikatori
     * @param actorUserId joriy actor identifikatori
     * @return membership header + biriktirilgan rol'lar ro'yxati
     * @throws IllegalArgumentException agar tenantId yoki membershipId null bo'lsa
     * @throws ResourceNotFoundException agar tenant yoki membership topilmasa
     *         (membership boshqa tenantga tegishli bo'lsa ham)
     */
    public MembershipRoleListView getMembershipRoles(UUID tenantId, UUID membershipId, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (membershipId == null) {
            throw new IllegalArgumentException("membershipId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeRead(tenantId, actorUserId);

        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        Membership membership = identityQueryService
                .findMembershipByIdAndTenantId(membershipId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership", membershipId));

        List<MembershipRoleBinding> bindings = identityQueryService.getMembershipRoles(membershipId);

        List<RoleItemView> items = bindings.stream()
                .map(MembershipRoleBinding::getRole)
                .sorted(Comparator.comparing(Role::getCode)
                        .thenComparing(Role::getId))
                .map(r -> new RoleItemView(
                        r.getId(),
                        r.getCode(),
                        r.getName(),
                        r.getDescription(),
                        r.isSystemRole(),
                        r.getCreatedAt()))
                .toList();

        return new MembershipRoleListView(
                tenantId,
                membership.getId(),
                membership.getUserId(),
                membership.getStatus().name(),
                items);
    }

    /**
     * Facade natija modeli — membership uchun biriktirilgan rol ro'yxati.
     */
    public record MembershipRoleListView(
            UUID tenantId,
            UUID membershipId,
            UUID userId,
            String membershipStatus,
            List<RoleItemView> items) {}

    /**
     * Berilgan a'zolik (membership) uchun to'liq detail ko'rinishini qaytaradi:
     * header (membershipId, status, createdAt) + nested user identity
     * (userId, telegramUserId, displayName, username).
     *
     * Validation-before-authorization ordering:
     * 1. tenantId null bo'lmasligi kerak
     * 2. membershipId null bo'lmasligi kerak
     * 3. authorizeRead chaqiriladi
     * 4. tenant mavjudligi tekshiriladi
     * 5. membership shu tenantga tegishli bo'lishi tekshiriladi (tenant-safe)
     * 6. membership.userId orqali AppUser identity yig'iladi
     *
     * User semantics:
     * - Membership child entity bo'lib, u FK orqali AppUser'ga bog'langan.
     * - Normal holatda user mavjud bo'lishi kerak.
     * - Topilmasa (orphan reference) — ResourceNotFoundException("User", userId)
     *   tashlanadi. List view defensive null pattern detail uchun mos kelmaydi:
     *   bu yerda admin'ga buzilgan invariant aniq ko'rsatilishi kerak.
     *
     * Bu endpoint a'zolikning rol items'ini QAYTARMAYDI — buning uchun alohida
     * endpoint mavjud: GET /memberships/{membershipId}/roles.
     *
     * @param tenantId tenant identifikatori
     * @param membershipId a'zolik identifikatori
     * @param actorUserId joriy actor identifikatori
     * @return membership header + nested user identity
     * @throws IllegalArgumentException agar tenantId yoki membershipId null bo'lsa
     * @throws ResourceNotFoundException agar tenant, membership yoki user topilmasa
     */
    public MembershipDetailView getMembershipDetails(
            UUID tenantId, UUID membershipId, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (membershipId == null) {
            throw new IllegalArgumentException("membershipId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeRead(tenantId, actorUserId);

        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        Membership membership = identityQueryService
                .findMembershipByIdAndTenantId(membershipId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership", membershipId));

        AppUser user = identityQueryService.findUserById(membership.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", membership.getUserId()));

        UserIdentityView userIdentity = new UserIdentityView(
                user.getId(),
                user.getTelegramUserId(),
                user.getDisplayName(),
                user.getUsername());

        return new MembershipDetailView(
                tenantId,
                membership.getId(),
                membership.getStatus().name(),
                membership.getCreatedAt(),
                userIdentity);
    }

    /**
     * Facade natija modeli — membership to'liq detail.
     */
    public record MembershipDetailView(
            UUID tenantId,
            UUID membershipId,
            String membershipStatus,
            Instant createdAt,
            UserIdentityView userIdentity) {}

    public record UserIdentityView(
            UUID userId,
            Long telegramUserId,
            String displayName,
            String username) {}

    /**
     * Global ruxsat (permission) katalogi ro'yxatini qaytaradi.
     *
     * Rollar kabi ruxsatlar ham GLOBAL — tenantga tegishli emas. tenantId endpoint-family
     * izchilligi va admin kontekst validatsiyasi uchun tekshiriladi.
     *
     * Ordering: code ASC -> id ASC
     *
     * @param tenantId admin kontekst tenant identifikatori
     * @param actorUserId joriy actor identifikatori
     * @return global ruxsat katalogi ro'yxati
     * @throws IllegalArgumentException agar tenantId null bo'lsa
     * @throws ResourceNotFoundException agar tenant topilmasa
     */
    public PermissionListView getPermissions(UUID tenantId, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeRead(tenantId, actorUserId);

        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        List<Permission> permissions = identityQueryService.listAllPermissions();

        List<PermissionItemView> items = permissions.stream()
                .sorted(Comparator.comparing(Permission::getCode)
                        .thenComparing(Permission::getId))
                .map(p -> new PermissionItemView(
                        p.getId(),
                        p.getCode(),
                        p.getDescription(),
                        p.getCreatedAt()))
                .toList();

        return new PermissionListView(tenantId, items);
    }

    /**
     * Facade natija modeli — global ruxsat katalogi ro'yxati.
     */
    public record PermissionListView(
            UUID tenantId,
            List<PermissionItemView> items) {}

    public record PermissionItemView(
            UUID permissionId,
            String code,
            String description,
            Instant createdAt) {}

    /**
     * Tenant uchun barcha a'zoliklarning compact ro'yxatini qaytaradi.
     *
     * Har bir membership uchun user ma'lumotlari va rol nomlari ham olinadi —
     * IdentityQueryService public API orqali.
     *
     * Ordering: status order (ACTIVE=0, SUSPENDED=1, REMOVED=2) -> displayName ASC (nulls last) -> id ASC
     *
     * @param tenantId tenant identifikatori
     * @return a'zoliklar ro'yxati
     * @throws IllegalArgumentException agar tenantId null bo'lsa
     * @throws ResourceNotFoundException agar tenant topilmasa
     */
    public MembershipListView getMemberships(UUID tenantId, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeRead(tenantId, actorUserId);

        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        List<Membership> memberships = identityQueryService.listAllMembers(tenantId);

        List<MembershipItemView> items = memberships.stream()
                .map(m -> {
                    AppUser user = identityQueryService.findUserById(m.getUserId())
                            .orElse(null);

                    List<String> roleNames = identityQueryService.getMembershipRoles(m.getId())
                            .stream()
                            .map(MembershipRoleBinding::getRole)
                            .map(role -> role.getName())
                            .sorted()
                            .toList();

                    return new MembershipItemView(
                            m.getId(),
                            m.getUserId(),
                            user != null ? user.getTelegramUserId() : null,
                            user != null ? user.getDisplayName() : null,
                            user != null ? user.getUsername() : null,
                            m.getStatus().name(),
                            roleNames.isEmpty() ? null : roleNames,
                            m.getCreatedAt());
                })
                .sorted(Comparator.comparingInt(
                                (MembershipItemView v) -> statusOrder(v.membershipStatus()))
                        .thenComparing(MembershipItemView::displayName,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(MembershipItemView::membershipId))
                .toList();

        return new MembershipListView(tenantId, items);
    }

    private static int statusOrder(String status) {
        return switch (status) {
            case "ACTIVE" -> 0;
            case "SUSPENDED" -> 1;
            case "REMOVED" -> 2;
            default -> 3;
        };
    }

    /**
     * Facade natija modeli — a'zoliklar ro'yxati.
     */
    public record MembershipListView(
            UUID tenantId,
            List<MembershipItemView> items) {}

    public record MembershipItemView(
            UUID membershipId,
            UUID userId,
            Long telegramUserId,
            String displayName,
            String username,
            String membershipStatus,
            List<String> roleNames,
            Instant createdAt) {}

    /**
     * Tenant uchun barcha workflow ta'riflarining compact ro'yxatini qaytaradi.
     *
     * Ordering: name ASC -> id ASC
     *
     * @param tenantId tenant identifikatori
     * @return workflow ta'riflari ro'yxati
     * @throws IllegalArgumentException agar tenantId null bo'lsa
     * @throws ResourceNotFoundException agar tenant topilmasa
     */
    public WorkflowDefinitionListView getWorkflowDefinitions(UUID tenantId, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeRead(tenantId, actorUserId);

        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        List<WorkflowDefinition> definitions =
                tenantConfigQueryService.listAllWorkflowDefinitions(tenantId);

        List<WorkflowDefinitionItemView> items = definitions.stream()
                .sorted(Comparator.comparing(WorkflowDefinition::getName)
                        .thenComparing(WorkflowDefinition::getId))
                .map(d -> new WorkflowDefinitionItemView(
                        d.getId(),
                        d.getName(),
                        d.getWorkItemType(),
                        d.getDescription(),
                        d.isActive(),
                        d.getCreatedAt()))
                .toList();

        return new WorkflowDefinitionListView(tenantId, items);
    }

    /**
     * Berilgan workflow definition uchun to'liq detail ko'rinishini qaytaradi:
     * header (id, name, workItemType, description, active, createdAt)
     * + statuses[] (id, name, statusOrder, initial, terminal)
     * + transitionRules[] (id, fromStatus*, toStatus*, requiredPermissionId)
     *
     * Eslatma: status va transition rule sub-entity'larida createdAt ataylab
     * chiqarilmaydi — ular workflow definition bilan birga seed qilinadi va
     * alohida yaratilish vaqti operatsion qiymat bermaydi.
     *
     * Validation-before-authorization ordering:
     * 1. tenantId null bo'lmasligi kerak
     * 2. definitionId null bo'lmasligi kerak
     * 3. authorizeRead chaqiriladi
     * 4. tenant mavjudligi tekshiriladi
     * 5. workflow definition shu tenantga tegishli bo'lishi tekshiriladi
     *    (tenant-safe lookup — boshqa tenantga tegishli bo'lsa NOT FOUND)
     * 6. statuses + transitionRules yig'iladi
     *
     * Statuses ordering: statusOrder ASC -> name ASC -> id ASC
     * Transition rules ordering: fromStatus.name ASC -> toStatus.name ASC -> id ASC
     *
     * Transition rule item'da fromStatus va toStatus ikkala name ham id ham
     * ko'rsatiladi — admin/support workflow shape'ini darhol tushunsin.
     *
     * @param tenantId tenant identifikatori
     * @param definitionId workflow definition identifikatori
     * @param actorUserId joriy actor identifikatori
     * @return workflow definition header + statuses + transition rules
     * @throws IllegalArgumentException agar tenantId yoki definitionId null bo'lsa
     * @throws ResourceNotFoundException agar tenant yoki workflow definition topilmasa
     */
    public WorkflowDefinitionDetailView getWorkflowDefinitionDetails(
            UUID tenantId, UUID definitionId, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (definitionId == null) {
            throw new IllegalArgumentException("definitionId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeRead(tenantId, actorUserId);

        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        WorkflowDefinition definition = tenantConfigQueryService
                .findWorkflowDefinitionById(tenantId, definitionId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowDefinition", definitionId));

        List<WorkflowStatusItemView> statusItems = definition.getStatuses().stream()
                .sorted(Comparator.comparingInt(WorkflowStatus::getStatusOrder)
                        .thenComparing(WorkflowStatus::getName)
                        .thenComparing(WorkflowStatus::getId))
                .map(s -> new WorkflowStatusItemView(
                        s.getId(),
                        s.getName(),
                        s.getStatusOrder(),
                        s.isInitial(),
                        s.isTerminal()))
                .toList();

        List<WorkflowTransitionRuleItemView> ruleItems = definition.getTransitionRules().stream()
                .sorted(Comparator.comparing((WorkflowTransitionRule r) -> r.getFromStatus().getName())
                        .thenComparing(r -> r.getToStatus().getName())
                        .thenComparing(WorkflowTransitionRule::getId))
                .map(r -> new WorkflowTransitionRuleItemView(
                        r.getId(),
                        r.getFromStatus().getId(),
                        r.getFromStatus().getName(),
                        r.getToStatus().getId(),
                        r.getToStatus().getName(),
                        r.getRequiredPermissionId()))
                .toList();

        return new WorkflowDefinitionDetailView(
                tenantId,
                definition.getId(),
                definition.getName(),
                definition.getWorkItemType(),
                definition.getDescription(),
                definition.isActive(),
                definition.getCreatedAt(),
                statusItems,
                ruleItems);
    }

    /**
     * Facade natija modeli — workflow definition to'liq detail.
     */
    public record WorkflowDefinitionDetailView(
            UUID tenantId,
            UUID definitionId,
            String name,
            String workItemType,
            String description,
            boolean active,
            Instant createdAt,
            List<WorkflowStatusItemView> statuses,
            List<WorkflowTransitionRuleItemView> transitionRules) {}

    public record WorkflowStatusItemView(
            UUID statusId,
            String name,
            int statusOrder,
            boolean initial,
            boolean terminal) {}

    public record WorkflowTransitionRuleItemView(
            UUID ruleId,
            UUID fromStatusId,
            String fromStatusName,
            UUID toStatusId,
            String toStatusName,
            UUID requiredPermissionId) {}

    /**
     * Tenant uchun barcha routing qoidalarining compact ro'yxatini qaytaradi.
     *
     * Ordering: priority DESC -> name ASC -> id ASC
     *
     * @param tenantId tenant identifikatori
     * @return routing qoidalari ro'yxati
     * @throws IllegalArgumentException agar tenantId null bo'lsa
     * @throws ResourceNotFoundException agar tenant topilmasa
     */
    public RoutingRuleListView getRoutingRules(UUID tenantId, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeRead(tenantId, actorUserId);

        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        List<RoutingRule> rules =
                tenantConfigQueryService.listAllRoutingRules(tenantId);

        List<RoutingRuleItemView> items = rules.stream()
                .sorted(Comparator.comparingInt(RoutingRule::getPriority).reversed()
                        .thenComparing(RoutingRule::getName)
                        .thenComparing(RoutingRule::getId))
                .map(r -> new RoutingRuleItemView(
                        r.getId(),
                        r.getName(),
                        r.getWorkItemType(),
                        r.getPriority(),
                        r.getTargetTopicBindingId(),
                        r.isActive(),
                        r.getCreatedAt()))
                .toList();

        return new RoutingRuleListView(tenantId, items);
    }

    /**
     * Berilgan routing rule uchun to'liq detail ko'rinishini qaytaradi:
     * header (id, name, workItemType, priority, conditionExpression, active, createdAt)
     * + (ixtiyoriy) targetTopicBinding context (topic + parent chat).
     *
     * Validation-before-authorization ordering:
     * 1. tenantId null bo'lmasligi kerak
     * 2. ruleId null bo'lmasligi kerak
     * 3. authorizeRead chaqiriladi
     * 4. tenant mavjudligi tekshiriladi
     * 5. routing rule shu tenantga tegishli bo'lishi tekshiriladi
     *    (tenant-safe lookup — boshqa tenantga tegishli bo'lsa NOT FOUND)
     * 6. agar targetTopicBindingId mavjud bo'lsa, tenant-safe topic binding
     *    context MAJBURIY ravishda topiladi; topic binding orqali parent chat
     *    binding ham olinadi
     *
     * Target semantics:
     * - targetTopicBindingId == null  → nested target null (JSON omit)
     * - targetTopicBindingId != null  → target binding mavjud bo'lishi shart;
     *   topilmasa (jumladan cross-tenant dangling reference)
     *   ResourceNotFoundException("TopicBinding", targetId) tashlanadi.
     *   Silent omit qilinmaydi — dangling target invariantni buzgan konfiguratsiya
     *   bo'lib, admin'ga 404 sifatida ko'rsatilishi kerak.
     *
     * @param tenantId tenant identifikatori
     * @param ruleId routing rule identifikatori
     * @param actorUserId joriy actor identifikatori
     * @return routing rule header + ixtiyoriy target context
     * @throws IllegalArgumentException agar tenantId yoki ruleId null bo'lsa
     * @throws ResourceNotFoundException agar tenant, routing rule yoki
     *         non-null targetTopicBindingId ko'rsatgan topic binding topilmasa
     */
    public RoutingRuleDetailView getRoutingRuleDetails(
            UUID tenantId, UUID ruleId, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (ruleId == null) {
            throw new IllegalArgumentException("ruleId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeRead(tenantId, actorUserId);

        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        RoutingRule rule = tenantConfigQueryService
                .findRoutingRuleById(tenantId, ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("RoutingRule", ruleId));

        TargetTopicBindingView target = null;
        UUID targetId = rule.getTargetTopicBindingId();
        if (targetId != null) {
            TelegramTopicBinding topicBinding = tenantConfigQueryService
                    .findTopicBindingById(tenantId, targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("TopicBinding", targetId));
            TelegramChatBinding chatBinding = topicBinding.getChatBinding();
            target = new TargetTopicBindingView(
                    topicBinding.getId(),
                    topicBinding.getTopicId(),
                    topicBinding.getTopicName(),
                    topicBinding.getPurpose(),
                    topicBinding.isActive(),
                    chatBinding.getId(),
                    chatBinding.getChatId(),
                    chatBinding.getChatTitle(),
                    chatBinding.getBindingType().name());
        }

        return new RoutingRuleDetailView(
                tenantId,
                rule.getId(),
                rule.getName(),
                rule.getWorkItemType(),
                rule.getPriority(),
                rule.getConditionExpression(),
                rule.isActive(),
                rule.getCreatedAt(),
                target);
    }

    /**
     * Facade natija modeli — routing rule to'liq detail.
     */
    public record RoutingRuleDetailView(
            UUID tenantId,
            UUID ruleId,
            String name,
            String workItemType,
            int priority,
            String conditionExpression,
            boolean active,
            Instant createdAt,
            TargetTopicBindingView targetTopicBinding) {}

    public record TargetTopicBindingView(
            UUID topicBindingId,
            long topicId,
            String topicName,
            String purpose,
            boolean active,
            UUID chatBindingId,
            long chatId,
            String chatTitle,
            String chatBindingType) {}

    /**
     * Tenant uchun barcha Telegram chat bog'lanishlarining compact ro'yxatini qaytaradi.
     *
     * Har bir chat binding uchun activeTopicBindingCount ham hisoblanadi —
     * mavjud listActiveTopicBindings() orqali.
     *
     * Ordering: bindingType name ASC -> id ASC
     *
     * @param tenantId tenant identifikatori
     * @return chat bog'lanishlari ro'yxati
     * @throws IllegalArgumentException agar tenantId null bo'lsa
     * @throws ResourceNotFoundException agar tenant topilmasa
     */
    public ChatBindingListView getChatBindings(UUID tenantId, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeRead(tenantId, actorUserId);

        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        List<TelegramChatBinding> bindings =
                tenantConfigQueryService.listAllChatBindings(tenantId);

        List<ChatBindingItemView> items = bindings.stream()
                .sorted(Comparator.comparing(
                                (TelegramChatBinding cb) -> cb.getBindingType().name())
                        .thenComparing(TelegramChatBinding::getId))
                .map(cb -> {
                    int activeTopicCount = tenantConfigQueryService
                            .listActiveTopicBindings(cb.getId()).size();
                    return new ChatBindingItemView(
                            cb.getId(),
                            cb.getChatId(),
                            cb.getChatTitle(),
                            cb.getBindingType().name(),
                            cb.isActive(),
                            activeTopicCount,
                            cb.getCreatedAt());
                })
                .toList();

        return new ChatBindingListView(tenantId, items);
    }

    /**
     * Berilgan chat binding uchun to'liq detail ko'rinishini qaytaradi:
     * header (id, chatId, chatTitle, bindingType, active, createdAt)
     * + topicBindings[] (id, topicId, topicName, purpose, active, createdAt).
     *
     * Validation-before-authorization ordering:
     * 1. tenantId null bo'lmasligi kerak
     * 2. chatBindingId null bo'lmasligi kerak
     * 3. authorizeRead chaqiriladi
     * 4. tenant mavjudligi tekshiriladi
     * 5. chat binding shu tenantga tegishli bo'lishi tekshiriladi
     *    (tenant-safe lookup — boshqa tenantga tegishli bo'lsa NOT FOUND)
     * 6. shu chat binding uchun topic binding ro'yxati yig'iladi
     *
     * Topic bindings ordering: purpose ASC -> topicId ASC -> topicBindingId ASC
     *
     * Topic item shape parent context'siz qaytadi (chat header allaqachon outer
     * level'da bor) — `TopicBindingItemView` flat list shape'idan farqli.
     *
     * @param tenantId tenant identifikatori
     * @param chatBindingId chat binding identifikatori
     * @param actorUserId joriy actor identifikatori
     * @return chat binding header + nested topic bindings
     * @throws IllegalArgumentException agar tenantId yoki chatBindingId null bo'lsa
     * @throws ResourceNotFoundException agar tenant yoki chat binding topilmasa
     */
    public ChatBindingDetailView getChatBindingDetails(
            UUID tenantId, UUID chatBindingId, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (chatBindingId == null) {
            throw new IllegalArgumentException("chatBindingId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeRead(tenantId, actorUserId);

        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        TelegramChatBinding chatBinding = tenantConfigQueryService
                .findChatBindingById(tenantId, chatBindingId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatBinding", chatBindingId));

        List<ChatBindingTopicItemView> topicItems = tenantConfigQueryService
                .listAllTopicBindings(chatBinding.getId()).stream()
                .sorted(Comparator.comparing(TelegramTopicBinding::getPurpose)
                        .thenComparingLong(TelegramTopicBinding::getTopicId)
                        .thenComparing(TelegramTopicBinding::getId))
                .map(t -> new ChatBindingTopicItemView(
                        t.getId(),
                        t.getTopicId(),
                        t.getTopicName(),
                        t.getPurpose(),
                        t.isActive(),
                        t.getCreatedAt()))
                .toList();

        return new ChatBindingDetailView(
                tenantId,
                chatBinding.getId(),
                chatBinding.getChatId(),
                chatBinding.getChatTitle(),
                chatBinding.getBindingType().name(),
                chatBinding.isActive(),
                chatBinding.getCreatedAt(),
                topicItems);
    }

    /**
     * Facade natija modeli — chat binding to'liq detail.
     */
    public record ChatBindingDetailView(
            UUID tenantId,
            UUID chatBindingId,
            long chatId,
            String chatTitle,
            String bindingType,
            boolean active,
            Instant createdAt,
            List<ChatBindingTopicItemView> topicBindings) {}

    public record ChatBindingTopicItemView(
            UUID topicBindingId,
            long topicId,
            String topicName,
            String purpose,
            boolean active,
            Instant createdAt) {}

    /**
     * Berilgan topic binding uchun to'liq detail ko'rinishini qaytaradi:
     * header (id, topicId, topicName, purpose, active, createdAt)
     * + parent chat binding context (id, chatId, chatTitle, bindingType).
     *
     * Validation-before-authorization ordering:
     * 1. tenantId null bo'lmasligi kerak
     * 2. topicBindingId null bo'lmasligi kerak
     * 3. authorizeRead chaqiriladi
     * 4. tenant mavjudligi tekshiriladi
     * 5. topic binding shu tenantga tegishli bo'lishi tekshiriladi
     *    (tenant-safe lookup — boshqa tenantga tegishli bo'lsa NOT FOUND)
     *
     * Topic binding bu child entity — parent chat kontekst child'siz tushunarsiz
     * bo'lgani uchun majburiy nested obyekt sifatida qaytadi. Parent kontekst
     * `topicBinding.getChatBinding()` lazy navigation orqali olinadi (read-only
     * tx ichida ishlaydi, qo'shimcha lookup talab qilmaydi).
     *
     * @param tenantId tenant identifikatori
     * @param topicBindingId topic binding identifikatori
     * @param actorUserId joriy actor identifikatori
     * @return topic binding header + parent chat binding context
     * @throws IllegalArgumentException agar tenantId yoki topicBindingId null bo'lsa
     * @throws ResourceNotFoundException agar tenant yoki topic binding topilmasa
     */
    public TopicBindingDetailView getTopicBindingDetails(
            UUID tenantId, UUID topicBindingId, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (topicBindingId == null) {
            throw new IllegalArgumentException("topicBindingId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeRead(tenantId, actorUserId);

        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        TelegramTopicBinding topicBinding = tenantConfigQueryService
                .findTopicBindingById(tenantId, topicBindingId)
                .orElseThrow(() -> new ResourceNotFoundException("TopicBinding", topicBindingId));

        TelegramChatBinding chatBinding = topicBinding.getChatBinding();
        ParentChatBindingView parent = new ParentChatBindingView(
                chatBinding.getId(),
                chatBinding.getChatId(),
                chatBinding.getChatTitle(),
                chatBinding.getBindingType().name());

        return new TopicBindingDetailView(
                tenantId,
                topicBinding.getId(),
                topicBinding.getTopicId(),
                topicBinding.getTopicName(),
                topicBinding.getPurpose(),
                topicBinding.isActive(),
                topicBinding.getCreatedAt(),
                parent);
    }

    /**
     * Facade natija modeli — topic binding to'liq detail (parent chat context bilan).
     */
    public record TopicBindingDetailView(
            UUID tenantId,
            UUID topicBindingId,
            long topicId,
            String topicName,
            String purpose,
            boolean active,
            Instant createdAt,
            ParentChatBindingView parentChatBinding) {}

    public record ParentChatBindingView(
            UUID chatBindingId,
            long chatId,
            String chatTitle,
            String bindingType) {}

    /**
     * Tenant uchun barcha Telegram topic bog'lanishlarining compact flat ro'yxatini qaytaradi.
     *
     * Barcha chat binding'lar bo'ylab iteratsiya qilib, har birining topic binding'larini
     * yig'adi. Chat kontekst field'lari (chatId, chatTitle) flat sifatida kiritiladi.
     *
     * Ordering: purpose ASC -> id ASC
     *
     * @param tenantId tenant identifikatori
     * @return topic bog'lanishlari flat ro'yxati
     * @throws IllegalArgumentException agar tenantId null bo'lsa
     * @throws ResourceNotFoundException agar tenant topilmasa
     */
    public TopicBindingListView getTopicBindings(UUID tenantId, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeRead(tenantId, actorUserId);

        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        List<TelegramChatBinding> chatBindings =
                tenantConfigQueryService.listAllChatBindings(tenantId);

        List<TopicBindingItemView> items = chatBindings.stream()
                .flatMap(cb -> tenantConfigQueryService.listAllTopicBindings(cb.getId())
                        .stream()
                        .map(tb -> new TopicBindingItemView(
                                tb.getId(),
                                cb.getId(),
                                cb.getChatId(),
                                cb.getChatTitle(),
                                tb.getTopicId(),
                                tb.getTopicName(),
                                tb.getPurpose(),
                                tb.isActive(),
                                tb.getCreatedAt())))
                .sorted(Comparator.comparing(TopicBindingItemView::purpose)
                        .thenComparing(TopicBindingItemView::topicBindingId))
                .toList();

        return new TopicBindingListView(tenantId, items);
    }

    /**
     * Facade natija modeli — topic bog'lanishlari ro'yxati.
     */
    public record TopicBindingListView(
            UUID tenantId,
            List<TopicBindingItemView> items) {}

    public record TopicBindingItemView(
            UUID topicBindingId,
            UUID chatBindingId,
            long chatId,
            String chatTitle,
            long topicId,
            String topicName,
            String purpose,
            boolean active,
            Instant createdAt) {}

    /**
     * Facade natija modeli — chat bog'lanishlari ro'yxati.
     */
    public record ChatBindingListView(
            UUID tenantId,
            List<ChatBindingItemView> items) {}

    public record ChatBindingItemView(
            UUID chatBindingId,
            long chatId,
            String chatTitle,
            String bindingType,
            boolean active,
            int activeTopicBindingCount,
            Instant createdAt) {}

    /**
     * Facade natija modeli — routing qoidalari ro'yxati.
     */
    public record RoutingRuleListView(
            UUID tenantId,
            List<RoutingRuleItemView> items) {}

    public record RoutingRuleItemView(
            UUID ruleId,
            String name,
            String workItemType,
            int priority,
            UUID targetTopicBindingId,
            boolean active,
            Instant createdAt) {}

    /**
     * Facade natija modeli — workflow ta'riflari ro'yxati.
     */
    public record WorkflowDefinitionListView(
            UUID tenantId,
            List<WorkflowDefinitionItemView> items) {}

    public record WorkflowDefinitionItemView(
            UUID definitionId,
            String name,
            String workItemType,
            String description,
            boolean active,
            Instant createdAt) {}

    /**
     * Facade natija modeli — compact tenant config details.
     */
    public record TenantConfigDetailsView(
            UUID tenantId,
            String name,
            String slug,
            String timezone,
            String status,
            Instant createdAt,
            int totalMembershipCount,
            int activeMembershipCount,
            int totalWorkflowDefinitionCount,
            int activeWorkflowDefinitionCount,
            int totalRoutingRuleCount,
            int activeRoutingRuleCount,
            int activeChatBindingCount,
            int activeTopicBindingCount) {}
}
