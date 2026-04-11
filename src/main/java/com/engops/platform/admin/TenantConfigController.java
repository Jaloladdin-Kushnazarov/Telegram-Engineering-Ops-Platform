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
 * - DELETE /workflow-definitions/{definitionId} — workflow definition o'chirish
 * - POST /chat-bindings — yangi chat binding yaratish
 * - PATCH /chat-bindings/{chatBindingId} — chat binding metadata yangilash
 * - POST /chat-bindings/{chatBindingId}/activate — chat binding aktivlashtirish
 * - POST /chat-bindings/{chatBindingId}/deactivate — chat binding deaktivlashtirish
 * - DELETE /chat-bindings/{chatBindingId} — chat binding o'chirish
 * - POST /topic-bindings — yangi topic binding yaratish
 * - PATCH /topic-bindings/{topicBindingId} — topic binding metadata yangilash
 * - POST /topic-bindings/{topicBindingId}/activate — topic binding aktivlashtirish
 * - POST /topic-bindings/{topicBindingId}/deactivate — topic binding deaktivlashtirish
 * - DELETE /topic-bindings/{topicBindingId} — topic binding o'chirish
 * - POST /memberships/{membershipId}/activate — a'zolikni aktivlashtirish
 * - POST /memberships/{membershipId}/suspend — a'zolikni SUSPENDED holatga o'tkazish
 * - POST /memberships/{membershipId}/remove — a'zolikni REMOVED holatga o'tkazish
 * - POST /memberships/{membershipId}/roles — a'zolikka rol tayinlash
 * - DELETE /memberships/{membershipId}/roles/{roleId} — a'zolikdan rolni olib tashlash
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
     * Workflow definition'ni o'chiradi.
     *
     * @param definitionId workflow definition identifikatori
     * @param tenantId tenant identifikatori
     * @return 204 No Content
     */
    @DeleteMapping("/workflow-definitions/{definitionId}")
    public ResponseEntity<Void> deleteWorkflowDefinition(
            @PathVariable UUID definitionId,
            @RequestParam UUID tenantId) {

        writeFacade.deleteWorkflowDefinition(tenantId, definitionId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Yangi chat binding yaratadi.
     *
     * @param tenantId tenant identifikatori
     * @param request yaratish so'rovi
     * @return yaratilgan chat binding (201 Created)
     */
    @PostMapping("/chat-bindings")
    public ResponseEntity<TenantConfigChatBindingCreatedResponse> createChatBinding(
            @RequestParam UUID tenantId,
            @RequestBody CreateChatBindingRequest request) {

        TenantConfigWriteFacade.ChatBindingCreatedView view =
                writeFacade.createChatBinding(tenantId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toChatBindingCreatedResponse(view));
    }

    /**
     * Chat binding metadata'sini yangilaydi (chatTitle, bindingType).
     *
     * @param chatBindingId chat binding identifikatori
     * @param tenantId tenant identifikatori
     * @param request yangilash so'rovi
     * @return yangilangan chat binding (200 OK)
     */
    @PatchMapping("/chat-bindings/{chatBindingId}")
    public ResponseEntity<TenantConfigChatBindingCreatedResponse> updateChatBinding(
            @PathVariable UUID chatBindingId,
            @RequestParam UUID tenantId,
            @RequestBody UpdateChatBindingRequest request) {

        TenantConfigWriteFacade.ChatBindingCreatedView view =
                writeFacade.updateChatBinding(tenantId, chatBindingId, request);

        return ResponseEntity.ok(toChatBindingCreatedResponse(view));
    }

    /**
     * Chat binding'ni aktiv holatga o'tkazadi.
     *
     * @param chatBindingId chat binding identifikatori
     * @param tenantId tenant identifikatori
     * @return yangilangan chat binding (200 OK)
     */
    @PostMapping("/chat-bindings/{chatBindingId}/activate")
    public ResponseEntity<TenantConfigChatBindingCreatedResponse> activateChatBinding(
            @PathVariable UUID chatBindingId,
            @RequestParam UUID tenantId) {

        TenantConfigWriteFacade.ChatBindingCreatedView view =
                writeFacade.activateChatBinding(tenantId, chatBindingId);

        return ResponseEntity.ok(toChatBindingCreatedResponse(view));
    }

    /**
     * Chat binding'ni noaktiv holatga o'tkazadi.
     *
     * @param chatBindingId chat binding identifikatori
     * @param tenantId tenant identifikatori
     * @return yangilangan chat binding (200 OK)
     */
    @PostMapping("/chat-bindings/{chatBindingId}/deactivate")
    public ResponseEntity<TenantConfigChatBindingCreatedResponse> deactivateChatBinding(
            @PathVariable UUID chatBindingId,
            @RequestParam UUID tenantId) {

        TenantConfigWriteFacade.ChatBindingCreatedView view =
                writeFacade.deactivateChatBinding(tenantId, chatBindingId);

        return ResponseEntity.ok(toChatBindingCreatedResponse(view));
    }

    /**
     * Chat binding'ni o'chiradi.
     *
     * @param chatBindingId chat binding identifikatori
     * @param tenantId tenant identifikatori
     * @return 204 No Content
     */
    @DeleteMapping("/chat-bindings/{chatBindingId}")
    public ResponseEntity<Void> deleteChatBinding(
            @PathVariable UUID chatBindingId,
            @RequestParam UUID tenantId) {

        writeFacade.deleteChatBinding(tenantId, chatBindingId);

        return ResponseEntity.noContent().build();
    }

    private TenantConfigChatBindingCreatedResponse toChatBindingCreatedResponse(
            TenantConfigWriteFacade.ChatBindingCreatedView view) {
        return new TenantConfigChatBindingCreatedResponse(
                view.tenantId(),
                view.chatBindingId(),
                view.chatId(),
                view.chatTitle(),
                view.bindingType(),
                view.active(),
                view.createdAt());
    }

    // ========== TelegramTopicBinding write endpoint'lar ==========

    /**
     * Yangi topic binding yaratadi.
     *
     * @param tenantId tenant identifikatori
     * @param request yaratish so'rovi
     * @return yaratilgan topic binding (201 Created)
     */
    @PostMapping("/topic-bindings")
    public ResponseEntity<TenantConfigTopicBindingCreatedResponse> createTopicBinding(
            @RequestParam UUID tenantId,
            @RequestBody CreateTopicBindingRequest request) {

        TenantConfigWriteFacade.TopicBindingView view =
                writeFacade.createTopicBinding(tenantId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toTopicBindingResponse(view));
    }

    /**
     * Topic binding metadata'sini yangilaydi (topicName).
     *
     * @param topicBindingId topic binding identifikatori
     * @param tenantId tenant identifikatori
     * @param request yangilash so'rovi
     * @return yangilangan topic binding (200 OK)
     */
    @PatchMapping("/topic-bindings/{topicBindingId}")
    public ResponseEntity<TenantConfigTopicBindingCreatedResponse> updateTopicBinding(
            @PathVariable UUID topicBindingId,
            @RequestParam UUID tenantId,
            @RequestBody UpdateTopicBindingRequest request) {

        TenantConfigWriteFacade.TopicBindingView view =
                writeFacade.updateTopicBinding(tenantId, topicBindingId, request);

        return ResponseEntity.ok(toTopicBindingResponse(view));
    }

    /**
     * Topic binding'ni aktiv holatga o'tkazadi.
     *
     * @param topicBindingId topic binding identifikatori
     * @param tenantId tenant identifikatori
     * @return yangilangan topic binding (200 OK)
     */
    @PostMapping("/topic-bindings/{topicBindingId}/activate")
    public ResponseEntity<TenantConfigTopicBindingCreatedResponse> activateTopicBinding(
            @PathVariable UUID topicBindingId,
            @RequestParam UUID tenantId) {

        TenantConfigWriteFacade.TopicBindingView view =
                writeFacade.activateTopicBinding(tenantId, topicBindingId);

        return ResponseEntity.ok(toTopicBindingResponse(view));
    }

    /**
     * Topic binding'ni noaktiv holatga o'tkazadi.
     *
     * @param topicBindingId topic binding identifikatori
     * @param tenantId tenant identifikatori
     * @return yangilangan topic binding (200 OK)
     */
    @PostMapping("/topic-bindings/{topicBindingId}/deactivate")
    public ResponseEntity<TenantConfigTopicBindingCreatedResponse> deactivateTopicBinding(
            @PathVariable UUID topicBindingId,
            @RequestParam UUID tenantId) {

        TenantConfigWriteFacade.TopicBindingView view =
                writeFacade.deactivateTopicBinding(tenantId, topicBindingId);

        return ResponseEntity.ok(toTopicBindingResponse(view));
    }

    /**
     * Topic binding'ni o'chiradi.
     *
     * @param topicBindingId topic binding identifikatori
     * @param tenantId tenant identifikatori
     * @return 204 No Content
     */
    @DeleteMapping("/topic-bindings/{topicBindingId}")
    public ResponseEntity<Void> deleteTopicBinding(
            @PathVariable UUID topicBindingId,
            @RequestParam UUID tenantId) {

        writeFacade.deleteTopicBinding(tenantId, topicBindingId);

        return ResponseEntity.noContent().build();
    }

    private TenantConfigTopicBindingCreatedResponse toTopicBindingResponse(
            TenantConfigWriteFacade.TopicBindingView view) {
        return new TenantConfigTopicBindingCreatedResponse(
                view.tenantId(),
                view.topicBindingId(),
                view.chatBindingId(),
                view.topicId(),
                view.topicName(),
                view.purpose(),
                view.active(),
                view.createdAt());
    }

    // ========== Membership status lifecycle endpoint'lar ==========

    /**
     * A'zolikni aktiv holatga o'tkazadi.
     *
     * @param membershipId a'zolik identifikatori
     * @param tenantId tenant identifikatori
     * @return yangilangan membership (200 OK)
     */
    @PostMapping("/memberships/{membershipId}/activate")
    public ResponseEntity<TenantConfigMembershipStatusResponse> activateMembership(
            @PathVariable UUID membershipId,
            @RequestParam UUID tenantId) {

        TenantConfigWriteFacade.MembershipStatusView view =
                writeFacade.activateMembership(tenantId, membershipId);

        return ResponseEntity.ok(toMembershipStatusResponse(view));
    }

    /**
     * A'zolikni SUSPENDED holatga o'tkazadi.
     *
     * @param membershipId a'zolik identifikatori
     * @param tenantId tenant identifikatori
     * @return yangilangan membership (200 OK)
     */
    @PostMapping("/memberships/{membershipId}/suspend")
    public ResponseEntity<TenantConfigMembershipStatusResponse> suspendMembership(
            @PathVariable UUID membershipId,
            @RequestParam UUID tenantId) {

        TenantConfigWriteFacade.MembershipStatusView view =
                writeFacade.suspendMembership(tenantId, membershipId);

        return ResponseEntity.ok(toMembershipStatusResponse(view));
    }

    /**
     * A'zolikni REMOVED holatga o'tkazadi (lifecycle status transition — hard delete emas).
     *
     * @param membershipId a'zolik identifikatori
     * @param tenantId tenant identifikatori
     * @return yangilangan membership (200 OK)
     */
    @PostMapping("/memberships/{membershipId}/remove")
    public ResponseEntity<TenantConfigMembershipStatusResponse> removeMembership(
            @PathVariable UUID membershipId,
            @RequestParam UUID tenantId) {

        TenantConfigWriteFacade.MembershipStatusView view =
                writeFacade.removeMembership(tenantId, membershipId);

        return ResponseEntity.ok(toMembershipStatusResponse(view));
    }

    /**
     * A'zolikka global rolni tayinlaydi (membership-role binding yaratadi).
     *
     * @param membershipId a'zolik identifikatori
     * @param tenantId tenant identifikatori
     * @param request yaratish so'rovi
     * @return yaratilgan binding (201 Created)
     */
    @PostMapping("/memberships/{membershipId}/roles")
    public ResponseEntity<TenantConfigMembershipRoleBindingCreatedResponse> assignRoleToMembership(
            @PathVariable UUID membershipId,
            @RequestParam UUID tenantId,
            @RequestBody CreateMembershipRoleBindingRequest request) {

        TenantConfigWriteFacade.MembershipRoleBindingView view =
                writeFacade.assignRoleToMembership(tenantId, membershipId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toMembershipRoleBindingResponse(view));
    }

    /**
     * A'zolikdan rolni olib tashlaydi (membership-role binding o'chiradi).
     *
     * @param membershipId a'zolik identifikatori
     * @param roleId rol identifikatori
     * @param tenantId tenant identifikatori
     * @return 204 No Content
     */
    @DeleteMapping("/memberships/{membershipId}/roles/{roleId}")
    public ResponseEntity<Void> unassignRoleFromMembership(
            @PathVariable UUID membershipId,
            @PathVariable UUID roleId,
            @RequestParam UUID tenantId) {

        writeFacade.unassignRoleFromMembership(tenantId, membershipId, roleId);

        return ResponseEntity.noContent().build();
    }

    private TenantConfigMembershipRoleBindingCreatedResponse toMembershipRoleBindingResponse(
            TenantConfigWriteFacade.MembershipRoleBindingView view) {
        return new TenantConfigMembershipRoleBindingCreatedResponse(
                view.tenantId(),
                view.membershipId(),
                view.bindingId(),
                view.roleId(),
                view.roleCode(),
                view.createdAt());
    }

    private TenantConfigMembershipStatusResponse toMembershipStatusResponse(
            TenantConfigWriteFacade.MembershipStatusView view) {
        return new TenantConfigMembershipStatusResponse(
                view.tenantId(),
                view.membershipId(),
                view.userId(),
                view.status(),
                view.createdAt());
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
