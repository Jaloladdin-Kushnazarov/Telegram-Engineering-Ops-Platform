package com.engops.platform.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Tenant konfiguratsiyasi uchun read-only admin endpoint'lar.
 *
 * Endpoint'lar:
 * - GET /details — tenant konfiguratsiyasining compact details view'i
 *
 * Faqat GET — write operatsiya yo'q.
 *
 * Bu controller thin adapter:
 * - HTTP request parametrlarini facade'ga uzatadi
 * - Facade natijasini response DTO'ga map qiladi
 * - ResourceNotFoundException (404) va IllegalArgumentException (400)
 *   GlobalExceptionHandler tomonidan qayta ishlanadi
 */
@RestController
@RequestMapping("/api/admin/tenant-config")
public class TenantConfigController {

    private final TenantConfigDetailsFacade detailsFacade;

    public TenantConfigController(TenantConfigDetailsFacade detailsFacade) {
        this.detailsFacade = detailsFacade;
    }

    /**
     * Tenant konfiguratsiyasining compact details view'ini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @return compact tenant config details
     */
    @GetMapping("/details")
    public ResponseEntity<TenantConfigDetailsResponse> getDetails(
            @RequestParam UUID tenantId) {

        TenantConfigDetailsFacade.TenantConfigDetailsView view =
                detailsFacade.getDetails(tenantId);

        return ResponseEntity.ok(toResponse(view));
    }

    private TenantConfigDetailsResponse toResponse(
            TenantConfigDetailsFacade.TenantConfigDetailsView view) {
        return new TenantConfigDetailsResponse(
                new TenantConfigDetailsResponse.TenantSection(
                        view.tenantId(),
                        view.name(),
                        view.slug(),
                        view.timezone(),
                        view.status(),
                        view.createdAt()),
                new TenantConfigDetailsResponse.MembershipsSummarySection(
                        view.totalMembershipCount(),
                        view.activeMembershipCount()),
                new TenantConfigDetailsResponse.WorkflowSummarySection(
                        view.totalWorkflowDefinitionCount(),
                        view.activeWorkflowDefinitionCount()),
                new TenantConfigDetailsResponse.RoutingSummarySection(
                        view.totalRoutingRuleCount(),
                        view.activeRoutingRuleCount()),
                new TenantConfigDetailsResponse.TelegramSummarySection(
                        view.activeChatBindingCount(),
                        view.activeTopicBindingCount()));
    }
}
