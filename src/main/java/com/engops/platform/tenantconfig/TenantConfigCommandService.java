package com.engops.platform.tenantconfig;

import com.engops.platform.audit.AuditService;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.tenantconfig.model.ChatBindingType;
import com.engops.platform.tenantconfig.model.RoutingRule;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.model.TelegramChatBinding;
import com.engops.platform.tenantconfig.model.TelegramTopicBinding;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.model.WorkflowStatus;
import com.engops.platform.tenantconfig.model.WorkflowTransitionRule;
import com.engops.platform.tenantconfig.repository.RoutingRuleRepository;
import com.engops.platform.tenantconfig.repository.TelegramChatBindingRepository;
import com.engops.platform.tenantconfig.repository.TelegramTopicBindingRepository;
import com.engops.platform.tenantconfig.repository.TenantRepository;
import com.engops.platform.tenantconfig.repository.WorkflowDefinitionRepository;
import com.engops.platform.tenantconfig.repository.WorkflowStatusRepository;
import com.engops.platform.tenantconfig.repository.WorkflowTransitionRuleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Tenant konfiguratsiyasi uchun buyruq (command) servisi — yaratish, o'zgartirish operatsiyalari.
 *
 * Cross-module bog'lanishlar:
 * - AuditService — audit yozish uchun (public API)
 *
 * Muhim:
 * - Faqat tenant-config module'ning o'z repository'laridan foydalanadi
 * - Read-only operatsiyalar TenantConfigQueryService orqali
 * - Bu servis write tranzaksiyasini own qiladi
 */
@Service
@Transactional
public class TenantConfigCommandService {

    private final TenantRepository tenantRepository;
    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final WorkflowTransitionRuleRepository workflowTransitionRuleRepository;
    private final RoutingRuleRepository routingRuleRepository;
    private final TelegramChatBindingRepository telegramChatBindingRepository;
    private final TelegramTopicBindingRepository telegramTopicBindingRepository;
    private final AuditService auditService;

    public TenantConfigCommandService(TenantRepository tenantRepository,
                                       WorkflowDefinitionRepository workflowDefinitionRepository,
                                       WorkflowStatusRepository workflowStatusRepository,
                                       WorkflowTransitionRuleRepository workflowTransitionRuleRepository,
                                       RoutingRuleRepository routingRuleRepository,
                                       TelegramChatBindingRepository telegramChatBindingRepository,
                                       TelegramTopicBindingRepository telegramTopicBindingRepository,
                                       AuditService auditService) {
        this.tenantRepository = tenantRepository;
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.workflowStatusRepository = workflowStatusRepository;
        this.workflowTransitionRuleRepository = workflowTransitionRuleRepository;
        this.routingRuleRepository = routingRuleRepository;
        this.telegramChatBindingRepository = telegramChatBindingRepository;
        this.telegramTopicBindingRepository = telegramTopicBindingRepository;
        this.auditService = auditService;
    }

    // ========== Tenant operations ==========

    /**
     * Yangi tenant yaratadi.
     *
     * Caller (TenantConfigWriteFacade) input'larni allaqachon normallashtirgan
     * deb taxmin qilinadi (Phase 115 mini-fix bilan o'rnatilgan pattern):
     * facade boundary'da strip + lowercase(slug) + length cap + default
     * timezone bajariladi. Bu service'da takroriy normalizatsiya qilinmaydi —
     * yagona caller pattern'i bo'lsa, defensive in-service normalizatsiya
     * ortiqcha bo'ladi va Phase 115 createWorkflowStatus + Phase 116
     * createWorkflowTransitionRule sxemasiga zid bo'ladi.
     *
     * Validatsiyalar:
     * 1. Tenant slug global ravishda unikal bo'lishi kerak
     *    (DB constraint: UNIQUE on tenant.slug — pre-check + DB fallback)
     *
     * Authorization: TenantConfigWriteFacade'da admin-context tenantId orqali
     * TENANT_CONFIG_WRITE tekshiriladi (yangi yaratiladigan tenant emas).
     * Bu yangi tenant root resurs bo'lib, mavjud admin context tenantning
     * a'zosiga ruxsat beradi (role catalog write surface bilan bir xil
     * pattern). Birinchi tenant uchun chicken-and-egg muammosi qoladi —
     * u faqat manual SQL/Flyway seed orqali yechiladi.
     *
     * Audit: tenantId argumenti `null` — yaratilgan tenant o'zi root resurs.
     * Bu role catalog audit shape bilan bir xil (ROLE/CREATED ham tenantId=null).
     *
     * @param name normallashgan tenant nomi (non-blank, max 255)
     * @param slug normallashgan slug (lowercase, non-blank, max 100, unikal)
     * @param timezone normallashgan timezone (non-blank, max 50; facade'da "UTC" default)
     * @return yaratilgan Tenant
     * @throws BusinessRuleException agar slug allaqachon mavjud bo'lsa
     */
    public Tenant createTenant(String name, String slug, String timezone) {
        if (tenantRepository.existsBySlug(slug)) {
            throw new BusinessRuleException("DUPLICATE_TENANT_SLUG",
                    "'" + slug + "' slug bilan tenant allaqachon mavjud");
        }

        Tenant tenant = new Tenant(name, slug);
        tenant.setTimezone(timezone);

        try {
            tenant = tenantRepository.save(tenant);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateTenantSlugConstraint(ex)) {
                throw new BusinessRuleException("DUPLICATE_TENANT_SLUG",
                        "'" + slug + "' slug bilan tenant allaqachon mavjud");
            }
            throw ex;
        }

        auditService.recordEvent(null, "TENANT", tenant.getId(),
                "CREATED", null, "ADMIN_API", null, slug);

