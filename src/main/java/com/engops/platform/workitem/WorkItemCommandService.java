package com.engops.platform.workitem;

import com.engops.platform.audit.AuditService;
import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.model.WorkflowStatus;
import com.engops.platform.workitem.model.UpdateType;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import com.engops.platform.workitem.model.WorkItemUpdate;
import com.engops.platform.workitem.repository.WorkItemRepository;
import com.engops.platform.workitem.repository.WorkItemUpdateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * WorkItem buyruq (command) servisi — yaratish, owner tayinlash, yangilash.
 * Status o'tkazish WorkflowTransitionService orqali amalga oshiriladi.
 *
 * Cross-module bog'lanishlar:
 * - IdentityQueryService — membership tekshiruvi uchun (public API)
 * - TenantConfigQueryService — workflow definition olish uchun (public API)
 * - AuditService — audit yozish uchun (public API)
 */
@Service
@Transactional
public class WorkItemCommandService {

    /**
     * Phase 190 — MVP uchun ruxsat etilgan priority kodlari (bounded enum-like).
     * DB ustuni {@code VARCHAR(50)} ekanligi sababli schema-darajada enforce
     * qilinmaydi; service layer fail-closed validatsiya qiladi.
     */
    static final Set<String> ALLOWED_PRIORITY_CODES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    /**
     * Phase 190 — MVP uchun ruxsat etilgan severity kodlari (bounded enum-like).
     */
    static final Set<String> ALLOWED_SEVERITY_CODES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final WorkItemRepository workItemRepository;
    private final WorkItemUpdateRepository workItemUpdateRepository;
    private final WorkItemCodeGenerator codeGenerator;
    private final AuditService auditService;
    private final TenantConfigQueryService tenantConfigQueryService;
    private final IdentityQueryService identityQueryService;

    public WorkItemCommandService(WorkItemRepository workItemRepository,
                                   WorkItemUpdateRepository workItemUpdateRepository,
                                   WorkItemCodeGenerator codeGenerator,
                                   AuditService auditService,
                                   TenantConfigQueryService tenantConfigQueryService,
                                   IdentityQueryService identityQueryService) {
        this.workItemRepository = workItemRepository;
        this.workItemUpdateRepository = workItemUpdateRepository;
        this.codeGenerator = codeGenerator;
        this.auditService = auditService;
        this.tenantConfigQueryService = tenantConfigQueryService;
        this.identityQueryService = identityQueryService;
    }

    /**
     * Yangi work item yaratadi.
     *
     * @param description ixtiyoriy tavsif (nullable)
     *
     * Validatsiyalar:
     * 1. Workflow definition tenant ga tegishli bo'lishi kerak
     * 2. Workflow definition work item type ga mos kelishi kerak
     * 3. initialStatusCode workflow definition ichida bo'lishi kerak
     * 4. initialStatusCode initial=true deb belgilangan bo'lishi kerak
     */
    public WorkItem create(UUID tenantId, WorkItemType typeCode, UUID workflowDefinitionId,
                            String title, String description, String initialStatusCode,
                            UUID createdByUserId, String actionSource) {
        // Workflow definition tenant-safe tekshiruv (facade orqali)
        WorkflowDefinition definition = tenantConfigQueryService
                .findWorkflowDefinitionById(tenantId, workflowDefinitionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "WorkflowDefinition", workflowDefinitionId));

        // Workflow active tekshiruvi
        if (!definition.isActive()) {
            throw new BusinessRuleException("INACTIVE_WORKFLOW",
                    "Workflow '" + definition.getName() + "' aktiv emas. "
                            + "Faqat aktiv workflow bilan work item yaratish mumkin");
        }

        // Workflow type va WorkItem type mosligi tekshiruvi
        validateWorkflowTypeCompatibility(definition, typeCode);

        // Initial status validatsiya
        validateInitialStatus(definition, initialStatusCode);

        String code = codeGenerator.generate(tenantId, typeCode);

