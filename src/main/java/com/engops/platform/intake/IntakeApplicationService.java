package com.engops.platform.intake;

import com.engops.platform.routing.RoutingDecision;
import com.engops.platform.routing.RoutingDecisionService;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.model.WorkflowStatus;
import com.engops.platform.workitem.OperationalAuthorizationService;
import com.engops.platform.workitem.WorkItemCommandService;
import com.engops.platform.workitem.model.WorkItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Intake application servisi — yangi work item yaratish uchun yagona kirish nuqtasi.
 *
 * Tashqi adapterlar (Telegram, REST, integration) shu servis orqali ishlaydi.
 * Bu servis:
 * 1. Kiruvchi commandni validatsiya qiladi
 * 2. Workflow definitionni aniqlaydi (explicit yoki auto-resolve)
 * 3. Initial statusni aniqlaydi (explicit yoki auto-resolve)
 * 4. RoutingDecisionService orqali routing qarorini oladi (fail-fast, mutation'dan oldin)
 * 5. WorkItemCommandService orqali work item yaratadi
 * 6. Adapter-ready structured natija qaytaradi (work item + resolved routing target)
 *
 * Muhim: Routing resolution work item yaratishdan OLDIN bo'ladi.
 * Bu fail-fast ta'minlaydi — invalid routing config bilan work item yaratilmaydi.
 *
 * Cross-module bog'lanishlar:
 * - WorkItemCommandService — work item yaratish uchun (public API)
 * - TenantConfigQueryService — workflow definition olish uchun (public API)
 * - RoutingDecisionService — routing qarori olish uchun (public API)
 * - ApplicationEventPublisher — Phase 164, AFTER_COMMIT Telegram dispatch eventi
 *   publish qilish uchun (Spring infra)
 *
 * <p><strong>Phase 164 — Telegram dispatch AFTER_COMMIT'ga ko'chirildi:</strong>
 * work item muvaffaqiyatli yaratilgandan keyin, agar routing prepared bo'lsa,
 * intake faqat {@link TelegramCardDispatchRequested} eventini publish qiladi.
 * Telegram render + outbound + delivery_attempt persistence
 * {@link TelegramCardDispatchEventListener} tomonidan
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} bilan, intake
 * transaction commit'idan KEYIN amalga oshiriladi. Telegram HTTP I/O endi
 * intake DB transaction'i ichida emas — DB connection pool blokirovka
 * qilinmaydi va send-then-rollback divergentsiyasi bartaraf etiladi.</p>
 *
 * <p><strong>Fail-soft kontrakt (Phase 164 vintage):</strong> work item
 * yaratish manba-haqiqat. Event publish qadamida kutilmagan
 * {@link RuntimeException} ushlanadi va bounded metadata ({@code tenantId},
 * {@code workItemId}, {@code exceptionType}) bilan log yoziladi —
 * {@code ex.getMessage()} ataylab log'ga chiqarilmaydi (Phase 160
 * mini-fix logging hardening pattern). Listener ichidagi dispatch xatolari
 * o'sha listener'ning fail-soft boundary'ida ushlanadi va bu yerga
 * etib kelmaydi.</p>
 */
@Service
@Transactional
public class IntakeApplicationService {

    private static final Logger log = LoggerFactory.getLogger(IntakeApplicationService.class);

    private final WorkItemCommandService workItemCommandService;
    private final TenantConfigQueryService tenantConfigQueryService;
    private final RoutingDecisionService routingDecisionService;
    private final OperationalAuthorizationService operationalAuthorizationService;
    private final ApplicationEventPublisher eventPublisher;

