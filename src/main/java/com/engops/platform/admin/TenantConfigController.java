package com.engops.platform.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Tenant konfiguratsiyasi uchun admin endpoint'lar.
 *
 * Read endpoint'lar:
 * - GET /details — tenant konfiguratsiyasining compact details view'i
 * - GET /workflow-definitions — tenant workflow ta'riflari ro'yxati
 * - GET /routing-rules — tenant routing qoidalari ro'yxati
 * - GET /chat-bindings — tenant Telegram chat bog'lanishlari ro'yxati
 * - GET /topic-bindings — tenant Telegram topic bog'lanishlari ro'yxati
 * - GET /memberships — tenant a'zolik ro'yxati
 * - GET /roles — global rol katalogi ro'yxati
 *
 * Write endpoint'lar:
 * - POST /workflow-definitions — yangi workflow definition yaratish
 * - PATCH /workflow-definitions/{definitionId} — workflow definition metadata yangilash
 * - POST /workflow-definitions/{definitionId}/activate — workflow definition aktivlashtirish
 * - POST /workflow-definitions/{definitionId}/deactivate — workflow definition deaktivlashtirish
 * - POST /routing-rules — yangi routing rule yaratish
 * - PATCH /routing-rules/{ruleId} — routing rule metadata yangilash
 * - POST /routing-rules/{ruleId}/activate — routing rule aktivlashtirish
 * - POST /routing-rules/{ruleId}/deactivate — routing rule deaktivlashtirish
 * - DELETE /routing-rules/{ruleId} — routing rule o'chirish
 *
 * Bu controller thin adapter:
 * - HTTP request parametrlarini facade'ga uzatadi
 * - Facade natijasini response DTO'ga map qiladi
 * - ResourceNotFoundException (404), IllegalArgumentException (400),
 *   BusinessRuleException (422) GlobalExceptionHandler tomonidan qayta ishlanadi
 */
@RestController
@RequestMapping("/api/admin/tenant-config")
public class TenantConfigController {

    private final TenantConfigDetailsFacade detailsFacade;
    private final TenantConfigWriteFacade writeFacade;