        return tenant;
    }

    /**
     * Yangi workflow definition yaratadi.
     *
     * Validatsiyalar:
     * 1. Tenant mavjud bo'lishi kerak
     * 2. Tenant ichida workflow nomi unikal bo'lishi kerak
     *
     * @param tenantId tenant identifikatori
     * @param name workflow nomi
     * @param workItemType work item turi (BUG, INCIDENT, TASK)
     * @param description ixtiyoriy tavsif (nullable)
     * @return yaratilgan WorkflowDefinition
     * @throws ResourceNotFoundException agar tenant topilmasa
     * @throws BusinessRuleException agar shu nomli workflow allaqachon mavjud bo'lsa
     */
    public WorkflowDefinition createWorkflowDefinition(UUID tenantId, String name,
                                                         String workItemType, String description) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        workflowDefinitionRepository.findByTenantIdAndName(tenantId, name)
                .ifPresent(existing -> {
                    throw new BusinessRuleException("DUPLICATE_WORKFLOW_NAME",
                            "Tenant ichida '" + name + "' nomli workflow allaqachon mavjud");
                });

        WorkflowDefinition definition = new WorkflowDefinition(tenantId, name, workItemType);
        if (description != null && !description.isBlank()) {
            definition.setDescription(description);
        }

        try {
            definition = workflowDefinitionRepository.save(definition);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateNameConstraint(ex)) {
                throw new BusinessRuleException("DUPLICATE_WORKFLOW_NAME",
                        "Tenant ichida '" + name + "' nomli workflow allaqachon mavjud");
            }
            throw ex;
        }

        auditService.recordEvent(tenantId, "WORKFLOW_DEFINITION", definition.getId(),
                "CREATED", null, "ADMIN_API", null, name);

        return definition;
    }

    /**
     * Workflow definition metadata'sini partial yangilaydi (PATCH semantikasi).
     *
     * Faqat provided=true field'lar yangilanadi:
     * - nameProvided=false → nom o'zgarmaydi
     * - nameProvided=true → yangi nom o'rnatiladi, duplicate check bajariladi
     * - descriptionProvided=false → tavsif o'zgarmaydi
     * - descriptionProvided=true, description null/blank → tavsif tozalanadi
     * - descriptionProvided=true, description non-blank → tavsif yangilanadi
     *
     * @param tenantId tenant identifikatori
     * @param definitionId workflow definition identifikatori
     * @param name yangi nom (faqat nameProvided=true bo'lganda ishlatiladi)
     * @param nameProvided name field JSON'da berilganmi
     * @param description yangi tavsif (faqat descriptionProvided=true bo'lganda ishlatiladi)
     * @param descriptionProvided description field JSON'da berilganmi
     * @return yangilangan WorkflowDefinition
     * @throws ResourceNotFoundException agar tenant yoki workflow definition topilmasa
     * @throws BusinessRuleException agar shu nomli workflow allaqachon mavjud bo'lsa
     */
    public WorkflowDefinition updateWorkflowDefinition(UUID tenantId, UUID definitionId,
                                                         String name, boolean nameProvided,
                                                         String description, boolean descriptionProvided) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        WorkflowDefinition definition = workflowDefinitionRepository.findByTenantIdAndId(tenantId, definitionId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowDefinition", definitionId));

        String oldName = definition.getName();
        String oldDescription = definition.getDescription();

        if (nameProvided) {
            if (!name.equals(definition.getName())) {
                workflowDefinitionRepository.findByTenantIdAndName(tenantId, name)
                        .ifPresent(existing -> {
                            throw new BusinessRuleException("DUPLICATE_WORKFLOW_NAME",
                                    "Tenant ichida '" + name + "' nomli workflow allaqachon mavjud");
                        });
            }
            definition.setName(name);
        }

        if (descriptionProvided) {
            definition.setDescription(description != null && !description.isBlank() ? description : null);
        }

        try {
            definition = workflowDefinitionRepository.save(definition);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateNameConstraint(ex)) {
                String failedName = nameProvided ? name : definition.getName();
                throw new BusinessRuleException("DUPLICATE_WORKFLOW_NAME",
                        "Tenant ichida '" + failedName + "' nomli workflow allaqachon mavjud");
            }
            throw ex;
        }

        String newName = definition.getName();
        String newDescription = definition.getDescription();
        String oldValue = oldName + (oldDescription != null ? " | " + oldDescription : "");
        String newValue = newName + (newDescription != null ? " | " + newDescription : "");

        auditService.recordEvent(tenantId, "WORKFLOW_DEFINITION", definition.getId(),
                "UPDATED", null, "ADMIN_API", oldValue, newValue);

        return definition;
    }

    /**
     * Workflow definition'ni aktiv holatga o'tkazadi.
     *
     * Idempotent: allaqachon aktiv bo'lsa, hech narsa o'zgarmaydi.
     *
     * @param tenantId tenant identifikatori
     * @param definitionId workflow definition identifikatori
     * @return yangilangan WorkflowDefinition
     * @throws ResourceNotFoundException agar tenant yoki workflow definition topilmasa
     */
    public WorkflowDefinition activateWorkflowDefinition(UUID tenantId, UUID definitionId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        WorkflowDefinition definition = workflowDefinitionRepository.findByTenantIdAndId(tenantId, definitionId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowDefinition", definitionId));

        if (definition.isActive()) {
            return definition;
        }

        definition.setActive(true);
        definition = workflowDefinitionRepository.save(definition);

        auditService.recordEvent(tenantId, "WORKFLOW_DEFINITION", definition.getId(),
                "ACTIVATED", null, "ADMIN_API", "false", "true");

        return definition;
    }

    /**
     * Workflow definition'ni noaktiv holatga o'tkazadi.
     *
     * Idempotent: allaqachon noaktiv bo'lsa, hech narsa o'zgarmaydi.
     *
     * @param tenantId tenant identifikatori
     * @param definitionId workflow definition identifikatori
     * @return yangilangan WorkflowDefinition
     * @throws ResourceNotFoundException agar tenant yoki workflow definition topilmasa
     */
    public WorkflowDefinition deactivateWorkflowDefinition(UUID tenantId, UUID definitionId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        WorkflowDefinition definition = workflowDefinitionRepository.findByTenantIdAndId(tenantId, definitionId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowDefinition", definitionId));

        if (!definition.isActive()) {
            return definition;
        }

        definition.setActive(false);
        definition = workflowDefinitionRepository.save(definition);

        auditService.recordEvent(tenantId, "WORKFLOW_DEFINITION", definition.getId(),
                "DEACTIVATED", null, "ADMIN_API", "true", "false");

        return definition;
    }

    /**
     * Workflow definition'ni o'chiradi (hard delete).
     *
     * @param tenantId tenant identifikatori
     * @param definitionId workflow definition identifikatori
     * @throws ResourceNotFoundException agar tenant yoki workflow definition topilmasa
     */
    public void deleteWorkflowDefinition(UUID tenantId, UUID definitionId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        WorkflowDefinition definition = workflowDefinitionRepository.findByTenantIdAndId(tenantId, definitionId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowDefinition", definitionId));

        String oldValue = definition.getName() + " | type=" + definition.getWorkItemType();

        // FK violation -> clean 422. Workflow definition uchun 3 ta inbound FK
        // mavjud (work_item, workflow_status, workflow_transition_rule).
        // Cross-module pre-check qilish workitem modulga sun'iy bog'lanish hosil
        // qilar edi, shuning uchun Phase 78 (deleteRole) fallback patterni bilan
        // bir xil DB constraint translation ishlatiladi.
        try {
            workflowDefinitionRepository.delete(definition);
            workflowDefinitionRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            if (isWorkflowDefinitionReferencedConstraint(ex)) {
                throw new BusinessRuleException("WORKFLOW_DEFINITION_IN_USE",
                        "Workflow definition hozirda ishlatilmoqda, o'chirilmaydi "
                                + "(definitionId=" + definitionId + ")");
            }
            throw ex;
        }

        auditService.recordEvent(tenantId, "WORKFLOW_DEFINITION", definitionId,
                "DELETED", null, "ADMIN_API", oldValue, null);
    }

    // ========== WorkflowStatus operations ==========

    /**
     * Mavjud workflow definition uchun yangi status (holat) yaratadi.
     *
     * Validatsiyalar:
     * 1. Tenant mavjud bo'lishi kerak
     * 2. Workflow definition shu tenantga tegishli bo'lishi kerak (tenant-safe lookup)
     * 3. Shu workflow definition ichida nom unikal bo'lishi kerak
     *    (DB constraint: UNIQUE (workflow_definition_id, name) — pre-check + DB fallback)
     * 4. Agar initial=true bo'lsa, shu workflow definition'da boshqa initial status
     *    bo'lmasligi kerak (faqat application-level — DB partial unique index YO'Q)
     *
     * Concurrency:
     * - Duplicate status nomi uchun DB unique constraint ham himoya beradi
     * - Duplicate initial uchun faqat application-level pre-check — concurrent
     *   ikkita create initial=true rivoj qilinsa, ikkalasi ham DB ga tushishi mumkin.
     *   Bu phase qabul qilingan trade-off; haqiqiy partial index DB-level
     *   hardening keyingi alohida migration phase'iga qoldirilgan.
     *
     * @param tenantId tenant identifikatori
     * @param workflowDefinitionId workflow definition identifikatori
     * @param name status nomi
     * @param statusOrder status tartibi (>= 0)
     * @param initial boshlang'ich status flag'i
     * @param terminal yakuniy status flag'i
     * @return yaratilgan WorkflowStatus
     * @throws ResourceNotFoundException agar tenant yoki workflow definition topilmasa
     * @throws BusinessRuleException agar nom takrorlansa yoki ikkinchi initial bo'lsa
     */
    public WorkflowStatus createWorkflowStatus(UUID tenantId, UUID workflowDefinitionId,
                                                 String name, int statusOrder,
                                                 boolean initial, boolean terminal) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        WorkflowDefinition definition = workflowDefinitionRepository
                .findByTenantIdAndId(tenantId, workflowDefinitionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "WorkflowDefinition", workflowDefinitionId));

        if (workflowStatusRepository.existsByWorkflowDefinition_IdAndName(workflowDefinitionId, name)) {
            throw new BusinessRuleException("DUPLICATE_WORKFLOW_STATUS_NAME",
                    "Workflow definition ichida '" + name + "' nomli status allaqachon mavjud");
        }

        if (initial && workflowStatusRepository
                .existsByWorkflowDefinition_IdAndInitialTrue(workflowDefinitionId)) {
            throw new BusinessRuleException("DUPLICATE_INITIAL_STATUS",
                    "Workflow definition uchun boshlang'ich status allaqachon belgilangan "
                            + "(definitionId=" + workflowDefinitionId + ")");
        }

        WorkflowStatus status = new WorkflowStatus(definition, name, statusOrder, initial, terminal);

        try {
            status = workflowStatusRepository.save(status);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateWorkflowStatusNameConstraint(ex)) {
                throw new BusinessRuleException("DUPLICATE_WORKFLOW_STATUS_NAME",
                        "Workflow definition ichida '" + name + "' nomli status allaqachon mavjud");
            }
            throw ex;
        }

        auditService.recordEvent(tenantId, "WORKFLOW_STATUS", status.getId(),
                "CREATED", null, "ADMIN_API", null, name);

        return status;
    }

    // ========== WorkflowTransitionRule operations ==========

    /**
     * Mavjud workflow definition uchun yangi transition rule (status o'tish qoidasi)
     * yaratadi. Rule "fromStatus → toStatus" yo'nalishini ruxsat etilgan deb belgilaydi.
     *
     * Validatsiyalar:
     * 1. Tenant mavjud bo'lishi kerak
     * 2. Workflow definition shu tenantga tegishli bo'lishi kerak
     * 3. fromStatus mavjud va shu workflow definition'ga tegishli bo'lishi kerak
     * 4. toStatus mavjud va shu workflow definition'ga tegishli bo'lishi kerak
     * 5. fromStatusId != toStatusId — self-transition rad etiladi
     *    (WorkflowTransitionService.transition runtime'da SAME_STATUS sifatida
     *    rad etadi; create-time gate ham shu kontraktni qo'llab-quvvatlaydi —
     *    aks holda hech qachon ishga tushmaydigan dead config saqlanardi).
     * 6. Bir xil (definition, from, to) uchun rule allaqachon mavjud bo'lmasligi
     *    kerak (DB constraint: UNIQUE (workflow_definition_id, from_status_id,
     *    to_status_id) — pre-check + DB fallback).
     *
     * Tenant/status safety: cross-tenant yoki cross-definition status'lar
     * ResourceNotFoundException sifatida 404 qaytaradi (mavjud
     * findByIdAndTenantId pattern bilan bir xil — "boshqa scope'da topilmadi").
     *
     * requiredPermissionId Phase 116 surface'ga kirmaydi:
     * WorkflowTransitionService.validateTransition runtime hozirda uni e'tiborga
     * olmaydi — schema field bo'lsa ham hech qanday xulq-atvor effekti yo'q.
     * Runtime gate kelajak phase'ida qo'shilganda surface ham yangilanadi.
     *
     * @param tenantId tenant identifikatori
     * @param workflowDefinitionId workflow definition identifikatori
     * @param fromStatusId boshlang'ich status identifikatori
     * @param toStatusId maqsad status identifikatori
     * @return yaratilgan WorkflowTransitionRule
     * @throws ResourceNotFoundException tenant, workflow definition yoki status
     *         topilmasa (cross-definition status ham 404)
     * @throws BusinessRuleException self-transition yoki duplicate rule
     */
    public WorkflowTransitionRule createWorkflowTransitionRule(UUID tenantId,
                                                                UUID workflowDefinitionId,
                                                                UUID fromStatusId,
                                                                UUID toStatusId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (workflowDefinitionId == null) {
            throw new IllegalArgumentException("workflowDefinitionId null bo'lishi mumkin emas");
        }
        if (fromStatusId == null) {
            throw new IllegalArgumentException("fromStatusId null bo'lishi mumkin emas");
        }
        if (toStatusId == null) {
            throw new IllegalArgumentException("toStatusId null bo'lishi mumkin emas");
        }

        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        WorkflowDefinition definition = workflowDefinitionRepository
                .findByTenantIdAndId(tenantId, workflowDefinitionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "WorkflowDefinition", workflowDefinitionId));

        if (fromStatusId.equals(toStatusId)) {
            throw new BusinessRuleException("SELF_TRANSITION_NOT_ALLOWED",
                    "Workflow transition rule fromStatus va toStatus bir xil bo'lishi mumkin emas "
                            + "(statusId=" + fromStatusId + ")");
        }

        WorkflowStatus fromStatus = resolveStatusInDefinition(
                fromStatusId, workflowDefinitionId, tenantId, "fromStatus");
        WorkflowStatus toStatus = resolveStatusInDefinition(
                toStatusId, workflowDefinitionId, tenantId, "toStatus");

        if (workflowTransitionRuleRepository
                .existsByWorkflowDefinition_IdAndFromStatus_IdAndToStatus_Id(
                        workflowDefinitionId, fromStatusId, toStatusId)) {
            throw new BusinessRuleException("DUPLICATE_WORKFLOW_TRANSITION_RULE",
                    "Workflow definition ichida '" + fromStatus.getName() + " -> "
                            + toStatus.getName() + "' transition rule allaqachon mavjud");
        }

        WorkflowTransitionRule rule = new WorkflowTransitionRule(definition, fromStatus, toStatus);

        try {
            rule = workflowTransitionRuleRepository.save(rule);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateWorkflowTransitionRuleConstraint(ex)) {
                throw new BusinessRuleException("DUPLICATE_WORKFLOW_TRANSITION_RULE",
                        "Workflow definition ichida '" + fromStatus.getName() + " -> "
                                + toStatus.getName() + "' transition rule allaqachon mavjud");
            }
            throw ex;
        }

        auditService.recordEvent(tenantId, "WORKFLOW_TRANSITION_RULE", rule.getId(),
                "CREATED", null, "ADMIN_API", null,
                fromStatus.getName() + " -> " + toStatus.getName());

        return rule;
    }

    /**
     * Status'ni (tenant + workflow definition) scope ichida bitta tenant-safe
     * SQL bilan topadi. Cross-tenant yoki cross-definition status — 404.
     *
     * In-memory scope check ishlatilmaydi — tenant-safety discipline
     * (mavjud findByIdAndTenantId pattern bilan bir xil) repository darajasida
     * ta'minlanadi.
     */
    private WorkflowStatus resolveStatusInDefinition(UUID statusId, UUID workflowDefinitionId,
                                                       UUID tenantId, String label) {
        return workflowStatusRepository
                .findByIdAndWorkflowDefinition_IdAndWorkflowDefinition_TenantId(
                        statusId, workflowDefinitionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "WorkflowStatus", label + "=" + statusId));
    }

    // ========== TelegramChatBinding operations ==========

    /**
     * Yangi Telegram chat binding yaratadi.
     *
     * Validatsiyalar:
     * 1. Tenant mavjud bo'lishi kerak
     * 2. Shu tenant ichida shu chatId uchun binding allaqachon mavjud bo'lmasligi kerak
     *
     * @param tenantId tenant identifikatori
     * @param chatId Telegram chat identifikatori
     * @param chatTitle chat sarlavhasi (nullable)
     * @param bindingType binding turi (MAIN_GROUP, NOTIFICATION_GROUP)
     * @return yaratilgan TelegramChatBinding
     * @throws ResourceNotFoundException agar tenant topilmasa
     * @throws BusinessRuleException agar duplicate chat binding mavjud bo'lsa
     */
    public TelegramChatBinding createChatBinding(UUID tenantId, long chatId,
                                                   String chatTitle, ChatBindingType bindingType) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        telegramChatBindingRepository.findByTenantIdAndChatId(tenantId, chatId)
                .ifPresent(existing -> {
                    throw new BusinessRuleException("DUPLICATE_CHAT_BINDING",
                            "Tenant ichida chatId=" + chatId + " uchun binding allaqachon mavjud");
                });

        TelegramChatBinding binding = new TelegramChatBinding(tenantId, chatId,
                chatTitle != null && !chatTitle.isBlank() ? chatTitle : null);
        binding.setBindingType(bindingType);

        try {
            binding = telegramChatBindingRepository.save(binding);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateChatBindingConstraint(ex)) {
                throw new BusinessRuleException("DUPLICATE_CHAT_BINDING",
                        "Tenant ichida chatId=" + chatId + " uchun binding allaqachon mavjud");
            }
            throw ex;
        }

        String newValue = chatId + " | " + bindingType.name()
                + (binding.getChatTitle() != null ? " | " + binding.getChatTitle() : "");

        auditService.recordEvent(tenantId, "CHAT_BINDING", binding.getId(),
                "CREATED", null, "ADMIN_API", null, newValue);

        return binding;
    }

    /**
     * Chat binding metadata'sini partial yangilaydi (PATCH semantikasi).
     *
     * Faqat provided=true field'lar yangilanadi:
     * - chatTitleProvided=false → sarlavha o'zgarmaydi
     * - chatTitleProvided=true, null/blank → sarlavha tozalanadi
     * - chatTitleProvided=true, non-blank → sarlavha yangilanadi
     * - bindingTypeProvided=false → tur o'zgarmaydi
     * - bindingTypeProvided=true → yangi tur o'rnatiladi
     *
     * @param tenantId tenant identifikatori
     * @param chatBindingId chat binding identifikatori
     * @param chatTitle yangi sarlavha (faqat chatTitleProvided=true bo'lganda)
     * @param chatTitleProvided chatTitle field JSON'da berilganmi
     * @param bindingType yangi binding turi (faqat bindingTypeProvided=true bo'lganda)
     * @param bindingTypeProvided bindingType field JSON'da berilganmi
     * @return yangilangan TelegramChatBinding
     * @throws ResourceNotFoundException agar tenant yoki chat binding topilmasa
     */
    public TelegramChatBinding updateChatBinding(UUID tenantId, UUID chatBindingId,
                                                   String chatTitle, boolean chatTitleProvided,
                                                   ChatBindingType bindingType, boolean bindingTypeProvided) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        TelegramChatBinding binding = telegramChatBindingRepository.findByIdAndTenantId(chatBindingId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatBinding", chatBindingId));

        String oldChatTitle = binding.getChatTitle();
        String oldBindingType = binding.getBindingType().name();

        if (chatTitleProvided) {
            binding.setChatTitle(chatTitle != null && !chatTitle.isBlank() ? chatTitle : null);
        }

        if (bindingTypeProvided) {
            binding.setBindingType(bindingType);
        }

        binding = telegramChatBindingRepository.save(binding);

        String oldValue = oldBindingType + (oldChatTitle != null ? " | " + oldChatTitle : "");
        String newValue = binding.getBindingType().name()
                + (binding.getChatTitle() != null ? " | " + binding.getChatTitle() : "");

        auditService.recordEvent(tenantId, "CHAT_BINDING", binding.getId(),
                "UPDATED", null, "ADMIN_API", oldValue, newValue);

        return binding;
    }

    /**
     * Chat binding'ni aktiv holatga o'tkazadi.
     *
     * Idempotent: allaqachon aktiv bo'lsa, hech narsa o'zgarmaydi.
     *
     * @param tenantId tenant identifikatori
     * @param chatBindingId chat binding identifikatori
     * @return yangilangan TelegramChatBinding
     * @throws ResourceNotFoundException agar tenant yoki chat binding topilmasa
     */
    public TelegramChatBinding activateChatBinding(UUID tenantId, UUID chatBindingId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        TelegramChatBinding binding = telegramChatBindingRepository.findByIdAndTenantId(chatBindingId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatBinding", chatBindingId));

        if (binding.isActive()) {
            return binding;
        }

        binding.setActive(true);
        binding = telegramChatBindingRepository.save(binding);

        auditService.recordEvent(tenantId, "CHAT_BINDING", binding.getId(),
                "ACTIVATED", null, "ADMIN_API", "false", "true");

        return binding;
    }

    /**
     * Chat binding'ni noaktiv holatga o'tkazadi.
     *
     * Idempotent: allaqachon noaktiv bo'lsa, hech narsa o'zgarmaydi.
     *
     * @param tenantId tenant identifikatori
     * @param chatBindingId chat binding identifikatori
     * @return yangilangan TelegramChatBinding
     * @throws ResourceNotFoundException agar tenant yoki chat binding topilmasa
     */
    public TelegramChatBinding deactivateChatBinding(UUID tenantId, UUID chatBindingId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        TelegramChatBinding binding = telegramChatBindingRepository.findByIdAndTenantId(chatBindingId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatBinding", chatBindingId));

        if (!binding.isActive()) {
            return binding;
        }

        binding.setActive(false);
        binding = telegramChatBindingRepository.save(binding);

        auditService.recordEvent(tenantId, "CHAT_BINDING", binding.getId(),
                "DEACTIVATED", null, "ADMIN_API", "true", "false");

        return binding;
    }

    /**
     * Chat binding'ni o'chiradi (hard delete).
     *
     * @param tenantId tenant identifikatori
     * @param chatBindingId chat binding identifikatori
     * @throws ResourceNotFoundException agar tenant yoki chat binding topilmasa
     */
    public void deleteChatBinding(UUID tenantId, UUID chatBindingId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        TelegramChatBinding binding = telegramChatBindingRepository.findByIdAndTenantId(chatBindingId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatBinding", chatBindingId));

        String oldValue = binding.getChatId() + " | " + binding.getBindingType().name()
                + (binding.getChatTitle() != null ? " | " + binding.getChatTitle() : "");

        telegramChatBindingRepository.delete(binding);

        auditService.recordEvent(tenantId, "CHAT_BINDING", chatBindingId,
                "DELETED", null, "ADMIN_API", oldValue, null);
    }

    // ========== TelegramTopicBinding operations ==========

    /**
     * Yangi Telegram topic binding yaratadi.
     *
     * Validatsiyalar:
     * 1. Tenant mavjud bo'lishi kerak
     * 2. Ota chat binding mavjud va shu tenantga tegishli bo'lishi kerak (tenant-safe)
     * 3. Shu chat binding ichida shu topicId uchun binding allaqachon mavjud bo'lmasligi kerak
     *
     * @param tenantId tenant identifikatori
     * @param chatBindingId ota chat binding identifikatori
     * @param topicId Telegram topic identifikatori
     * @param topicName topic nomi (nullable)
     * @param purpose topic maqsadi
     * @return yaratilgan TelegramTopicBinding
     * @throws ResourceNotFoundException agar tenant topilmasa
     * @throws BusinessRuleException agar chat binding yaroqsiz yoki duplicate topic bo'lsa
     */
    public TelegramTopicBinding createTopicBinding(UUID tenantId, UUID chatBindingId,
                                                     long topicId, String topicName, String purpose) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        TelegramChatBinding chatBinding = telegramChatBindingRepository
                .findByIdAndTenantId(chatBindingId, tenantId)
                .orElseThrow(() -> new BusinessRuleException("INVALID_CHAT_BINDING",
                        "Chat binding (id=" + chatBindingId
                                + ") topilmadi yoki shu tenantga tegishli emas"));

        telegramTopicBindingRepository.findByChatBindingIdAndTopicId(chatBindingId, topicId)
                .ifPresent(existing -> {
                    throw new BusinessRuleException("DUPLICATE_TOPIC_BINDING",
                            "Chat binding ichida topicId=" + topicId
                                    + " uchun binding allaqachon mavjud");
                });

        TelegramTopicBinding binding = new TelegramTopicBinding(
                chatBinding, topicId,
                topicName != null && !topicName.isBlank() ? topicName : null,
                purpose);

        try {
            binding = telegramTopicBindingRepository.save(binding);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateTopicBindingConstraint(ex)) {
                throw new BusinessRuleException("DUPLICATE_TOPIC_BINDING",
                        "Chat binding ichida topicId=" + topicId
                                + " uchun binding allaqachon mavjud");
            }
            throw ex;
        }

        String newValue = topicId + " | " + purpose
                + (binding.getTopicName() != null ? " | " + binding.getTopicName() : "");

        auditService.recordEvent(tenantId, "TOPIC_BINDING", binding.getId(),
                "CREATED", null, "ADMIN_API", null, newValue);

        return binding;
    }

    /**
     * Topic binding metadata'sini partial yangilaydi (PATCH semantikasi).
     *
     * Faqat provided=true field'lar yangilanadi:
     * - topicNameProvided=false → nom o'zgarmaydi
     * - topicNameProvided=true, null/blank → nom tozalanadi
     * - topicNameProvided=true, non-blank → nom yangilanadi
     *
     * @param tenantId tenant identifikatori
     * @param topicBindingId topic binding identifikatori
     * @param topicName yangi nom (faqat topicNameProvided=true bo'lganda)
     * @param topicNameProvided topicName field JSON'da berilganmi
     * @return yangilangan TelegramTopicBinding
     * @throws ResourceNotFoundException agar tenant yoki topic binding topilmasa
     */
    public TelegramTopicBinding updateTopicBinding(UUID tenantId, UUID topicBindingId,
                                                     String topicName, boolean topicNameProvided) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        TelegramTopicBinding binding = telegramTopicBindingRepository
                .findByIdAndChatBinding_TenantId(topicBindingId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("TopicBinding", topicBindingId));

        String oldTopicName = binding.getTopicName();

        if (topicNameProvided) {
            binding.setTopicName(topicName != null && !topicName.isBlank() ? topicName : null);
        }

        binding = telegramTopicBindingRepository.save(binding);

        String oldValue = binding.getPurpose()
                + (oldTopicName != null ? " | " + oldTopicName : "");
        String newValue = binding.getPurpose()
                + (binding.getTopicName() != null ? " | " + binding.getTopicName() : "");

        auditService.recordEvent(tenantId, "TOPIC_BINDING", binding.getId(),
                "UPDATED", null, "ADMIN_API", oldValue, newValue);

        return binding;
    }

    /**
     * Topic binding'ni aktiv holatga o'tkazadi.
     *
     * Idempotent: allaqachon aktiv bo'lsa, hech narsa o'zgarmaydi.
     *
     * @param tenantId tenant identifikatori
     * @param topicBindingId topic binding identifikatori
     * @return yangilangan TelegramTopicBinding
     * @throws ResourceNotFoundException agar tenant yoki topic binding topilmasa
     */
    public TelegramTopicBinding activateTopicBinding(UUID tenantId, UUID topicBindingId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        TelegramTopicBinding binding = telegramTopicBindingRepository
                .findByIdAndChatBinding_TenantId(topicBindingId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("TopicBinding", topicBindingId));

        if (binding.isActive()) {
            return binding;
        }

        binding.setActive(true);
        binding = telegramTopicBindingRepository.save(binding);

        auditService.recordEvent(tenantId, "TOPIC_BINDING", binding.getId(),
                "ACTIVATED", null, "ADMIN_API", "false", "true");

        return binding;
    }

    /**
     * Topic binding'ni noaktiv holatga o'tkazadi.
     *
     * Idempotent: allaqachon noaktiv bo'lsa, hech narsa o'zgarmaydi.
     *
     * @param tenantId tenant identifikatori
     * @param topicBindingId topic binding identifikatori
     * @return yangilangan TelegramTopicBinding
     * @throws ResourceNotFoundException agar tenant yoki topic binding topilmasa
     */
    public TelegramTopicBinding deactivateTopicBinding(UUID tenantId, UUID topicBindingId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        TelegramTopicBinding binding = telegramTopicBindingRepository
                .findByIdAndChatBinding_TenantId(topicBindingId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("TopicBinding", topicBindingId));

        if (!binding.isActive()) {
            return binding;
        }

        binding.setActive(false);
        binding = telegramTopicBindingRepository.save(binding);

        auditService.recordEvent(tenantId, "TOPIC_BINDING", binding.getId(),
                "DEACTIVATED", null, "ADMIN_API", "true", "false");

        return binding;
    }

    /**
     * Topic binding'ni o'chiradi (hard delete).
     *
     * @param tenantId tenant identifikatori
     * @param topicBindingId topic binding identifikatori
     * @throws ResourceNotFoundException agar tenant yoki topic binding topilmasa
     */
    public void deleteTopicBinding(UUID tenantId, UUID topicBindingId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        TelegramTopicBinding binding = telegramTopicBindingRepository
                .findByIdAndChatBinding_TenantId(topicBindingId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("TopicBinding", topicBindingId));

        String oldValue = binding.getTopicId() + " | " + binding.getPurpose()
                + (binding.getTopicName() != null ? " | " + binding.getTopicName() : "");

        telegramTopicBindingRepository.delete(binding);

        auditService.recordEvent(tenantId, "TOPIC_BINDING", topicBindingId,
                "DELETED", null, "ADMIN_API", oldValue, null);
    }

    // ========== RoutingRule operations ==========

    /**
     * Yangi routing rule yaratadi.
     *
     * Validatsiyalar:
     * 1. Tenant mavjud bo'lishi kerak
     * 2. targetTopicBindingId berilsa, topic binding mavjud va shu tenantga tegishli bo'lishi kerak
     *
     * @param tenantId tenant identifikatori
     * @param name rule nomi
     * @param workItemType work item turi (BUG, INCIDENT, TASK)
     * @param priority rule prioriteti
     * @param targetTopicBindingId target topic binding (nullable)
     * @param conditionExpression shart ifodasi (nullable)
     * @return yaratilgan RoutingRule
     * @throws ResourceNotFoundException agar tenant yoki topic binding topilmasa
     */
    public RoutingRule createRoutingRule(UUID tenantId, String name, String workItemType,
                                         int priority, UUID targetTopicBindingId,
                                         String conditionExpression) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        if (targetTopicBindingId != null) {
            telegramTopicBindingRepository.findByIdAndChatBinding_TenantId(targetTopicBindingId, tenantId)
                    .orElseThrow(() -> new BusinessRuleException("INVALID_TOPIC_BINDING",
                            "Topic binding (id=" + targetTopicBindingId
                                    + ") topilmadi yoki shu tenantga tegishli emas"));
        }

        RoutingRule rule = new RoutingRule(tenantId, name, workItemType);
        rule.setPriority(priority);
        rule.setTargetTopicBindingId(targetTopicBindingId);
        if (conditionExpression != null && !conditionExpression.isBlank()) {
            rule.setConditionExpression(conditionExpression);
        }

        rule = routingRuleRepository.save(rule);

        auditService.recordEvent(tenantId, "ROUTING_RULE", rule.getId(),
                "CREATED", null, "ADMIN_API", null, name);

        return rule;
    }

    /**
     * Mavjud routing rule metadata'sini partial yangilaydi (PATCH semantikasi).
     *
     * Faqat provided=true field'lar yangilanadi:
     * - nameProvided=true → yangi nom o'rnatiladi
     * - priorityProvided=true → yangi prioritet o'rnatiladi
     * - targetTopicBindingIdProvided=true, null → tozalanadi
     * - targetTopicBindingIdProvided=true, non-null → tenant-safe validate va set
     * - conditionExpressionProvided=true, null/blank → tozalanadi
     * - conditionExpressionProvided=true, non-blank → yangilanadi
     *
     * @param tenantId tenant identifikatori
     * @param ruleId routing rule identifikatori
     * @param name yangi nom (faqat nameProvided=true bo'lganda)
     * @param nameProvided name field JSON'da berilganmi
     * @param priority yangi prioritet (faqat priorityProvided=true bo'lganda)
     * @param priorityProvided priority field JSON'da berilganmi
     * @param targetTopicBindingId yangi topic binding (faqat provided=true bo'lganda)
     * @param targetTopicBindingIdProvided field JSON'da berilganmi
     * @param conditionExpression yangi shart ifodasi (faqat provided=true bo'lganda)
     * @param conditionExpressionProvided field JSON'da berilganmi
     * @return yangilangan RoutingRule
     * @throws ResourceNotFoundException agar tenant yoki routing rule topilmasa
     * @throws BusinessRuleException agar topic binding yaroqsiz bo'lsa
     */
    public RoutingRule updateRoutingRule(UUID tenantId, UUID ruleId,
                                          String name, boolean nameProvided,
                                          Integer priority, boolean priorityProvided,
                                          UUID targetTopicBindingId, boolean targetTopicBindingIdProvided,
                                          String conditionExpression, boolean conditionExpressionProvided) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        RoutingRule rule = routingRuleRepository.findByIdAndTenantId(ruleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("RoutingRule", ruleId));

        String oldName = rule.getName();
        int oldPriority = rule.getPriority();
        UUID oldTopicBindingId = rule.getTargetTopicBindingId();
        String oldConditionExpression = rule.getConditionExpression();

        if (nameProvided) {
            rule.setName(name);
        }

        if (priorityProvided) {
            rule.setPriority(priority);
        }

        if (targetTopicBindingIdProvided) {
            if (targetTopicBindingId == null) {
                rule.setTargetTopicBindingId(null);
            } else {
                telegramTopicBindingRepository.findByIdAndChatBinding_TenantId(targetTopicBindingId, tenantId)
                        .orElseThrow(() -> new BusinessRuleException("INVALID_TOPIC_BINDING",
                                "Topic binding (id=" + targetTopicBindingId
                                        + ") topilmadi yoki shu tenantga tegishli emas"));
                rule.setTargetTopicBindingId(targetTopicBindingId);
            }
        }

        if (conditionExpressionProvided) {
            rule.setConditionExpression(
                    conditionExpression != null && !conditionExpression.isBlank()
                            ? conditionExpression : null);
        }

        rule = routingRuleRepository.save(rule);

        String oldValue = oldName + " | p=" + oldPriority
                + (oldTopicBindingId != null ? " | tb=" + oldTopicBindingId : "")
                + (oldConditionExpression != null ? " | ce=" + oldConditionExpression : "");
        String newValue = rule.getName() + " | p=" + rule.getPriority()
                + (rule.getTargetTopicBindingId() != null ? " | tb=" + rule.getTargetTopicBindingId() : "")
                + (rule.getConditionExpression() != null ? " | ce=" + rule.getConditionExpression() : "");

        auditService.recordEvent(tenantId, "ROUTING_RULE", rule.getId(),
                "UPDATED", null, "ADMIN_API", oldValue, newValue);

        return rule;
    }

    /**
     * Routing rule'ni aktiv holatga o'tkazadi.
     *
     * Idempotent: allaqachon aktiv bo'lsa, hech narsa o'zgarmaydi.
     *
     * @param tenantId tenant identifikatori
     * @param ruleId routing rule identifikatori
     * @return yangilangan RoutingRule
     * @throws ResourceNotFoundException agar tenant yoki routing rule topilmasa
     */
    public RoutingRule activateRoutingRule(UUID tenantId, UUID ruleId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        RoutingRule rule = routingRuleRepository.findByIdAndTenantId(ruleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("RoutingRule", ruleId));

        if (rule.isActive()) {
            return rule;
        }

        rule.setActive(true);
        rule = routingRuleRepository.save(rule);

        auditService.recordEvent(tenantId, "ROUTING_RULE", rule.getId(),
                "ACTIVATED", null, "ADMIN_API", "false", "true");

        return rule;
    }

    /**
     * Routing rule'ni noaktiv holatga o'tkazadi.
     *
     * Idempotent: allaqachon noaktiv bo'lsa, hech narsa o'zgarmaydi.
     *
     * @param tenantId tenant identifikatori
     * @param ruleId routing rule identifikatori
     * @return yangilangan RoutingRule
     * @throws ResourceNotFoundException agar tenant yoki routing rule topilmasa
     */
    public RoutingRule deactivateRoutingRule(UUID tenantId, UUID ruleId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        RoutingRule rule = routingRuleRepository.findByIdAndTenantId(ruleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("RoutingRule", ruleId));

        if (!rule.isActive()) {
            return rule;
        }

        rule.setActive(false);
        rule = routingRuleRepository.save(rule);

        auditService.recordEvent(tenantId, "ROUTING_RULE", rule.getId(),
                "DEACTIVATED", null, "ADMIN_API", "true", "false");

        return rule;
    }

    /**
     * Routing rule'ni o'chiradi (hard delete).
     *
     * @param tenantId tenant identifikatori
     * @param ruleId routing rule identifikatori
     * @throws ResourceNotFoundException agar tenant yoki routing rule topilmasa
     */
    public void deleteRoutingRule(UUID tenantId, UUID ruleId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        RoutingRule rule = routingRuleRepository.findByIdAndTenantId(ruleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("RoutingRule", ruleId));

        String oldValue = rule.getName() + " | p=" + rule.getPriority();

        routingRuleRepository.delete(rule);

        auditService.recordEvent(tenantId, "ROUTING_RULE", ruleId,
                "DELETED", null, "ADMIN_API", oldValue, null);
    }

    /**
     * DataIntegrityViolationException workflow_definition (tenant_id, name) unique
     * constraint violation ekanligini tekshiradi.
     *
     * PostgreSQL auto-generated constraint nomi: workflow_definition_tenant_id_name_key
     */
    private static boolean isDuplicateNameConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            return constraintName != null
                    && constraintName.contains("workflow_definition")
                    && constraintName.contains("tenant_id")
                    && constraintName.contains("name");
        }
        return false;
    }

    /**
     * DataIntegrityViolationException telegram_chat_binding (tenant_id, chat_id) unique
     * constraint violation ekanligini tekshiradi.
     */
    /**
     * DataIntegrityViolationException telegram_topic_binding (chat_binding_id, topic_id) unique
     * constraint violation ekanligini tekshiradi.
     */
    private static boolean isDuplicateTopicBindingConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            return constraintName != null
                    && constraintName.contains("telegram_topic_binding")
                    && constraintName.contains("chat_binding_id")
                    && constraintName.contains("topic_id");
        }
        return false;
    }

    private static boolean isDuplicateChatBindingConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            return constraintName != null
                    && constraintName.contains("telegram_chat_binding")
                    && constraintName.contains("tenant_id")
                    && constraintName.contains("chat_id");
        }
        return false;
    }

    /**
     * DataIntegrityViolationException tenant.slug unique constraint violation
     * ekanligini tekshiradi.
     *
     * PostgreSQL avtomatik nomi: tenant_slug_key (qisqa, kesilmaydi).
     * Pattern truncation'ga ham chidamli — faqat ikkita kalit bo'lakni tekshiradi.
     */
    private static boolean isDuplicateTenantSlugConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            return constraintName != null
                    && constraintName.contains("tenant")
                    && constraintName.contains("slug");
        }
        return false;
    }

    /**
     * DataIntegrityViolationException workflow_status (workflow_definition_id, name)
     * unique constraint violation ekanligini tekshiradi.
     *
     * PostgreSQL auto-generated constraint nomi:
     * workflow_status_workflow_definition_id_name_key
     */
    private static boolean isDuplicateWorkflowStatusNameConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            return constraintName != null
                    && constraintName.contains("workflow_status")
                    && constraintName.contains("workflow_definition_id")
                    && constraintName.contains("name");
        }
        return false;
    }

    /**
     * DataIntegrityViolationException workflow_transition_rule
     * (workflow_definition_id, from_status_id, to_status_id) unique constraint
     * violation ekanligini tekshiradi.
     *
     * PostgreSQL avtomatik constraint nomlari identifier uzunligi limiti (63 belgi)
     * tufayli kesilishi mumkin. Masalan:
     * "workflow_transition_rule_workflow_definition_id_from_status_id_"
     * — bu yerda "to_status_id" qoldiq belgilarga sig'maydi va kesib tashlanadi.
     * Shuning uchun pattern faqat "workflow_transition_rule" + ikkita kalit
     * column'gacha tekshiradi (workflow_definition_id va from_status_id) —
     * boshqa workflow_transition_rule unique constraint'i mavjud emas.
     */
    private static boolean isDuplicateWorkflowTransitionRuleConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            return constraintName != null
                    && constraintName.contains("workflow_transition_rule")
                    && constraintName.contains("workflow_definition_id")
                    && constraintName.contains("from_status_id");
        }
        return false;
    }

    /**
     * DataIntegrityViolationException workflow_definition delete paytida child
     * jadvallardan biri (work_item, workflow_status, workflow_transition_rule)
     * tomonidan referans (FK) violation ekanligini tekshiradi.
     */
    private static boolean isWorkflowDefinitionReferencedConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            return constraintName != null
                    && constraintName.contains("workflow_definition_id");
        }
        return false;
    }
}
