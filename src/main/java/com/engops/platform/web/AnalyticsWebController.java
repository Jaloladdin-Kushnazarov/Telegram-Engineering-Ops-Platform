package com.engops.platform.web;

import com.engops.platform.analytics.AnalyticsAggregateResult;
import com.engops.platform.analytics.AnalyticsQueryService;
import com.engops.platform.infrastructure.security.CurrentActor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Phase 209B — HTMX-friendly web-side shim over Phase 205 analytics.
 *
 * <p>Bu controller {@code /web/api/analytics/*} URL'larida Thymeleaf
 * HTML fragment'larini qaytaradi (JSON emas). Dashboard cards
 * {@code hx-get} bilan ushbu endpoint'larga ulanadi va javob fragment'ni
 * skeleton card o'rniga {@code hx-swap="outerHTML"} bilan almashtiradi.</p>
 *
 * <p>Phase 205 {@link AnalyticsQueryService} mutlaqo o'zgartirilmaydi —
 * bu adapter shu service'ni qayta ishlatadi. Authorization service
 * layer'da (TENANT_CONFIG_READ orqali AdminAuthorizationService).</p>
 *
 * <p>{@code /web/api/**} JWT-protected (Phase 209 SecurityConfig
 * matcher). Browser-side {@code auth.js} HTMX so'rovlariga
 * {@code Authorization: Bearer ...} header'ini avtomatik ulaydi
 * (Phase 208).</p>
 */
@Controller
@RequestMapping("/web/api/analytics")
public class AnalyticsWebController {

    private final AnalyticsQueryService analyticsQueryService;

    public AnalyticsWebController(AnalyticsQueryService analyticsQueryService) {
        this.analyticsQueryService = analyticsQueryService;
    }

    @GetMapping("/by-status")
    public String byStatus(@RequestParam UUID tenantId,
                            @CurrentActor UUID actorUserId,
                            Model model) {
        AnalyticsAggregateResult result =
                analyticsQueryService.workItemsByStatus(tenantId, actorUserId);
        model.addAttribute("title", "By status");
        model.addAttribute("result", result);
        return "web/fragments/chart-bucket-list :: chart";
    }

    @GetMapping("/by-type")
    public String byType(@RequestParam UUID tenantId,
                          @CurrentActor UUID actorUserId,
                          Model model) {
        AnalyticsAggregateResult result =
                analyticsQueryService.workItemsByType(tenantId, actorUserId);
        model.addAttribute("title", "By type");
        model.addAttribute("result", result);
        return "web/fragments/chart-bucket-list :: chart";
    }

    @GetMapping("/by-severity")
    public String bySeverity(@RequestParam UUID tenantId,
                              @CurrentActor UUID actorUserId,
                              Model model) {
        AnalyticsAggregateResult result =
                analyticsQueryService.workItemsBySeverity(tenantId, actorUserId);
        model.addAttribute("title", "By severity");
        model.addAttribute("result", result);
        return "web/fragments/chart-bucket-list :: chart";
    }
}