    public TenantConfigController(TenantConfigDetailsFacade detailsFacade,
                                   TenantConfigWriteFacade writeFacade) {
        this.detailsFacade = detailsFacade;
        this.writeFacade = writeFacade;
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

    /**
     * Tenant Telegram topic bog'lanishlari ro'yxatini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @return topic bog'lanishlari ro'yxati
     */
    @GetMapping("/topic-bindings")
    public ResponseEntity<TenantConfigTopicBindingListResponse> getTopicBindings(
            @RequestParam UUID tenantId) {

        TenantConfigDetailsFacade.TopicBindingListView view =
                detailsFacade.getTopicBindings(tenantId);

        return ResponseEntity.ok(toTopicBindingListResponse(view));
    }

    /**
     * Tenant a'zolik ro'yxatini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @return a'zoliklar ro'yxati
     */
    @GetMapping("/memberships")
    public ResponseEntity<TenantConfigMembershipListResponse> getMemberships(
            @RequestParam UUID tenantId) {

        TenantConfigDetailsFacade.MembershipListView view =
                detailsFacade.getMemberships(tenantId);

        return ResponseEntity.ok(toMembershipListResponse(view));
    }

    /**
     * Global rol katalogi ro'yxatini qaytaradi.
     *
     * @param tenantId admin kontekst tenant identifikatori
     * @return global rol katalogi ro'yxati
     */
    @GetMapping("/roles")
    public ResponseEntity<TenantConfigRoleListResponse> getRoles(
            @RequestParam UUID tenantId) {

        TenantConfigDetailsFacade.RoleListView view =
                detailsFacade.getRoles(tenantId);

        return ResponseEntity.ok(toRoleListResponse(view));
    }

    // ========== Write endpoint'lar ==========

    /**
     * Yangi workflow definition yaratadi.
     *
     * @param tenantId tenant identifikatori
     * @param request yaratish so'rovi
     * @return yaratilgan workflow definition (201 Created)
     */
    @PostMapping("/workflow-definitions")
    public ResponseEntity<TenantConfigWorkflowDefinitionCreatedResponse> createWorkflowDefinition(
            @RequestParam UUID tenantId,
            @RequestBody CreateWorkflowDefinitionRequest request) {

        TenantConfigWriteFacade.WorkflowDefinitionCreatedView view =
                writeFacade.createWorkflowDefinition(tenantId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toCreatedResponse(view));
    }

    /**
     * Workflow definition metadata'sini yangilaydi (name, description).
     *
     * @param definitionId workflow definition identifikatori
     * @param tenantId tenant identifikatori
     * @param request yangilash so'rovi
     * @return yangilangan workflow definition (200 OK)
     */
    @PatchMapping("/workflow-definitions/{definitionId}")
    public ResponseEntity<TenantConfigWorkflowDefinitionCreatedResponse> updateWorkflowDefinition(
            @PathVariable UUID definitionId,
            @RequestParam UUID tenantId,
            @RequestBody UpdateWorkflowDefinitionRequest request) {

        TenantConfigWriteFacade.WorkflowDefinitionUpdatedView view =
                writeFacade.updateWorkflowDefinition(tenantId, definitionId, request);

        return ResponseEntity.ok(toUpdatedResponse(view));
    }

    /**
     * Workflow definition'ni aktiv holatga o'tkazadi.
     *
     * @param definitionId workflow definition identifikatori
     * @param tenantId tenant identifikatori
     * @return yangilangan workflow definition (200 OK)
     */
    @PostMapping("/workflow-definitions/{definitionId}/activate")
    public ResponseEntity<TenantConfigWorkflowDefinitionCreatedResponse> activateWorkflowDefinition(
            @PathVariable UUID definitionId,
            @RequestParam UUID tenantId) {

        TenantConfigWriteFacade.WorkflowDefinitionUpdatedView view =
                writeFacade.activateWorkflowDefinition(tenantId, definitionId);

        return ResponseEntity.ok(toUpdatedResponse(view));
    }

    /**
     * Workflow definition'ni noaktiv holatga o'tkazadi.
     *
     * @param definitionId workflow definition identifikatori
     * @param tenantId tenant identifikatori
     * @return yangilangan workflow definition (200 OK)
     */
    @PostMapping("/workflow-definitions/{definitionId}/deactivate")
    public ResponseEntity<TenantConfigWorkflowDefinitionCreatedResponse> deactivateWorkflowDefinition(
            @PathVariable UUID definitionId,
            @RequestParam UUID tenantId) {

        TenantConfigWriteFacade.WorkflowDefinitionUpdatedView view =
                writeFacade.deactivateWorkflowDefinition(tenantId, definitionId);

        return ResponseEntity.ok(toUpdatedResponse(view));
    }

    /**
     * Yangi routing rule yaratadi.
     *
     * @param tenantId tenant identifikatori
     * @param request yaratish so'rovi
     * @return yaratilgan routing rule (201 Created)
     */
    @PostMapping("/routing-rules")
    public ResponseEntity<TenantConfigRoutingRuleCreatedResponse> createRoutingRule(
            @RequestParam UUID tenantId,
            @RequestBody CreateRoutingRuleRequest request) {

        TenantConfigWriteFacade.RoutingRuleCreatedView view =
                writeFacade.createRoutingRule(tenantId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toRoutingRuleCreatedResponse(view));
    }

    /**
     * Routing rule metadata'sini yangilaydi (name, priority, targetTopicBindingId, conditionExpression).
     *
     * @param ruleId routing rule identifikatori
     * @param tenantId tenant identifikatori
     * @param request yangilash so'rovi
     * @return yangilangan routing rule (200 OK)
     */
    @PatchMapping("/routing-rules/{ruleId}")
    public ResponseEntity<TenantConfigRoutingRuleUpdatedResponse> updateRoutingRule(
            @PathVariable UUID ruleId,
            @RequestParam UUID tenantId,
            @RequestBody UpdateRoutingRuleRequest request) {

        TenantConfigWriteFacade.RoutingRuleUpdatedView view =
                writeFacade.updateRoutingRule(tenantId, ruleId, request);

        return ResponseEntity.ok(toRoutingRuleUpdatedResponse(view));
    }

    /**
     * Routing rule'ni aktiv holatga o'tkazadi.
     *
     * @param ruleId routing rule identifikatori
     * @param tenantId tenant identifikatori
     * @return yangilangan routing rule (200 OK)
     */
    @PostMapping("/routing-rules/{ruleId}/activate")
    public ResponseEntity<TenantConfigRoutingRuleUpdatedResponse> activateRoutingRule(
            @PathVariable UUID ruleId,
            @RequestParam UUID tenantId) {

        TenantConfigWriteFacade.RoutingRuleUpdatedView view =
                writeFacade.activateRoutingRule(tenantId, ruleId);

        return ResponseEntity.ok(toRoutingRuleUpdatedResponse(view));
    }

    /**
     * Routing rule'ni noaktiv holatga o'tkazadi.
     *
     * @param ruleId routing rule identifikatori
     * @param tenantId tenant identifikatori
     * @return yangilangan routing rule (200 OK)
     */
    @PostMapping("/routing-rules/{ruleId}/deactivate")
    public ResponseEntity<TenantConfigRoutingRuleUpdatedResponse> deactivateRoutingRule(
            @PathVariable UUID ruleId,
            @RequestParam UUID tenantId) {

        TenantConfigWriteFacade.RoutingRuleUpdatedView view =
                writeFacade.deactivateRoutingRule(tenantId, ruleId);

        return ResponseEntity.ok(toRoutingRuleUpdatedResponse(view));
    }

    /**
     * Routing rule'ni o'chiradi.
     *
     * @param ruleId routing rule identifikatori
     * @param tenantId tenant identifikatori
     * @return 204 No Content
     */
    @DeleteMapping("/routing-rules/{ruleId}")
    public ResponseEntity<Void> deleteRoutingRule(
            @PathVariable UUID ruleId,
            @RequestParam UUID tenantId) {

        writeFacade.deleteRoutingRule(tenantId, ruleId);

        return ResponseEntity.noContent().build();
    }

    private TenantConfigRoutingRuleUpdatedResponse toRoutingRuleUpdatedResponse(
            TenantConfigWriteFacade.RoutingRuleUpdatedView view) {
        return new TenantConfigRoutingRuleUpdatedResponse(
                view.tenantId(),
                view.ruleId(),
                view.name(),
                view.priority(),
                view.targetTopicBindingId(),
                view.conditionExpression(),
                view.active(),
                view.createdAt());
    }

    private TenantConfigRoutingRuleCreatedResponse toRoutingRuleCreatedResponse(
            TenantConfigWriteFacade.RoutingRuleCreatedView view) {
        return new TenantConfigRoutingRuleCreatedResponse(
                view.tenantId(),
                view.ruleId(),
                view.name(),
                view.workItemType(),
                view.priority(),
                view.targetTopicBindingId(),
                view.active(),
                view.createdAt());
    }

    private TenantConfigWorkflowDefinitionCreatedResponse toUpdatedResponse(
            TenantConfigWriteFacade.WorkflowDefinitionUpdatedView view) {
        return new TenantConfigWorkflowDefinitionCreatedResponse(
                view.tenantId(),
                view.definitionId(),
                view.name(),
                view.workItemType(),
                view.description(),
                view.active(),
                view.createdAt());
    }

    private TenantConfigWorkflowDefinitionCreatedResponse toCreatedResponse(
            TenantConfigWriteFacade.WorkflowDefinitionCreatedView view) {
        return new TenantConfigWorkflowDefinitionCreatedResponse(
                view.tenantId(),
                view.definitionId(),
                view.name(),
                view.workItemType(),
                view.description(),
                view.active(),
                view.createdAt());
    }

    // ========== Read response mapping ==========

    private TenantConfigRoleListResponse toRoleListResponse(
            TenantConfigDetailsFacade.RoleListView view) {
        List<TenantConfigRoleListResponse.RoleItem> items = view.items().stream()
                .map(i -> new TenantConfigRoleListResponse.RoleItem(
                        i.roleId(),
                        i.code(),
                        i.name(),
                        i.description(),
                        i.systemRole(),
                        i.createdAt()))
                .toList();
        return new TenantConfigRoleListResponse(view.tenantId(), items);
    }

    private TenantConfigMembershipListResponse toMembershipListResponse(
            TenantConfigDetailsFacade.MembershipListView view) {
        List<TenantConfigMembershipListResponse.MembershipItem> items = view.items().stream()
                .map(i -> new TenantConfigMembershipListResponse.MembershipItem(
                        i.membershipId(),
                        i.userId(),
                        i.telegramUserId(),
                        i.displayName(),
                        i.username(),
                        i.membershipStatus(),
                        i.roleNames(),
                        i.createdAt()))
                .toList();
        return new TenantConfigMembershipListResponse(view.tenantId(), items);
    }

    private TenantConfigTopicBindingListResponse toTopicBindingListResponse(
            TenantConfigDetailsFacade.TopicBindingListView view) {
        List<TenantConfigTopicBindingListResponse.TopicBindingItem> items = view.items().stream()
                .map(i -> new TenantConfigTopicBindingListResponse.TopicBindingItem(
                        i.topicBindingId(),
                        i.chatBindingId(),
                        i.chatId(),
                        i.chatTitle(),
                        i.topicId(),
                        i.topicName(),
                        i.purpose(),
                        i.active(),
                        i.createdAt()))
                .toList();
        return new TenantConfigTopicBindingListResponse(view.tenantId(), items);
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