        WorkItem workItem = new WorkItem(tenantId, code, typeCode, workflowDefinitionId,
                title, initialStatusCode, createdByUserId);

        if (description != null && !description.isBlank()) {
            workItem.setDescription(description);
        }

        workItem = workItemRepository.save(workItem);

        auditService.recordEvent(tenantId, "WORK_ITEM", workItem.getId(),
                "CREATED", createdByUserId, actionSource, null, code);

        return workItem;
    }

    /**
     * Phase 195 — yangi work item yaratadi va create vaqtida ixtiyoriy
     * boshlang'ich atributlar (priority, severity, owner)'ni o'rnatadi.
     *
     * <p><strong>Mavjud {@link #create} signaturesi tegmasdan saqlanadi.</strong>
     * Bu overload yangi audit payload formati (bounded JSON object) bilan
     * yoziladi: clienlar shu metod bilan yaratganda CREATED qatori
     * {@code newValueJson} maydoni JSON shaklida bo'ladi, masalan:
     * {@code {"code":"BUG-1","priority":"HIGH","severity":"CRITICAL","ownerUserId":"..."}}.
     * Eski {@code create(...)} chaqiruvchilari yo'lida {@code newValueJson}
     * plain string (work item code) sifatida saqlanadi (byte-compat).</p>
     *
     * <p><strong>Validatsiyalar tartibi (fail-fast — mutation'dan oldin):</strong></p>
     * <ol>
     *   <li>Agar {@code priorityCode} non-null va non-blank — Phase 190
     *       {@link #validateBoundedCode} ({@link #ALLOWED_PRIORITY_CODES},
     *       {@code INVALID_PRIORITY_CODE}).</li>
     *   <li>Agar {@code severityCode} non-null va non-blank — bir xil
     *       ({@link #ALLOWED_SEVERITY_CODES}, {@code INVALID_SEVERITY_CODE}).</li>
     *   <li>Agar {@code ownerUserId} non-null — Phase 190
     *       {@link #validateActiveMembership} ({@code INVALID_OWNER}).</li>
     *   <li>Mavjud {@link #create} bilan bir xil workflow validatsiyalar:
     *       definition tenant-safe lookup, {@code isActive()},
     *       {@link #validateWorkflowTypeCompatibility},
     *       {@link #validateInitialStatus}.</li>
     * </ol>
     *
     * <p><strong>Permission:</strong> bu metod o'z ichida authorization
     * tekshirmaydi — caller (intake application service'i) {@code WORK_ITEM_CREATE}
     * ruxsatini allaqachon majburiy qilgan. Phase 190 admin write
     * {@code WORK_ITEM_UPDATE} / {@code WORK_ITEM_ASSIGN} permission'lari bu
     * yo'lda ataylab talab qilinmaydi — boshlang'ich atributlar create
     * jarayonining bir qismi (post-create modifikatsiya emas).</p>
     *
     * <p><strong>Audit:</strong> bitta {@code CREATED} qatori yoziladi.
     * Alohida {@code PRIORITY_CHANGED} / {@code SEVERITY_CHANGED} /
     * {@code OWNER_ASSIGNED} qatorlari yaratilmaydi — boshlang'ich
     * holatni atomik tarzda CREATED payload'ida saqlash kerak.</p>
     *
     * @param priorityCode nullable bounded code; blank null sifatida qaraladi
     * @param severityCode nullable bounded code; blank null sifatida qaraladi
     * @param ownerUserId nullable owner id; non-null bo'lsa ACTIVE membership
     *                    tekshiriladi
     */
    public WorkItem createWithAttributes(UUID tenantId, WorkItemType typeCode,
                                          UUID workflowDefinitionId,
                                          String title, String description,
                                          String initialStatusCode,
                                          UUID createdByUserId, String actionSource,
                                          String priorityCode, String severityCode,
                                          UUID ownerUserId) {
        // 1. Atribut validatsiyalari — mutation'dan oldin.
        String normalizedPriority = null;
        if (priorityCode != null && !priorityCode.isBlank()) {
            normalizedPriority = validateBoundedCode(
                    "priorityCode", priorityCode, ALLOWED_PRIORITY_CODES,
                    "INVALID_PRIORITY_CODE");
        }
        String normalizedSeverity = null;
        if (severityCode != null && !severityCode.isBlank()) {
            normalizedSeverity = validateBoundedCode(
                    "severityCode", severityCode, ALLOWED_SEVERITY_CODES,
                    "INVALID_SEVERITY_CODE");
        }
        if (ownerUserId != null) {
            validateActiveMembership(tenantId, ownerUserId);
        }

        // 2. Workflow / status validatsiyalari — mavjud create(...) bilan
        //    bir xil zanjir. Refactor qilinmaydi (correctness > DRY).
        WorkflowDefinition definition = tenantConfigQueryService
                .findWorkflowDefinitionById(tenantId, workflowDefinitionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "WorkflowDefinition", workflowDefinitionId));
        if (!definition.isActive()) {
            throw new BusinessRuleException("INACTIVE_WORKFLOW",
                    "Workflow '" + definition.getName() + "' aktiv emas. "
                            + "Faqat aktiv workflow bilan work item yaratish mumkin");
        }
        validateWorkflowTypeCompatibility(definition, typeCode);
        validateInitialStatus(definition, initialStatusCode);

        // 3. WorkItem'ni quramiz va boshlang'ich atributlarni o'rnatamiz.
        String code = codeGenerator.generate(tenantId, typeCode);
        WorkItem workItem = new WorkItem(tenantId, code, typeCode, workflowDefinitionId,
                title, initialStatusCode, createdByUserId);
        if (description != null && !description.isBlank()) {
            workItem.setDescription(description);
        }
        if (normalizedPriority != null) {
            workItem.setPriorityCode(normalizedPriority);
        }
        if (normalizedSeverity != null) {
            workItem.setSeverityCode(normalizedSeverity);
        }
        if (ownerUserId != null) {
            workItem.assignOwner(ownerUserId);
            // Phase 190 pattern: owner set bo'lsa updatedByUserId ham yoziladi.
            workItem.setUpdatedByUserId(createdByUserId);
        }

        workItem = workItemRepository.save(workItem);

        // 4. Audit — bounded JSON payload. Plain code path'i ({@link #create})
        //    o'zgartirilmaydi; yangi yo'lda CREATED.newValueJson JSON object.
        String createdPayload = buildCreatedPayload(workItem);
        auditService.recordEvent(tenantId, "WORK_ITEM", workItem.getId(),
                "CREATED", createdByUserId, actionSource, null, createdPayload);

        return workItem;
    }

    /**
     * Phase 195 — CREATED audit qatori uchun bounded JSON payload quradi.
     *
     * <p>Faqat non-null maydonlar JSON object'iga kiritiladi. {@code code}
     * har doim mavjud. Defense-in-depth: Phase 185 {@code jsonStringOrNull}
     * pattern bilan bir xil — bounded enum-like qiymatlar bo'lsa-da, mavjud
     * code'larda backslash/quote escape qilinadi.</p>
     */
    static String buildCreatedPayload(WorkItem wi) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"code\":").append(jsonStringOrNull(wi.getWorkItemCode()));
        if (wi.getPriorityCode() != null && !wi.getPriorityCode().isBlank()) {
            sb.append(",\"priority\":").append(jsonStringOrNull(wi.getPriorityCode()));
        }
        if (wi.getSeverityCode() != null && !wi.getSeverityCode().isBlank()) {
            sb.append(",\"severity\":").append(jsonStringOrNull(wi.getSeverityCode()));
        }
        if (wi.getCurrentOwnerUserId() != null) {
            sb.append(",\"ownerUserId\":")
                    .append(jsonStringOrNull(wi.getCurrentOwnerUserId().toString()));
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * Phase 185 jsonStringOrNull pattern (defense-in-depth). Bounded
     * code'lar (BUG-N, LOW/MEDIUM/HIGH/CRITICAL, UUID toString) escape
     * talab qilmaydi, lekin bu helper kelajakdagi kengayishlardan
     * himoyalanish uchun ataylab saqlanadi.
     */
    private static String jsonStringOrNull(String value) {
        if (value == null) {
            return "null";
        }
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    /**
     * Work item'ga owner tayinlaydi.
     *
     * Validatsiya: owner shu tenantda active membership ga ega bo'lishi kerak.
     */
    public WorkItem assignOwner(UUID tenantId, UUID workItemId, UUID ownerUserId,
                                 UUID actorUserId, String actionSource) {
        WorkItem workItem = findWorkItem(tenantId, workItemId);

        // Owner membership validatsiya (facade orqali)
        validateActiveMembership(tenantId, ownerUserId);

        UUID previousOwner = workItem.getCurrentOwnerUserId();
        workItem.assignOwner(ownerUserId);
        workItem.setUpdatedByUserId(actorUserId);

        workItem = workItemRepository.save(workItem);

        workItemUpdateRepository.save(new WorkItemUpdate(
                tenantId, workItemId, actorUserId, UpdateType.ASSIGNMENT,
                "Owner tayinlandi: " + ownerUserId));

        auditService.recordEvent(tenantId, "WORK_ITEM", workItemId,
                "OWNER_ASSIGNED", actorUserId, actionSource,
                previousOwner != null ? previousOwner.toString() : null,
                ownerUserId.toString());

        return workItem;
    }

    /**
     * Phase 190 — work item priority kodini yangilaydi.
     *
     * <p>Validatsiyalar:</p>
     * <ol>
     *   <li>tenantId, workItemId, actorUserId, actionSource majburiy;</li>
     *   <li>newPriorityCode {@link #ALLOWED_PRIORITY_CODES} ichida bo'lishi shart
     *       (LOW / MEDIUM / HIGH / CRITICAL);</li>
     *   <li>WorkItem tenant-scoped lookup orqali topilishi shart.</li>
     * </ol>
     *
     * <p>Yon-ta'sirlar bitta tranzaksiya ichida:</p>
     * <ul>
     *   <li>{@link WorkItem#setPriorityCode(String)} chaqiriladi va
     *       {@code updatedByUserId} actor'ga yoziladi;</li>
     *   <li>{@code WorkItemUpdate} qator yoziladi
     *       ({@link UpdateType#PRIORITY_CHANGE} bilan, body — yangi qiymat);</li>
     *   <li>Audit event {@code PRIORITY_CHANGED} (MANDATORY propagation orqali
     *       joriy biznes tranzaksiyaga qo'shiladi). {@code oldValueJson}/
     *       {@code newValueJson} faqat bounded enum-like qiymatni saqlaydi —
     *       request body dump qilinmaydi.</li>
     * </ul>
     *
     * @param tenantId tenant identifikatori (majburiy)
     * @param workItemId work item identifikatori (majburiy)
     * @param newPriorityCode yangi priority kodi (LOW / MEDIUM / HIGH / CRITICAL)
     * @param actorUserId amal bajaruvchi (majburiy)
     * @param actionSource amal manbai (masalan {@code ADMIN_API})
     * @return yangilangan WorkItem
     */
    public WorkItem updatePriority(UUID tenantId, UUID workItemId, String newPriorityCode,
                                    UUID actorUserId, String actionSource) {
        validateUpdateArguments(tenantId, workItemId, actorUserId, actionSource);
        String normalizedPriority = validateBoundedCode(
                "priorityCode", newPriorityCode, ALLOWED_PRIORITY_CODES,
                "INVALID_PRIORITY_CODE");

        WorkItem workItem = findWorkItem(tenantId, workItemId);

        String previousPriority = workItem.getPriorityCode();
        workItem.setPriorityCode(normalizedPriority);
        workItem.setUpdatedByUserId(actorUserId);

        workItem = workItemRepository.save(workItem);

        workItemUpdateRepository.save(new WorkItemUpdate(
                tenantId, workItemId, actorUserId, UpdateType.PRIORITY_CHANGE,
                normalizedPriority));

        auditService.recordEvent(tenantId, "WORK_ITEM", workItemId,
                "PRIORITY_CHANGED", actorUserId, actionSource,
                previousPriority,
                normalizedPriority);

        return workItem;
    }

    /**
     * Phase 190 — work item severity kodini yangilaydi.
     *
     * <p>Validatsiyalar:</p>
     * <ol>
     *   <li>tenantId, workItemId, actorUserId, actionSource majburiy;</li>
     *   <li>newSeverityCode {@link #ALLOWED_SEVERITY_CODES} ichida bo'lishi shart
     *       (LOW / MEDIUM / HIGH / CRITICAL);</li>
     *   <li>WorkItem tenant-scoped lookup orqali topilishi shart.</li>
     * </ol>
     *
     * <p>Yon-ta'sirlar bitta tranzaksiya ichida:</p>
     * <ul>
     *   <li>{@link WorkItem#setSeverityCode(String)} chaqiriladi va
     *       {@code updatedByUserId} actor'ga yoziladi;</li>
     *   <li>{@code WorkItemUpdate} qator yoziladi
     *       ({@link UpdateType#SEVERITY_CHANGE} bilan, body — yangi qiymat);</li>
     *   <li>Audit event {@code SEVERITY_CHANGED} (MANDATORY propagation orqali
     *       joriy biznes tranzaksiyaga qo'shiladi). {@code oldValueJson}/
     *       {@code newValueJson} faqat bounded enum-like qiymatni saqlaydi.</li>
     * </ul>
     *
     * @param tenantId tenant identifikatori (majburiy)
     * @param workItemId work item identifikatori (majburiy)
     * @param newSeverityCode yangi severity kodi (LOW / MEDIUM / HIGH / CRITICAL)
     * @param actorUserId amal bajaruvchi (majburiy)
     * @param actionSource amal manbai (masalan {@code ADMIN_API})
     * @return yangilangan WorkItem
     */
    public WorkItem updateSeverity(UUID tenantId, UUID workItemId, String newSeverityCode,
                                    UUID actorUserId, String actionSource) {
        validateUpdateArguments(tenantId, workItemId, actorUserId, actionSource);
        String normalizedSeverity = validateBoundedCode(
                "severityCode", newSeverityCode, ALLOWED_SEVERITY_CODES,
                "INVALID_SEVERITY_CODE");

        WorkItem workItem = findWorkItem(tenantId, workItemId);

        String previousSeverity = workItem.getSeverityCode();
        workItem.setSeverityCode(normalizedSeverity);
        workItem.setUpdatedByUserId(actorUserId);

        workItem = workItemRepository.save(workItem);

        workItemUpdateRepository.save(new WorkItemUpdate(
                tenantId, workItemId, actorUserId, UpdateType.SEVERITY_CHANGE,
                normalizedSeverity));

        auditService.recordEvent(tenantId, "WORK_ITEM", workItemId,
                "SEVERITY_CHANGED", actorUserId, actionSource,
                previousSeverity,
                normalizedSeverity);

        return workItem;
    }

    /**
     * Phase 190 — {@link #updatePriority} va {@link #updateSeverity} uchun umumiy
     * argument validatsiya. Hech qaysi maydon null bo'lishi mumkin emas;
     * actionSource bo'sh string ham qabul qilinmaydi.
     */
    private void validateUpdateArguments(UUID tenantId, UUID workItemId, UUID actorUserId,
                                          String actionSource) {
        if (tenantId == null) {
            throw new BusinessRuleException("INVALID_ARGUMENT", "tenantId majburiy");
        }
        if (workItemId == null) {
            throw new BusinessRuleException("INVALID_ARGUMENT", "workItemId majburiy");
        }
        if (actorUserId == null) {
            throw new BusinessRuleException("INVALID_ARGUMENT", "actorUserId majburiy");
        }
        if (actionSource == null || actionSource.isBlank()) {
            throw new BusinessRuleException("INVALID_ARGUMENT",
                    "actionSource bo'sh bo'lishi mumkin emas");
        }
    }

    /**
     * Phase 190 — bounded enum-like kod validatsiyasi. Null/blank/noma'lum
     * qiymatlar rad etiladi. Allowed set ichida bo'lsa, original qiymat
     * (case-preserving) qaytariladi.
     */
    private String validateBoundedCode(String fieldName, String value,
                                        Set<String> allowed, String errorCode) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(errorCode,
                    fieldName + " bo'sh bo'lishi mumkin emas");
        }
        if (!allowed.contains(value)) {
            throw new BusinessRuleException(errorCode,
                    fieldName + " noto'g'ri: '" + value + "' (ruxsat etilganlar: "
                            + allowed + ")");
        }
        return value;
    }

    /**
     * Work item'ga tizimli yangilanish (izoh) qo'shadi.
     */
    public WorkItemUpdate addUpdate(UUID tenantId, UUID workItemId, UUID authorUserId,
                                     UpdateType updateType, String body, String actionSource) {
        findWorkItem(tenantId, workItemId);

        WorkItemUpdate update = new WorkItemUpdate(tenantId, workItemId, authorUserId,
                updateType, body);

        update = workItemUpdateRepository.save(update);

        auditService.recordEvent(tenantId, "WORK_ITEM", workItemId,
                "UPDATE_ADDED", authorUserId, actionSource,
                null, updateType.name());

        return update;
    }

    /**
     * Workflow definition va WorkItem type mosligi tekshiruvi.
     * Agar workflow definition boshqa type uchun yaratilgan bo'lsa — rad etiladi.
     */
    private void validateWorkflowTypeCompatibility(WorkflowDefinition definition,
                                                     WorkItemType typeCode) {
        if (!definition.getWorkItemType().equals(typeCode.name())) {
            throw new BusinessRuleException("WORKFLOW_TYPE_MISMATCH",
                    "Workflow '" + definition.getName() + "' turi '"
                            + definition.getWorkItemType() + "', lekin work item turi '"
                            + typeCode.name() + "'. Mos kelmaydi");
        }
    }

    /**
     * Initial status ni validatsiya qiladi:
     * 1. Status workflow definition ichida mavjud bo'lishi kerak
     * 2. Status initial=true deb belgilangan bo'lishi kerak
     */
    private void validateInitialStatus(WorkflowDefinition definition, String statusCode) {
        Optional<WorkflowStatus> statusOpt = definition.getStatuses().stream()
                .filter(s -> s.getName().equals(statusCode))
                .findFirst();

        if (statusOpt.isEmpty()) {
            throw new BusinessRuleException("INVALID_INITIAL_STATUS",
                    "'" + statusCode + "' statusi '" + definition.getName()
                            + "' workflow ta'rifida topilmadi");
        }

        if (!statusOpt.get().isInitial()) {
            throw new BusinessRuleException("NOT_INITIAL_STATUS",
                    "'" + statusCode + "' statusi boshlang'ich holat emas. "
                            + "Faqat initial=true belgilangan status bilan yaratish mumkin");
        }
    }

    /**
     * Foydalanuvchining shu tenantda active membership ga ega ekanligini tekshiradi
     * (IdentityQueryService facade orqali).
     */
    private void validateActiveMembership(UUID tenantId, UUID userId) {
        if (!identityQueryService.hasActiveMembership(tenantId, userId)) {
            throw new BusinessRuleException("INVALID_OWNER",
                    "Foydalanuvchi (id=" + userId + ") shu tenantda faol a'zo emas");
        }
    }

    /**
     * Work item'ni saqlaydi.
     * Boshqa modullar work item holatini o'zgartirgandan keyin persist qilish uchun ishlatiladi.
     */
    public WorkItem save(WorkItem workItem) {
        return workItemRepository.save(workItem);
    }

    private WorkItem findWorkItem(UUID tenantId, UUID workItemId) {
        return workItemRepository.findByTenantIdAndId(tenantId, workItemId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkItem", workItemId));
    }
}