    public IntakeApplicationService(WorkItemCommandService workItemCommandService,
                                     TenantConfigQueryService tenantConfigQueryService,
                                     RoutingDecisionService routingDecisionService,
                                     OperationalAuthorizationService operationalAuthorizationService,
                                     ApplicationEventPublisher eventPublisher) {
        this.workItemCommandService = workItemCommandService;
        this.tenantConfigQueryService = tenantConfigQueryService;
        this.routingDecisionService = routingDecisionService;
        this.operationalAuthorizationService = operationalAuthorizationService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Yangi work item yaratadi intake command asosida.
     *
     * @param command intake buyrug'i
     * @return yaratilgan work item haqida structured natija
     */
    public IntakeResult submit(IntakeCommand command) {
        validateCommand(command);

        // Phase 139: actor WORK_ITEM_CREATE ruxsatiga ega bo'lishi shart.
        // validateCommand'dan keyin (validation-before-authorization), lekin
        // workflow/routing/audit/mutation'dan OLDIN — ruxsatsiz actor hech qanday
        // downstream chaqiruvni amalga oshira olmaydi.
        operationalAuthorizationService.authorizeIntake(
                command.getTenantId(), command.getCreatedByUserId());

        // 1. Workflow definition aniqlash
        WorkflowDefinition definition = resolveWorkflowDefinition(command);

        // 2. Initial status aniqlash
        String initialStatusCode = resolveInitialStatus(command, definition);

        // 3. Routing decision — mutation'dan OLDIN (fail-fast: invalid config bilan work item yaratilmaydi)
        RoutingDecision routing = routingDecisionService.resolve(
                command.getTenantId(), command.getTypeCode().name());

        // 4. WorkItemCommandService orqali yaratish (domain validatsiya u yerda bo'ladi)
        WorkItem workItem = workItemCommandService.create(
                command.getTenantId(),
                command.getTypeCode(),
                definition.getId(),
                command.getTitle(),
                command.getDescription(),
                initialStatusCode,
                command.getCreatedByUserId(),
                command.getActionSource());

        // 5. Adapter-ready result
        IntakeResult result = new IntakeResult(
                workItem.getId(),
                workItem.getWorkItemCode(),
                workItem.getTypeCode().name(),
                workItem.getTitle(),
                workItem.getCurrentStatusCode(),
                workItem.getWorkflowDefinitionId(),
                workItem.getTenantId(),
                routing.isPrepared(),
                routing.getMatchedRoutingRuleId(),
                routing.getTargetTopicBindingId(),
                routing.getTargetChatBindingId(),
                routing.getTargetTopicId());

        // 6. Phase 164: Telegram dispatch endi AFTER_COMMIT listener orqali
        // amalga oshiriladi — bu yerda faqat event publish bo'ladi va
        // intake transaction commit'idan keyin listener Telegram HTTP'ga
        // boradi. Routing prepared bo'lmasa event umuman publish bo'lmaydi.
        publishTelegramCardDispatchEventSafely(result);

        return result;
    }

    /**
     * Phase 164 — fail-soft Telegram dispatch event publish boundary.
     *
     * <p>Routing prepared bo'lmagan holatda hech qanday event publish
     * bo'lmaydi (listener'da defensiv guard ham bor, lekin bu yerda
     * fail-fast — kerak emas event'ni emas yo'naltirish).</p>
     *
     * <p>Kutilmagan {@link RuntimeException} (masalan
     * {@link IntakeResult#toPreparedDeliveryTarget()} ichidagi NPE yoki
     * publishEvent infrastructure muammosi) intake javobini buzmasligi
     * uchun ushlanadi. Log faqat <em>bounded metadata</em>:
     * {@code tenantId}, {@code workItemId}, {@code exceptionType}
     * (Phase 160 mini-fix logging hardening pattern bilan bir xil).</p>
     */
    private void publishTelegramCardDispatchEventSafely(IntakeResult result) {
        if (!result.isRoutingPrepared()) {
            return;
        }
        try {
            PreparedDeliveryTarget target = result.toPreparedDeliveryTarget();
            eventPublisher.publishEvent(new TelegramCardDispatchRequested(
                    target,
                    TelegramCardDispatchRequested.SOURCE_INTAKE,
                    null));
        } catch (RuntimeException ex) {
            log.warn("Telegram card dispatch event publish failed (fail-soft) tenantId={} workItemId={} exceptionType={}",
                    result.getTenantId(), result.getWorkItemId(),
                    ex.getClass().getSimpleName());
        }
    }

    /**
     * Command invariantlarini tekshiradi.
     * Bu application-level validatsiya — domain rule'lar WorkItemCommandService ichida qoladi.
     */
    private void validateCommand(IntakeCommand command) {
        if (command.getTenantId() == null) {
            throw new BusinessRuleException("INTAKE_VALIDATION",
                    "tenantId majburiy");
        }
        if (command.getTypeCode() == null) {
            throw new BusinessRuleException("INTAKE_VALIDATION",
                    "typeCode majburiy");
        }
        if (command.getTitle() == null || command.getTitle().isBlank()) {
            throw new BusinessRuleException("INTAKE_VALIDATION",
                    "title bo'sh bo'lishi mumkin emas");
        }
        if (command.getCreatedByUserId() == null) {
            throw new BusinessRuleException("INTAKE_VALIDATION",
                    "createdByUserId majburiy");
        }
        if (command.getActionSource() == null || command.getActionSource().isBlank()) {
            throw new BusinessRuleException("INTAKE_VALIDATION",
                    "actionSource bo'sh bo'lishi mumkin emas");
        }
    }

    /**
     * Workflow definitionni aniqlaydi:
     * - Agar command'da workflowDefinitionId berilgan bo'lsa — tenant-safe lookup qiladi
     * - Agar berilmagan bo'lsa — tenant va typeCode bo'yicha active workflow topadi
     */
    private WorkflowDefinition resolveWorkflowDefinition(IntakeCommand command) {
        if (command.getWorkflowDefinitionId() != null) {
            return tenantConfigQueryService
                    .findWorkflowDefinitionById(command.getTenantId(), command.getWorkflowDefinitionId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "WorkflowDefinition", command.getWorkflowDefinitionId()));
        }

        // Type bo'yicha active workflow auto-resolve (deterministic: 0→fail, 1→use, >1→fail)
        List<WorkflowDefinition> activeWorkflows = tenantConfigQueryService
                .findActiveWorkflowDefinitionsByType(command.getTenantId(), command.getTypeCode().name());

        if (activeWorkflows.isEmpty()) {
            throw new BusinessRuleException("NO_ACTIVE_WORKFLOW",
                    "'" + command.getTypeCode() + "' turi uchun aktiv workflow ta'rifi topilmadi");
        }

        if (activeWorkflows.size() > 1) {
            throw new BusinessRuleException("AMBIGUOUS_WORKFLOW",
                    "'" + command.getTypeCode() + "' turi uchun " + activeWorkflows.size()
                            + " ta aktiv workflow topildi. workflowDefinitionId ni aniq ko'rsating");
        }

        return activeWorkflows.getFirst();
    }

    /**
     * Initial statusni aniqlaydi:
     * - Agar command'da initialStatusCode berilgan bo'lsa — shuni ishlatadi
     * - Agar berilmagan bo'lsa — workflow definition'dagi initial=true statusni topadi
     */
    private String resolveInitialStatus(IntakeCommand command, WorkflowDefinition definition) {
        if (command.getInitialStatusCode() != null && !command.getInitialStatusCode().isBlank()) {
            return command.getInitialStatusCode();
        }

        // Workflow definition'dan initial status auto-resolve
        List<WorkflowStatus> initialStatuses = definition.getStatuses().stream()
                .filter(WorkflowStatus::isInitial)
                .toList();

        if (initialStatuses.isEmpty()) {
            throw new BusinessRuleException("NO_INITIAL_STATUS",
                    "'" + definition.getName() + "' workflow ta'rifida boshlang'ich status topilmadi");
        }

        if (initialStatuses.size() > 1) {
            throw new BusinessRuleException("AMBIGUOUS_INITIAL_STATUS",
                    "'" + definition.getName() + "' workflow ta'rifida bir nechta boshlang'ich status mavjud. "
                            + "initialStatusCode ni aniq ko'rsating");
        }

        return initialStatuses.getFirst().getName();
    }
}
