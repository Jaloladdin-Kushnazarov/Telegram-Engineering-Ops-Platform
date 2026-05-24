package com.engops.platform.web;

import com.engops.platform.admin.WorkItemSummaryItem;
import com.engops.platform.admin.WorkItemSummaryReadFacade;
import com.engops.platform.infrastructure.security.CurrentActor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Phase 210 — work items list HTMX shim.
 *
 * <p>Returns a Thymeleaf row fragment for the {@code /web/work-items}
 * page table. Reuses Phase 195+ {@link WorkItemSummaryReadFacade} which
 * already enforces {@code TENANT_CONFIG_READ} via
 * {@code AdminAuthorizationService.authorizeRead(tenantId, actorUserId)}.</p>
 *
 * <p>Phase 210 scope: top 50 by deterministic order from the existing
 * facade (openedAt DESC). No pagination — Phase 211 will add
 * cursor-based hx-trigger="revealed" loading.</p>
 */
@Controller
@RequestMapping("/web/api/work-items")
public class WorkItemsWebController {

    static final int DEFAULT_LIMIT = 50;

    private final WorkItemSummaryReadFacade workItemSummaryReadFacade;

    public WorkItemsWebController(WorkItemSummaryReadFacade workItemSummaryReadFacade) {
        this.workItemSummaryReadFacade = workItemSummaryReadFacade;
    }

    @GetMapping("/list")
    public String list(@RequestParam UUID tenantId,
                        @CurrentActor UUID actorUserId,
                        Model model) {
        List<WorkItemSummaryItem> items =
                workItemSummaryReadFacade.getSummaryList(tenantId, DEFAULT_LIMIT, actorUserId);
        model.addAttribute("items", items);
        return "web/fragments/work-item-rows :: rows";
    }
}
