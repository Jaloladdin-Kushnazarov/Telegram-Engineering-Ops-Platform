package com.engops.platform.admin;

import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
