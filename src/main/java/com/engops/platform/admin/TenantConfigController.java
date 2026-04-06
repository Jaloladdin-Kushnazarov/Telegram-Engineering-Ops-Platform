package com.engops.platform.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Tenant konfiguratsiyasi uchun read-only admin endpoint'lar.
 *
 * Endpoint'lar:
 * - GET /details — tenant konfiguratsiyasining compact details view'i
 * - GET /workflow-definitions — tenant workflow ta'riflari ro'yxati
 * - GET /routing-rules — tenant routing qoidalari ro'yxati
 * - GET /chat-bindings — tenant Telegram chat bog'lanishlari ro'yxati
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

    /**
     * Tenant workflow ta'riflari ro'yxatini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @return workflow ta'riflari ro'yxati
     */
    @GetMapping("/workflow-definitions")
    public ResponseEntity<TenantConfigWorkflowListResponse> getWorkflowDefinitions(
            @RequestParam UUID tenantId) {

        TenantConfigDetailsFacade.WorkflowDefinitionListView view =
                detailsFacade.getWorkflowDefinitions(tenantId);

        return ResponseEntity.ok(toWorkflowListResponse(view));
    }

    /**
     * Tenant routing qoidalari ro'yxatini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @return routing qoidalari ro'yxati
     */
    @GetMapping("/routing-rules")
    public ResponseEntity<TenantConfigRoutingRuleListResponse> getRoutingRules(
            @RequestParam UUID tenantId) {

        TenantConfigDetailsFacade.RoutingRuleListView view =
                detailsFacade.getRoutingRules(tenantId);

        return ResponseEntity.ok(toRoutingRuleListResponse(view));
    }

    /**
     * Tenant Telegram chat bog'lanishlari ro'yxatini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @return chat bog'lanishlari ro'yxati
     */
    @GetMapping("/chat-bindings")
    public ResponseEntity<TenantConfigChatBindingListResponse> getChatBindings(
            @RequestParam UUID tenantId) {

        TenantConfigDetailsFacade.ChatBindingListView view =
                detailsFacade.getChatBindings(tenantId);

        return ResponseEntity.ok(toChatBindingListResponse(view));
    }

    private TenantConfigChatBindingListResponse toChatBindingListResponse(
            TenantConfigDetailsFacade.ChatBindingListView view) {
        List<TenantConfigChatBindingListResponse.ChatBindingItem> items = view.items().stream()
                .map(i -> new TenantConfigChatBindingListResponse.ChatBindingItem(
                        i.chatBindingId(),
                        i.chatId(),
                        i.chatTitle(),
                        i.bindingType(),
                        i.active(),
                        i.activeTopicBindingCount(),
                        i.createdAt()))
                .toList();
        return new TenantConfigChatBindingListResponse(view.tenantId(), items);
    }

    private TenantConfigRoutingRuleListResponse toRoutingRuleListResponse(
            TenantConfigDetailsFacade.RoutingRuleListView view) {
        List<TenantConfigRoutingRuleListResponse.RoutingRuleItem> items = view.items().stream()
                .map(i -> new TenantConfigRoutingRuleListResponse.RoutingRuleItem(
                        i.ruleId(),
                        i.name(),
                        i.workItemType(),
                        i.priority(),
                        i.targetTopicBindingId(),
                        i.active(),
                        i.createdAt()))
                .toList();
        return new TenantConfigRoutingRuleListResponse(view.tenantId(), items);
    }

    private TenantConfigWorkflowListResponse toWorkflowListResponse(
            TenantConfigDetailsFacade.WorkflowDefinitionListView view) {
        List<TenantConfigWorkflowListResponse.WorkflowDefinitionItem> items = view.items().stream()
                .map(i -> new TenantConfigWorkflowListResponse.WorkflowDefinitionItem(
                        i.definitionId(),
                        i.name(),
                        i.workItemType(),
                        i.description(),
                        i.active(),
                        i.createdAt()))
                .toList();
        return new TenantConfigWorkflowListResponse(view.tenantId(), items);
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
