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
