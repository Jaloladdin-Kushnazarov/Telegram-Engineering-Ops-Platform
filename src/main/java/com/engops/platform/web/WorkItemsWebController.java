package com.engops.platform.web;

import com.engops.platform.admin.WorkItemSummaryItem;
import com.engops.platform.admin.WorkItemSummaryReadFacade;
import com.engops.platform.infrastructure.security.CurrentActor;
import com.engops.platform.intake.IntakeApplicationService;
import com.engops.platform.intake.IntakeCommand;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.workitem.model.WorkItemType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Phase 210 — work items list HTMX shim.
 * Phase 220b — create + assign POST shim (intake-based).
 *
 * <p>Returns a Thymeleaf row fragment for the {@code /web/work-items}
 * page table. Reuses Phase 195+ {@link WorkItemSummaryReadFacade} which
 * already enforces {@code TENANT_CONFIG_READ} via
 * {@code AdminAuthorizationService.authorizeRead(tenantId, actorUserId)}.</p>
 *
 * <p><strong>Phase 220b POST:</strong> {@code create} <em>does not</em> call
 * {@code WorkItemCommandService} directly — it delegates to
 * {@link IntakeApplicationService#submit(IntakeCommand)} which enforces
 * {@code WORK_ITEM_CREATE}, auto-resolves the active workflow definition +
 * initial status, validates the optional owner ({@code INVALID_OWNER}), and
 * triggers the AFTER_COMMIT Telegram dispatch chain. The web layer only maps
 * form params → {@link IntakeCommand} and renders the HTMX result/error.</p>
 *
 * <p>Web layer exception model (HTMX-friendly): AccessDenied / BusinessRule /
 * ResourceNotFound / bad enum-or-UUID → 200 + inline error fragment retargeted
 * to {@code #work-item-error} (mirrors Phase 219b member invite). The success
 * path re-renders the rows fragment and sets {@code HX-Trigger: workItemCreated}
 * so the page table refreshes and the modal closes.</p>
 */
@Controller
@RequestMapping("/web/api/work-items")
public class WorkItemsWebController {

    static final int DEFAULT_LIMIT = 50;
    static final String ACTION_SOURCE = "WEB_UI";

    private final WorkItemSummaryReadFacade workItemSummaryReadFacade;
    private final IntakeApplicationService intakeApplicationService;

    public WorkItemsWebController(WorkItemSummaryReadFacade workItemSummaryReadFacade,
                                  IntakeApplicationService intakeApplicationService) {
        this.workItemSummaryReadFacade = workItemSummaryReadFacade;
        this.intakeApplicationService = intakeApplicationService;
    }

    @GetMapping("/list")
    public String list(@RequestParam UUID tenantId,
                        @CurrentActor UUID actorUserId,
                        Model model) {
        populateRows(tenantId, actorUserId, model);
        return "web/fragments/work-item-rows :: rows";
    }

    /**
     * Phase 220b — yangi work item yaratadi (ixtiyoriy assignee = owner) va
     * yangilangan rows fragment'ini qaytaradi.
     */
    @PostMapping
    public String create(@RequestParam UUID tenantId,
                         @CurrentActor UUID actorUserId,
                         @RequestParam String type,
                         @RequestParam String severity,
                         @RequestParam String title,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) String assigneeUserId,
                         Model model,
                         HttpServletResponse response) {
        try {
            IntakeCommand command = IntakeCommand.builder()
                    .tenantId(tenantId)
                    .createdByUserId(actorUserId)
                    .typeCode(WorkItemType.valueOf(type))
                    .severityCode(severity)
                    .title(title)
                    .description(emptyToNull(description))
                    .ownerUserId(parseOwner(assigneeUserId))
                    .actionSource(ACTION_SOURCE)
                    // workflowDefinitionId + initialStatusCode — intake auto-resolve.
                    .build();
            intakeApplicationService.submit(command);

            populateRows(tenantId, actorUserId, model);
            response.setHeader("HX-Trigger", "workItemCreated");
            return "web/fragments/work-item-rows :: rows";
        } catch (AccessDeniedException | BusinessRuleException
                 | ResourceNotFoundException | IllegalArgumentException ex) {
            response.setHeader("HX-Reswap", "innerHTML");
            response.setHeader("HX-Retarget", "#work-item-error");
            model.addAttribute("error", ex.getMessage());
            return "web/fragments/work-item-rows :: createError";
        }
    }

    private void populateRows(UUID tenantId, UUID actorUserId, Model model) {
        List<WorkItemSummaryItem> items =
                workItemSummaryReadFacade.getSummaryList(tenantId, DEFAULT_LIMIT, actorUserId);
        model.addAttribute("items", items);
    }

    /** Bo'sh/whitespace stringni null'ga normalizatsiya qiladi. */
    private static String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /** Bo'sh assignee → null (tayinlanmagan); aks holda UUID parse. */
    private static UUID parseOwner(String assigneeUserId) {
        String normalized = emptyToNull(assigneeUserId);
        return normalized == null ? null : UUID.fromString(normalized);
    }
}
