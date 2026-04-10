package com.engops.platform.admin;

import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TenantConfigController @WebMvcTest testlari.
 *
 * Tekshiruvlar:
 * - details success path: to'g'ri HTTP status va response body
 * - details tenant not found: 404 qaytariladi
 * - details null tenantId: 400 qaytariladi
 * - response JSON structure nested section'lar bilan to'g'ri
 */
@WebMvcTest(TenantConfigController.class)
class TenantConfigControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantConfigDetailsFacade detailsFacade;

    @MockBean
    private TenantConfigWriteFacade writeFacade;

    @Test
    void detailsReturnsCorrectStructuredResponse() throws Exception {
        var view = new TenantConfigDetailsFacade.TenantConfigDetailsView(
                TENANT_ID,
                "Test Tenant",
                "test-tenant",
                "Asia/Tashkent",
                "ACTIVE",
                Instant.parse("2026-01-15T08:00:00Z"),
                5, 3,     // membership: total, active
                2, 1,     // workflow: total, active
                4, 2,     // routing: total, active
                1, 3);    // telegram: chat bindings, topic bindings

        when(detailsFacade.getDetails(TENANT_ID)).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/details")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                // tenant section
                .andExpect(jsonPath("$.tenant.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.tenant.name").value("Test Tenant"))
                .andExpect(jsonPath("$.tenant.slug").value("test-tenant"))
                .andExpect(jsonPath("$.tenant.timezone").value("Asia/Tashkent"))
                .andExpect(jsonPath("$.tenant.status").value("ACTIVE"))
                .andExpect(jsonPath("$.tenant.createdAt").value("2026-01-15T08:00:00Z"))
                // membershipsSummary section
                .andExpect(jsonPath("$.membershipsSummary.totalMembershipCount").value(5))
                .andExpect(jsonPath("$.membershipsSummary.activeMembershipCount").value(3))
                // workflowSummary section
                .andExpect(jsonPath("$.workflowSummary.totalWorkflowDefinitionCount").value(2))
                .andExpect(jsonPath("$.workflowSummary.activeWorkflowDefinitionCount").value(1))
                // routingSummary section
                .andExpect(jsonPath("$.routingSummary.totalRoutingRuleCount").value(4))
                .andExpect(jsonPath("$.routingSummary.activeRoutingRuleCount").value(2))
                // telegramSummary section
                .andExpect(jsonPath("$.telegramSummary.activeChatBindingCount").value(1))
                .andExpect(jsonPath("$.telegramSummary.activeTopicBindingCount").value(3));
    }

    @Test
    void detailsWithZeroCountsReturnsValidResponse() throws Exception {
        var view = new TenantConfigDetailsFacade.TenantConfigDetailsView(
                TENANT_ID,
                "Empty Tenant",
                "empty-tenant",
                "UTC",
                "ACTIVE",
                Instant.parse("2026-03-01T00:00:00Z"),
                0, 0, 0, 0, 0, 0, 0, 0);

        when(detailsFacade.getDetails(TENANT_ID)).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/details")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.tenant.name").value("Empty Tenant"))
                .andExpect(jsonPath("$.membershipsSummary.totalMembershipCount").value(0))
                .andExpect(jsonPath("$.membershipsSummary.activeMembershipCount").value(0))
                .andExpect(jsonPath("$.workflowSummary.totalWorkflowDefinitionCount").value(0))
                .andExpect(jsonPath("$.workflowSummary.activeWorkflowDefinitionCount").value(0))
                .andExpect(jsonPath("$.routingSummary.totalRoutingRuleCount").value(0))
                .andExpect(jsonPath("$.routingSummary.activeRoutingRuleCount").value(0))
                .andExpect(jsonPath("$.telegramSummary.activeChatBindingCount").value(0))
                .andExpect(jsonPath("$.telegramSummary.activeTopicBindingCount").value(0));
    }

    @Test
    void detailsTenantNotFoundReturns404() throws Exception {
        when(detailsFacade.getDetails(TENANT_ID))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/details")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void detailsInvalidTenantIdReturns400() throws Exception {
        when(detailsFacade.getDetails(TENANT_ID))
                .thenThrow(new IllegalArgumentException("tenantId null bo'lishi mumkin emas"));

        mockMvc.perform(get("/api/admin/tenant-config/details")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void detailsMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/tenant-config/details"))
                .andExpect(status().isBadRequest());
    }

    // ========== workflow-definitions endpoint ==========

    @Test
    void workflowDefinitionsReturnsCorrectStructuredResponse() throws Exception {
        var items = List.of(
                new TenantConfigDetailsFacade.WorkflowDefinitionItemView(
                        UUID.fromString("22222222-2222-2222-2222-222222222221"),
                        "Bug Flow",
                        "BUG",
                        "Bug workflow",
                        true,
                        Instant.parse("2026-02-01T10:00:00Z")),
                new TenantConfigDetailsFacade.WorkflowDefinitionItemView(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        "Incident Flow",
                        "INCIDENT",
                        null,
                        false,
                        Instant.parse("2026-02-10T12:00:00Z")));
        var view = new TenantConfigDetailsFacade.WorkflowDefinitionListView(TENANT_ID, items);

        when(detailsFacade.getWorkflowDefinitions(TENANT_ID)).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/workflow-definitions")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].definitionId").value("22222222-2222-2222-2222-222222222221"))
                .andExpect(jsonPath("$.items[0].name").value("Bug Flow"))
                .andExpect(jsonPath("$.items[0].workItemType").value("BUG"))
                .andExpect(jsonPath("$.items[0].description").value("Bug workflow"))
                .andExpect(jsonPath("$.items[0].active").value(true))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-02-01T10:00:00Z"))
                .andExpect(jsonPath("$.items[1].definitionId").value("22222222-2222-2222-2222-222222222222"))
                .andExpect(jsonPath("$.items[1].name").value("Incident Flow"))
                .andExpect(jsonPath("$.items[1].workItemType").value("INCIDENT"))
                .andExpect(jsonPath("$.items[1].description").doesNotExist())
                .andExpect(jsonPath("$.items[1].active").value(false));
    }

    @Test
    void workflowDefinitionsWithEmptyListReturnsValidResponse() throws Exception {
        var view = new TenantConfigDetailsFacade.WorkflowDefinitionListView(
                TENANT_ID, List.of());

        when(detailsFacade.getWorkflowDefinitions(TENANT_ID)).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/workflow-definitions")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void workflowDefinitionsTenantNotFoundReturns404() throws Exception {
        when(detailsFacade.getWorkflowDefinitions(TENANT_ID))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/workflow-definitions")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void workflowDefinitionsInvalidTenantIdReturns400() throws Exception {
        when(detailsFacade.getWorkflowDefinitions(TENANT_ID))
                .thenThrow(new IllegalArgumentException("tenantId null bo'lishi mumkin emas"));

        mockMvc.perform(get("/api/admin/tenant-config/workflow-definitions")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void workflowDefinitionsMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/tenant-config/workflow-definitions"))
                .andExpect(status().isBadRequest());
    }

    // ========== routing-rules endpoint ==========

    @Test
    void routingRulesReturnsCorrectStructuredResponse() throws Exception {
        var items = List.of(
                new TenantConfigDetailsFacade.RoutingRuleItemView(
                        UUID.fromString("33333333-3333-3333-3333-333333333331"),
                        "Route Bugs",
                        "BUG",
                        20,
                        UUID.fromString("44444444-4444-4444-4444-444444444444"),
                        true,
                        Instant.parse("2026-03-01T10:00:00Z")),
                new TenantConfigDetailsFacade.RoutingRuleItemView(
                        UUID.fromString("33333333-3333-3333-3333-333333333332"),
                        "Route Incidents",
                        "INCIDENT",
                        10,
                        null,
                        false,
                        Instant.parse("2026-03-05T12:00:00Z")));
        var view = new TenantConfigDetailsFacade.RoutingRuleListView(TENANT_ID, items);

        when(detailsFacade.getRoutingRules(TENANT_ID)).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/routing-rules")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].ruleId").value("33333333-3333-3333-3333-333333333331"))
                .andExpect(jsonPath("$.items[0].name").value("Route Bugs"))
                .andExpect(jsonPath("$.items[0].workItemType").value("BUG"))
                .andExpect(jsonPath("$.items[0].priority").value(20))
                .andExpect(jsonPath("$.items[0].targetTopicBindingId").value("44444444-4444-4444-4444-444444444444"))
                .andExpect(jsonPath("$.items[0].active").value(true))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-03-01T10:00:00Z"))
                .andExpect(jsonPath("$.items[1].ruleId").value("33333333-3333-3333-3333-333333333332"))
                .andExpect(jsonPath("$.items[1].name").value("Route Incidents"))
                .andExpect(jsonPath("$.items[1].workItemType").value("INCIDENT"))
                .andExpect(jsonPath("$.items[1].priority").value(10))
                .andExpect(jsonPath("$.items[1].targetTopicBindingId").doesNotExist())
                .andExpect(jsonPath("$.items[1].active").value(false));
    }

    @Test
    void routingRulesWithEmptyListReturnsValidResponse() throws Exception {
        var view = new TenantConfigDetailsFacade.RoutingRuleListView(
                TENANT_ID, List.of());

        when(detailsFacade.getRoutingRules(TENANT_ID)).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/routing-rules")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void routingRulesTenantNotFoundReturns404() throws Exception {
        when(detailsFacade.getRoutingRules(TENANT_ID))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/routing-rules")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void routingRulesInvalidTenantIdReturns400() throws Exception {
        when(detailsFacade.getRoutingRules(TENANT_ID))
                .thenThrow(new IllegalArgumentException("tenantId null bo'lishi mumkin emas"));

        mockMvc.perform(get("/api/admin/tenant-config/routing-rules")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void routingRulesMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/tenant-config/routing-rules"))
                .andExpect(status().isBadRequest());
    }

    // ========== chat-bindings endpoint ==========

    @Test
    void chatBindingsReturnsCorrectStructuredResponse() throws Exception {
        var items = List.of(
                new TenantConfigDetailsFacade.ChatBindingItemView(
                        UUID.fromString("55555555-5555-5555-5555-555555555551"),
                        200L,
                        "Main Group",
                        "MAIN_GROUP",
                        true,
                        3,
                        Instant.parse("2026-03-15T12:00:00Z")),
                new TenantConfigDetailsFacade.ChatBindingItemView(
                        UUID.fromString("55555555-5555-5555-5555-555555555552"),
                        100L,
                        null,
                        "NOTIFICATION_GROUP",
                        false,
                        1,
                        Instant.parse("2026-03-10T10:00:00Z")));
        var view = new TenantConfigDetailsFacade.ChatBindingListView(TENANT_ID, items);

        when(detailsFacade.getChatBindings(TENANT_ID)).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/chat-bindings")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].chatBindingId").value("55555555-5555-5555-5555-555555555551"))
                .andExpect(jsonPath("$.items[0].chatId").value(200))
                .andExpect(jsonPath("$.items[0].chatTitle").value("Main Group"))
                .andExpect(jsonPath("$.items[0].bindingType").value("MAIN_GROUP"))
                .andExpect(jsonPath("$.items[0].active").value(true))
                .andExpect(jsonPath("$.items[0].activeTopicBindingCount").value(3))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-03-15T12:00:00Z"))
                .andExpect(jsonPath("$.items[1].chatBindingId").value("55555555-5555-5555-5555-555555555552"))
                .andExpect(jsonPath("$.items[1].chatId").value(100))
                .andExpect(jsonPath("$.items[1].chatTitle").doesNotExist())
                .andExpect(jsonPath("$.items[1].bindingType").value("NOTIFICATION_GROUP"))
                .andExpect(jsonPath("$.items[1].active").value(false))
                .andExpect(jsonPath("$.items[1].activeTopicBindingCount").value(1));
    }

    @Test
    void chatBindingsWithEmptyListReturnsValidResponse() throws Exception {
        var view = new TenantConfigDetailsFacade.ChatBindingListView(
                TENANT_ID, List.of());

        when(detailsFacade.getChatBindings(TENANT_ID)).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/chat-bindings")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void chatBindingsTenantNotFoundReturns404() throws Exception {
        when(detailsFacade.getChatBindings(TENANT_ID))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/chat-bindings")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void chatBindingsInvalidTenantIdReturns400() throws Exception {
        when(detailsFacade.getChatBindings(TENANT_ID))
                .thenThrow(new IllegalArgumentException("tenantId null bo'lishi mumkin emas"));

        mockMvc.perform(get("/api/admin/tenant-config/chat-bindings")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void chatBindingsMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/tenant-config/chat-bindings"))
                .andExpect(status().isBadRequest());
    }

    // ========== topic-bindings endpoint ==========

    @Test
    void topicBindingsReturnsCorrectStructuredResponse() throws Exception {
        var items = List.of(
                new TenantConfigDetailsFacade.TopicBindingItemView(
                        UUID.fromString("77777777-7777-7777-7777-777777777771"),
                        UUID.fromString("66666666-6666-6666-6666-666666666661"),
                        100L,
                        "Main Group",
                        10L,
                        "Bugs Topic",
                        "bugs",
                        true,
                        Instant.parse("2026-03-20T10:00:00Z")),
                new TenantConfigDetailsFacade.TopicBindingItemView(
                        UUID.fromString("77777777-7777-7777-7777-777777777772"),
                        UUID.fromString("66666666-6666-6666-6666-666666666661"),
                        100L,
                        "Main Group",
                        20L,
                        null,
                        "incidents",
                        false,
                        Instant.parse("2026-03-25T12:00:00Z")));
        var view = new TenantConfigDetailsFacade.TopicBindingListView(TENANT_ID, items);

        when(detailsFacade.getTopicBindings(TENANT_ID)).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/topic-bindings")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].topicBindingId").value("77777777-7777-7777-7777-777777777771"))
                .andExpect(jsonPath("$.items[0].chatBindingId").value("66666666-6666-6666-6666-666666666661"))
                .andExpect(jsonPath("$.items[0].chatId").value(100))
                .andExpect(jsonPath("$.items[0].chatTitle").value("Main Group"))
                .andExpect(jsonPath("$.items[0].topicId").value(10))
                .andExpect(jsonPath("$.items[0].topicName").value("Bugs Topic"))
                .andExpect(jsonPath("$.items[0].purpose").value("bugs"))
                .andExpect(jsonPath("$.items[0].active").value(true))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-03-20T10:00:00Z"))
                .andExpect(jsonPath("$.items[1].topicBindingId").value("77777777-7777-7777-7777-777777777772"))
                .andExpect(jsonPath("$.items[1].chatId").value(100))
                .andExpect(jsonPath("$.items[1].topicName").doesNotExist())
                .andExpect(jsonPath("$.items[1].purpose").value("incidents"))
                .andExpect(jsonPath("$.items[1].active").value(false));
    }

    @Test
    void topicBindingsWithEmptyListReturnsValidResponse() throws Exception {
        var view = new TenantConfigDetailsFacade.TopicBindingListView(
                TENANT_ID, List.of());

        when(detailsFacade.getTopicBindings(TENANT_ID)).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/topic-bindings")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void topicBindingsTenantNotFoundReturns404() throws Exception {
        when(detailsFacade.getTopicBindings(TENANT_ID))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/topic-bindings")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void topicBindingsInvalidTenantIdReturns400() throws Exception {
        when(detailsFacade.getTopicBindings(TENANT_ID))
                .thenThrow(new IllegalArgumentException("tenantId null bo'lishi mumkin emas"));

        mockMvc.perform(get("/api/admin/tenant-config/topic-bindings")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void topicBindingsMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/tenant-config/topic-bindings"))
                .andExpect(status().isBadRequest());
    }

    // ========== memberships endpoint ==========

    @Test
    void membershipsReturnsCorrectStructuredResponse() throws Exception {
        var items = List.of(
                new TenantConfigDetailsFacade.MembershipItemView(
                        UUID.fromString("aa111111-1111-1111-1111-111111111111"),
                        UUID.fromString("bb111111-1111-1111-1111-111111111111"),
                        1001L,
                        "Anvar",
                        null,
                        "ACTIVE",
                        List.of("Administrator"),
                        Instant.parse("2026-03-01T08:00:00Z")),
                new TenantConfigDetailsFacade.MembershipItemView(
                        UUID.fromString("aa222222-2222-2222-2222-222222222222"),
                        UUID.fromString("bb222222-2222-2222-2222-222222222222"),
                        1002L,
                        "Zafar",
                        "zafar_dev",
                        "SUSPENDED",
                        null,
                        Instant.parse("2026-02-01T08:00:00Z")));
        var view = new TenantConfigDetailsFacade.MembershipListView(TENANT_ID, items);

        when(detailsFacade.getMemberships(TENANT_ID)).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/memberships")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].membershipId").value("aa111111-1111-1111-1111-111111111111"))
                .andExpect(jsonPath("$.items[0].userId").value("bb111111-1111-1111-1111-111111111111"))
                .andExpect(jsonPath("$.items[0].telegramUserId").value(1001))
                .andExpect(jsonPath("$.items[0].displayName").value("Anvar"))
                .andExpect(jsonPath("$.items[0].username").doesNotExist())
                .andExpect(jsonPath("$.items[0].membershipStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].roleNames[0]").value("Administrator"))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-03-01T08:00:00Z"))
                .andExpect(jsonPath("$.items[1].membershipId").value("aa222222-2222-2222-2222-222222222222"))
                .andExpect(jsonPath("$.items[1].displayName").value("Zafar"))
                .andExpect(jsonPath("$.items[1].username").value("zafar_dev"))
                .andExpect(jsonPath("$.items[1].membershipStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.items[1].roleNames").doesNotExist());
    }

    @Test
    void membershipsWithEmptyListReturnsValidResponse() throws Exception {
        var view = new TenantConfigDetailsFacade.MembershipListView(
                TENANT_ID, List.of());

        when(detailsFacade.getMemberships(TENANT_ID)).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/memberships")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void membershipsTenantNotFoundReturns404() throws Exception {
        when(detailsFacade.getMemberships(TENANT_ID))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/memberships")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void membershipsInvalidTenantIdReturns400() throws Exception {
        when(detailsFacade.getMemberships(TENANT_ID))
                .thenThrow(new IllegalArgumentException("tenantId null bo'lishi mumkin emas"));

        mockMvc.perform(get("/api/admin/tenant-config/memberships")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void membershipsMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/tenant-config/memberships"))
                .andExpect(status().isBadRequest());
    }

    // ========== roles endpoint ==========

    @Test
    void rolesReturnsCorrectStructuredResponse() throws Exception {
        var items = List.of(
                new TenantConfigDetailsFacade.RoleItemView(
                        UUID.fromString("cc111111-1111-1111-1111-111111111111"),
                        "ADMIN",
                        "Administrator",
                        null,
                        true,
                        Instant.parse("2026-01-05T08:00:00Z")),
                new TenantConfigDetailsFacade.RoleItemView(
                        UUID.fromString("cc222222-2222-2222-2222-222222222222"),
                        "ENGINEER",
                        "Engineer",
                        "Engineering role",
                        false,
                        Instant.parse("2026-01-10T08:00:00Z")));
        var view = new TenantConfigDetailsFacade.RoleListView(TENANT_ID, items);

        when(detailsFacade.getRoles(TENANT_ID)).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/roles")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].roleId").value("cc111111-1111-1111-1111-111111111111"))
                .andExpect(jsonPath("$.items[0].code").value("ADMIN"))
                .andExpect(jsonPath("$.items[0].name").value("Administrator"))
                .andExpect(jsonPath("$.items[0].description").doesNotExist())
                .andExpect(jsonPath("$.items[0].systemRole").value(true))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-01-05T08:00:00Z"))
                .andExpect(jsonPath("$.items[1].roleId").value("cc222222-2222-2222-2222-222222222222"))
                .andExpect(jsonPath("$.items[1].code").value("ENGINEER"))
                .andExpect(jsonPath("$.items[1].name").value("Engineer"))
                .andExpect(jsonPath("$.items[1].description").value("Engineering role"))
                .andExpect(jsonPath("$.items[1].systemRole").value(false));
    }

    @Test
    void rolesWithEmptyListReturnsValidResponse() throws Exception {
        var view = new TenantConfigDetailsFacade.RoleListView(
                TENANT_ID, List.of());

        when(detailsFacade.getRoles(TENANT_ID)).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/roles")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void rolesTenantNotFoundReturns404() throws Exception {
        when(detailsFacade.getRoles(TENANT_ID))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/roles")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void rolesInvalidTenantIdReturns400() throws Exception {
        when(detailsFacade.getRoles(TENANT_ID))
                .thenThrow(new IllegalArgumentException("tenantId null bo'lishi mumkin emas"));

        mockMvc.perform(get("/api/admin/tenant-config/roles")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void rolesMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/tenant-config/roles"))
                .andExpect(status().isBadRequest());
    }

    // ========== POST /workflow-definitions endpoint ==========

    @Test
    void createWorkflowDefinitionReturns201WithCreatedResource() throws Exception {
        var view = new TenantConfigWriteFacade.WorkflowDefinitionCreatedView(
                TENANT_ID,
                UUID.fromString("22222222-2222-2222-2222-222222222221"),
                "Bug Flow",
                "BUG",
                "Bug workflow",
                true,
                Instant.parse("2026-04-08T10:00:00Z"));

        when(writeFacade.createWorkflowDefinition(eq(TENANT_ID),
                any(CreateWorkflowDefinitionRequest.class))).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions")
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bug Flow","workItemType":"BUG","description":"Bug workflow"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.definitionId").value("22222222-2222-2222-2222-222222222221"))
                .andExpect(jsonPath("$.name").value("Bug Flow"))
                .andExpect(jsonPath("$.workItemType").value("BUG"))
                .andExpect(jsonPath("$.description").value("Bug workflow"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").value("2026-04-08T10:00:00Z"));
    }

    @Test
    void createWorkflowDefinitionWithNullDescriptionOmitsField() throws Exception {
        var view = new TenantConfigWriteFacade.WorkflowDefinitionCreatedView(
                TENANT_ID,
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "Incident Flow",
                "INCIDENT",
                null,
                true,
                Instant.parse("2026-04-08T10:00:00Z"));

        when(writeFacade.createWorkflowDefinition(eq(TENANT_ID),
                any(CreateWorkflowDefinitionRequest.class))).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions")
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Incident Flow","workItemType":"INCIDENT"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void createWorkflowDefinitionMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bug Flow","workItemType":"BUG"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWorkflowDefinitionInvalidNameReturns400() throws Exception {
        when(writeFacade.createWorkflowDefinition(eq(TENANT_ID),
                any(CreateWorkflowDefinitionRequest.class)))
                .thenThrow(new IllegalArgumentException("name null yoki bo'sh bo'lishi mumkin emas"));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions")
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","workItemType":"BUG"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void createWorkflowDefinitionInvalidWorkItemTypeReturns400() throws Exception {
        when(writeFacade.createWorkflowDefinition(eq(TENANT_ID),
                any(CreateWorkflowDefinitionRequest.class)))
                .thenThrow(new IllegalArgumentException(
                        "workItemType faqat BUG, INCIDENT, TASK bo'lishi mumkin: FEATURE"));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions")
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bug Flow","workItemType":"FEATURE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void createWorkflowDefinitionTenantNotFoundReturns404() throws Exception {
        when(writeFacade.createWorkflowDefinition(eq(TENANT_ID),
                any(CreateWorkflowDefinitionRequest.class)))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions")
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bug Flow","workItemType":"BUG"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void createWorkflowDefinitionEmptyBodyReturns400() throws Exception {
        when(writeFacade.createWorkflowDefinition(eq(TENANT_ID),
                any(CreateWorkflowDefinitionRequest.class)))
                .thenThrow(new IllegalArgumentException("name null yoki bo'sh bo'lishi mumkin emas"));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions")
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWorkflowDefinitionDuplicateNameReturns422() throws Exception {
        when(writeFacade.createWorkflowDefinition(eq(TENANT_ID),
                any(CreateWorkflowDefinitionRequest.class)))
                .thenThrow(new BusinessRuleException("DUPLICATE_WORKFLOW_NAME",
                        "Tenant ichida 'Bug Flow' nomli workflow allaqachon mavjud"));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions")
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bug Flow","workItemType":"BUG"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_WORKFLOW_NAME"));
    }

    // ========== PATCH /workflow-definitions/{definitionId} endpoint ==========

    private static final UUID DEF_ID = UUID.fromString("22222222-2222-2222-2222-222222222221");

    @Test
    void updateWorkflowDefinitionReturns200WithUpdatedResource() throws Exception {
        var view = new TenantConfigWriteFacade.WorkflowDefinitionUpdatedView(
                TENANT_ID,
                DEF_ID,
                "Updated Flow",
                "BUG",
                "New description",
                true,
                Instant.parse("2026-04-08T10:00:00Z"));

        when(writeFacade.updateWorkflowDefinition(eq(TENANT_ID), eq(DEF_ID),
                any(UpdateWorkflowDefinitionRequest.class))).thenReturn(view);

        mockMvc.perform(patch("/api/admin/tenant-config/workflow-definitions/{definitionId}", DEF_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated Flow","description":"New description"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.definitionId").value(DEF_ID.toString()))
                .andExpect(jsonPath("$.name").value("Updated Flow"))
                .andExpect(jsonPath("$.workItemType").value("BUG"))
                .andExpect(jsonPath("$.description").value("New description"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").value("2026-04-08T10:00:00Z"));
    }

    @Test
    void updateWorkflowDefinitionWithNullDescriptionOmitsField() throws Exception {
        var view = new TenantConfigWriteFacade.WorkflowDefinitionUpdatedView(
                TENANT_ID, DEF_ID, "Flow", "BUG", null, true,
                Instant.parse("2026-04-08T10:00:00Z"));

        when(writeFacade.updateWorkflowDefinition(eq(TENANT_ID), eq(DEF_ID),
                any(UpdateWorkflowDefinitionRequest.class))).thenReturn(view);

        mockMvc.perform(patch("/api/admin/tenant-config/workflow-definitions/{definitionId}", DEF_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Flow"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void updateWorkflowDefinitionOnlyDescriptionSucceeds() throws Exception {
        var view = new TenantConfigWriteFacade.WorkflowDefinitionUpdatedView(
                TENANT_ID, DEF_ID, "Kept Name", "BUG", "Updated desc", true,
                Instant.parse("2026-04-08T10:00:00Z"));

        when(writeFacade.updateWorkflowDefinition(eq(TENANT_ID), eq(DEF_ID),
                any(UpdateWorkflowDefinitionRequest.class))).thenReturn(view);

        mockMvc.perform(patch("/api/admin/tenant-config/workflow-definitions/{definitionId}", DEF_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Updated desc"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kept Name"))
                .andExpect(jsonPath("$.description").value("Updated desc"));
    }

    @Test
    void updateWorkflowDefinitionEmptyBodyReturns400() throws Exception {
        when(writeFacade.updateWorkflowDefinition(eq(TENANT_ID), eq(DEF_ID),
                any(UpdateWorkflowDefinitionRequest.class)))
                .thenThrow(new IllegalArgumentException("Kamida bitta yangilanuvchi field berilishi kerak"));

        mockMvc.perform(patch("/api/admin/tenant-config/workflow-definitions/{definitionId}", DEF_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void updateWorkflowDefinitionExplicitNullDescriptionClearsIt() throws Exception {
        var view = new TenantConfigWriteFacade.WorkflowDefinitionUpdatedView(
                TENANT_ID, DEF_ID, "Flow", "BUG", null, true,
                Instant.parse("2026-04-08T10:00:00Z"));

        when(writeFacade.updateWorkflowDefinition(eq(TENANT_ID), eq(DEF_ID),
                any(UpdateWorkflowDefinitionRequest.class))).thenReturn(view);

        mockMvc.perform(patch("/api/admin/tenant-config/workflow-definitions/{definitionId}", DEF_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void updateWorkflowDefinitionMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(patch("/api/admin/tenant-config/workflow-definitions/{definitionId}", DEF_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Flow"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateWorkflowDefinitionBlankNameReturns400() throws Exception {
        when(writeFacade.updateWorkflowDefinition(eq(TENANT_ID), eq(DEF_ID),
                any(UpdateWorkflowDefinitionRequest.class)))
                .thenThrow(new IllegalArgumentException("name null yoki bo'sh bo'lishi mumkin emas"));

        mockMvc.perform(patch("/api/admin/tenant-config/workflow-definitions/{definitionId}", DEF_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void updateWorkflowDefinitionTenantNotFoundReturns404() throws Exception {
        when(writeFacade.updateWorkflowDefinition(eq(TENANT_ID), eq(DEF_ID),
                any(UpdateWorkflowDefinitionRequest.class)))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(patch("/api/admin/tenant-config/workflow-definitions/{definitionId}", DEF_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Flow"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void updateWorkflowDefinitionNotFoundReturns404() throws Exception {
        when(writeFacade.updateWorkflowDefinition(eq(TENANT_ID), eq(DEF_ID),
                any(UpdateWorkflowDefinitionRequest.class)))
                .thenThrow(new ResourceNotFoundException("WorkflowDefinition", DEF_ID));

        mockMvc.perform(patch("/api/admin/tenant-config/workflow-definitions/{definitionId}", DEF_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Flow"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void updateWorkflowDefinitionDuplicateNameReturns422() throws Exception {
        when(writeFacade.updateWorkflowDefinition(eq(TENANT_ID), eq(DEF_ID),
                any(UpdateWorkflowDefinitionRequest.class)))
                .thenThrow(new BusinessRuleException("DUPLICATE_WORKFLOW_NAME",
                        "Tenant ichida 'Taken' nomli workflow allaqachon mavjud"));

        mockMvc.perform(patch("/api/admin/tenant-config/workflow-definitions/{definitionId}", DEF_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Taken"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_WORKFLOW_NAME"));
    }

    // ========== POST /workflow-definitions/{definitionId}/activate endpoint ==========

    @Test
    void activateWorkflowDefinitionReturns200() throws Exception {
        var view = new TenantConfigWriteFacade.WorkflowDefinitionUpdatedView(
                TENANT_ID, DEF_ID, "Flow", "BUG", null, true,
                Instant.parse("2026-04-08T10:00:00Z"));

        when(writeFacade.activateWorkflowDefinition(TENANT_ID, DEF_ID)).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/activate", DEF_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definitionId").value(DEF_ID.toString()))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void activateWorkflowDefinitionMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/activate", DEF_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void activateWorkflowDefinitionTenantNotFoundReturns404() throws Exception {
        when(writeFacade.activateWorkflowDefinition(TENANT_ID, DEF_ID))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/activate", DEF_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void activateWorkflowDefinitionNotFoundReturns404() throws Exception {
        when(writeFacade.activateWorkflowDefinition(TENANT_ID, DEF_ID))
                .thenThrow(new ResourceNotFoundException("WorkflowDefinition", DEF_ID));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/activate", DEF_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ========== POST /workflow-definitions/{definitionId}/deactivate endpoint ==========

    @Test
    void deactivateWorkflowDefinitionReturns200() throws Exception {
        var view = new TenantConfigWriteFacade.WorkflowDefinitionUpdatedView(
                TENANT_ID, DEF_ID, "Flow", "BUG", null, false,
                Instant.parse("2026-04-08T10:00:00Z"));

        when(writeFacade.deactivateWorkflowDefinition(TENANT_ID, DEF_ID)).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/deactivate", DEF_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definitionId").value(DEF_ID.toString()))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deactivateWorkflowDefinitionMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/deactivate", DEF_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deactivateWorkflowDefinitionTenantNotFoundReturns404() throws Exception {
        when(writeFacade.deactivateWorkflowDefinition(TENANT_ID, DEF_ID))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/deactivate", DEF_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void deactivateWorkflowDefinitionNotFoundReturns404() throws Exception {
        when(writeFacade.deactivateWorkflowDefinition(TENANT_ID, DEF_ID))
                .thenThrow(new ResourceNotFoundException("WorkflowDefinition", DEF_ID));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/deactivate", DEF_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ========== DELETE /workflow-definitions/{definitionId} endpoint ==========

    @Test
    void deleteWorkflowDefinitionReturns204() throws Exception {
        mockMvc.perform(delete("/api/admin/tenant-config/workflow-definitions/{definitionId}", DEF_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void deleteWorkflowDefinitionMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(delete("/api/admin/tenant-config/workflow-definitions/{definitionId}", DEF_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteWorkflowDefinitionTenantNotFoundReturns404() throws Exception {
        doThrow(new ResourceNotFoundException("Tenant", TENANT_ID))
                .when(writeFacade).deleteWorkflowDefinition(TENANT_ID, DEF_ID);

        mockMvc.perform(delete("/api/admin/tenant-config/workflow-definitions/{definitionId}", DEF_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void deleteWorkflowDefinitionNotFoundReturns404() throws Exception {
        doThrow(new ResourceNotFoundException("WorkflowDefinition", DEF_ID))
                .when(writeFacade).deleteWorkflowDefinition(TENANT_ID, DEF_ID);

        mockMvc.perform(delete("/api/admin/tenant-config/workflow-definitions/{definitionId}", DEF_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ========== POST /chat-bindings endpoint ==========

    @Test
    void createChatBindingReturns201WithCreatedResource() throws Exception {
        var view = new TenantConfigWriteFacade.ChatBindingCreatedView(
                TENANT_ID,
                UUID.fromString("55555555-5555-5555-5555-555555555551"),
                -1001234567890L,
                "Dev Team Chat",
                "MAIN_GROUP",
                true,
                Instant.parse("2026-04-10T12:00:00Z"));

        when(writeFacade.createChatBinding(eq(TENANT_ID),
                any(CreateChatBindingRequest.class))).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/chat-bindings")
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chatId":-1001234567890,"chatTitle":"Dev Team Chat","bindingType":"MAIN_GROUP"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.chatBindingId").value("55555555-5555-5555-5555-555555555551"))
                .andExpect(jsonPath("$.chatId").value(-1001234567890L))
                .andExpect(jsonPath("$.chatTitle").value("Dev Team Chat"))
                .andExpect(jsonPath("$.bindingType").value("MAIN_GROUP"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").value("2026-04-10T12:00:00Z"));
    }

    @Test
    void createChatBindingWithNullTitleOmitsField() throws Exception {
        var view = new TenantConfigWriteFacade.ChatBindingCreatedView(
                TENANT_ID,
                UUID.fromString("55555555-5555-5555-5555-555555555552"),
                -1001234567891L,
                null,
                "NOTIFICATION_GROUP",
                true,
                Instant.parse("2026-04-10T12:00:00Z"));

        when(writeFacade.createChatBinding(eq(TENANT_ID),
                any(CreateChatBindingRequest.class))).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/chat-bindings")
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chatId":-1001234567891,"bindingType":"NOTIFICATION_GROUP"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chatTitle").doesNotExist());
    }

    @Test
    void createChatBindingMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/chat-bindings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chatId":-1001234567890,"bindingType":"MAIN_GROUP"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createChatBindingInvalidBindingTypeReturns400() throws Exception {
        when(writeFacade.createChatBinding(eq(TENANT_ID),
                any(CreateChatBindingRequest.class)))
                .thenThrow(new IllegalArgumentException(
                        "bindingType faqat MAIN_GROUP, NOTIFICATION_GROUP bo'lishi mumkin: PRIVATE_CHAT"));

        mockMvc.perform(post("/api/admin/tenant-config/chat-bindings")
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chatId":-1001234567890,"bindingType":"PRIVATE_CHAT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void createChatBindingTenantNotFoundReturns404() throws Exception {
        when(writeFacade.createChatBinding(eq(TENANT_ID),
                any(CreateChatBindingRequest.class)))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(post("/api/admin/tenant-config/chat-bindings")
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chatId":-1001234567890,"bindingType":"MAIN_GROUP"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void createChatBindingDuplicateReturns422() throws Exception {
        when(writeFacade.createChatBinding(eq(TENANT_ID),
                any(CreateChatBindingRequest.class)))
                .thenThrow(new BusinessRuleException("DUPLICATE_CHAT_BINDING",
                        "Tenant ichida chatId=-1001234567890 uchun binding allaqachon mavjud"));

        mockMvc.perform(post("/api/admin/tenant-config/chat-bindings")
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chatId":-1001234567890,"bindingType":"MAIN_GROUP"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_CHAT_BINDING"));
    }

    // ========== POST /routing-rules endpoint ==========

    @Test
    void createRoutingRuleReturns201WithCreatedResource() throws Exception {
        UUID topicBindingId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        var view = new TenantConfigWriteFacade.RoutingRuleCreatedView(
                TENANT_ID,
                UUID.fromString("33333333-3333-3333-3333-333333333331"),
                "Route Bugs",
                "BUG",
                10,
                topicBindingId,
                true,
                Instant.parse("2026-04-08T12:00:00Z"));

        when(writeFacade.createRoutingRule(eq(TENANT_ID),
                any(CreateRoutingRuleRequest.class))).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/routing-rules")
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Route Bugs","workItemType":"BUG","priority":10,
                                 "targetTopicBindingId":"44444444-4444-4444-4444-444444444444"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.ruleId").value("33333333-3333-3333-3333-333333333331"))
                .andExpect(jsonPath("$.name").value("Route Bugs"))
                .andExpect(jsonPath("$.workItemType").value("BUG"))
                .andExpect(jsonPath("$.priority").value(10))
                .andExpect(jsonPath("$.targetTopicBindingId").value(topicBindingId.toString()))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").value("2026-04-08T12:00:00Z"));
    }

    @Test
    void createRoutingRuleWithNullTopicBindingOmitsField() throws Exception {
        var view = new TenantConfigWriteFacade.RoutingRuleCreatedView(
                TENANT_ID,
                UUID.fromString("33333333-3333-3333-3333-333333333332"),
                "Catch All",
                "INCIDENT",
                5,
                null,
                true,
                Instant.parse("2026-04-08T12:00:00Z"));

        when(writeFacade.createRoutingRule(eq(TENANT_ID),
                any(CreateRoutingRuleRequest.class))).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/routing-rules")
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Catch All","workItemType":"INCIDENT","priority":5}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetTopicBindingId").doesNotExist());
    }

    @Test
    void createRoutingRuleMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/routing-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Rule","workItemType":"BUG","priority":10}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRoutingRuleInvalidNameReturns400() throws Exception {
        when(writeFacade.createRoutingRule(eq(TENANT_ID),
                any(CreateRoutingRuleRequest.class)))
                .thenThrow(new IllegalArgumentException("name null yoki bo'sh bo'lishi mumkin emas"));

        mockMvc.perform(post("/api/admin/tenant-config/routing-rules")
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","workItemType":"BUG","priority":10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void createRoutingRuleInvalidWorkItemTypeReturns400() throws Exception {
        when(writeFacade.createRoutingRule(eq(TENANT_ID),
                any(CreateRoutingRuleRequest.class)))
                .thenThrow(new IllegalArgumentException(
                        "workItemType faqat BUG, INCIDENT, TASK bo'lishi mumkin: FEATURE"));

        mockMvc.perform(post("/api/admin/tenant-config/routing-rules")
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Rule","workItemType":"FEATURE","priority":10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void createRoutingRuleTenantNotFoundReturns404() throws Exception {
        when(writeFacade.createRoutingRule(eq(TENANT_ID),
                any(CreateRoutingRuleRequest.class)))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(post("/api/admin/tenant-config/routing-rules")
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Rule","workItemType":"BUG","priority":10}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void createRoutingRuleInvalidTopicBindingReturns422() throws Exception {
        when(writeFacade.createRoutingRule(eq(TENANT_ID),
                any(CreateRoutingRuleRequest.class)))
                .thenThrow(new BusinessRuleException("INVALID_TOPIC_BINDING",
                        "Topic binding topilmadi yoki shu tenantga tegishli emas"));

        mockMvc.perform(post("/api/admin/tenant-config/routing-rules")
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Rule","workItemType":"BUG","priority":10,
                                 "targetTopicBindingId":"44444444-4444-4444-4444-444444444445"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOPIC_BINDING"));
    }

    // ========== PATCH /routing-rules/{ruleId} endpoint ==========

    private static final UUID RULE_ID = UUID.fromString("33333333-3333-3333-3333-333333333331");

    @Test
    void updateRoutingRuleReturns200WithMultipleFields() throws Exception {
        UUID topicBindingId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        var view = new TenantConfigWriteFacade.RoutingRuleUpdatedView(
                TENANT_ID,
                RULE_ID,
                "Updated Rule",
                20,
                topicBindingId,
                "severity == HIGH",
                true,
                Instant.parse("2026-04-08T12:00:00Z"));

        when(writeFacade.updateRoutingRule(eq(TENANT_ID), eq(RULE_ID),
                any(UpdateRoutingRuleRequest.class))).thenReturn(view);

        mockMvc.perform(patch("/api/admin/tenant-config/routing-rules/{ruleId}", RULE_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated Rule","priority":20,
                                 "targetTopicBindingId":"44444444-4444-4444-4444-444444444444",
                                 "conditionExpression":"severity == HIGH"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.ruleId").value(RULE_ID.toString()))
                .andExpect(jsonPath("$.name").value("Updated Rule"))
                .andExpect(jsonPath("$.priority").value(20))
                .andExpect(jsonPath("$.targetTopicBindingId").value(topicBindingId.toString()))
                .andExpect(jsonPath("$.conditionExpression").value("severity == HIGH"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").value("2026-04-08T12:00:00Z"));
    }

    @Test
    void updateRoutingRuleWithOnlyPriority() throws Exception {
        var view = new TenantConfigWriteFacade.RoutingRuleUpdatedView(
                TENANT_ID, RULE_ID, "Rule", 50, null, null, true,
                Instant.parse("2026-04-08T12:00:00Z"));

        when(writeFacade.updateRoutingRule(eq(TENANT_ID), eq(RULE_ID),
                any(UpdateRoutingRuleRequest.class))).thenReturn(view);

        mockMvc.perform(patch("/api/admin/tenant-config/routing-rules/{ruleId}", RULE_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priority":50}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value(50))
                .andExpect(jsonPath("$.targetTopicBindingId").doesNotExist())
                .andExpect(jsonPath("$.conditionExpression").doesNotExist());
    }

    @Test
    void updateRoutingRuleWithOnlyTargetTopicBindingId() throws Exception {
        UUID topicId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        var view = new TenantConfigWriteFacade.RoutingRuleUpdatedView(
                TENANT_ID, RULE_ID, "Rule", 10, topicId, null, true,
                Instant.parse("2026-04-08T12:00:00Z"));

        when(writeFacade.updateRoutingRule(eq(TENANT_ID), eq(RULE_ID),
                any(UpdateRoutingRuleRequest.class))).thenReturn(view);

        mockMvc.perform(patch("/api/admin/tenant-config/routing-rules/{ruleId}", RULE_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetTopicBindingId":"44444444-4444-4444-4444-444444444444"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetTopicBindingId").value(topicId.toString()));
    }

    @Test
    void updateRoutingRuleExplicitNullTopicBindingOmitsField() throws Exception {
        var view = new TenantConfigWriteFacade.RoutingRuleUpdatedView(
                TENANT_ID, RULE_ID, "Rule", 10, null, null, true,
                Instant.parse("2026-04-08T12:00:00Z"));

        when(writeFacade.updateRoutingRule(eq(TENANT_ID), eq(RULE_ID),
                any(UpdateRoutingRuleRequest.class))).thenReturn(view);

        mockMvc.perform(patch("/api/admin/tenant-config/routing-rules/{ruleId}", RULE_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetTopicBindingId":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetTopicBindingId").doesNotExist());
    }

    @Test
    void updateRoutingRuleWithOnlyConditionExpression() throws Exception {
        var view = new TenantConfigWriteFacade.RoutingRuleUpdatedView(
                TENANT_ID, RULE_ID, "Rule", 10, null, "type == CRITICAL", true,
                Instant.parse("2026-04-08T12:00:00Z"));

        when(writeFacade.updateRoutingRule(eq(TENANT_ID), eq(RULE_ID),
                any(UpdateRoutingRuleRequest.class))).thenReturn(view);

        mockMvc.perform(patch("/api/admin/tenant-config/routing-rules/{ruleId}", RULE_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conditionExpression":"type == CRITICAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conditionExpression").value("type == CRITICAL"));
    }

    @Test
    void updateRoutingRuleMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(patch("/api/admin/tenant-config/routing-rules/{ruleId}", RULE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Rule"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRoutingRuleInvalidNameReturns400() throws Exception {
        when(writeFacade.updateRoutingRule(eq(TENANT_ID), eq(RULE_ID),
                any(UpdateRoutingRuleRequest.class)))
                .thenThrow(new IllegalArgumentException("name null yoki bo'sh bo'lishi mumkin emas"));

        mockMvc.perform(patch("/api/admin/tenant-config/routing-rules/{ruleId}", RULE_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void updateRoutingRuleTenantNotFoundReturns404() throws Exception {
        when(writeFacade.updateRoutingRule(eq(TENANT_ID), eq(RULE_ID),
                any(UpdateRoutingRuleRequest.class)))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(patch("/api/admin/tenant-config/routing-rules/{ruleId}", RULE_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Rule"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void updateRoutingRuleNotFoundReturns404() throws Exception {
        when(writeFacade.updateRoutingRule(eq(TENANT_ID), eq(RULE_ID),
                any(UpdateRoutingRuleRequest.class)))
                .thenThrow(new ResourceNotFoundException("RoutingRule", RULE_ID));

        mockMvc.perform(patch("/api/admin/tenant-config/routing-rules/{ruleId}", RULE_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Rule"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void updateRoutingRuleInvalidTopicBindingReturns422() throws Exception {
        when(writeFacade.updateRoutingRule(eq(TENANT_ID), eq(RULE_ID),
                any(UpdateRoutingRuleRequest.class)))
                .thenThrow(new BusinessRuleException("INVALID_TOPIC_BINDING",
                        "Topic binding topilmadi yoki shu tenantga tegishli emas"));

        mockMvc.perform(patch("/api/admin/tenant-config/routing-rules/{ruleId}", RULE_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetTopicBindingId":"44444444-4444-4444-4444-444444444499"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOPIC_BINDING"));
    }

    @Test
    void updateRoutingRuleNullPriorityReturns400() throws Exception {
        when(writeFacade.updateRoutingRule(eq(TENANT_ID), eq(RULE_ID),
                any(UpdateRoutingRuleRequest.class)))
                .thenThrow(new IllegalArgumentException("priority null bo'lishi mumkin emas"));

        mockMvc.perform(patch("/api/admin/tenant-config/routing-rules/{ruleId}", RULE_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priority":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void updateRoutingRuleEmptyBodyReturns400() throws Exception {
        when(writeFacade.updateRoutingRule(eq(TENANT_ID), eq(RULE_ID),
                any(UpdateRoutingRuleRequest.class)))
                .thenThrow(new IllegalArgumentException("Kamida bitta yangilanuvchi field berilishi kerak"));

        mockMvc.perform(patch("/api/admin/tenant-config/routing-rules/{ruleId}", RULE_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    // ========== POST /routing-rules/{ruleId}/activate endpoint ==========

    @Test
    void activateRoutingRuleReturns200() throws Exception {
        var view = new TenantConfigWriteFacade.RoutingRuleUpdatedView(
                TENANT_ID, RULE_ID, "Rule", 10, null, null, true,
                Instant.parse("2026-04-08T12:00:00Z"));

        when(writeFacade.activateRoutingRule(TENANT_ID, RULE_ID)).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/routing-rules/{ruleId}/activate", RULE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.ruleId").value(RULE_ID.toString()))
                .andExpect(jsonPath("$.name").value("Rule"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.workItemType").doesNotExist());
    }

    @Test
    void activateRoutingRuleMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/routing-rules/{ruleId}/activate", RULE_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void activateRoutingRuleTenantNotFoundReturns404() throws Exception {
        when(writeFacade.activateRoutingRule(TENANT_ID, RULE_ID))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(post("/api/admin/tenant-config/routing-rules/{ruleId}/activate", RULE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void activateRoutingRuleNotFoundReturns404() throws Exception {
        when(writeFacade.activateRoutingRule(TENANT_ID, RULE_ID))
                .thenThrow(new ResourceNotFoundException("RoutingRule", RULE_ID));

        mockMvc.perform(post("/api/admin/tenant-config/routing-rules/{ruleId}/activate", RULE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ========== POST /routing-rules/{ruleId}/deactivate endpoint ==========

    @Test
    void deactivateRoutingRuleReturns200() throws Exception {
        var view = new TenantConfigWriteFacade.RoutingRuleUpdatedView(
                TENANT_ID, RULE_ID, "Rule", 10, null, null, false,
                Instant.parse("2026-04-08T12:00:00Z"));

        when(writeFacade.deactivateRoutingRule(TENANT_ID, RULE_ID)).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/routing-rules/{ruleId}/deactivate", RULE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.ruleId").value(RULE_ID.toString()))
                .andExpect(jsonPath("$.name").value("Rule"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.workItemType").doesNotExist());
    }

    @Test
    void deactivateRoutingRuleMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/routing-rules/{ruleId}/deactivate", RULE_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deactivateRoutingRuleTenantNotFoundReturns404() throws Exception {
        when(writeFacade.deactivateRoutingRule(TENANT_ID, RULE_ID))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(post("/api/admin/tenant-config/routing-rules/{ruleId}/deactivate", RULE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void deactivateRoutingRuleNotFoundReturns404() throws Exception {
        when(writeFacade.deactivateRoutingRule(TENANT_ID, RULE_ID))
                .thenThrow(new ResourceNotFoundException("RoutingRule", RULE_ID));

        mockMvc.perform(post("/api/admin/tenant-config/routing-rules/{ruleId}/deactivate", RULE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ========== DELETE /routing-rules/{ruleId} endpoint ==========

    @Test
    void deleteRoutingRuleReturns204() throws Exception {
        mockMvc.perform(delete("/api/admin/tenant-config/routing-rules/{ruleId}", RULE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void deleteRoutingRuleMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(delete("/api/admin/tenant-config/routing-rules/{ruleId}", RULE_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRoutingRuleTenantNotFoundReturns404() throws Exception {
        doThrow(new ResourceNotFoundException("Tenant", TENANT_ID))
                .when(writeFacade).deleteRoutingRule(TENANT_ID, RULE_ID);

        mockMvc.perform(delete("/api/admin/tenant-config/routing-rules/{ruleId}", RULE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void deleteRoutingRuleNotFoundReturns404() throws Exception {
        doThrow(new ResourceNotFoundException("RoutingRule", RULE_ID))
                .when(writeFacade).deleteRoutingRule(TENANT_ID, RULE_ID);

        mockMvc.perform(delete("/api/admin/tenant-config/routing-rules/{ruleId}", RULE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }
}
