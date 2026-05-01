package com.engops.platform.admin;

import com.engops.platform.identity.IdentityCommandService;
import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipRoleBinding;
import com.engops.platform.identity.model.Role;
import com.engops.platform.identity.model.RolePermission;
import com.engops.platform.tenantconfig.TenantConfigCommandService;
import com.engops.platform.tenantconfig.model.ChatBindingType;
import com.engops.platform.tenantconfig.model.RoutingRule;
import com.engops.platform.tenantconfig.model.TelegramChatBinding;
import com.engops.platform.tenantconfig.model.TelegramTopicBinding;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.model.WorkflowStatus;
import com.engops.platform.tenantconfig.model.WorkflowTransitionRule;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * Tenant konfiguratsiyasi uchun write orchestration facade.
 *
 * Admin controller'dan write request'larni qabul qilib,
 * request boundary validatsiyasini bajarib, TenantConfigCommandService'ga uzatadi.
 *
 * Muhim:
 * - Tranzaksiya bu facade'da emas — TenantConfigCommandService ichida
 * - Faqat request boundary validation bu yerda
 * - Business validation TenantConfigCommandService ichida
 * - Read operatsiyalar uchun TenantConfigDetailsFacade ishlatiladi
 */
@Service
public class TenantConfigWriteFacade {

    private static final Set<String> ALLOWED_WORK_ITEM_TYPES = Set.of("BUG", "INCIDENT", "TASK");
    private static final Set<String> ALLOWED_BINDING_TYPES = Set.of("MAIN_GROUP", "NOTIFICATION_GROUP");

    private final TenantConfigCommandService commandService;
    private final IdentityCommandService identityCommandService;
    private final AdminAuthorizationService authorizationService;

    public TenantConfigWriteFacade(TenantConfigCommandService commandService,
                                    IdentityCommandService identityCommandService,
                                    AdminAuthorizationService authorizationService) {
        this.commandService = commandService;
        this.identityCommandService = identityCommandService;
        this.authorizationService = authorizationService;
    }

    // ========== Tenant operations ==========

    private static final String DEFAULT_TIMEZONE = "UTC";
    private static final int TENANT_NAME_MAX_LENGTH = 255;
    private static final int TENANT_SLUG_MAX_LENGTH = 100;
    private static final int TENANT_TIMEZONE_MAX_LENGTH = 50;

    /**
     * Yangi tenant yaratish uchun request boundary validatsiyasi, normalizatsiya
     * va delegation.
     *
     * adminContextTenantId — bu YANGI yaratiladigan tenant emas, balki actor
     * uning a'zosi sifatida TENANT_CONFIG_WRITE ruxsatiga ega bo'lgan mavjud
     * tenant. Yangi tenant root resurs bo'lganligi sababli, mavjud admin context
     * tenant orqali ruxsat tekshiriladi (role catalog write surface bilan
     * bir xil pattern).
     *
     * Birinchi tenant uchun chicken-and-egg muammosi qoladi — u faqat manual
     * SQL/Flyway seed orqali yechiladi va bu phase'da ko'rib chiqilmaydi.
     *
     * Validation-before-authorization ordering:
     * 1. adminContextTenantId null bo'lmasligi
     * 2. request null bo'lmasligi
     * 3. name strip + non-blank + max 255
     * 4. slug strip + lowercase(Locale.ROOT) + non-blank + max 100
     * 5. timezone null/blank bo'lsa "UTC" default; aks holda strip + max 50
     * 6. authorizeWrite(adminContextTenantId, actorUserId) chaqiriladi
     * 7. CommandService.createTenant normallashgan input bilan delegate qilinadi
     *
     * @param adminContextTenantId actor admin kontekst tenant identifikatori
     * @param request yaratish so'rovi
     * @param actorUserId joriy actor
     * @return yaratilgan tenant view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public TenantCreatedView createTenant(UUID adminContextTenantId,
                                            CreateTenantRequest request,
                                            UUID actorUserId) {
        if (adminContextTenantId == null) {
            throw new IllegalArgumentException("adminContextTenantId null bo'lishi mumkin emas");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        if (request.name() == null) {
            throw new IllegalArgumentException("name null yoki bo'sh bo'lishi mumkin emas");
        }
        String normalizedName = request.name().strip();
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("name null yoki bo'sh bo'lishi mumkin emas");
        }
        if (normalizedName.length() > TENANT_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "name " + TENANT_NAME_MAX_LENGTH + " belgidan oshmasligi kerak: "
                            + normalizedName.length());
        }
        if (request.slug() == null) {
            throw new IllegalArgumentException("slug null yoki bo'sh bo'lishi mumkin emas");
        }
        String normalizedSlug = request.slug().strip().toLowerCase(java.util.Locale.ROOT);
        if (normalizedSlug.isBlank()) {
            throw new IllegalArgumentException("slug null yoki bo'sh bo'lishi mumkin emas");
        }
        if (normalizedSlug.length() > TENANT_SLUG_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "slug " + TENANT_SLUG_MAX_LENGTH + " belgidan oshmasligi kerak: "
                            + normalizedSlug.length());
        }
        String normalizedTimezone;
        if (request.timezone() == null || request.timezone().isBlank()) {
            normalizedTimezone = DEFAULT_TIMEZONE;
        } else {
            normalizedTimezone = request.timezone().strip();
            if (normalizedTimezone.length() > TENANT_TIMEZONE_MAX_LENGTH) {
                throw new IllegalArgumentException(
                        "timezone " + TENANT_TIMEZONE_MAX_LENGTH + " belgidan oshmasligi kerak: "
                                + normalizedTimezone.length());
            }
        }
        authorizationService.authorizeWrite(adminContextTenantId, actorUserId);

        Tenant tenant = commandService.createTenant(
                normalizedName, normalizedSlug, normalizedTimezone);

        return new TenantCreatedView(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getTimezone(),
                tenant.getStatus() != null ? tenant.getStatus().name() : null,
                tenant.getCreatedAt());
    }

    /**
     * Facade natija modeli — yaratilgan tenant.
     */
    public record TenantCreatedView(
            UUID tenantId,
            String name,
            String slug,
            String timezone,
            String status,
            java.time.Instant createdAt) {}

    // ========== AppUser operations ==========

    private static final int APP_USER_USERNAME_MAX_LENGTH = 255;
    private static final int APP_USER_DISPLAY_NAME_MAX_LENGTH = 255;

    /**
     * Yangi AppUser yaratish uchun request boundary validatsiyasi, normalizatsiya
     * va delegation.
     *
     * adminContextTenantId — bu YANGI yaratiladigan user emas va membership emas,
     * balki actor TENANT_CONFIG_WRITE ruxsatiga ega bo'lgan mavjud tenant.
     * AppUser global root identity resurs (role catalog write surface bilan
     * bir xil pattern).
     *
     * Birinchi user uchun bootstrap muammosi qoladi — hech bo'lmaganda bitta
     * actor tenant a'zosi sifatida TENANT_CONFIG_WRITE ruxsatiga ega bo'lishi
     * kerak. Bu phase'da hal qilinmaydi (Phase 118 chicken-and-egg muammosi
     * bilan bir xil — manual SQL/Flyway seed orqali yechiladi).
     *
     * Validation-before-authorization ordering:
     * 1. adminContextTenantId null bo'lmasligi
     * 2. request null bo'lmasligi
     * 3. telegramUserId null bo'lmasligi
     * 4. telegramUserId > 0 (positive)
     * 5. username strip; blank → null; max 255
     * 6. displayName strip; blank → null; max 255
     * 7. authorizeWrite(adminContextTenantId, actorUserId)
     * 8. IdentityCommandService.createAppUser delegate qilinadi
     *
     * @param adminContextTenantId actor admin kontekst tenant identifikatori
     * @param request yaratish so'rovi
     * @param actorUserId joriy actor
     * @return yaratilgan user view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public AppUserCreatedView createAppUser(UUID adminContextTenantId,
                                              CreateAppUserRequest request,
                                              UUID actorUserId) {
        if (adminContextTenantId == null) {
            throw new IllegalArgumentException("adminContextTenantId null bo'lishi mumkin emas");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        if (request.telegramUserId() == null) {
            throw new IllegalArgumentException("telegramUserId null bo'lishi mumkin emas");
        }
        if (request.telegramUserId() <= 0) {
            throw new IllegalArgumentException(
                    "telegramUserId musbat bo'lishi kerak: " + request.telegramUserId());
        }
        String normalizedUsername = normalizeOptionalString(
                request.username(), APP_USER_USERNAME_MAX_LENGTH, "username");
        String normalizedDisplayName = normalizeOptionalString(
                request.displayName(), APP_USER_DISPLAY_NAME_MAX_LENGTH, "displayName");

        authorizationService.authorizeWrite(adminContextTenantId, actorUserId);

        AppUser user = identityCommandService.createAppUser(
                request.telegramUserId(), normalizedUsername, normalizedDisplayName);

        return new AppUserCreatedView(
                user.getId(),
                user.getTelegramUserId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getStatus() != null ? user.getStatus().name() : null,
                user.getCreatedAt());
    }

    /**
     * Optional string field uchun normalizatsiya:
     * null/blank-after-strip → null; aks holda strip + length cap.
     */
    private static String normalizeOptionalString(String value, int maxLength, String fieldName) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        if (stripped.isBlank()) {
            return null;
        }
        if (stripped.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " " + maxLength + " belgidan oshmasligi kerak: " + stripped.length());
        }
        return stripped;
    }

    /**
     * Facade natija modeli — yaratilgan AppUser.
     */
    public record AppUserCreatedView(
            UUID userId,
            Long telegramUserId,
            String username,
            String displayName,
            String status,
            java.time.Instant createdAt) {}

    /**
     * Yangi workflow definition yaratish uchun request boundary validatsiyasi va delegation.
     *
     * Request boundary validatsiya:
     * - tenantId null bo'lmasligi kerak
     * - request null bo'lmasligi kerak
     * - name null/blank bo'lmasligi kerak
     * - workItemType null/blank bo'lmasligi kerak
     * - workItemType faqat: BUG, INCIDENT, TASK
     *
     * @param tenantId tenant identifikatori
     * @param request yaratish so'rovi
     * @return yaratilgan workflow definition view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public WorkflowDefinitionCreatedView createWorkflowDefinition(UUID tenantId,
                                                                    CreateWorkflowDefinitionRequest request,
                                                                    UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("name null yoki bo'sh bo'lishi mumkin emas");
        }
        if (request.workItemType() == null || request.workItemType().isBlank()) {
            throw new IllegalArgumentException("workItemType null yoki bo'sh bo'lishi mumkin emas");
        }
        if (!ALLOWED_WORK_ITEM_TYPES.contains(request.workItemType())) {
            throw new IllegalArgumentException(
                    "workItemType faqat BUG, INCIDENT, TASK bo'lishi mumkin: " + request.workItemType());
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        WorkflowDefinition definition = commandService.createWorkflowDefinition(
                tenantId, request.name(), request.workItemType(), request.description());

        return new WorkflowDefinitionCreatedView(
                definition.getTenantId(),
                definition.getId(),
                definition.getName(),
                definition.getWorkItemType(),
                definition.getDescription(),
                definition.isActive(),
                definition.getCreatedAt());
    }

    /**
     * Workflow definition metadata'sini PATCH yangilash uchun request boundary validatsiyasi va delegation.
     *
     * PATCH semantikasi:
     * - faqat JSON'da mavjud field'lar yangilanadi
     * - kamida bitta field berilishi kerak
     * - name berilsa, blank bo'lmasligi kerak
     * - description berilmasa o'zgarmaydi, null/blank berilsa tozalanadi
     *
     * @param tenantId tenant identifikatori
     * @param definitionId workflow definition identifikatori
     * @param request yangilash so'rovi
     * @return yangilangan workflow definition view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public WorkflowDefinitionUpdatedView updateWorkflowDefinition(UUID tenantId, UUID definitionId,
                                                                    UpdateWorkflowDefinitionRequest request,
                                                                    UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (definitionId == null) {
            throw new IllegalArgumentException("definitionId null bo'lishi mumkin emas");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        if (!request.isNameProvided() && !request.isDescriptionProvided()) {
            throw new IllegalArgumentException("Kamida bitta yangilanuvchi field berilishi kerak");
        }
        if (request.isNameProvided() && (request.getName() == null || request.getName().isBlank())) {
            throw new IllegalArgumentException("name null yoki bo'sh bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        WorkflowDefinition definition = commandService.updateWorkflowDefinition(
                tenantId, definitionId,
                request.getName(), request.isNameProvided(),
                request.getDescription(), request.isDescriptionProvided());

        return new WorkflowDefinitionUpdatedView(
                definition.getTenantId(),
                definition.getId(),
                definition.getName(),
                definition.getWorkItemType(),
                definition.getDescription(),
                definition.isActive(),
                definition.getCreatedAt());
    }

    /**
     * Workflow definition'ni aktiv holatga o'tkazadi.
     *
     * @param tenantId tenant identifikatori
     * @param definitionId workflow definition identifikatori
     * @return yangilangan workflow definition view
     * @throws IllegalArgumentException tenantId yoki definitionId null bo'lsa
     */
    public WorkflowDefinitionUpdatedView activateWorkflowDefinition(UUID tenantId, UUID definitionId,
                                                                      UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (definitionId == null) {
            throw new IllegalArgumentException("definitionId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        WorkflowDefinition definition = commandService.activateWorkflowDefinition(tenantId, definitionId);
        return toUpdatedView(definition);
    }

    /**
     * Workflow definition'ni noaktiv holatga o'tkazadi.
     *
     * @param tenantId tenant identifikatori
     * @param definitionId workflow definition identifikatori
     * @return yangilangan workflow definition view
     * @throws IllegalArgumentException tenantId yoki definitionId null bo'lsa
     */
    public WorkflowDefinitionUpdatedView deactivateWorkflowDefinition(UUID tenantId, UUID definitionId,
                                                                        UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (definitionId == null) {
            throw new IllegalArgumentException("definitionId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        WorkflowDefinition definition = commandService.deactivateWorkflowDefinition(tenantId, definitionId);
        return toUpdatedView(definition);
    }

    private WorkflowDefinitionUpdatedView toUpdatedView(WorkflowDefinition definition) {
        return new WorkflowDefinitionUpdatedView(
                definition.getTenantId(),
                definition.getId(),
                definition.getName(),
                definition.getWorkItemType(),
                definition.getDescription(),
                definition.isActive(),
                definition.getCreatedAt());
    }

    /**
     * Workflow definition'ni o'chiradi.
     *
     * @param tenantId tenant identifikatori
     * @param definitionId workflow definition identifikatori
     * @throws IllegalArgumentException tenantId yoki definitionId null bo'lsa
     */
    public void deleteWorkflowDefinition(UUID tenantId, UUID definitionId, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (definitionId == null) {
            throw new IllegalArgumentException("definitionId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        commandService.deleteWorkflowDefinition(tenantId, definitionId);
    }

    // ========== WorkflowStatus operations ==========

    /**
     * Mavjud workflow definition uchun yangi status yaratish — request boundary
     * validatsiyasi va delegation.
     *
     * Validation-before-authorization ordering:
     * 1. tenantId, definitionId null bo'lmasligi
     * 2. request null bo'lmasligi
     * 3. name null/blank bo'lmasligi
     * 4. statusOrder >= 0
     * 5. authorizeWrite chaqiriladi
     * 6. CommandService.createWorkflowStatus delegate qilinadi
     *
     * @param tenantId tenant identifikatori
     * @param definitionId workflow definition identifikatori
     * @param request yaratish so'rovi
     * @param actorUserId joriy actor
     * @return yaratilgan workflow status view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public WorkflowStatusCreatedView createWorkflowStatus(UUID tenantId, UUID definitionId,
                                                            CreateWorkflowStatusRequest request,
                                                            UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (definitionId == null) {
            throw new IllegalArgumentException("definitionId null bo'lishi mumkin emas");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        if (request.name() == null) {
            throw new IllegalArgumentException("name null yoki bo'sh bo'lishi mumkin emas");
        }
        // Strip first so "  BUGS  " → "BUGS"; downstream pre-check, persistence,
        // DB duplicate message va audit newValue normallashgan nomdan foydalanadi.
        String normalizedName = request.name().strip();
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("name null yoki bo'sh bo'lishi mumkin emas");
        }
        // WorkflowStatus.@Size(max = 100) — boundary'da clean 400 qaytaring,
        // pastki qatlam validation/DB error'idan oldin.
        if (normalizedName.length() > 100) {
            throw new IllegalArgumentException(
                    "name 100 belgidan oshmasligi kerak: " + normalizedName.length());
        }
        if (request.statusOrder() < 0) {
            throw new IllegalArgumentException(
                    "statusOrder manfiy bo'lishi mumkin emas: " + request.statusOrder());
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        WorkflowStatus status = commandService.createWorkflowStatus(
                tenantId, definitionId, normalizedName,
                request.statusOrder(), request.initial(), request.terminal());

        return new WorkflowStatusCreatedView(
                tenantId,
                definitionId,
                status.getId(),
                status.getName(),
                status.getStatusOrder(),
                status.isInitial(),
                status.isTerminal(),
                status.getCreatedAt());
    }

    /**
     * Facade natija modeli — yaratilgan workflow status.
     */
    public record WorkflowStatusCreatedView(
            UUID tenantId,
            UUID workflowDefinitionId,
            UUID statusId,
            String name,
            int statusOrder,
            boolean initial,
            boolean terminal,
            java.time.Instant createdAt) {}

    // ========== WorkflowTransitionRule operations ==========

    /**
     * Mavjud workflow definition uchun yangi transition rule yaratish — request
     * boundary validatsiyasi va delegation.
     *
     * Validation-before-authorization ordering:
     * 1. tenantId, definitionId null bo'lmasligi
     * 2. request null bo'lmasligi
     * 3. fromStatusId, toStatusId null bo'lmasligi
     * 4. authorizeWrite chaqiriladi
     * 5. CommandService.createWorkflowTransitionRule delegate qilinadi
     *
     * Phase 116 surface'i string action/actionCode field bermaydi (model va schema'da
     * yo'q) — shuning uchun normalize/length-check qilish uchun string yo'q.
     *
     * @param tenantId tenant identifikatori
     * @param definitionId workflow definition identifikatori
     * @param request yaratish so'rovi
     * @param actorUserId joriy actor
     * @return yaratilgan transition rule view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public WorkflowTransitionRuleCreatedView createWorkflowTransitionRule(
            UUID tenantId, UUID definitionId,
            CreateWorkflowTransitionRuleRequest request, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (definitionId == null) {
            throw new IllegalArgumentException("definitionId null bo'lishi mumkin emas");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        if (request.fromStatusId() == null) {
            throw new IllegalArgumentException("fromStatusId null bo'lishi mumkin emas");
        }
        if (request.toStatusId() == null) {
            throw new IllegalArgumentException("toStatusId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        WorkflowTransitionRule rule = commandService.createWorkflowTransitionRule(
                tenantId, definitionId, request.fromStatusId(), request.toStatusId());

        return new WorkflowTransitionRuleCreatedView(
                tenantId,
                definitionId,
                rule.getId(),
                rule.getFromStatus() != null ? rule.getFromStatus().getId() : null,
                rule.getToStatus() != null ? rule.getToStatus().getId() : null,
                rule.getCreatedAt());
    }

    /**
     * Facade natija modeli — yaratilgan workflow transition rule.
     */
    public record WorkflowTransitionRuleCreatedView(
            UUID tenantId,
            UUID workflowDefinitionId,
            UUID transitionRuleId,
            UUID fromStatusId,
            UUID toStatusId,
            java.time.Instant createdAt) {}

    // ========== TelegramChatBinding operations ==========

    /**
     * Yangi chat binding yaratish uchun request boundary validatsiyasi va delegation.
     *
     * @param tenantId tenant identifikatori
     * @param request yaratish so'rovi
     * @return yaratilgan chat binding view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public ChatBindingCreatedView createChatBinding(UUID tenantId,
                                                      CreateChatBindingRequest request,
                                                      UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        if (request.chatId() == null) {
            throw new IllegalArgumentException("chatId null bo'lishi mumkin emas");
        }
        if (request.bindingType() == null || request.bindingType().isBlank()) {
            throw new IllegalArgumentException("bindingType null yoki bo'sh bo'lishi mumkin emas");
        }
        if (!ALLOWED_BINDING_TYPES.contains(request.bindingType())) {
            throw new IllegalArgumentException(
                    "bindingType faqat MAIN_GROUP, NOTIFICATION_GROUP bo'lishi mumkin: " + request.bindingType());
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        TelegramChatBinding binding = commandService.createChatBinding(
                tenantId, request.chatId(), request.chatTitle(),
                ChatBindingType.valueOf(request.bindingType()));

        return new ChatBindingCreatedView(
                binding.getTenantId(),
                binding.getId(),
                binding.getChatId(),
                binding.getChatTitle(),
                binding.getBindingType().name(),
                binding.isActive(),
                binding.getCreatedAt());
    }

    /**
     * Chat binding metadata'sini PATCH yangilash uchun request boundary validatsiyasi va delegation.
     *
     * @param tenantId tenant identifikatori
     * @param chatBindingId chat binding identifikatori
     * @param request yangilash so'rovi
     * @return yangilangan chat binding view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public ChatBindingCreatedView updateChatBinding(UUID tenantId, UUID chatBindingId,
                                                      UpdateChatBindingRequest request,
                                                      UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (chatBindingId == null) {
            throw new IllegalArgumentException("chatBindingId null bo'lishi mumkin emas");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        if (!request.isChatTitleProvided() && !request.isBindingTypeProvided()) {
            throw new IllegalArgumentException("Kamida bitta yangilanuvchi field berilishi kerak");
        }
        if (request.isBindingTypeProvided()) {
            if (request.getBindingType() == null || request.getBindingType().isBlank()) {
                throw new IllegalArgumentException("bindingType null yoki bo'sh bo'lishi mumkin emas");
            }
            if (!ALLOWED_BINDING_TYPES.contains(request.getBindingType())) {
                throw new IllegalArgumentException(
                        "bindingType faqat MAIN_GROUP, NOTIFICATION_GROUP bo'lishi mumkin: "
                                + request.getBindingType());
            }
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        ChatBindingType bindingType = request.isBindingTypeProvided()
                ? ChatBindingType.valueOf(request.getBindingType()) : null;

        TelegramChatBinding binding = commandService.updateChatBinding(
                tenantId, chatBindingId,
                request.getChatTitle(), request.isChatTitleProvided(),
                bindingType, request.isBindingTypeProvided());

        return new ChatBindingCreatedView(
                binding.getTenantId(),
                binding.getId(),
                binding.getChatId(),
                binding.getChatTitle(),
                binding.getBindingType().name(),
                binding.isActive(),
                binding.getCreatedAt());
    }

    /**
     * Chat binding'ni aktiv holatga o'tkazadi.
     *
     * @param tenantId tenant identifikatori
     * @param chatBindingId chat binding identifikatori
     * @return yangilangan chat binding view
     * @throws IllegalArgumentException tenantId yoki chatBindingId null bo'lsa
     */
    public ChatBindingCreatedView activateChatBinding(UUID tenantId, UUID chatBindingId,
                                                        UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (chatBindingId == null) {
            throw new IllegalArgumentException("chatBindingId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        TelegramChatBinding binding = commandService.activateChatBinding(tenantId, chatBindingId);
        return toChatBindingView(binding);
    }

    /**
     * Chat binding'ni noaktiv holatga o'tkazadi.
     *
     * @param tenantId tenant identifikatori
     * @param chatBindingId chat binding identifikatori
     * @return yangilangan chat binding view
     * @throws IllegalArgumentException tenantId yoki chatBindingId null bo'lsa
     */
    public ChatBindingCreatedView deactivateChatBinding(UUID tenantId, UUID chatBindingId,
                                                          UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (chatBindingId == null) {
            throw new IllegalArgumentException("chatBindingId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        TelegramChatBinding binding = commandService.deactivateChatBinding(tenantId, chatBindingId);
        return toChatBindingView(binding);
    }

    /**
     * Chat binding'ni o'chiradi.
     *
     * @param tenantId tenant identifikatori
     * @param chatBindingId chat binding identifikatori
     * @throws IllegalArgumentException tenantId yoki chatBindingId null bo'lsa
     */
    public void deleteChatBinding(UUID tenantId, UUID chatBindingId, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (chatBindingId == null) {
            throw new IllegalArgumentException("chatBindingId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        commandService.deleteChatBinding(tenantId, chatBindingId);
    }

    private ChatBindingCreatedView toChatBindingView(TelegramChatBinding binding) {
        return new ChatBindingCreatedView(
                binding.getTenantId(),
                binding.getId(),
                binding.getChatId(),
                binding.getChatTitle(),
                binding.getBindingType().name(),
                binding.isActive(),
                binding.getCreatedAt());
    }

    /**
     * Facade natija modeli — yaratilgan chat binding.
     */
    public record ChatBindingCreatedView(
            UUID tenantId,
            UUID chatBindingId,
            long chatId,
            String chatTitle,
            String bindingType,
            boolean active,
            java.time.Instant createdAt) {}

    // ========== TelegramTopicBinding operations ==========

    /**
     * Yangi topic binding yaratish uchun request boundary validatsiyasi va delegation.
     *
     * @param tenantId tenant identifikatori
     * @param request yaratish so'rovi
     * @return yaratilgan topic binding view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public TopicBindingView createTopicBinding(UUID tenantId,
                                                 CreateTopicBindingRequest request,
                                                 UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        if (request.chatBindingId() == null) {
            throw new IllegalArgumentException("chatBindingId null bo'lishi mumkin emas");
        }
        if (request.topicId() == null) {
            throw new IllegalArgumentException("topicId null bo'lishi mumkin emas");
        }
        if (request.purpose() == null || request.purpose().isBlank()) {
            throw new IllegalArgumentException("purpose null yoki bo'sh bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        TelegramTopicBinding binding = commandService.createTopicBinding(
                tenantId, request.chatBindingId(), request.topicId(),
                request.topicName(), request.purpose());

        return toTopicBindingView(tenantId, binding);
    }

    /**
     * Topic binding metadata'sini PATCH yangilash uchun request boundary validatsiyasi va delegation.
     *
     * @param tenantId tenant identifikatori
     * @param topicBindingId topic binding identifikatori
     * @param request yangilash so'rovi
     * @return yangilangan topic binding view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public TopicBindingView updateTopicBinding(UUID tenantId, UUID topicBindingId,
                                                 UpdateTopicBindingRequest request,
                                                 UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (topicBindingId == null) {
            throw new IllegalArgumentException("topicBindingId null bo'lishi mumkin emas");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        if (!request.isTopicNameProvided()) {
            throw new IllegalArgumentException("Kamida bitta yangilanuvchi field berilishi kerak");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        TelegramTopicBinding binding = commandService.updateTopicBinding(
                tenantId, topicBindingId,
                request.getTopicName(), request.isTopicNameProvided());

        return toTopicBindingView(tenantId, binding);
    }

    /**
     * Topic binding'ni aktiv holatga o'tkazadi.
     *
     * @param tenantId tenant identifikatori
     * @param topicBindingId topic binding identifikatori
     * @return yangilangan topic binding view
     * @throws IllegalArgumentException tenantId yoki topicBindingId null bo'lsa
     */
    public TopicBindingView activateTopicBinding(UUID tenantId, UUID topicBindingId,
                                                    UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (topicBindingId == null) {
            throw new IllegalArgumentException("topicBindingId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        TelegramTopicBinding binding = commandService.activateTopicBinding(tenantId, topicBindingId);
        return toTopicBindingView(tenantId, binding);
    }

    /**
     * Topic binding'ni noaktiv holatga o'tkazadi.
     *
     * @param tenantId tenant identifikatori
     * @param topicBindingId topic binding identifikatori
     * @return yangilangan topic binding view
     * @throws IllegalArgumentException tenantId yoki topicBindingId null bo'lsa
     */
    public TopicBindingView deactivateTopicBinding(UUID tenantId, UUID topicBindingId,
                                                      UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (topicBindingId == null) {
            throw new IllegalArgumentException("topicBindingId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        TelegramTopicBinding binding = commandService.deactivateTopicBinding(tenantId, topicBindingId);
        return toTopicBindingView(tenantId, binding);
    }

    /**
     * Topic binding'ni o'chiradi.
     *
     * @param tenantId tenant identifikatori
     * @param topicBindingId topic binding identifikatori
     * @throws IllegalArgumentException tenantId yoki topicBindingId null bo'lsa
     */
    public void deleteTopicBinding(UUID tenantId, UUID topicBindingId, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (topicBindingId == null) {
            throw new IllegalArgumentException("topicBindingId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        commandService.deleteTopicBinding(tenantId, topicBindingId);
    }

    private TopicBindingView toTopicBindingView(UUID tenantId, TelegramTopicBinding binding) {
        UUID chatBindingId = binding.getChatBinding() != null ? binding.getChatBinding().getId() : null;
        return new TopicBindingView(
                tenantId,
                binding.getId(),
                chatBindingId,
                binding.getTopicId(),
                binding.getTopicName(),
                binding.getPurpose(),
                binding.isActive(),
                binding.getCreatedAt());
    }

    /**
     * Facade natija modeli — topic binding write natijasi.
     */
    public record TopicBindingView(
            UUID tenantId,
            UUID topicBindingId,
            UUID chatBindingId,
            long topicId,
            String topicName,
            String purpose,
            boolean active,
            java.time.Instant createdAt) {}

    // ========== RoutingRule operations ==========

    /**
     * Yangi routing rule yaratish uchun request boundary validatsiyasi va delegation.
     *
     * @param tenantId tenant identifikatori
     * @param request yaratish so'rovi
     * @return yaratilgan routing rule view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public RoutingRuleCreatedView createRoutingRule(UUID tenantId,
                                                     CreateRoutingRuleRequest request,
                                                     UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("name null yoki bo'sh bo'lishi mumkin emas");
        }
        if (request.workItemType() == null || request.workItemType().isBlank()) {
            throw new IllegalArgumentException("workItemType null yoki bo'sh bo'lishi mumkin emas");
        }
        if (!ALLOWED_WORK_ITEM_TYPES.contains(request.workItemType())) {
            throw new IllegalArgumentException(
                    "workItemType faqat BUG, INCIDENT, TASK bo'lishi mumkin: " + request.workItemType());
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        RoutingRule rule = commandService.createRoutingRule(
                tenantId, request.name(), request.workItemType(),
                request.priority(), request.targetTopicBindingId(),
                request.conditionExpression());

        return new RoutingRuleCreatedView(
                rule.getTenantId(),
                rule.getId(),
                rule.getName(),
                rule.getWorkItemType(),
                rule.getPriority(),
                rule.getTargetTopicBindingId(),
                rule.isActive(),
                rule.getCreatedAt());
    }

    /**
     * Mavjud routing rule metadata'sini PATCH yangilash uchun request boundary validatsiyasi va delegation.
     *
     * PATCH semantikasi:
     * - faqat JSON'da mavjud field'lar yangilanadi
     * - kamida bitta field berilishi kerak
     * - name berilsa, blank bo'lmasligi kerak
     * - targetTopicBindingId berilmasa o'zgarmaydi, explicit null berilsa tozalanadi
     * - conditionExpression berilmasa o'zgarmaydi, null/blank berilsa tozalanadi
     * - priority berilmasa o'zgarmaydi
     *
     * @param tenantId tenant identifikatori
     * @param ruleId routing rule identifikatori
     * @param request yangilash so'rovi
     * @return yangilangan routing rule view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public RoutingRuleUpdatedView updateRoutingRule(UUID tenantId, UUID ruleId,
                                                      UpdateRoutingRuleRequest request,
                                                      UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (ruleId == null) {
            throw new IllegalArgumentException("ruleId null bo'lishi mumkin emas");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        if (!request.isNameProvided() && !request.isPriorityProvided()
                && !request.isTargetTopicBindingIdProvided() && !request.isConditionExpressionProvided()) {
            throw new IllegalArgumentException("Kamida bitta yangilanuvchi field berilishi kerak");
        }
        if (request.isNameProvided() && (request.getName() == null || request.getName().isBlank())) {
            throw new IllegalArgumentException("name null yoki bo'sh bo'lishi mumkin emas");
        }
        if (request.isPriorityProvided() && request.getPriority() == null) {
            throw new IllegalArgumentException("priority null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        RoutingRule rule = commandService.updateRoutingRule(
                tenantId, ruleId,
                request.getName(), request.isNameProvided(),
                request.getPriority(), request.isPriorityProvided(),
                request.getTargetTopicBindingId(), request.isTargetTopicBindingIdProvided(),
                request.getConditionExpression(), request.isConditionExpressionProvided());

        return new RoutingRuleUpdatedView(
                rule.getTenantId(),
                rule.getId(),
                rule.getName(),
                rule.getPriority(),
                rule.getTargetTopicBindingId(),
                rule.getConditionExpression(),
                rule.isActive(),
                rule.getCreatedAt());
    }

    /**
     * Routing rule'ni aktiv holatga o'tkazadi.
     *
     * @param tenantId tenant identifikatori
     * @param ruleId routing rule identifikatori
     * @return yangilangan routing rule view
     * @throws IllegalArgumentException tenantId yoki ruleId null bo'lsa
     */
    public RoutingRuleUpdatedView activateRoutingRule(UUID tenantId, UUID ruleId,
                                                        UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (ruleId == null) {
            throw new IllegalArgumentException("ruleId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        RoutingRule rule = commandService.activateRoutingRule(tenantId, ruleId);
        return toRoutingRuleUpdatedView(rule);
    }

    /**
     * Routing rule'ni noaktiv holatga o'tkazadi.
     *
     * @param tenantId tenant identifikatori
     * @param ruleId routing rule identifikatori
     * @return yangilangan routing rule view
     * @throws IllegalArgumentException tenantId yoki ruleId null bo'lsa
     */
    public RoutingRuleUpdatedView deactivateRoutingRule(UUID tenantId, UUID ruleId,
                                                          UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (ruleId == null) {
            throw new IllegalArgumentException("ruleId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        RoutingRule rule = commandService.deactivateRoutingRule(tenantId, ruleId);
        return toRoutingRuleUpdatedView(rule);
    }

    /**
     * Routing rule'ni o'chiradi.
     *
     * @param tenantId tenant identifikatori
     * @param ruleId routing rule identifikatori
     * @throws IllegalArgumentException tenantId yoki ruleId null bo'lsa
     */
    public void deleteRoutingRule(UUID tenantId, UUID ruleId, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (ruleId == null) {
            throw new IllegalArgumentException("ruleId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        commandService.deleteRoutingRule(tenantId, ruleId);
    }

    private RoutingRuleUpdatedView toRoutingRuleUpdatedView(RoutingRule rule) {
        return new RoutingRuleUpdatedView(
                rule.getTenantId(),
                rule.getId(),
                rule.getName(),
                rule.getPriority(),
                rule.getTargetTopicBindingId(),
                rule.getConditionExpression(),
                rule.isActive(),
                rule.getCreatedAt());
    }

    /**
     * Facade natija modeli — yangilangan routing rule.
     */
    public record RoutingRuleUpdatedView(
            UUID tenantId,
            UUID ruleId,
            String name,
            int priority,
            UUID targetTopicBindingId,
            String conditionExpression,
            boolean active,
            java.time.Instant createdAt) {}

    /**
     * Facade natija modeli — yaratilgan routing rule.
     */
    public record RoutingRuleCreatedView(
            UUID tenantId,
            UUID ruleId,
            String name,
            String workItemType,
            int priority,
            UUID targetTopicBindingId,
            boolean active,
            java.time.Instant createdAt) {}

    /**
     * Facade natija modeli — yangilangan workflow definition.
     */
    public record WorkflowDefinitionUpdatedView(
            UUID tenantId,
            UUID definitionId,
            String name,
            String workItemType,
            String description,
            boolean active,
            java.time.Instant createdAt) {}

    /**
     * Facade natija modeli — yaratilgan workflow definition.
     */
    public record WorkflowDefinitionCreatedView(
            UUID tenantId,
            UUID definitionId,
            String name,
            String workItemType,
            String description,
            boolean active,
            java.time.Instant createdAt) {}

    // ========== Membership lifecycle ==========

    /**
     * Mavjud foydalanuvchi uchun tenantda yangi a'zolik yaratadi.
     *
     * @param tenantId tenant identifikatori
     * @param request yaratish so'rovi (userId bilan)
     * @return yaratilgan membership view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public MembershipStatusView createMembership(UUID tenantId, CreateMembershipRequest request,
                                                    UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        if (request.userId() == null) {
            throw new IllegalArgumentException("userId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        Membership membership = identityCommandService.createMembership(tenantId, request.userId());
        return toMembershipStatusView(membership);
    }

    /**
     * A'zolikni aktiv holatga o'tkazadi.
     *
     * @param tenantId tenant identifikatori
     * @param membershipId a'zolik identifikatori
     * @return yangilangan membership view
     * @throws IllegalArgumentException tenantId yoki membershipId null bo'lsa
     */
    public MembershipStatusView activateMembership(UUID tenantId, UUID membershipId,
                                                      UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (membershipId == null) {
            throw new IllegalArgumentException("membershipId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        Membership membership = identityCommandService.activateMembership(tenantId, membershipId);
        return toMembershipStatusView(membership);
    }

    /**
     * A'zolikni SUSPENDED holatga o'tkazadi.
     *
     * @param tenantId tenant identifikatori
     * @param membershipId a'zolik identifikatori
     * @return yangilangan membership view
     * @throws IllegalArgumentException tenantId yoki membershipId null bo'lsa
     */
    public MembershipStatusView suspendMembership(UUID tenantId, UUID membershipId,
                                                     UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (membershipId == null) {
            throw new IllegalArgumentException("membershipId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        Membership membership = identityCommandService.suspendMembership(tenantId, membershipId);
        return toMembershipStatusView(membership);
    }

    /**
     * A'zolikni REMOVED holatga o'tkazadi (lifecycle status transition).
     *
     * @param tenantId tenant identifikatori
     * @param membershipId a'zolik identifikatori
     * @return yangilangan membership view
     * @throws IllegalArgumentException tenantId yoki membershipId null bo'lsa
     */
    public MembershipStatusView removeMembership(UUID tenantId, UUID membershipId,
                                                    UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (membershipId == null) {
            throw new IllegalArgumentException("membershipId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        Membership membership = identityCommandService.removeMembership(tenantId, membershipId);
        return toMembershipStatusView(membership);
    }

    private MembershipStatusView toMembershipStatusView(Membership membership) {
        return new MembershipStatusView(
                membership.getTenantId(),
                membership.getId(),
                membership.getUserId(),
                membership.getStatus().name(),
                membership.getCreatedAt());
    }

    /**
     * Facade natija modeli — membership status o'zgarishi.
     */
    public record MembershipStatusView(
            UUID tenantId,
            UUID membershipId,
            UUID userId,
            String status,
            java.time.Instant createdAt) {}

    // ========== MembershipRoleBinding lifecycle ==========

    /**
     * A'zolikka global rolni tayinlaydi.
     *
     * @param tenantId tenant identifikatori
     * @param membershipId a'zolik identifikatori
     * @param request yaratish so'rovi (roleId bilan)
     * @return yaratilgan binding view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public MembershipRoleBindingView assignRoleToMembership(UUID tenantId, UUID membershipId,
                                                              CreateMembershipRoleBindingRequest request,
                                                              UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (membershipId == null) {
            throw new IllegalArgumentException("membershipId null bo'lishi mumkin emas");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        if (request.roleId() == null) {
            throw new IllegalArgumentException("roleId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        MembershipRoleBinding binding = identityCommandService.assignRoleToMembership(
                tenantId, membershipId, request.roleId());

        return toMembershipRoleBindingView(tenantId, membershipId, binding);
    }

    /**
     * A'zolikdan rolni olib tashlaydi.
     *
     * @param tenantId tenant identifikatori
     * @param membershipId a'zolik identifikatori
     * @param roleId rol identifikatori
     * @throws IllegalArgumentException tenantId, membershipId yoki roleId null bo'lsa
     */
    public void unassignRoleFromMembership(UUID tenantId, UUID membershipId, UUID roleId,
                                             UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (membershipId == null) {
            throw new IllegalArgumentException("membershipId null bo'lishi mumkin emas");
        }
        if (roleId == null) {
            throw new IllegalArgumentException("roleId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        identityCommandService.unassignRoleFromMembership(tenantId, membershipId, roleId);
    }

    private MembershipRoleBindingView toMembershipRoleBindingView(UUID tenantId, UUID membershipId,
                                                                    MembershipRoleBinding binding) {
        UUID roleId = binding.getRole() != null ? binding.getRole().getId() : null;
        String roleCode = binding.getRole() != null ? binding.getRole().getCode() : null;
        return new MembershipRoleBindingView(
                tenantId,
                membershipId,
                binding.getId(),
                roleId,
                roleCode,
                binding.getCreatedAt());
    }

    /**
     * Facade natija modeli — membership-role binding.
     */
    public record MembershipRoleBindingView(
            UUID tenantId,
            UUID membershipId,
            UUID bindingId,
            UUID roleId,
            String roleCode,
            java.time.Instant createdAt) {}

    // ========== Global Role catalog operations ==========

    /**
     * Global rol katalogida yangi rol yaratadi.
     *
     * @param request yaratish so'rovi (code, name, description)
     * @return yaratilgan rol view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public RoleCatalogView createRole(UUID tenantId, CreateRoleRequest request, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        if (request.code() == null || request.code().isBlank()) {
            throw new IllegalArgumentException("code null yoki bo'sh bo'lishi mumkin emas");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("name null yoki bo'sh bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        Role role = identityCommandService.createRole(
                request.code(), request.name(), request.description());

        return toRoleCatalogView(role);
    }

    /**
     * Global rol metadata'sini PATCH yangilash.
     *
     * @param roleId rol identifikatori
     * @param request yangilash so'rovi
     * @return yangilangan rol view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public RoleCatalogView updateRole(UUID tenantId, UUID roleId, UpdateRoleRequest request,
                                       UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (roleId == null) {
            throw new IllegalArgumentException("roleId null bo'lishi mumkin emas");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        if (!request.isNameProvided() && !request.isDescriptionProvided()) {
            throw new IllegalArgumentException("Kamida bitta yangilanuvchi field berilishi kerak");
        }
        if (request.isNameProvided() && (request.getName() == null || request.getName().isBlank())) {
            throw new IllegalArgumentException("name null yoki bo'sh bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        Role role = identityCommandService.updateRole(
                roleId,
                request.getName(), request.isNameProvided(),
                request.getDescription(), request.isDescriptionProvided());

        return toRoleCatalogView(role);
    }

    /**
     * Global rolni aktiv holatga o'tkazadi.
     *
     * @param roleId rol identifikatori
     * @return yangilangan rol view
     * @throws IllegalArgumentException roleId null bo'lsa
     */
    public RoleCatalogView activateRole(UUID tenantId, UUID roleId, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (roleId == null) {
            throw new IllegalArgumentException("roleId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        Role role = identityCommandService.activateRole(roleId);
        return toRoleCatalogView(role);
    }

    /**
     * Global rolni noaktiv holatga o'tkazadi.
     *
     * @param roleId rol identifikatori
     * @return yangilangan rol view
     * @throws IllegalArgumentException roleId null bo'lsa
     */
    public RoleCatalogView deactivateRole(UUID tenantId, UUID roleId, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (roleId == null) {
            throw new IllegalArgumentException("roleId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        Role role = identityCommandService.deactivateRole(roleId);
        return toRoleCatalogView(role);
    }

    /**
     * Global rolni o'chiradi.
     *
     * @param roleId rol identifikatori
     * @throws IllegalArgumentException roleId null bo'lsa
     */
    public void deleteRole(UUID tenantId, UUID roleId, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (roleId == null) {
            throw new IllegalArgumentException("roleId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        identityCommandService.deleteRole(roleId);
    }

    // ========== RolePermission operations ==========

    /**
     * Global rolga ruxsat tayinlaydi (role-permission binding yaratadi).
     *
     * @param tenantId tenant identifikatori (authorization uchun)
     * @param roleId rol identifikatori
     * @param request yaratish so'rovi (permissionId)
     * @param actorUserId joriy actor identifikatori
     * @return yaratilgan role-permission binding view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public RolePermissionView assignPermissionToRole(UUID tenantId, UUID roleId,
                                                       CreateRolePermissionRequest request,
                                                       UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (roleId == null) {
            throw new IllegalArgumentException("roleId null bo'lishi mumkin emas");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        if (request.permissionId() == null) {
            throw new IllegalArgumentException("permissionId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        RolePermission binding = identityCommandService.assignPermissionToRole(
                roleId, request.permissionId());

        return toRolePermissionView(binding);
    }

    private RolePermissionView toRolePermissionView(RolePermission binding) {
        UUID roleId = binding.getRole() != null ? binding.getRole().getId() : null;
        String roleCode = binding.getRole() != null ? binding.getRole().getCode() : null;
        UUID permissionId = binding.getPermission() != null ? binding.getPermission().getId() : null;
        String permissionCode = binding.getPermission() != null ? binding.getPermission().getCode() : null;
        return new RolePermissionView(
                binding.getId(),
                roleId,
                roleCode,
                permissionId,
                permissionCode,
                binding.getCreatedAt());
    }

    /**
     * Facade natija modeli — role-permission binding.
     */
    public record RolePermissionView(
            UUID bindingId,
            UUID roleId,
            String roleCode,
            UUID permissionId,
            String permissionCode,
            java.time.Instant createdAt) {}

    /**
     * Global roldan ruxsatni olib tashlaydi.
     *
     * @param tenantId tenant identifikatori (authorization uchun)
     * @param roleId rol identifikatori
     * @param permissionId ruxsat identifikatori
     * @param actorUserId joriy actor identifikatori
     * @throws IllegalArgumentException tenantId, roleId yoki permissionId null bo'lsa
     */
    public void unassignPermissionFromRole(UUID tenantId, UUID roleId, UUID permissionId,
                                             UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (roleId == null) {
            throw new IllegalArgumentException("roleId null bo'lishi mumkin emas");
        }
        if (permissionId == null) {
            throw new IllegalArgumentException("permissionId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeWrite(tenantId, actorUserId);

        identityCommandService.unassignPermissionFromRole(roleId, permissionId);
    }

    private RoleCatalogView toRoleCatalogView(Role role) {
        return new RoleCatalogView(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.isSystemRole(),
                role.isActive(),
                role.getCreatedAt());
    }

    /**
     * Facade natija modeli — global rol.
     */
    public record RoleCatalogView(
            UUID roleId,
            String code,
            String name,
            String description,
            boolean systemRole,
            boolean active,
            java.time.Instant createdAt) {}
}
