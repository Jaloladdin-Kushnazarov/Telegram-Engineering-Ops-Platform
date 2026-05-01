package com.engops.platform.admin;

import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.engops.platform.infrastructure.security.SecurityConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
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
@Import(SecurityConfig.class)
class TenantConfigControllerTest {

    
private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String ACTOR_HEADER = "X-Actor-User-Id";


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

        when(detailsFacade.getDetails(eq(TENANT_ID), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/details")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
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

        when(detailsFacade.getDetails(eq(TENANT_ID), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/details")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
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
        when(detailsFacade.getDetails(eq(TENANT_ID), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/details")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void detailsInvalidTenantIdReturns400() throws Exception {
        when(detailsFacade.getDetails(eq(TENANT_ID), any()))
                .thenThrow(new IllegalArgumentException("tenantId null bo'lishi mumkin emas"));

        mockMvc.perform(get("/api/admin/tenant-config/details")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
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

        when(detailsFacade.getWorkflowDefinitions(eq(TENANT_ID), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/workflow-definitions")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
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

        when(detailsFacade.getWorkflowDefinitions(eq(TENANT_ID), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/workflow-definitions")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void workflowDefinitionsTenantNotFoundReturns404() throws Exception {
        when(detailsFacade.getWorkflowDefinitions(eq(TENANT_ID), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/workflow-definitions")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void workflowDefinitionsInvalidTenantIdReturns400() throws Exception {
        when(detailsFacade.getWorkflowDefinitions(eq(TENANT_ID), any()))
                .thenThrow(new IllegalArgumentException("tenantId null bo'lishi mumkin emas"));

        mockMvc.perform(get("/api/admin/tenant-config/workflow-definitions")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
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

        when(detailsFacade.getRoutingRules(eq(TENANT_ID), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/routing-rules")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
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

        when(detailsFacade.getRoutingRules(eq(TENANT_ID), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/routing-rules")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void routingRulesTenantNotFoundReturns404() throws Exception {
        when(detailsFacade.getRoutingRules(eq(TENANT_ID), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/routing-rules")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void routingRulesInvalidTenantIdReturns400() throws Exception {
        when(detailsFacade.getRoutingRules(eq(TENANT_ID), any()))
                .thenThrow(new IllegalArgumentException("tenantId null bo'lishi mumkin emas"));

        mockMvc.perform(get("/api/admin/tenant-config/routing-rules")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
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

        when(detailsFacade.getChatBindings(eq(TENANT_ID), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/chat-bindings")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
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

        when(detailsFacade.getChatBindings(eq(TENANT_ID), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/chat-bindings")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void chatBindingsTenantNotFoundReturns404() throws Exception {
        when(detailsFacade.getChatBindings(eq(TENANT_ID), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/chat-bindings")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void chatBindingsInvalidTenantIdReturns400() throws Exception {
        when(detailsFacade.getChatBindings(eq(TENANT_ID), any()))
                .thenThrow(new IllegalArgumentException("tenantId null bo'lishi mumkin emas"));

        mockMvc.perform(get("/api/admin/tenant-config/chat-bindings")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
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

        when(detailsFacade.getTopicBindings(eq(TENANT_ID), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/topic-bindings")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
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

        when(detailsFacade.getTopicBindings(eq(TENANT_ID), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/topic-bindings")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void topicBindingsTenantNotFoundReturns404() throws Exception {
        when(detailsFacade.getTopicBindings(eq(TENANT_ID), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/topic-bindings")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void topicBindingsInvalidTenantIdReturns400() throws Exception {
        when(detailsFacade.getTopicBindings(eq(TENANT_ID), any()))
                .thenThrow(new IllegalArgumentException("tenantId null bo'lishi mumkin emas"));

        mockMvc.perform(get("/api/admin/tenant-config/topic-bindings")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
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

        when(detailsFacade.getMemberships(eq(TENANT_ID), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/memberships")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
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

        when(detailsFacade.getMemberships(eq(TENANT_ID), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/memberships")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void membershipsTenantNotFoundReturns404() throws Exception {
        when(detailsFacade.getMemberships(eq(TENANT_ID), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/memberships")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void membershipsInvalidTenantIdReturns400() throws Exception {
        when(detailsFacade.getMemberships(eq(TENANT_ID), any()))
                .thenThrow(new IllegalArgumentException("tenantId null bo'lishi mumkin emas"));

        mockMvc.perform(get("/api/admin/tenant-config/memberships")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
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

        when(detailsFacade.getRoles(eq(TENANT_ID), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/roles")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
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

        when(detailsFacade.getRoles(eq(TENANT_ID), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/roles")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void rolesTenantNotFoundReturns404() throws Exception {
        when(detailsFacade.getRoles(eq(TENANT_ID), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/roles")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void rolesInvalidTenantIdReturns400() throws Exception {
        when(detailsFacade.getRoles(eq(TENANT_ID), any()))
                .thenThrow(new IllegalArgumentException("tenantId null bo'lishi mumkin emas"));

        mockMvc.perform(get("/api/admin/tenant-config/roles")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void rolesMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/tenant-config/roles"))
                .andExpect(status().isBadRequest());
    }

    // ========== GET /permissions — global permission catalog list ==========

    @Test
    void permissionsReturnsOkWithItems() throws Exception {
        var items = List.of(
                new TenantConfigDetailsFacade.PermissionItemView(
                        UUID.fromString("dd111111-1111-1111-1111-111111111111"),
                        "TENANT_CONFIG_READ",
                        null,
                        Instant.parse("2026-01-05T08:00:00Z")),
                new TenantConfigDetailsFacade.PermissionItemView(
                        UUID.fromString("dd222222-2222-2222-2222-222222222222"),
                        "TENANT_CONFIG_WRITE",
                        "Tenant config yozish",
                        Instant.parse("2026-01-10T08:00:00Z")));
        var view = new TenantConfigDetailsFacade.PermissionListView(TENANT_ID, items);

        when(detailsFacade.getPermissions(eq(TENANT_ID), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/permissions")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].permissionId").value("dd111111-1111-1111-1111-111111111111"))
                .andExpect(jsonPath("$.items[0].code").value("TENANT_CONFIG_READ"))
                .andExpect(jsonPath("$.items[0].description").doesNotExist())
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-01-05T08:00:00Z"))
                .andExpect(jsonPath("$.items[1].permissionId").value("dd222222-2222-2222-2222-222222222222"))
                .andExpect(jsonPath("$.items[1].code").value("TENANT_CONFIG_WRITE"))
                .andExpect(jsonPath("$.items[1].description").value("Tenant config yozish"));
    }

    @Test
    void permissionsWithEmptyListReturnsValidResponse() throws Exception {
        var view = new TenantConfigDetailsFacade.PermissionListView(
                TENANT_ID, List.of());

        when(detailsFacade.getPermissions(eq(TENANT_ID), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/permissions")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void permissionsTenantNotFoundReturns404() throws Exception {
        when(detailsFacade.getPermissions(eq(TENANT_ID), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/permissions")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void permissionsMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/tenant-config/permissions"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void permissionsInvalidTenantIdFormatReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/tenant-config/permissions")
                        .param("tenantId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void permissionsAccessDeniedReturns403() throws Exception {
        when(detailsFacade.getPermissions(eq(TENANT_ID), any()))
                .thenThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"));

        mockMvc.perform(get("/api/admin/tenant-config/permissions")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ========== GET /roles/{roleId}/permissions — role-permission read endpoint ==========

    @Test
    void rolePermissionsReturnsOkWithItems() throws Exception {
        UUID roleId = UUID.fromString("cc111111-1111-1111-1111-111111111111");
        var items = List.of(
                new TenantConfigDetailsFacade.PermissionItemView(
                        UUID.fromString("dd111111-1111-1111-1111-111111111111"),
                        "TENANT_CONFIG_READ",
                        null,
                        Instant.parse("2026-01-05T08:00:00Z")),
                new TenantConfigDetailsFacade.PermissionItemView(
                        UUID.fromString("dd222222-2222-2222-2222-222222222222"),
                        "TENANT_CONFIG_WRITE",
                        "Tenant config yozish",
                        Instant.parse("2026-01-10T08:00:00Z")));
        var view = new TenantConfigDetailsFacade.RolePermissionListView(
                TENANT_ID, roleId, "ADMIN", "Administrator", items);

        when(detailsFacade.getRolePermissions(eq(TENANT_ID), eq(roleId), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/roles/{roleId}/permissions", roleId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.roleId").value(roleId.toString()))
                .andExpect(jsonPath("$.roleCode").value("ADMIN"))
                .andExpect(jsonPath("$.roleName").value("Administrator"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].permissionId").value("dd111111-1111-1111-1111-111111111111"))
                .andExpect(jsonPath("$.items[0].code").value("TENANT_CONFIG_READ"))
                .andExpect(jsonPath("$.items[0].description").doesNotExist())
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-01-05T08:00:00Z"))
                .andExpect(jsonPath("$.items[1].permissionId").value("dd222222-2222-2222-2222-222222222222"))
                .andExpect(jsonPath("$.items[1].code").value("TENANT_CONFIG_WRITE"))
                .andExpect(jsonPath("$.items[1].description").value("Tenant config yozish"));
    }

    @Test
    void rolePermissionsWithEmptyListReturnsValidResponse() throws Exception {
        UUID roleId = UUID.fromString("cc111111-1111-1111-1111-111111111111");
        var view = new TenantConfigDetailsFacade.RolePermissionListView(
                TENANT_ID, roleId, "ADMIN", "Administrator", List.of());

        when(detailsFacade.getRolePermissions(eq(TENANT_ID), eq(roleId), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/roles/{roleId}/permissions", roleId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.roleId").value(roleId.toString()))
                .andExpect(jsonPath("$.roleCode").value("ADMIN"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void rolePermissionsTenantNotFoundReturns404() throws Exception {
        UUID roleId = UUID.fromString("cc111111-1111-1111-1111-111111111111");
        when(detailsFacade.getRolePermissions(eq(TENANT_ID), eq(roleId), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/roles/{roleId}/permissions", roleId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void rolePermissionsRoleNotFoundReturns404() throws Exception {
        UUID roleId = UUID.fromString("cc111111-1111-1111-1111-111111111111");
        when(detailsFacade.getRolePermissions(eq(TENANT_ID), eq(roleId), any()))
                .thenThrow(new ResourceNotFoundException("Role", roleId));

        mockMvc.perform(get("/api/admin/tenant-config/roles/{roleId}/permissions", roleId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void rolePermissionsMissingTenantIdReturns400() throws Exception {
        UUID roleId = UUID.fromString("cc111111-1111-1111-1111-111111111111");
        mockMvc.perform(get("/api/admin/tenant-config/roles/{roleId}/permissions", roleId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rolePermissionsInvalidTenantIdFormatReturns400() throws Exception {
        UUID roleId = UUID.fromString("cc111111-1111-1111-1111-111111111111");
        mockMvc.perform(get("/api/admin/tenant-config/roles/{roleId}/permissions", roleId)
                        .param("tenantId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rolePermissionsInvalidRoleIdFormatReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/tenant-config/roles/{roleId}/permissions", "not-a-uuid")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rolePermissionsAccessDeniedReturns403() throws Exception {
        UUID roleId = UUID.fromString("cc111111-1111-1111-1111-111111111111");
        when(detailsFacade.getRolePermissions(eq(TENANT_ID), eq(roleId), any()))
                .thenThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"));

        mockMvc.perform(get("/api/admin/tenant-config/roles/{roleId}/permissions", roleId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void rolePermissionsNullDescriptionOmittedFromJson() throws Exception {
        UUID roleId = UUID.fromString("cc111111-1111-1111-1111-111111111111");
        var items = List.of(
                new TenantConfigDetailsFacade.PermissionItemView(
                        UUID.fromString("dd111111-1111-1111-1111-111111111111"),
                        "TENANT_CONFIG_READ",
                        null,
                        Instant.parse("2026-01-05T08:00:00Z")));
        var view = new TenantConfigDetailsFacade.RolePermissionListView(
                TENANT_ID, roleId, "ADMIN", "Administrator", items);

        when(detailsFacade.getRolePermissions(eq(TENANT_ID), eq(roleId), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/roles/{roleId}/permissions", roleId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].description").doesNotExist());
    }

    // ========== GET /permissions/{permissionId}/roles — permission-role read endpoint ==========

    @Test
    void permissionRolesReturnsOkWithItems() throws Exception {
        UUID permissionId = UUID.fromString("dd111111-1111-1111-1111-111111111111");
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
        var view = new TenantConfigDetailsFacade.PermissionRoleListView(
                TENANT_ID, permissionId, "TENANT_CONFIG_READ", items);

        when(detailsFacade.getPermissionRoles(eq(TENANT_ID), eq(permissionId), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/permissions/{permissionId}/roles", permissionId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.permissionId").value(permissionId.toString()))
                .andExpect(jsonPath("$.permissionCode").value("TENANT_CONFIG_READ"))
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
    void permissionRolesWithEmptyListReturnsValidResponse() throws Exception {
        UUID permissionId = UUID.fromString("dd111111-1111-1111-1111-111111111111");
        var view = new TenantConfigDetailsFacade.PermissionRoleListView(
                TENANT_ID, permissionId, "TENANT_CONFIG_READ", List.of());

        when(detailsFacade.getPermissionRoles(eq(TENANT_ID), eq(permissionId), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/permissions/{permissionId}/roles", permissionId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.permissionId").value(permissionId.toString()))
                .andExpect(jsonPath("$.permissionCode").value("TENANT_CONFIG_READ"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void permissionRolesTenantNotFoundReturns404() throws Exception {
        UUID permissionId = UUID.fromString("dd111111-1111-1111-1111-111111111111");
        when(detailsFacade.getPermissionRoles(eq(TENANT_ID), eq(permissionId), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/permissions/{permissionId}/roles", permissionId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void permissionRolesPermissionNotFoundReturns404() throws Exception {
        UUID permissionId = UUID.fromString("dd111111-1111-1111-1111-111111111111");
        when(detailsFacade.getPermissionRoles(eq(TENANT_ID), eq(permissionId), any()))
                .thenThrow(new ResourceNotFoundException("Permission", permissionId));

        mockMvc.perform(get("/api/admin/tenant-config/permissions/{permissionId}/roles", permissionId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void permissionRolesMissingTenantIdReturns400() throws Exception {
        UUID permissionId = UUID.fromString("dd111111-1111-1111-1111-111111111111");
        mockMvc.perform(get("/api/admin/tenant-config/permissions/{permissionId}/roles", permissionId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void permissionRolesInvalidTenantIdFormatReturns400() throws Exception {
        UUID permissionId = UUID.fromString("dd111111-1111-1111-1111-111111111111");
        mockMvc.perform(get("/api/admin/tenant-config/permissions/{permissionId}/roles", permissionId)
                        .param("tenantId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void permissionRolesInvalidPermissionIdFormatReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/tenant-config/permissions/{permissionId}/roles", "not-a-uuid")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void permissionRolesAccessDeniedReturns403() throws Exception {
        UUID permissionId = UUID.fromString("dd111111-1111-1111-1111-111111111111");
        when(detailsFacade.getPermissionRoles(eq(TENANT_ID), eq(permissionId), any()))
                .thenThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"));

        mockMvc.perform(get("/api/admin/tenant-config/permissions/{permissionId}/roles", permissionId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void permissionRolesNullDescriptionOmittedFromJson() throws Exception {
        UUID permissionId = UUID.fromString("dd111111-1111-1111-1111-111111111111");
        var items = List.of(
                new TenantConfigDetailsFacade.RoleItemView(
                        UUID.fromString("cc111111-1111-1111-1111-111111111111"),
                        "ADMIN",
                        "Administrator",
                        null,
                        true,
                        Instant.parse("2026-01-05T08:00:00Z")));
        var view = new TenantConfigDetailsFacade.PermissionRoleListView(
                TENANT_ID, permissionId, "TENANT_CONFIG_READ", items);

        when(detailsFacade.getPermissionRoles(eq(TENANT_ID), eq(permissionId), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/permissions/{permissionId}/roles", permissionId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].description").doesNotExist());
    }

    // ========== GET /memberships/{membershipId}/roles — membership-role read endpoint ==========

    @Test
    void membershipRolesReturnsOkWithItems() throws Exception {
        UUID membershipId = UUID.fromString("ee111111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("ff111111-1111-1111-1111-111111111111");
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
        var view = new TenantConfigDetailsFacade.MembershipRoleListView(
                TENANT_ID, membershipId, userId, "ACTIVE", items);

        when(detailsFacade.getMembershipRoles(eq(TENANT_ID), eq(membershipId), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/memberships/{membershipId}/roles", membershipId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.membershipId").value(membershipId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"))
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
    void membershipRolesWithEmptyListReturnsValidResponse() throws Exception {
        UUID membershipId = UUID.fromString("ee111111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("ff111111-1111-1111-1111-111111111111");
        var view = new TenantConfigDetailsFacade.MembershipRoleListView(
                TENANT_ID, membershipId, userId, "SUSPENDED", List.of());

        when(detailsFacade.getMembershipRoles(eq(TENANT_ID), eq(membershipId), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/memberships/{membershipId}/roles", membershipId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.membershipId").value(membershipId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.membershipStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void membershipRolesTenantNotFoundReturns404() throws Exception {
        UUID membershipId = UUID.fromString("ee111111-1111-1111-1111-111111111111");
        when(detailsFacade.getMembershipRoles(eq(TENANT_ID), eq(membershipId), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/memberships/{membershipId}/roles", membershipId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void membershipRolesMembershipNotFoundReturns404() throws Exception {
        UUID membershipId = UUID.fromString("ee111111-1111-1111-1111-111111111111");
        when(detailsFacade.getMembershipRoles(eq(TENANT_ID), eq(membershipId), any()))
                .thenThrow(new ResourceNotFoundException("Membership", membershipId));

        mockMvc.perform(get("/api/admin/tenant-config/memberships/{membershipId}/roles", membershipId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void membershipRolesMissingTenantIdReturns400() throws Exception {
        UUID membershipId = UUID.fromString("ee111111-1111-1111-1111-111111111111");
        mockMvc.perform(get("/api/admin/tenant-config/memberships/{membershipId}/roles", membershipId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void membershipRolesInvalidTenantIdFormatReturns400() throws Exception {
        UUID membershipId = UUID.fromString("ee111111-1111-1111-1111-111111111111");
        mockMvc.perform(get("/api/admin/tenant-config/memberships/{membershipId}/roles", membershipId)
                        .param("tenantId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void membershipRolesInvalidMembershipIdFormatReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/tenant-config/memberships/{membershipId}/roles", "not-a-uuid")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void membershipRolesAccessDeniedReturns403() throws Exception {
        UUID membershipId = UUID.fromString("ee111111-1111-1111-1111-111111111111");
        when(detailsFacade.getMembershipRoles(eq(TENANT_ID), eq(membershipId), any()))
                .thenThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"));

        mockMvc.perform(get("/api/admin/tenant-config/memberships/{membershipId}/roles", membershipId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void membershipRolesNullDescriptionOmittedFromJson() throws Exception {
        UUID membershipId = UUID.fromString("ee111111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("ff111111-1111-1111-1111-111111111111");
        var items = List.of(
                new TenantConfigDetailsFacade.RoleItemView(
                        UUID.fromString("cc111111-1111-1111-1111-111111111111"),
                        "ADMIN",
                        "Administrator",
                        null,
                        true,
                        Instant.parse("2026-01-05T08:00:00Z")));
        var view = new TenantConfigDetailsFacade.MembershipRoleListView(
                TENANT_ID, membershipId, userId, "ACTIVE", items);

        when(detailsFacade.getMembershipRoles(eq(TENANT_ID), eq(membershipId), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/memberships/{membershipId}/roles", membershipId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].description").doesNotExist());
    }

    // ========== GET /workflow-definitions/{definitionId} — workflow detail read endpoint ==========

    @Test
    void workflowDetailsReturnsOkWithStatusesAndRules() throws Exception {
        UUID definitionId = UUID.fromString("aa111111-1111-1111-1111-111111111111");
        UUID s1Id = UUID.fromString("bb111111-1111-1111-1111-111111111111");
        UUID s2Id = UUID.fromString("bb222222-2222-2222-2222-222222222222");
        UUID r1Id = UUID.fromString("dd111111-1111-1111-1111-111111111111");
        UUID requiredPermId = UUID.fromString("ee111111-1111-1111-1111-111111111111");

        var statuses = List.of(
                new TenantConfigDetailsFacade.WorkflowStatusItemView(s1Id, "BUGS", 0, true, false),
                new TenantConfigDetailsFacade.WorkflowStatusItemView(s2Id, "FIXED", 1, false, true));
        var rules = List.of(
                new TenantConfigDetailsFacade.WorkflowTransitionRuleItemView(
                        r1Id, s1Id, "BUGS", s2Id, "FIXED", requiredPermId));
        var view = new TenantConfigDetailsFacade.WorkflowDefinitionDetailView(
                TENANT_ID, definitionId,
                "Bug Flow", "BUG", "Bug workflow", true,
                Instant.parse("2026-01-15T08:00:00Z"),
                statuses, rules);

        when(detailsFacade.getWorkflowDefinitionDetails(eq(TENANT_ID), eq(definitionId), any()))
                .thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/workflow-definitions/{definitionId}", definitionId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.definitionId").value(definitionId.toString()))
                .andExpect(jsonPath("$.name").value("Bug Flow"))
                .andExpect(jsonPath("$.workItemType").value("BUG"))
                .andExpect(jsonPath("$.description").value("Bug workflow"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").value("2026-01-15T08:00:00Z"))
                .andExpect(jsonPath("$.statuses").isArray())
                .andExpect(jsonPath("$.statuses.length()").value(2))
                .andExpect(jsonPath("$.statuses[0].statusId").value(s1Id.toString()))
                .andExpect(jsonPath("$.statuses[0].name").value("BUGS"))
                .andExpect(jsonPath("$.statuses[0].statusOrder").value(0))
                .andExpect(jsonPath("$.statuses[0].initial").value(true))
                .andExpect(jsonPath("$.statuses[0].terminal").value(false))
                .andExpect(jsonPath("$.statuses[1].name").value("FIXED"))
                .andExpect(jsonPath("$.statuses[1].terminal").value(true))
                .andExpect(jsonPath("$.transitionRules").isArray())
                .andExpect(jsonPath("$.transitionRules.length()").value(1))
                .andExpect(jsonPath("$.transitionRules[0].ruleId").value(r1Id.toString()))
                .andExpect(jsonPath("$.transitionRules[0].fromStatusId").value(s1Id.toString()))
                .andExpect(jsonPath("$.transitionRules[0].fromStatusName").value("BUGS"))
                .andExpect(jsonPath("$.transitionRules[0].toStatusId").value(s2Id.toString()))
                .andExpect(jsonPath("$.transitionRules[0].toStatusName").value("FIXED"))
                .andExpect(jsonPath("$.transitionRules[0].requiredPermissionId").value(requiredPermId.toString()));
    }

    @Test
    void workflowDetailsReturnsOkWithEmptyStatusesAndRules() throws Exception {
        UUID definitionId = UUID.fromString("aa111111-1111-1111-1111-111111111111");
        var view = new TenantConfigDetailsFacade.WorkflowDefinitionDetailView(
                TENANT_ID, definitionId,
                "Empty Wf", "TASK", null, false,
                Instant.parse("2026-01-15T08:00:00Z"),
                List.of(), List.of());

        when(detailsFacade.getWorkflowDefinitionDetails(eq(TENANT_ID), eq(definitionId), any()))
                .thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/workflow-definitions/{definitionId}", definitionId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statuses").isArray())
                .andExpect(jsonPath("$.statuses.length()").value(0))
                .andExpect(jsonPath("$.transitionRules").isArray())
                .andExpect(jsonPath("$.transitionRules.length()").value(0))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void workflowDetailsTenantNotFoundReturns404() throws Exception {
        UUID definitionId = UUID.fromString("aa111111-1111-1111-1111-111111111111");
        when(detailsFacade.getWorkflowDefinitionDetails(eq(TENANT_ID), eq(definitionId), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/workflow-definitions/{definitionId}", definitionId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void workflowDetailsDefinitionNotFoundReturns404() throws Exception {
        UUID definitionId = UUID.fromString("aa111111-1111-1111-1111-111111111111");
        when(detailsFacade.getWorkflowDefinitionDetails(eq(TENANT_ID), eq(definitionId), any()))
                .thenThrow(new ResourceNotFoundException("WorkflowDefinition", definitionId));

        mockMvc.perform(get("/api/admin/tenant-config/workflow-definitions/{definitionId}", definitionId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void workflowDetailsMissingTenantIdReturns400() throws Exception {
        UUID definitionId = UUID.fromString("aa111111-1111-1111-1111-111111111111");
        mockMvc.perform(get("/api/admin/tenant-config/workflow-definitions/{definitionId}", definitionId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void workflowDetailsInvalidTenantIdFormatReturns400() throws Exception {
        UUID definitionId = UUID.fromString("aa111111-1111-1111-1111-111111111111");
        mockMvc.perform(get("/api/admin/tenant-config/workflow-definitions/{definitionId}", definitionId)
                        .param("tenantId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void workflowDetailsInvalidDefinitionIdFormatReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/tenant-config/workflow-definitions/{definitionId}", "not-a-uuid")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void workflowDetailsAccessDeniedReturns403() throws Exception {
        UUID definitionId = UUID.fromString("aa111111-1111-1111-1111-111111111111");
        when(detailsFacade.getWorkflowDefinitionDetails(eq(TENANT_ID), eq(definitionId), any()))
                .thenThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"));

        mockMvc.perform(get("/api/admin/tenant-config/workflow-definitions/{definitionId}", definitionId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void workflowDetailsNullDescriptionAndNullRequiredPermissionOmittedFromJson() throws Exception {
        UUID definitionId = UUID.fromString("aa111111-1111-1111-1111-111111111111");
        UUID s1Id = UUID.fromString("bb111111-1111-1111-1111-111111111111");
        UUID s2Id = UUID.fromString("bb222222-2222-2222-2222-222222222222");
        UUID r1Id = UUID.fromString("dd111111-1111-1111-1111-111111111111");

        var statuses = List.of(
                new TenantConfigDetailsFacade.WorkflowStatusItemView(s1Id, "BUGS", 0, true, false),
                new TenantConfigDetailsFacade.WorkflowStatusItemView(s2Id, "FIXED", 1, false, true));
        var rules = List.of(
                new TenantConfigDetailsFacade.WorkflowTransitionRuleItemView(
                        r1Id, s1Id, "BUGS", s2Id, "FIXED", null));
        var view = new TenantConfigDetailsFacade.WorkflowDefinitionDetailView(
                TENANT_ID, definitionId,
                "Bug Flow", "BUG", null, true,
                Instant.parse("2026-01-15T08:00:00Z"),
                statuses, rules);

        when(detailsFacade.getWorkflowDefinitionDetails(eq(TENANT_ID), eq(definitionId), any()))
                .thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/workflow-definitions/{definitionId}", definitionId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.transitionRules[0].requiredPermissionId").doesNotExist());
    }

    // ========== GET /routing-rules/{ruleId} — routing rule detail read endpoint ==========

    @Test
    void routingRuleDetailsReturnsOkWithFullTargetContext() throws Exception {
        UUID ruleId = UUID.fromString("aa999999-9999-9999-9999-999999999991");
        UUID topicBindingId = UUID.fromString("bb999999-9999-9999-9999-999999999991");
        UUID chatBindingId = UUID.fromString("cc999999-9999-9999-9999-999999999991");

        var target = new TenantConfigDetailsFacade.TargetTopicBindingView(
                topicBindingId, 123L, "Bugs Topic", "BUGS", true,
                chatBindingId, -1001234567890L, "Engineering Chat", "MAIN_GROUP");
        var view = new TenantConfigDetailsFacade.RoutingRuleDetailView(
                TENANT_ID, ruleId, "Bug Routing", "BUG", 10, "priority == HIGH", true,
                Instant.parse("2026-01-15T08:00:00Z"), target);

        when(detailsFacade.getRoutingRuleDetails(eq(TENANT_ID), eq(ruleId), any()))
                .thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/routing-rules/{ruleId}", ruleId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.ruleId").value(ruleId.toString()))
                .andExpect(jsonPath("$.name").value("Bug Routing"))
                .andExpect(jsonPath("$.workItemType").value("BUG"))
                .andExpect(jsonPath("$.priority").value(10))
                .andExpect(jsonPath("$.conditionExpression").value("priority == HIGH"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").value("2026-01-15T08:00:00Z"))
                .andExpect(jsonPath("$.targetTopicBinding.topicBindingId").value(topicBindingId.toString()))
                .andExpect(jsonPath("$.targetTopicBinding.topicId").value(123))
                .andExpect(jsonPath("$.targetTopicBinding.topicName").value("Bugs Topic"))
                .andExpect(jsonPath("$.targetTopicBinding.purpose").value("BUGS"))
                .andExpect(jsonPath("$.targetTopicBinding.active").value(true))
                .andExpect(jsonPath("$.targetTopicBinding.chatBindingId").value(chatBindingId.toString()))
                .andExpect(jsonPath("$.targetTopicBinding.chatId").value(-1001234567890L))
                .andExpect(jsonPath("$.targetTopicBinding.chatTitle").value("Engineering Chat"))
                .andExpect(jsonPath("$.targetTopicBinding.chatBindingType").value("MAIN_GROUP"));
    }

    @Test
    void routingRuleDetailsReturnsOkWithoutTargetTopicBinding() throws Exception {
        UUID ruleId = UUID.fromString("aa999999-9999-9999-9999-999999999991");
        var view = new TenantConfigDetailsFacade.RoutingRuleDetailView(
                TENANT_ID, ruleId, "No Target", "TASK", 0, null, false,
                Instant.parse("2026-01-15T08:00:00Z"), null);

        when(detailsFacade.getRoutingRuleDetails(eq(TENANT_ID), eq(ruleId), any()))
                .thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/routing-rules/{ruleId}", ruleId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetTopicBinding").doesNotExist())
                .andExpect(jsonPath("$.conditionExpression").doesNotExist())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void routingRuleDetailsTenantNotFoundReturns404() throws Exception {
        UUID ruleId = UUID.fromString("aa999999-9999-9999-9999-999999999991");
        when(detailsFacade.getRoutingRuleDetails(eq(TENANT_ID), eq(ruleId), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/routing-rules/{ruleId}", ruleId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void routingRuleDetailsRuleNotFoundReturns404() throws Exception {
        UUID ruleId = UUID.fromString("aa999999-9999-9999-9999-999999999991");
        when(detailsFacade.getRoutingRuleDetails(eq(TENANT_ID), eq(ruleId), any()))
                .thenThrow(new ResourceNotFoundException("RoutingRule", ruleId));

        mockMvc.perform(get("/api/admin/tenant-config/routing-rules/{ruleId}", ruleId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void routingRuleDetailsTargetTopicBindingNotFoundReturns404() throws Exception {
        UUID ruleId = UUID.fromString("aa999999-9999-9999-9999-999999999991");
        UUID targetId = UUID.fromString("bb999999-9999-9999-9999-999999999991");
        when(detailsFacade.getRoutingRuleDetails(eq(TENANT_ID), eq(ruleId), any()))
                .thenThrow(new ResourceNotFoundException("TopicBinding", targetId));

        mockMvc.perform(get("/api/admin/tenant-config/routing-rules/{ruleId}", ruleId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void routingRuleDetailsMissingTenantIdReturns400() throws Exception {
        UUID ruleId = UUID.fromString("aa999999-9999-9999-9999-999999999991");
        mockMvc.perform(get("/api/admin/tenant-config/routing-rules/{ruleId}", ruleId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void routingRuleDetailsInvalidTenantIdFormatReturns400() throws Exception {
        UUID ruleId = UUID.fromString("aa999999-9999-9999-9999-999999999991");
        mockMvc.perform(get("/api/admin/tenant-config/routing-rules/{ruleId}", ruleId)
                        .param("tenantId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void routingRuleDetailsInvalidRuleIdFormatReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/tenant-config/routing-rules/{ruleId}", "not-a-uuid")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void routingRuleDetailsAccessDeniedReturns403() throws Exception {
        UUID ruleId = UUID.fromString("aa999999-9999-9999-9999-999999999991");
        when(detailsFacade.getRoutingRuleDetails(eq(TENANT_ID), eq(ruleId), any()))
                .thenThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"));

        mockMvc.perform(get("/api/admin/tenant-config/routing-rules/{ruleId}", ruleId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void routingRuleDetailsNullConditionExpressionOmittedFromJson() throws Exception {
        UUID ruleId = UUID.fromString("aa999999-9999-9999-9999-999999999991");
        var view = new TenantConfigDetailsFacade.RoutingRuleDetailView(
                TENANT_ID, ruleId, "Rule", "BUG", 5, null, true,
                Instant.parse("2026-01-15T08:00:00Z"), null);

        when(detailsFacade.getRoutingRuleDetails(eq(TENANT_ID), eq(ruleId), any()))
                .thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/routing-rules/{ruleId}", ruleId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conditionExpression").doesNotExist());
    }

    @Test
    void routingRuleDetailsNestedNullChatTitleAndTopicNameOmittedFromJson() throws Exception {
        UUID ruleId = UUID.fromString("aa999999-9999-9999-9999-999999999991");
        UUID topicBindingId = UUID.fromString("bb999999-9999-9999-9999-999999999991");
        UUID chatBindingId = UUID.fromString("cc999999-9999-9999-9999-999999999991");

        var target = new TenantConfigDetailsFacade.TargetTopicBindingView(
                topicBindingId, 123L, null, "BUGS", true,
                chatBindingId, -1001234567890L, null, "MAIN_GROUP");
        var view = new TenantConfigDetailsFacade.RoutingRuleDetailView(
                TENANT_ID, ruleId, "Rule", "BUG", 5, null, true,
                Instant.parse("2026-01-15T08:00:00Z"), target);

        when(detailsFacade.getRoutingRuleDetails(eq(TENANT_ID), eq(ruleId), any()))
                .thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/routing-rules/{ruleId}", ruleId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetTopicBinding.topicName").doesNotExist())
                .andExpect(jsonPath("$.targetTopicBinding.chatTitle").doesNotExist())
                .andExpect(jsonPath("$.targetTopicBinding.purpose").value("BUGS"))
                .andExpect(jsonPath("$.targetTopicBinding.chatBindingType").value("MAIN_GROUP"));
    }

    // ========== GET /chat-bindings/{chatBindingId} — chat binding detail read endpoint ==========

    @Test
    void chatBindingDetailsReturnsOkWithNestedTopicBindings() throws Exception {
        UUID chatBindingId = UUID.fromString("88991111-1111-1111-1111-111111111111");
        UUID t1Id = UUID.fromString("99991111-1111-1111-1111-111111111111");
        UUID t2Id = UUID.fromString("99992222-2222-2222-2222-222222222222");

        var topics = List.of(
                new TenantConfigDetailsFacade.ChatBindingTopicItemView(
                        t1Id, 101L, "Bugs", "BUGS", true,
                        Instant.parse("2026-01-16T08:00:00Z")),
                new TenantConfigDetailsFacade.ChatBindingTopicItemView(
                        t2Id, 202L, "Incidents", "INCIDENTS", false,
                        Instant.parse("2026-01-17T08:00:00Z")));
        var view = new TenantConfigDetailsFacade.ChatBindingDetailView(
                TENANT_ID, chatBindingId, -1001234567890L, "Engineering Chat",
                "MAIN_GROUP", true,
                Instant.parse("2026-01-15T08:00:00Z"),
                topics);

        when(detailsFacade.getChatBindingDetails(eq(TENANT_ID), eq(chatBindingId), any()))
                .thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/chat-bindings/{chatBindingId}", chatBindingId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.chatBindingId").value(chatBindingId.toString()))
                .andExpect(jsonPath("$.chatId").value(-1001234567890L))
                .andExpect(jsonPath("$.chatTitle").value("Engineering Chat"))
                .andExpect(jsonPath("$.bindingType").value("MAIN_GROUP"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").value("2026-01-15T08:00:00Z"))
                .andExpect(jsonPath("$.topicBindings").isArray())
                .andExpect(jsonPath("$.topicBindings.length()").value(2))
                .andExpect(jsonPath("$.topicBindings[0].topicBindingId").value(t1Id.toString()))
                .andExpect(jsonPath("$.topicBindings[0].topicId").value(101))
                .andExpect(jsonPath("$.topicBindings[0].topicName").value("Bugs"))
                .andExpect(jsonPath("$.topicBindings[0].purpose").value("BUGS"))
                .andExpect(jsonPath("$.topicBindings[0].active").value(true))
                .andExpect(jsonPath("$.topicBindings[1].topicBindingId").value(t2Id.toString()))
                .andExpect(jsonPath("$.topicBindings[1].purpose").value("INCIDENTS"))
                .andExpect(jsonPath("$.topicBindings[1].active").value(false));
    }

    @Test
    void chatBindingDetailsReturnsOkWithEmptyTopicBindings() throws Exception {
        UUID chatBindingId = UUID.fromString("88991111-1111-1111-1111-111111111111");
        var view = new TenantConfigDetailsFacade.ChatBindingDetailView(
                TENANT_ID, chatBindingId, -99L, null,
                "NOTIFICATION_GROUP", false,
                Instant.parse("2026-01-15T08:00:00Z"),
                List.of());

        when(detailsFacade.getChatBindingDetails(eq(TENANT_ID), eq(chatBindingId), any()))
                .thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/chat-bindings/{chatBindingId}", chatBindingId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bindingType").value("NOTIFICATION_GROUP"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.chatTitle").doesNotExist())
                .andExpect(jsonPath("$.topicBindings").isArray())
                .andExpect(jsonPath("$.topicBindings.length()").value(0));
    }

    @Test
    void chatBindingDetailsTenantNotFoundReturns404() throws Exception {
        UUID chatBindingId = UUID.fromString("88991111-1111-1111-1111-111111111111");
        when(detailsFacade.getChatBindingDetails(eq(TENANT_ID), eq(chatBindingId), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/chat-bindings/{chatBindingId}", chatBindingId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void chatBindingDetailsChatBindingNotFoundReturns404() throws Exception {
        UUID chatBindingId = UUID.fromString("88991111-1111-1111-1111-111111111111");
        when(detailsFacade.getChatBindingDetails(eq(TENANT_ID), eq(chatBindingId), any()))
                .thenThrow(new ResourceNotFoundException("ChatBinding", chatBindingId));

        mockMvc.perform(get("/api/admin/tenant-config/chat-bindings/{chatBindingId}", chatBindingId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void chatBindingDetailsMissingTenantIdReturns400() throws Exception {
        UUID chatBindingId = UUID.fromString("88991111-1111-1111-1111-111111111111");
        mockMvc.perform(get("/api/admin/tenant-config/chat-bindings/{chatBindingId}", chatBindingId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatBindingDetailsInvalidTenantIdFormatReturns400() throws Exception {
        UUID chatBindingId = UUID.fromString("88991111-1111-1111-1111-111111111111");
        mockMvc.perform(get("/api/admin/tenant-config/chat-bindings/{chatBindingId}", chatBindingId)
                        .param("tenantId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatBindingDetailsInvalidChatBindingIdFormatReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/tenant-config/chat-bindings/{chatBindingId}", "not-a-uuid")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatBindingDetailsAccessDeniedReturns403() throws Exception {
        UUID chatBindingId = UUID.fromString("88991111-1111-1111-1111-111111111111");
        when(detailsFacade.getChatBindingDetails(eq(TENANT_ID), eq(chatBindingId), any()))
                .thenThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"));

        mockMvc.perform(get("/api/admin/tenant-config/chat-bindings/{chatBindingId}", chatBindingId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void chatBindingDetailsNullTopicNameOmittedFromJson() throws Exception {
        UUID chatBindingId = UUID.fromString("88991111-1111-1111-1111-111111111111");
        UUID t1Id = UUID.fromString("99991111-1111-1111-1111-111111111111");
        var topics = List.of(
                new TenantConfigDetailsFacade.ChatBindingTopicItemView(
                        t1Id, 101L, null, "BUGS", true,
                        Instant.parse("2026-01-16T08:00:00Z")));
        var view = new TenantConfigDetailsFacade.ChatBindingDetailView(
                TENANT_ID, chatBindingId, -1L, null,
                "MAIN_GROUP", true,
                Instant.parse("2026-01-15T08:00:00Z"),
                topics);

        when(detailsFacade.getChatBindingDetails(eq(TENANT_ID), eq(chatBindingId), any()))
                .thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/chat-bindings/{chatBindingId}", chatBindingId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatTitle").doesNotExist())
                .andExpect(jsonPath("$.topicBindings[0].topicName").doesNotExist())
                .andExpect(jsonPath("$.topicBindings[0].purpose").value("BUGS"));
    }

    // ========== GET /topic-bindings/{topicBindingId} — topic binding detail read endpoint ==========

    @Test
    void topicBindingDetailsReturnsOkWithParentChatContext() throws Exception {
        UUID topicBindingId = UUID.fromString("aabb1111-1111-1111-1111-111111111111");
        UUID chatBindingId = UUID.fromString("aabb2222-2222-2222-2222-222222222222");

        var parent = new TenantConfigDetailsFacade.ParentChatBindingView(
                chatBindingId, -1001234567890L, "Engineering Chat", "MAIN_GROUP");
        var view = new TenantConfigDetailsFacade.TopicBindingDetailView(
                TENANT_ID, topicBindingId, 101L, "Bugs Topic", "BUGS", true,
                Instant.parse("2026-01-15T08:00:00Z"), parent);

        when(detailsFacade.getTopicBindingDetails(eq(TENANT_ID), eq(topicBindingId), any()))
                .thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/topic-bindings/{topicBindingId}", topicBindingId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.topicBindingId").value(topicBindingId.toString()))
                .andExpect(jsonPath("$.topicId").value(101))
                .andExpect(jsonPath("$.topicName").value("Bugs Topic"))
                .andExpect(jsonPath("$.purpose").value("BUGS"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").value("2026-01-15T08:00:00Z"))
                .andExpect(jsonPath("$.parentChatBinding.chatBindingId").value(chatBindingId.toString()))
                .andExpect(jsonPath("$.parentChatBinding.chatId").value(-1001234567890L))
                .andExpect(jsonPath("$.parentChatBinding.chatTitle").value("Engineering Chat"))
                .andExpect(jsonPath("$.parentChatBinding.bindingType").value("MAIN_GROUP"));
    }

    @Test
    void topicBindingDetailsNullTopicNameOmittedFromJson() throws Exception {
        UUID topicBindingId = UUID.fromString("aabb1111-1111-1111-1111-111111111111");
        UUID chatBindingId = UUID.fromString("aabb2222-2222-2222-2222-222222222222");

        var parent = new TenantConfigDetailsFacade.ParentChatBindingView(
                chatBindingId, -99L, "Chat", "NOTIFICATION_GROUP");
        var view = new TenantConfigDetailsFacade.TopicBindingDetailView(
                TENANT_ID, topicBindingId, 202L, null, "INCIDENTS", false,
                Instant.parse("2026-01-15T08:00:00Z"), parent);

        when(detailsFacade.getTopicBindingDetails(eq(TENANT_ID), eq(topicBindingId), any()))
                .thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/topic-bindings/{topicBindingId}", topicBindingId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topicName").doesNotExist())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.parentChatBinding.bindingType").value("NOTIFICATION_GROUP"));
    }

    @Test
    void topicBindingDetailsNullParentChatTitleOmittedFromJson() throws Exception {
        UUID topicBindingId = UUID.fromString("aabb1111-1111-1111-1111-111111111111");
        UUID chatBindingId = UUID.fromString("aabb2222-2222-2222-2222-222222222222");

        var parent = new TenantConfigDetailsFacade.ParentChatBindingView(
                chatBindingId, -99L, null, "MAIN_GROUP");
        var view = new TenantConfigDetailsFacade.TopicBindingDetailView(
                TENANT_ID, topicBindingId, 303L, "Tasks", "TASKS", true,
                Instant.parse("2026-01-15T08:00:00Z"), parent);

        when(detailsFacade.getTopicBindingDetails(eq(TENANT_ID), eq(topicBindingId), any()))
                .thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/topic-bindings/{topicBindingId}", topicBindingId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentChatBinding.chatTitle").doesNotExist())
                .andExpect(jsonPath("$.parentChatBinding.chatId").value(-99L))
                .andExpect(jsonPath("$.topicName").value("Tasks"));
    }

    @Test
    void topicBindingDetailsTenantNotFoundReturns404() throws Exception {
        UUID topicBindingId = UUID.fromString("aabb1111-1111-1111-1111-111111111111");
        when(detailsFacade.getTopicBindingDetails(eq(TENANT_ID), eq(topicBindingId), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/topic-bindings/{topicBindingId}", topicBindingId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void topicBindingDetailsTopicBindingNotFoundReturns404() throws Exception {
        UUID topicBindingId = UUID.fromString("aabb1111-1111-1111-1111-111111111111");
        when(detailsFacade.getTopicBindingDetails(eq(TENANT_ID), eq(topicBindingId), any()))
                .thenThrow(new ResourceNotFoundException("TopicBinding", topicBindingId));

        mockMvc.perform(get("/api/admin/tenant-config/topic-bindings/{topicBindingId}", topicBindingId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void topicBindingDetailsMissingTenantIdReturns400() throws Exception {
        UUID topicBindingId = UUID.fromString("aabb1111-1111-1111-1111-111111111111");
        mockMvc.perform(get("/api/admin/tenant-config/topic-bindings/{topicBindingId}", topicBindingId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void topicBindingDetailsInvalidTenantIdFormatReturns400() throws Exception {
        UUID topicBindingId = UUID.fromString("aabb1111-1111-1111-1111-111111111111");
        mockMvc.perform(get("/api/admin/tenant-config/topic-bindings/{topicBindingId}", topicBindingId)
                        .param("tenantId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void topicBindingDetailsInvalidTopicBindingIdFormatReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/tenant-config/topic-bindings/{topicBindingId}", "not-a-uuid")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void topicBindingDetailsAccessDeniedReturns403() throws Exception {
        UUID topicBindingId = UUID.fromString("aabb1111-1111-1111-1111-111111111111");
        when(detailsFacade.getTopicBindingDetails(eq(TENANT_ID), eq(topicBindingId), any()))
                .thenThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"));

        mockMvc.perform(get("/api/admin/tenant-config/topic-bindings/{topicBindingId}", topicBindingId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ========== GET /memberships/{membershipId} — membership detail read endpoint ==========

    @Test
    void membershipDetailsReturnsOkWithUserIdentity() throws Exception {
        UUID membershipId = UUID.fromString("ee881111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("ff881111-1111-1111-1111-111111111111");

        var userIdentity = new TenantConfigDetailsFacade.UserIdentityView(
                userId, 123456789L, "Engineer One", "eng_one");
        var view = new TenantConfigDetailsFacade.MembershipDetailView(
                TENANT_ID, membershipId, "ACTIVE",
                Instant.parse("2026-01-15T08:00:00Z"), userIdentity);

        when(detailsFacade.getMembershipDetails(eq(TENANT_ID), eq(membershipId), any()))
                .thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/memberships/{membershipId}", membershipId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.membershipId").value(membershipId.toString()))
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-15T08:00:00Z"))
                .andExpect(jsonPath("$.userIdentity.userId").value(userId.toString()))
                .andExpect(jsonPath("$.userIdentity.telegramUserId").value(123456789L))
                .andExpect(jsonPath("$.userIdentity.displayName").value("Engineer One"))
                .andExpect(jsonPath("$.userIdentity.username").value("eng_one"));
    }

    @Test
    void membershipDetailsNullDisplayNameAndUsernameOmittedFromJson() throws Exception {
        UUID membershipId = UUID.fromString("ee881111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("ff881111-1111-1111-1111-111111111111");

        var userIdentity = new TenantConfigDetailsFacade.UserIdentityView(
                userId, 987654321L, null, null);
        var view = new TenantConfigDetailsFacade.MembershipDetailView(
                TENANT_ID, membershipId, "SUSPENDED",
                Instant.parse("2026-02-01T08:00:00Z"), userIdentity);

        when(detailsFacade.getMembershipDetails(eq(TENANT_ID), eq(membershipId), any()))
                .thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/memberships/{membershipId}", membershipId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.userIdentity.telegramUserId").value(987654321L))
                .andExpect(jsonPath("$.userIdentity.displayName").doesNotExist())
                .andExpect(jsonPath("$.userIdentity.username").doesNotExist());
    }

    @Test
    void membershipDetailsTenantNotFoundReturns404() throws Exception {
        UUID membershipId = UUID.fromString("ee881111-1111-1111-1111-111111111111");
        when(detailsFacade.getMembershipDetails(eq(TENANT_ID), eq(membershipId), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/memberships/{membershipId}", membershipId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void membershipDetailsMembershipNotFoundReturns404() throws Exception {
        UUID membershipId = UUID.fromString("ee881111-1111-1111-1111-111111111111");
        when(detailsFacade.getMembershipDetails(eq(TENANT_ID), eq(membershipId), any()))
                .thenThrow(new ResourceNotFoundException("Membership", membershipId));

        mockMvc.perform(get("/api/admin/tenant-config/memberships/{membershipId}", membershipId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void membershipDetailsOrphanUserReturns404() throws Exception {
        UUID membershipId = UUID.fromString("ee881111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("ff881111-1111-1111-1111-111111111111");
        when(detailsFacade.getMembershipDetails(eq(TENANT_ID), eq(membershipId), any()))
                .thenThrow(new ResourceNotFoundException("User", userId));

        mockMvc.perform(get("/api/admin/tenant-config/memberships/{membershipId}", membershipId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void membershipDetailsMissingTenantIdReturns400() throws Exception {
        UUID membershipId = UUID.fromString("ee881111-1111-1111-1111-111111111111");
        mockMvc.perform(get("/api/admin/tenant-config/memberships/{membershipId}", membershipId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void membershipDetailsInvalidTenantIdFormatReturns400() throws Exception {
        UUID membershipId = UUID.fromString("ee881111-1111-1111-1111-111111111111");
        mockMvc.perform(get("/api/admin/tenant-config/memberships/{membershipId}", membershipId)
                        .param("tenantId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void membershipDetailsInvalidMembershipIdFormatReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/tenant-config/memberships/{membershipId}", "not-a-uuid")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void membershipDetailsAccessDeniedReturns403() throws Exception {
        UUID membershipId = UUID.fromString("ee881111-1111-1111-1111-111111111111");
        when(detailsFacade.getMembershipDetails(eq(TENANT_ID), eq(membershipId), any()))
                .thenThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"));

        mockMvc.perform(get("/api/admin/tenant-config/memberships/{membershipId}", membershipId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ========== GET /roles/{roleId} — role detail read endpoint ==========

    @Test
    void roleDetailsReturnsOkWithFullDetail() throws Exception {
        UUID roleId = UUID.fromString("cc991111-1111-1111-1111-111111111111");
        var view = new TenantConfigDetailsFacade.RoleDetailView(
                TENANT_ID, roleId, "ENGINEER", "Engineer", "Engineering role",
                true, true, Instant.parse("2026-01-15T08:00:00Z"));

        when(detailsFacade.getRoleDetails(eq(TENANT_ID), eq(roleId), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/roles/{roleId}", roleId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.roleId").value(roleId.toString()))
                .andExpect(jsonPath("$.code").value("ENGINEER"))
                .andExpect(jsonPath("$.name").value("Engineer"))
                .andExpect(jsonPath("$.description").value("Engineering role"))
                .andExpect(jsonPath("$.systemRole").value(true))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").value("2026-01-15T08:00:00Z"));
    }

    @Test
    void roleDetailsNullDescriptionOmittedFromJson() throws Exception {
        UUID roleId = UUID.fromString("cc991111-1111-1111-1111-111111111111");
        var view = new TenantConfigDetailsFacade.RoleDetailView(
                TENANT_ID, roleId, "CUSTOM", "Custom", null,
                false, false, Instant.parse("2026-02-01T08:00:00Z"));

        when(detailsFacade.getRoleDetails(eq(TENANT_ID), eq(roleId), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/roles/{roleId}", roleId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CUSTOM"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.systemRole").value(false))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void roleDetailsTenantNotFoundReturns404() throws Exception {
        UUID roleId = UUID.fromString("cc991111-1111-1111-1111-111111111111");
        when(detailsFacade.getRoleDetails(eq(TENANT_ID), eq(roleId), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/roles/{roleId}", roleId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void roleDetailsRoleNotFoundReturns404() throws Exception {
        UUID roleId = UUID.fromString("cc991111-1111-1111-1111-111111111111");
        when(detailsFacade.getRoleDetails(eq(TENANT_ID), eq(roleId), any()))
                .thenThrow(new ResourceNotFoundException("Role", roleId));

        mockMvc.perform(get("/api/admin/tenant-config/roles/{roleId}", roleId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void roleDetailsMissingTenantIdReturns400() throws Exception {
        UUID roleId = UUID.fromString("cc991111-1111-1111-1111-111111111111");
        mockMvc.perform(get("/api/admin/tenant-config/roles/{roleId}", roleId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void roleDetailsInvalidTenantIdFormatReturns400() throws Exception {
        UUID roleId = UUID.fromString("cc991111-1111-1111-1111-111111111111");
        mockMvc.perform(get("/api/admin/tenant-config/roles/{roleId}", roleId)
                        .param("tenantId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void roleDetailsInvalidRoleIdFormatReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/tenant-config/roles/{roleId}", "not-a-uuid")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void roleDetailsAccessDeniedReturns403() throws Exception {
        UUID roleId = UUID.fromString("cc991111-1111-1111-1111-111111111111");
        when(detailsFacade.getRoleDetails(eq(TENANT_ID), eq(roleId), any()))
                .thenThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"));

        mockMvc.perform(get("/api/admin/tenant-config/roles/{roleId}", roleId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ========== GET /permissions/{permissionId} — permission detail read endpoint ==========

    @Test
    void permissionDetailsReturnsOkWithFullDetail() throws Exception {
        UUID permissionId = UUID.fromString("dd991111-1111-1111-1111-111111111111");
        var view = new TenantConfigDetailsFacade.PermissionDetailView(
                TENANT_ID, permissionId, "TENANT_CONFIG_READ", "Tenant config o'qish",
                Instant.parse("2026-01-15T08:00:00Z"));

        when(detailsFacade.getPermissionDetails(eq(TENANT_ID), eq(permissionId), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/permissions/{permissionId}", permissionId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.permissionId").value(permissionId.toString()))
                .andExpect(jsonPath("$.code").value("TENANT_CONFIG_READ"))
                .andExpect(jsonPath("$.description").value("Tenant config o'qish"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-15T08:00:00Z"));
    }

    @Test
    void permissionDetailsNullDescriptionOmittedFromJson() throws Exception {
        UUID permissionId = UUID.fromString("dd991111-1111-1111-1111-111111111111");
        var view = new TenantConfigDetailsFacade.PermissionDetailView(
                TENANT_ID, permissionId, "CUSTOM_PERM", null,
                Instant.parse("2026-02-01T08:00:00Z"));

        when(detailsFacade.getPermissionDetails(eq(TENANT_ID), eq(permissionId), any())).thenReturn(view);

        mockMvc.perform(get("/api/admin/tenant-config/permissions/{permissionId}", permissionId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CUSTOM_PERM"))
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void permissionDetailsTenantNotFoundReturns404() throws Exception {
        UUID permissionId = UUID.fromString("dd991111-1111-1111-1111-111111111111");
        when(detailsFacade.getPermissionDetails(eq(TENANT_ID), eq(permissionId), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/permissions/{permissionId}", permissionId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void permissionDetailsPermissionNotFoundReturns404() throws Exception {
        UUID permissionId = UUID.fromString("dd991111-1111-1111-1111-111111111111");
        when(detailsFacade.getPermissionDetails(eq(TENANT_ID), eq(permissionId), any()))
                .thenThrow(new ResourceNotFoundException("Permission", permissionId));

        mockMvc.perform(get("/api/admin/tenant-config/permissions/{permissionId}", permissionId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void permissionDetailsMissingTenantIdReturns400() throws Exception {
        UUID permissionId = UUID.fromString("dd991111-1111-1111-1111-111111111111");
        mockMvc.perform(get("/api/admin/tenant-config/permissions/{permissionId}", permissionId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void permissionDetailsInvalidTenantIdFormatReturns400() throws Exception {
        UUID permissionId = UUID.fromString("dd991111-1111-1111-1111-111111111111");
        mockMvc.perform(get("/api/admin/tenant-config/permissions/{permissionId}", permissionId)
                        .param("tenantId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void permissionDetailsInvalidPermissionIdFormatReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/tenant-config/permissions/{permissionId}", "not-a-uuid")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void permissionDetailsAccessDeniedReturns403() throws Exception {
        UUID permissionId = UUID.fromString("dd991111-1111-1111-1111-111111111111");
        when(detailsFacade.getPermissionDetails(eq(TENANT_ID), eq(permissionId), any()))
                .thenThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"));

        mockMvc.perform(get("/api/admin/tenant-config/permissions/{permissionId}", permissionId)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
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
                any(CreateWorkflowDefinitionRequest.class), any())).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
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
                any(CreateWorkflowDefinitionRequest.class), any())).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
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
                any(CreateWorkflowDefinitionRequest.class), any()))
                .thenThrow(new IllegalArgumentException("name null yoki bo'sh bo'lishi mumkin emas"));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
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
                any(CreateWorkflowDefinitionRequest.class), any()))
                .thenThrow(new IllegalArgumentException(
                        "workItemType faqat BUG, INCIDENT, TASK bo'lishi mumkin: FEATURE"));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
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
                any(CreateWorkflowDefinitionRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
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
                any(CreateWorkflowDefinitionRequest.class), any()))
                .thenThrow(new IllegalArgumentException("name null yoki bo'sh bo'lishi mumkin emas"));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWorkflowDefinitionDuplicateNameReturns422() throws Exception {
        when(writeFacade.createWorkflowDefinition(eq(TENANT_ID),
                any(CreateWorkflowDefinitionRequest.class), any()))
                .thenThrow(new BusinessRuleException("DUPLICATE_WORKFLOW_NAME",
                        "Tenant ichida 'Bug Flow' nomli workflow allaqachon mavjud"));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
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
                any(UpdateWorkflowDefinitionRequest.class), any())).thenReturn(view);

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
                any(UpdateWorkflowDefinitionRequest.class), any())).thenReturn(view);

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
                any(UpdateWorkflowDefinitionRequest.class), any())).thenReturn(view);

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
                any(UpdateWorkflowDefinitionRequest.class), any()))
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
                any(UpdateWorkflowDefinitionRequest.class), any())).thenReturn(view);

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
                any(UpdateWorkflowDefinitionRequest.class), any()))
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
                any(UpdateWorkflowDefinitionRequest.class), any()))
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
                any(UpdateWorkflowDefinitionRequest.class), any()))
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
                any(UpdateWorkflowDefinitionRequest.class), any()))
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

        when(writeFacade.activateWorkflowDefinition(eq(TENANT_ID), eq(DEF_ID), any())).thenReturn(view);

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
        when(writeFacade.activateWorkflowDefinition(eq(TENANT_ID), eq(DEF_ID), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/activate", DEF_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void activateWorkflowDefinitionNotFoundReturns404() throws Exception {
        when(writeFacade.activateWorkflowDefinition(eq(TENANT_ID), eq(DEF_ID), any()))
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

        when(writeFacade.deactivateWorkflowDefinition(eq(TENANT_ID), eq(DEF_ID), any())).thenReturn(view);

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
        when(writeFacade.deactivateWorkflowDefinition(eq(TENANT_ID), eq(DEF_ID), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/deactivate", DEF_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void deactivateWorkflowDefinitionNotFoundReturns404() throws Exception {
        when(writeFacade.deactivateWorkflowDefinition(eq(TENANT_ID), eq(DEF_ID), any()))
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
                .when(writeFacade).deleteWorkflowDefinition(eq(TENANT_ID), eq(DEF_ID), any());

        mockMvc.perform(delete("/api/admin/tenant-config/workflow-definitions/{definitionId}", DEF_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void deleteWorkflowDefinitionNotFoundReturns404() throws Exception {
        doThrow(new ResourceNotFoundException("WorkflowDefinition", DEF_ID))
                .when(writeFacade).deleteWorkflowDefinition(eq(TENANT_ID), eq(DEF_ID), any());

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
                any(CreateChatBindingRequest.class), any())).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/chat-bindings")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
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
                any(CreateChatBindingRequest.class), any())).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/chat-bindings")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
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
                any(CreateChatBindingRequest.class), any()))
                .thenThrow(new IllegalArgumentException(
                        "bindingType faqat MAIN_GROUP, NOTIFICATION_GROUP bo'lishi mumkin: PRIVATE_CHAT"));

        mockMvc.perform(post("/api/admin/tenant-config/chat-bindings")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
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
                any(CreateChatBindingRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(post("/api/admin/tenant-config/chat-bindings")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
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
                any(CreateChatBindingRequest.class), any()))
                .thenThrow(new BusinessRuleException("DUPLICATE_CHAT_BINDING",
                        "Tenant ichida chatId=-1001234567890 uchun binding allaqachon mavjud"));

        mockMvc.perform(post("/api/admin/tenant-config/chat-bindings")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chatId":-1001234567890,"bindingType":"MAIN_GROUP"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_CHAT_BINDING"));
    }

    // ========== PATCH /chat-bindings/{chatBindingId} endpoint ==========

    private static final UUID CB_ID = UUID.fromString("55555555-5555-5555-5555-555555555551");

    @Test
    void updateChatBindingWithOnlyChatTitle() throws Exception {
        var view = new TenantConfigWriteFacade.ChatBindingCreatedView(
                TENANT_ID, CB_ID, -1001234567890L, "New Title", "MAIN_GROUP", true,
                Instant.parse("2026-04-10T12:00:00Z"));

        when(writeFacade.updateChatBinding(eq(TENANT_ID), eq(CB_ID),
                any(UpdateChatBindingRequest.class), any())).thenReturn(view);

        mockMvc.perform(patch("/api/admin/tenant-config/chat-bindings/{chatBindingId}", CB_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chatTitle":"New Title"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.chatBindingId").value(CB_ID.toString()))
                .andExpect(jsonPath("$.chatTitle").value("New Title"))
                .andExpect(jsonPath("$.bindingType").value("MAIN_GROUP"));
    }

    @Test
    void updateChatBindingWithOnlyBindingType() throws Exception {
        var view = new TenantConfigWriteFacade.ChatBindingCreatedView(
                TENANT_ID, CB_ID, -1001234567890L, "Chat", "NOTIFICATION_GROUP", true,
                Instant.parse("2026-04-10T12:00:00Z"));

        when(writeFacade.updateChatBinding(eq(TENANT_ID), eq(CB_ID),
                any(UpdateChatBindingRequest.class), any())).thenReturn(view);

        mockMvc.perform(patch("/api/admin/tenant-config/chat-bindings/{chatBindingId}", CB_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bindingType":"NOTIFICATION_GROUP"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bindingType").value("NOTIFICATION_GROUP"));
    }

    @Test
    void updateChatBindingWithBothFields() throws Exception {
        var view = new TenantConfigWriteFacade.ChatBindingCreatedView(
                TENANT_ID, CB_ID, -1001234567890L, "Updated", "NOTIFICATION_GROUP", true,
                Instant.parse("2026-04-10T12:00:00Z"));

        when(writeFacade.updateChatBinding(eq(TENANT_ID), eq(CB_ID),
                any(UpdateChatBindingRequest.class), any())).thenReturn(view);

        mockMvc.perform(patch("/api/admin/tenant-config/chat-bindings/{chatBindingId}", CB_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chatTitle":"Updated","bindingType":"NOTIFICATION_GROUP"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatTitle").value("Updated"))
                .andExpect(jsonPath("$.bindingType").value("NOTIFICATION_GROUP"));
    }

    @Test
    void updateChatBindingExplicitNullChatTitleOmitsField() throws Exception {
        var view = new TenantConfigWriteFacade.ChatBindingCreatedView(
                TENANT_ID, CB_ID, -1001234567890L, null, "MAIN_GROUP", true,
                Instant.parse("2026-04-10T12:00:00Z"));

        when(writeFacade.updateChatBinding(eq(TENANT_ID), eq(CB_ID),
                any(UpdateChatBindingRequest.class), any())).thenReturn(view);

        mockMvc.perform(patch("/api/admin/tenant-config/chat-bindings/{chatBindingId}", CB_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chatTitle":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatTitle").doesNotExist());
    }

    @Test
    void updateChatBindingMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(patch("/api/admin/tenant-config/chat-bindings/{chatBindingId}", CB_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chatTitle":"Title"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateChatBindingInvalidBindingTypeReturns400() throws Exception {
        when(writeFacade.updateChatBinding(eq(TENANT_ID), eq(CB_ID),
                any(UpdateChatBindingRequest.class), any()))
                .thenThrow(new IllegalArgumentException(
                        "bindingType faqat MAIN_GROUP, NOTIFICATION_GROUP bo'lishi mumkin: PRIVATE"));

        mockMvc.perform(patch("/api/admin/tenant-config/chat-bindings/{chatBindingId}", CB_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bindingType":"PRIVATE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void updateChatBindingTenantNotFoundReturns404() throws Exception {
        when(writeFacade.updateChatBinding(eq(TENANT_ID), eq(CB_ID),
                any(UpdateChatBindingRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(patch("/api/admin/tenant-config/chat-bindings/{chatBindingId}", CB_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chatTitle":"Title"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void updateChatBindingNotFoundReturns404() throws Exception {
        when(writeFacade.updateChatBinding(eq(TENANT_ID), eq(CB_ID),
                any(UpdateChatBindingRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("ChatBinding", CB_ID));

        mockMvc.perform(patch("/api/admin/tenant-config/chat-bindings/{chatBindingId}", CB_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chatTitle":"Title"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void updateChatBindingEmptyBodyReturns400() throws Exception {
        when(writeFacade.updateChatBinding(eq(TENANT_ID), eq(CB_ID),
                any(UpdateChatBindingRequest.class), any()))
                .thenThrow(new IllegalArgumentException("Kamida bitta yangilanuvchi field berilishi kerak"));

        mockMvc.perform(patch("/api/admin/tenant-config/chat-bindings/{chatBindingId}", CB_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    // ========== POST /chat-bindings/{chatBindingId}/activate endpoint ==========

    @Test
    void activateChatBindingReturns200() throws Exception {
        var view = new TenantConfigWriteFacade.ChatBindingCreatedView(
                TENANT_ID, CB_ID, -1001234567890L, "Dev Chat", "MAIN_GROUP", true,
                Instant.parse("2026-04-11T12:00:00Z"));

        when(writeFacade.activateChatBinding(eq(TENANT_ID), eq(CB_ID), any())).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/chat-bindings/{chatBindingId}/activate", CB_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.chatBindingId").value(CB_ID.toString()))
                .andExpect(jsonPath("$.chatId").value(-1001234567890L))
                .andExpect(jsonPath("$.chatTitle").value("Dev Chat"))
                .andExpect(jsonPath("$.bindingType").value("MAIN_GROUP"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").value("2026-04-11T12:00:00Z"));
    }

    @Test
    void activateChatBindingMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/chat-bindings/{chatBindingId}/activate", CB_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void activateChatBindingTenantNotFoundReturns404() throws Exception {
        when(writeFacade.activateChatBinding(eq(TENANT_ID), eq(CB_ID), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(post("/api/admin/tenant-config/chat-bindings/{chatBindingId}/activate", CB_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void activateChatBindingNotFoundReturns404() throws Exception {
        when(writeFacade.activateChatBinding(eq(TENANT_ID), eq(CB_ID), any()))
                .thenThrow(new ResourceNotFoundException("ChatBinding", CB_ID));

        mockMvc.perform(post("/api/admin/tenant-config/chat-bindings/{chatBindingId}/activate", CB_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ========== POST /chat-bindings/{chatBindingId}/deactivate endpoint ==========

    @Test
    void deactivateChatBindingReturns200() throws Exception {
        var view = new TenantConfigWriteFacade.ChatBindingCreatedView(
                TENANT_ID, CB_ID, -1001234567890L, "Dev Chat", "MAIN_GROUP", false,
                Instant.parse("2026-04-11T12:00:00Z"));

        when(writeFacade.deactivateChatBinding(eq(TENANT_ID), eq(CB_ID), any())).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/chat-bindings/{chatBindingId}/deactivate", CB_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.chatBindingId").value(CB_ID.toString()))
                .andExpect(jsonPath("$.chatId").value(-1001234567890L))
                .andExpect(jsonPath("$.chatTitle").value("Dev Chat"))
                .andExpect(jsonPath("$.bindingType").value("MAIN_GROUP"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.createdAt").value("2026-04-11T12:00:00Z"));
    }

    @Test
    void deactivateChatBindingMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/chat-bindings/{chatBindingId}/deactivate", CB_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deactivateChatBindingTenantNotFoundReturns404() throws Exception {
        when(writeFacade.deactivateChatBinding(eq(TENANT_ID), eq(CB_ID), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(post("/api/admin/tenant-config/chat-bindings/{chatBindingId}/deactivate", CB_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void deactivateChatBindingNotFoundReturns404() throws Exception {
        when(writeFacade.deactivateChatBinding(eq(TENANT_ID), eq(CB_ID), any()))
                .thenThrow(new ResourceNotFoundException("ChatBinding", CB_ID));

        mockMvc.perform(post("/api/admin/tenant-config/chat-bindings/{chatBindingId}/deactivate", CB_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ========== DELETE /chat-bindings/{chatBindingId} endpoint ==========

    @Test
    void deleteChatBindingReturns204() throws Exception {
        mockMvc.perform(delete("/api/admin/tenant-config/chat-bindings/{chatBindingId}", CB_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void deleteChatBindingMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(delete("/api/admin/tenant-config/chat-bindings/{chatBindingId}", CB_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteChatBindingTenantNotFoundReturns404() throws Exception {
        doThrow(new ResourceNotFoundException("Tenant", TENANT_ID))
                .when(writeFacade).deleteChatBinding(eq(TENANT_ID), eq(CB_ID), any());

        mockMvc.perform(delete("/api/admin/tenant-config/chat-bindings/{chatBindingId}", CB_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void deleteChatBindingNotFoundReturns404() throws Exception {
        doThrow(new ResourceNotFoundException("ChatBinding", CB_ID))
                .when(writeFacade).deleteChatBinding(eq(TENANT_ID), eq(CB_ID), any());

        mockMvc.perform(delete("/api/admin/tenant-config/chat-bindings/{chatBindingId}", CB_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ========== TelegramTopicBinding write endpoints ==========

    private static final UUID TB_ID = UUID.fromString("77777777-7777-7777-7777-777777777771");
    private static final UUID PARENT_CB_ID = UUID.fromString("55555555-5555-5555-5555-555555555551");

    @Test
    void createTopicBindingReturns201WithCreatedResource() throws Exception {
        var view = new TenantConfigWriteFacade.TopicBindingView(
                TENANT_ID, TB_ID, PARENT_CB_ID, 42L, "bugs-topic", "BUG_TRIAGE", true,
                Instant.parse("2026-04-12T10:00:00Z"));

        when(writeFacade.createTopicBinding(eq(TENANT_ID), any(CreateTopicBindingRequest.class), any()))
                .thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/topic-bindings")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chatBindingId":"55555555-5555-5555-5555-555555555551",
                                 "topicId":42,
                                 "topicName":"bugs-topic",
                                 "purpose":"BUG_TRIAGE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.topicBindingId").value(TB_ID.toString()))
                .andExpect(jsonPath("$.chatBindingId").value(PARENT_CB_ID.toString()))
                .andExpect(jsonPath("$.topicId").value(42))
                .andExpect(jsonPath("$.topicName").value("bugs-topic"))
                .andExpect(jsonPath("$.purpose").value("BUG_TRIAGE"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createTopicBindingMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/topic-bindings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chatBindingId":"55555555-5555-5555-5555-555555555551",
                                 "topicId":42,"purpose":"BUG_TRIAGE"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTopicBindingTenantNotFoundReturns404() throws Exception {
        when(writeFacade.createTopicBinding(eq(TENANT_ID), any(CreateTopicBindingRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(post("/api/admin/tenant-config/topic-bindings")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chatBindingId":"55555555-5555-5555-5555-555555555551",
                                 "topicId":42,"purpose":"BUG_TRIAGE"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void createTopicBindingInvalidChatBindingReturns422() throws Exception {
        when(writeFacade.createTopicBinding(eq(TENANT_ID), any(CreateTopicBindingRequest.class), any()))
                .thenThrow(new BusinessRuleException("INVALID_CHAT_BINDING", "chat binding topilmadi"));

        mockMvc.perform(post("/api/admin/tenant-config/topic-bindings")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chatBindingId":"55555555-5555-5555-5555-555555555551",
                                 "topicId":42,"purpose":"BUG_TRIAGE"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CHAT_BINDING"));
    }

    @Test
    void createTopicBindingDuplicateReturns422() throws Exception {
        when(writeFacade.createTopicBinding(eq(TENANT_ID), any(CreateTopicBindingRequest.class), any()))
                .thenThrow(new BusinessRuleException("DUPLICATE_TOPIC_BINDING",
                        "Chat binding ichida topicId=42 uchun binding allaqachon mavjud"));

        mockMvc.perform(post("/api/admin/tenant-config/topic-bindings")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chatBindingId":"55555555-5555-5555-5555-555555555551",
                                 "topicId":42,"purpose":"BUG_TRIAGE"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_TOPIC_BINDING"));
    }

    @Test
    void updateTopicBindingReturns200() throws Exception {
        var view = new TenantConfigWriteFacade.TopicBindingView(
                TENANT_ID, TB_ID, PARENT_CB_ID, 42L, "new-name", "BUG_TRIAGE", true,
                Instant.parse("2026-04-12T10:00:00Z"));

        when(writeFacade.updateTopicBinding(eq(TENANT_ID), eq(TB_ID), any(UpdateTopicBindingRequest.class), any()))
                .thenReturn(view);

        mockMvc.perform(patch("/api/admin/tenant-config/topic-bindings/{topicBindingId}", TB_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topicName":"new-name"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topicName").value("new-name"));
    }

    @Test
    void updateTopicBindingMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(patch("/api/admin/tenant-config/topic-bindings/{topicBindingId}", TB_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topicName":"x"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTopicBindingNotFoundReturns404() throws Exception {
        when(writeFacade.updateTopicBinding(eq(TENANT_ID), eq(TB_ID), any(UpdateTopicBindingRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("TopicBinding", TB_ID));

        mockMvc.perform(patch("/api/admin/tenant-config/topic-bindings/{topicBindingId}", TB_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topicName":"x"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void activateTopicBindingReturns200() throws Exception {
        var view = new TenantConfigWriteFacade.TopicBindingView(
                TENANT_ID, TB_ID, PARENT_CB_ID, 42L, "bugs-topic", "BUG_TRIAGE", true,
                Instant.parse("2026-04-12T10:00:00Z"));

        when(writeFacade.activateTopicBinding(eq(TENANT_ID), eq(TB_ID), any())).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/topic-bindings/{topicBindingId}/activate", TB_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topicBindingId").value(TB_ID.toString()))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void activateTopicBindingMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/topic-bindings/{topicBindingId}/activate", TB_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void activateTopicBindingNotFoundReturns404() throws Exception {
        when(writeFacade.activateTopicBinding(eq(TENANT_ID), eq(TB_ID), any()))
                .thenThrow(new ResourceNotFoundException("TopicBinding", TB_ID));

        mockMvc.perform(post("/api/admin/tenant-config/topic-bindings/{topicBindingId}/activate", TB_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void deactivateTopicBindingReturns200() throws Exception {
        var view = new TenantConfigWriteFacade.TopicBindingView(
                TENANT_ID, TB_ID, PARENT_CB_ID, 42L, "bugs-topic", "BUG_TRIAGE", false,
                Instant.parse("2026-04-12T10:00:00Z"));

        when(writeFacade.deactivateTopicBinding(eq(TENANT_ID), eq(TB_ID), any())).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/topic-bindings/{topicBindingId}/deactivate", TB_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deactivateTopicBindingMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/topic-bindings/{topicBindingId}/deactivate", TB_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deactivateTopicBindingTenantNotFoundReturns404() throws Exception {
        when(writeFacade.deactivateTopicBinding(eq(TENANT_ID), eq(TB_ID), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(post("/api/admin/tenant-config/topic-bindings/{topicBindingId}/deactivate", TB_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void deleteTopicBindingReturns204() throws Exception {
        mockMvc.perform(delete("/api/admin/tenant-config/topic-bindings/{topicBindingId}", TB_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void deleteTopicBindingMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(delete("/api/admin/tenant-config/topic-bindings/{topicBindingId}", TB_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteTopicBindingNotFoundReturns404() throws Exception {
        doThrow(new ResourceNotFoundException("TopicBinding", TB_ID))
                .when(writeFacade).deleteTopicBinding(eq(TENANT_ID), eq(TB_ID), any());

        mockMvc.perform(delete("/api/admin/tenant-config/topic-bindings/{topicBindingId}", TB_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ========== Membership lifecycle endpoints ==========

    private static final UUID MEMBERSHIP_ID = UUID.fromString("88888888-8888-8888-8888-888888888881");
    private static final UUID USER_ID = UUID.fromString("99999999-9999-9999-9999-999999999991");

    @Test
    void createMembershipReturns201WithCreatedResource() throws Exception {
        var view = new TenantConfigWriteFacade.MembershipStatusView(
                TENANT_ID, MEMBERSHIP_ID, USER_ID, "ACTIVE",
                Instant.parse("2026-04-12T10:00:00Z"));

        when(writeFacade.createMembership(eq(TENANT_ID), any(CreateMembershipRequest.class), any()))
                .thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/memberships")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"99999999-9999-9999-9999-999999999991"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.membershipId").value(MEMBERSHIP_ID.toString()))
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createMembershipMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/memberships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"99999999-9999-9999-9999-999999999991"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createMembershipEmptyBodyReturns400() throws Exception {
        when(writeFacade.createMembership(eq(TENANT_ID), any(CreateMembershipRequest.class), any()))
                .thenThrow(new IllegalArgumentException("userId null bo'lishi mumkin emas"));

        mockMvc.perform(post("/api/admin/tenant-config/memberships")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void createMembershipTenantNotFoundReturns404() throws Exception {
        when(writeFacade.createMembership(eq(TENANT_ID), any(CreateMembershipRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(post("/api/admin/tenant-config/memberships")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"99999999-9999-9999-9999-999999999991"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void createMembershipUserNotFoundReturns404() throws Exception {
        when(writeFacade.createMembership(eq(TENANT_ID), any(CreateMembershipRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("User", USER_ID));

        mockMvc.perform(post("/api/admin/tenant-config/memberships")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"99999999-9999-9999-9999-999999999991"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void createMembershipDuplicateReturns422() throws Exception {
        when(writeFacade.createMembership(eq(TENANT_ID), any(CreateMembershipRequest.class), any()))
                .thenThrow(new BusinessRuleException("DUPLICATE_MEMBERSHIP",
                        "Tenant ichida membership allaqachon mavjud"));

        mockMvc.perform(post("/api/admin/tenant-config/memberships")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"99999999-9999-9999-9999-999999999991"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_MEMBERSHIP"));
    }

    @Test
    void activateMembershipReturns200() throws Exception {
        var view = new TenantConfigWriteFacade.MembershipStatusView(
                TENANT_ID, MEMBERSHIP_ID, USER_ID, "ACTIVE",
                Instant.parse("2026-04-12T10:00:00Z"));

        when(writeFacade.activateMembership(eq(TENANT_ID), eq(MEMBERSHIP_ID), any())).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/memberships/{membershipId}/activate", MEMBERSHIP_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.membershipId").value(MEMBERSHIP_ID.toString()))
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void activateMembershipMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/memberships/{membershipId}/activate", MEMBERSHIP_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void activateMembershipNotFoundReturns404() throws Exception {
        when(writeFacade.activateMembership(eq(TENANT_ID), eq(MEMBERSHIP_ID), any()))
                .thenThrow(new ResourceNotFoundException("Membership", MEMBERSHIP_ID));

        mockMvc.perform(post("/api/admin/tenant-config/memberships/{membershipId}/activate", MEMBERSHIP_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void activateMembershipTenantNotFoundReturns404() throws Exception {
        when(writeFacade.activateMembership(eq(TENANT_ID), eq(MEMBERSHIP_ID), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(post("/api/admin/tenant-config/memberships/{membershipId}/activate", MEMBERSHIP_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void activateMembershipRemovedReturns422() throws Exception {
        when(writeFacade.activateMembership(eq(TENANT_ID), eq(MEMBERSHIP_ID), any()))
                .thenThrow(new BusinessRuleException("INVALID_STATUS_TRANSITION",
                        "REMOVED holatdagi membership aktivlashtirilmaydi"));

        mockMvc.perform(post("/api/admin/tenant-config/memberships/{membershipId}/activate", MEMBERSHIP_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void suspendMembershipReturns200() throws Exception {
        var view = new TenantConfigWriteFacade.MembershipStatusView(
                TENANT_ID, MEMBERSHIP_ID, USER_ID, "SUSPENDED",
                Instant.parse("2026-04-12T10:00:00Z"));

        when(writeFacade.suspendMembership(eq(TENANT_ID), eq(MEMBERSHIP_ID), any())).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/memberships/{membershipId}/suspend", MEMBERSHIP_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void suspendMembershipMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/memberships/{membershipId}/suspend", MEMBERSHIP_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void suspendMembershipNotFoundReturns404() throws Exception {
        when(writeFacade.suspendMembership(eq(TENANT_ID), eq(MEMBERSHIP_ID), any()))
                .thenThrow(new ResourceNotFoundException("Membership", MEMBERSHIP_ID));

        mockMvc.perform(post("/api/admin/tenant-config/memberships/{membershipId}/suspend", MEMBERSHIP_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void suspendMembershipTenantNotFoundReturns404() throws Exception {
        when(writeFacade.suspendMembership(eq(TENANT_ID), eq(MEMBERSHIP_ID), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(post("/api/admin/tenant-config/memberships/{membershipId}/suspend", MEMBERSHIP_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void suspendMembershipRemovedReturns422() throws Exception {
        when(writeFacade.suspendMembership(eq(TENANT_ID), eq(MEMBERSHIP_ID), any()))
                .thenThrow(new BusinessRuleException("INVALID_STATUS_TRANSITION",
                        "REMOVED holatdagi membership to'xtatilmaydi"));

        mockMvc.perform(post("/api/admin/tenant-config/memberships/{membershipId}/suspend", MEMBERSHIP_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    // ========== POST /memberships/{membershipId}/remove endpoint ==========

    @Test
    void removeMembershipReturns200() throws Exception {
        var view = new TenantConfigWriteFacade.MembershipStatusView(
                TENANT_ID, MEMBERSHIP_ID, USER_ID, "REMOVED",
                Instant.parse("2026-04-12T10:00:00Z"));

        when(writeFacade.removeMembership(eq(TENANT_ID), eq(MEMBERSHIP_ID), any())).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/memberships/{membershipId}/remove", MEMBERSHIP_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.membershipId").value(MEMBERSHIP_ID.toString()))
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.status").value("REMOVED"));
    }

    @Test
    void removeMembershipMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/memberships/{membershipId}/remove", MEMBERSHIP_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removeMembershipTenantNotFoundReturns404() throws Exception {
        when(writeFacade.removeMembership(eq(TENANT_ID), eq(MEMBERSHIP_ID), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(post("/api/admin/tenant-config/memberships/{membershipId}/remove", MEMBERSHIP_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void removeMembershipNotFoundReturns404() throws Exception {
        when(writeFacade.removeMembership(eq(TENANT_ID), eq(MEMBERSHIP_ID), any()))
                .thenThrow(new ResourceNotFoundException("Membership", MEMBERSHIP_ID));

        mockMvc.perform(post("/api/admin/tenant-config/memberships/{membershipId}/remove", MEMBERSHIP_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ========== Membership role binding endpoints ==========

    private static final UUID ROLE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private static final UUID BINDING_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1");
    private static final UUID PERMISSION_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc1");

    @Test
    void assignRoleToMembershipReturns201() throws Exception {
        var view = new TenantConfigWriteFacade.MembershipRoleBindingView(
                TENANT_ID, MEMBERSHIP_ID, BINDING_ID, ROLE_ID, "BUG_TRIAGER",
                Instant.parse("2026-04-12T12:00:00Z"));

        when(writeFacade.assignRoleToMembership(eq(TENANT_ID), eq(MEMBERSHIP_ID),
                any(CreateMembershipRoleBindingRequest.class), any())).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/memberships/{membershipId}/roles", MEMBERSHIP_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.membershipId").value(MEMBERSHIP_ID.toString()))
                .andExpect(jsonPath("$.bindingId").value(BINDING_ID.toString()))
                .andExpect(jsonPath("$.roleId").value(ROLE_ID.toString()))
                .andExpect(jsonPath("$.roleCode").value("BUG_TRIAGER"));
    }

    @Test
    void assignRoleToMembershipMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/memberships/{membershipId}/roles", MEMBERSHIP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assignRoleToMembershipTenantNotFoundReturns404() throws Exception {
        when(writeFacade.assignRoleToMembership(eq(TENANT_ID), eq(MEMBERSHIP_ID),
                any(CreateMembershipRoleBindingRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(post("/api/admin/tenant-config/memberships/{membershipId}/roles", MEMBERSHIP_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void assignRoleToMembershipMembershipNotFoundReturns404() throws Exception {
        when(writeFacade.assignRoleToMembership(eq(TENANT_ID), eq(MEMBERSHIP_ID),
                any(CreateMembershipRoleBindingRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("Membership", MEMBERSHIP_ID));

        mockMvc.perform(post("/api/admin/tenant-config/memberships/{membershipId}/roles", MEMBERSHIP_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void assignRoleToMembershipRoleNotFoundReturns404() throws Exception {
        when(writeFacade.assignRoleToMembership(eq(TENANT_ID), eq(MEMBERSHIP_ID),
                any(CreateMembershipRoleBindingRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("Role", ROLE_ID));

        mockMvc.perform(post("/api/admin/tenant-config/memberships/{membershipId}/roles", MEMBERSHIP_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void assignRoleToMembershipDuplicateReturns422() throws Exception {
        when(writeFacade.assignRoleToMembership(eq(TENANT_ID), eq(MEMBERSHIP_ID),
                any(CreateMembershipRoleBindingRequest.class), any()))
                .thenThrow(new BusinessRuleException("DUPLICATE_MEMBERSHIP_ROLE",
                        "Membership uchun rol allaqachon tayinlangan"));

        mockMvc.perform(post("/api/admin/tenant-config/memberships/{membershipId}/roles", MEMBERSHIP_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_MEMBERSHIP_ROLE"));
    }

    @Test
    void assignRoleToMembershipRemovedReturns422() throws Exception {
        when(writeFacade.assignRoleToMembership(eq(TENANT_ID), eq(MEMBERSHIP_ID),
                any(CreateMembershipRoleBindingRequest.class), any()))
                .thenThrow(new BusinessRuleException("INVALID_MEMBERSHIP_STATUS",
                        "REMOVED holatdagi membershipga yangi rol tayinlab bo'lmaydi"));

        mockMvc.perform(post("/api/admin/tenant-config/memberships/{membershipId}/roles", MEMBERSHIP_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_MEMBERSHIP_STATUS"));
    }

    @Test
    void unassignRoleFromMembershipReturns204() throws Exception {
        mockMvc.perform(delete("/api/admin/tenant-config/memberships/{membershipId}/roles/{roleId}",
                        MEMBERSHIP_ID, ROLE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void unassignRoleFromMembershipMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(delete("/api/admin/tenant-config/memberships/{membershipId}/roles/{roleId}",
                        MEMBERSHIP_ID, ROLE_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unassignRoleFromMembershipTenantNotFoundReturns404() throws Exception {
        doThrow(new ResourceNotFoundException("Tenant", TENANT_ID))
                .when(writeFacade).unassignRoleFromMembership(eq(TENANT_ID), eq(MEMBERSHIP_ID), eq(ROLE_ID), any());

        mockMvc.perform(delete("/api/admin/tenant-config/memberships/{membershipId}/roles/{roleId}",
                        MEMBERSHIP_ID, ROLE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unassignRoleFromMembershipMembershipNotFoundReturns404() throws Exception {
        doThrow(new ResourceNotFoundException("Membership", MEMBERSHIP_ID))
                .when(writeFacade).unassignRoleFromMembership(eq(TENANT_ID), eq(MEMBERSHIP_ID), eq(ROLE_ID), any());

        mockMvc.perform(delete("/api/admin/tenant-config/memberships/{membershipId}/roles/{roleId}",
                        MEMBERSHIP_ID, ROLE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unassignRoleFromMembershipBindingNotFoundReturns404() throws Exception {
        doThrow(new ResourceNotFoundException("MembershipRoleBinding", "x"))
                .when(writeFacade).unassignRoleFromMembership(eq(TENANT_ID), eq(MEMBERSHIP_ID), eq(ROLE_ID), any());

        mockMvc.perform(delete("/api/admin/tenant-config/memberships/{membershipId}/roles/{roleId}",
                        MEMBERSHIP_ID, ROLE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
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
                any(CreateRoutingRuleRequest.class), any())).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/routing-rules")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
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
                any(CreateRoutingRuleRequest.class), any())).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/routing-rules")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
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
                any(CreateRoutingRuleRequest.class), any()))
                .thenThrow(new IllegalArgumentException("name null yoki bo'sh bo'lishi mumkin emas"));

        mockMvc.perform(post("/api/admin/tenant-config/routing-rules")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
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
                any(CreateRoutingRuleRequest.class), any()))
                .thenThrow(new IllegalArgumentException(
                        "workItemType faqat BUG, INCIDENT, TASK bo'lishi mumkin: FEATURE"));

        mockMvc.perform(post("/api/admin/tenant-config/routing-rules")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
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
                any(CreateRoutingRuleRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(post("/api/admin/tenant-config/routing-rules")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
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
                any(CreateRoutingRuleRequest.class), any()))
                .thenThrow(new BusinessRuleException("INVALID_TOPIC_BINDING",
                        "Topic binding topilmadi yoki shu tenantga tegishli emas"));

        mockMvc.perform(post("/api/admin/tenant-config/routing-rules")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
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
                any(UpdateRoutingRuleRequest.class), any())).thenReturn(view);

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
                any(UpdateRoutingRuleRequest.class), any())).thenReturn(view);

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
                any(UpdateRoutingRuleRequest.class), any())).thenReturn(view);

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
                any(UpdateRoutingRuleRequest.class), any())).thenReturn(view);

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
                any(UpdateRoutingRuleRequest.class), any())).thenReturn(view);

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
                any(UpdateRoutingRuleRequest.class), any()))
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
                any(UpdateRoutingRuleRequest.class), any()))
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
                any(UpdateRoutingRuleRequest.class), any()))
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
                any(UpdateRoutingRuleRequest.class), any()))
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
                any(UpdateRoutingRuleRequest.class), any()))
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
                any(UpdateRoutingRuleRequest.class), any()))
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

        when(writeFacade.activateRoutingRule(eq(TENANT_ID), eq(RULE_ID), any())).thenReturn(view);

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
        when(writeFacade.activateRoutingRule(eq(TENANT_ID), eq(RULE_ID), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(post("/api/admin/tenant-config/routing-rules/{ruleId}/activate", RULE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void activateRoutingRuleNotFoundReturns404() throws Exception {
        when(writeFacade.activateRoutingRule(eq(TENANT_ID), eq(RULE_ID), any()))
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

        when(writeFacade.deactivateRoutingRule(eq(TENANT_ID), eq(RULE_ID), any())).thenReturn(view);

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
        when(writeFacade.deactivateRoutingRule(eq(TENANT_ID), eq(RULE_ID), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(post("/api/admin/tenant-config/routing-rules/{ruleId}/deactivate", RULE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void deactivateRoutingRuleNotFoundReturns404() throws Exception {
        when(writeFacade.deactivateRoutingRule(eq(TENANT_ID), eq(RULE_ID), any()))
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
                .when(writeFacade).deleteRoutingRule(eq(TENANT_ID), eq(RULE_ID), any());

        mockMvc.perform(delete("/api/admin/tenant-config/routing-rules/{ruleId}", RULE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void deleteRoutingRuleNotFoundReturns404() throws Exception {
        doThrow(new ResourceNotFoundException("RoutingRule", RULE_ID))
                .when(writeFacade).deleteRoutingRule(eq(TENANT_ID), eq(RULE_ID), any());

        mockMvc.perform(delete("/api/admin/tenant-config/routing-rules/{ruleId}", RULE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ========== POST /roles — global role create endpoint ==========

    @Test
    void createRoleReturns201() throws Exception {
        var view = new TenantConfigWriteFacade.RoleCatalogView(
                ROLE_ID, "BUG_REVIEWER", "Bug Reviewer", "Reviews bugs",
                false, true, Instant.parse("2026-04-12T12:00:00Z"));

        when(writeFacade.createRole(any(), any(CreateRoleRequest.class), any())).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/roles")
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"BUG_REVIEWER","name":"Bug Reviewer","description":"Reviews bugs"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roleId").value(ROLE_ID.toString()))
                .andExpect(jsonPath("$.code").value("BUG_REVIEWER"))
                .andExpect(jsonPath("$.name").value("Bug Reviewer"))
                .andExpect(jsonPath("$.description").value("Reviews bugs"))
                .andExpect(jsonPath("$.systemRole").value(false))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createRoleDuplicateReturns422() throws Exception {
        when(writeFacade.createRole(any(), any(CreateRoleRequest.class), any()))
                .thenThrow(new BusinessRuleException("DUPLICATE_ROLE_CODE",
                        "ADMIN kodli rol allaqachon mavjud"));

        mockMvc.perform(post("/api/admin/tenant-config/roles")
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ADMIN","name":"Admin"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_ROLE_CODE"));
    }

    @Test
    void createRoleEmptyBodyReturns400() throws Exception {
        when(writeFacade.createRole(any(), any(CreateRoleRequest.class), any()))
                .thenThrow(new IllegalArgumentException("code null yoki bo'sh"));

        mockMvc.perform(post("/api/admin/tenant-config/roles")
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":null,"name":null}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ========== PATCH /roles/{roleId} — global role update endpoint ==========

    @Test
    void updateRoleReturns200() throws Exception {
        var view = new TenantConfigWriteFacade.RoleCatalogView(
                ROLE_ID, "BUG_REVIEWER", "Updated Name", null,
                false, true, Instant.parse("2026-04-12T12:00:00Z"));

        when(writeFacade.updateRole(any(), eq(ROLE_ID), any(UpdateRoleRequest.class), any())).thenReturn(view);

        mockMvc.perform(patch("/api/admin/tenant-config/roles/{roleId}", ROLE_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated Name"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleId").value(ROLE_ID.toString()))
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    @Test
    void updateRoleNotFoundReturns404() throws Exception {
        when(writeFacade.updateRole(any(), eq(ROLE_ID), any(UpdateRoleRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("Role", ROLE_ID));

        mockMvc.perform(patch("/api/admin/tenant-config/roles/{roleId}", ROLE_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void updateRoleEmptyPatchReturns400() throws Exception {
        when(writeFacade.updateRole(any(), eq(ROLE_ID), any(UpdateRoleRequest.class), any()))
                .thenThrow(new IllegalArgumentException("Kamida bitta field berilishi kerak"));

        mockMvc.perform(patch("/api/admin/tenant-config/roles/{roleId}", ROLE_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRoleSystemRoleReturns422() throws Exception {
        when(writeFacade.updateRole(any(), eq(ROLE_ID), any(UpdateRoleRequest.class), any()))
                .thenThrow(new BusinessRuleException("SYSTEM_ROLE_UPDATE_FORBIDDEN",
                        "Tizim roli metadata'si o'zgartirilmaydi"));

        mockMvc.perform(patch("/api/admin/tenant-config/roles/{roleId}", ROLE_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"New Name"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("SYSTEM_ROLE_UPDATE_FORBIDDEN"));
    }

    // ========== POST /roles/{roleId}/activate — global role activate endpoint ==========

    @Test
    void activateRoleReturns200() throws Exception {
        var view = new TenantConfigWriteFacade.RoleCatalogView(
                ROLE_ID, "BUG_REVIEWER", "Bug Reviewer", null,
                false, true, Instant.parse("2026-04-12T12:00:00Z"));

        when(writeFacade.activateRole(any(), eq(ROLE_ID), any())).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/roles/{roleId}/activate", ROLE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleId").value(ROLE_ID.toString()))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void activateRoleNotFoundReturns404() throws Exception {
        when(writeFacade.activateRole(any(), eq(ROLE_ID), any()))
                .thenThrow(new ResourceNotFoundException("Role", ROLE_ID));

        mockMvc.perform(post("/api/admin/tenant-config/roles/{roleId}/activate", ROLE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ========== POST /roles/{roleId}/deactivate — global role deactivate endpoint ==========

    @Test
    void deactivateRoleReturns200() throws Exception {
        var view = new TenantConfigWriteFacade.RoleCatalogView(
                ROLE_ID, "BUG_REVIEWER", "Bug Reviewer", null,
                false, false, Instant.parse("2026-04-12T12:00:00Z"));

        when(writeFacade.deactivateRole(any(), eq(ROLE_ID), any())).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/roles/{roleId}/deactivate", ROLE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleId").value(ROLE_ID.toString()))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deactivateRoleNotFoundReturns404() throws Exception {
        when(writeFacade.deactivateRole(any(), eq(ROLE_ID), any()))
                .thenThrow(new ResourceNotFoundException("Role", ROLE_ID));

        mockMvc.perform(post("/api/admin/tenant-config/roles/{roleId}/deactivate", ROLE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void deactivateRoleSystemRoleReturns422() throws Exception {
        when(writeFacade.deactivateRole(any(), eq(ROLE_ID), any()))
                .thenThrow(new BusinessRuleException("SYSTEM_ROLE_DEACTIVATE_FORBIDDEN",
                        "Tizim roli deaktivlashtirilmaydi"));

        mockMvc.perform(post("/api/admin/tenant-config/roles/{roleId}/deactivate", ROLE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("SYSTEM_ROLE_DEACTIVATE_FORBIDDEN"));
    }

    // ========== DELETE /roles/{roleId} — global role delete endpoint ==========

    @Test
    void deleteRoleReturns204() throws Exception {
        mockMvc.perform(delete("/api/admin/tenant-config/roles/{roleId}", ROLE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void deleteRoleNotFoundReturns404() throws Exception {
        doThrow(new ResourceNotFoundException("Role", ROLE_ID))
                .when(writeFacade).deleteRole(any(), eq(ROLE_ID), any());

        mockMvc.perform(delete("/api/admin/tenant-config/roles/{roleId}", ROLE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void deleteRoleSystemRoleReturns422() throws Exception {
        doThrow(new BusinessRuleException("SYSTEM_ROLE_DELETE_FORBIDDEN",
                "Tizim roli o'chirilmaydi"))
                .when(writeFacade).deleteRole(any(), eq(ROLE_ID), any());

        mockMvc.perform(delete("/api/admin/tenant-config/roles/{roleId}", ROLE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("SYSTEM_ROLE_DELETE_FORBIDDEN"));
    }

    @Test
    void deleteRoleInUseReturns422() throws Exception {
        doThrow(new BusinessRuleException("ROLE_IN_USE",
                "Rol hozirda membership'larga tayinlangan"))
                .when(writeFacade).deleteRole(any(), eq(ROLE_ID), any());

        mockMvc.perform(delete("/api/admin/tenant-config/roles/{roleId}", ROLE_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ROLE_IN_USE"));
    }

    // ========== Authorization 403 tests ==========

    @Test
    void readEndpointReturns403WhenAccessDenied() throws Exception {
        when(detailsFacade.getDetails(eq(TENANT_ID), any()))
                .thenThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"));

        mockMvc.perform(get("/api/admin/tenant-config/details")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void writeEndpointReturns403WhenAccessDenied() throws Exception {
        when(writeFacade.createWorkflowDefinition(eq(TENANT_ID),
                any(CreateWorkflowDefinitionRequest.class), any()))
                .thenThrow(new AccessDeniedException("TENANT_CONFIG_WRITE ruxsati talab qilinadi"));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bug Flow","workItemType":"BUG"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void deleteEndpointReturns403WhenAccessDenied() throws Exception {
        doThrow(new AccessDeniedException("TENANT_CONFIG_WRITE ruxsati talab qilinadi"))
                .when(writeFacade).deleteWorkflowDefinition(eq(TENANT_ID), eq(DEF_ID), any());

        mockMvc.perform(delete("/api/admin/tenant-config/workflow-definitions/{definitionId}", DEF_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void missingActorHeaderStillReachesEndpoint() throws Exception {
        when(detailsFacade.getDetails(eq(TENANT_ID), any()))
                .thenThrow(new AccessDeniedException("Actor identifikatsiyasi talab qilinadi"));

        mockMvc.perform(get("/api/admin/tenant-config/details")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void existingNotFoundSemanticPreservedWith403() throws Exception {
        when(detailsFacade.getDetails(eq(TENANT_ID), any()))
                .thenThrow(new ResourceNotFoundException("Tenant", TENANT_ID));

        mockMvc.perform(get("/api/admin/tenant-config/details")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void existingBadRequestSemanticPreservedWith403() throws Exception {
        when(detailsFacade.getDetails(eq(TENANT_ID), any()))
                .thenThrow(new IllegalArgumentException("tenantId null bo'lishi mumkin emas"));

        mockMvc.perform(get("/api/admin/tenant-config/details")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    // ========== POST /roles/{roleId}/permissions — role-permission assignment endpoint ==========

    @Test
    void assignPermissionToRoleReturns201() throws Exception {
        var view = new TenantConfigWriteFacade.RolePermissionView(
                BINDING_ID, ROLE_ID, "BUG_TRIAGER", PERMISSION_ID, "TENANT_CONFIG_WRITE",
                Instant.parse("2026-04-17T12:00:00Z"));

        when(writeFacade.assignPermissionToRole(eq(TENANT_ID), eq(ROLE_ID),
                any(CreateRolePermissionRequest.class), any())).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/roles/{roleId}/permissions", ROLE_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissionId":"%s"}
                                """.formatted(PERMISSION_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bindingId").value(BINDING_ID.toString()))
                .andExpect(jsonPath("$.roleId").value(ROLE_ID.toString()))
                .andExpect(jsonPath("$.roleCode").value("BUG_TRIAGER"))
                .andExpect(jsonPath("$.permissionId").value(PERMISSION_ID.toString()))
                .andExpect(jsonPath("$.permissionCode").value("TENANT_CONFIG_WRITE"));
    }

    @Test
    void assignPermissionToRoleMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/roles/{roleId}/permissions", ROLE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissionId":"%s"}
                                """.formatted(PERMISSION_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assignPermissionToRoleRoleNotFoundReturns404() throws Exception {
        when(writeFacade.assignPermissionToRole(eq(TENANT_ID), eq(ROLE_ID),
                any(CreateRolePermissionRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("Role", ROLE_ID));

        mockMvc.perform(post("/api/admin/tenant-config/roles/{roleId}/permissions", ROLE_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissionId":"%s"}
                                """.formatted(PERMISSION_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void assignPermissionToRolePermissionNotFoundReturns404() throws Exception {
        when(writeFacade.assignPermissionToRole(eq(TENANT_ID), eq(ROLE_ID),
                any(CreateRolePermissionRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("Permission", PERMISSION_ID));

        mockMvc.perform(post("/api/admin/tenant-config/roles/{roleId}/permissions", ROLE_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissionId":"%s"}
                                """.formatted(PERMISSION_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void assignPermissionToRoleDuplicateReturns422() throws Exception {
        when(writeFacade.assignPermissionToRole(eq(TENANT_ID), eq(ROLE_ID),
                any(CreateRolePermissionRequest.class), any()))
                .thenThrow(new BusinessRuleException("DUPLICATE_ROLE_PERMISSION",
                        "Rol uchun ruxsat allaqachon tayinlangan"));

        mockMvc.perform(post("/api/admin/tenant-config/roles/{roleId}/permissions", ROLE_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissionId":"%s"}
                                """.formatted(PERMISSION_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_ROLE_PERMISSION"));
    }

    @Test
    void assignPermissionToRoleAccessDeniedReturns403() throws Exception {
        when(writeFacade.assignPermissionToRole(eq(TENANT_ID), eq(ROLE_ID),
                any(CreateRolePermissionRequest.class), any()))
                .thenThrow(new AccessDeniedException("TENANT_CONFIG_WRITE ruxsati talab qilinadi"));

        mockMvc.perform(post("/api/admin/tenant-config/roles/{roleId}/permissions", ROLE_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissionId":"%s"}
                                """.formatted(PERMISSION_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ========== DELETE /roles/{roleId}/permissions/{permissionId} — role-permission unassignment endpoint ==========

    @Test
    void unassignPermissionFromRoleReturns204() throws Exception {
        mockMvc.perform(delete("/api/admin/tenant-config/roles/{roleId}/permissions/{permissionId}",
                        ROLE_ID, PERMISSION_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void unassignPermissionFromRoleMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(delete("/api/admin/tenant-config/roles/{roleId}/permissions/{permissionId}",
                        ROLE_ID, PERMISSION_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unassignPermissionFromRoleRoleNotFoundReturns404() throws Exception {
        doThrow(new ResourceNotFoundException("Role", ROLE_ID))
                .when(writeFacade).unassignPermissionFromRole(eq(TENANT_ID), eq(ROLE_ID), eq(PERMISSION_ID), any());

        mockMvc.perform(delete("/api/admin/tenant-config/roles/{roleId}/permissions/{permissionId}",
                        ROLE_ID, PERMISSION_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unassignPermissionFromRolePermissionNotFoundReturns404() throws Exception {
        doThrow(new ResourceNotFoundException("Permission", PERMISSION_ID))
                .when(writeFacade).unassignPermissionFromRole(eq(TENANT_ID), eq(ROLE_ID), eq(PERMISSION_ID), any());

        mockMvc.perform(delete("/api/admin/tenant-config/roles/{roleId}/permissions/{permissionId}",
                        ROLE_ID, PERMISSION_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unassignPermissionFromRoleBindingNotFoundReturns404() throws Exception {
        doThrow(new ResourceNotFoundException("RolePermission", "x"))
                .when(writeFacade).unassignPermissionFromRole(eq(TENANT_ID), eq(ROLE_ID), eq(PERMISSION_ID), any());

        mockMvc.perform(delete("/api/admin/tenant-config/roles/{roleId}/permissions/{permissionId}",
                        ROLE_ID, PERMISSION_ID)
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unassignPermissionFromRoleAccessDeniedReturns403() throws Exception {
        doThrow(new AccessDeniedException("TENANT_CONFIG_WRITE ruxsati talab qilinadi"))
                .when(writeFacade).unassignPermissionFromRole(eq(TENANT_ID), eq(ROLE_ID), eq(PERMISSION_ID), any());

        mockMvc.perform(delete("/api/admin/tenant-config/roles/{roleId}/permissions/{permissionId}",
                        ROLE_ID, PERMISSION_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ========== POST /workflow-definitions/{definitionId}/statuses endpoint ==========

    private static final UUID WS_DEFINITION_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333331");
    private static final UUID WS_STATUS_ID =
            UUID.fromString("44444444-4444-4444-4444-444444444441");

    @Test
    void createWorkflowStatusReturns201WithCreatedResource() throws Exception {
        var view = new TenantConfigWriteFacade.WorkflowStatusCreatedView(
                TENANT_ID,
                WS_DEFINITION_ID,
                WS_STATUS_ID,
                "BUGS",
                0,
                true,
                false,
                Instant.parse("2026-04-29T10:00:00Z"));

        when(writeFacade.createWorkflowStatus(eq(TENANT_ID), eq(WS_DEFINITION_ID),
                any(CreateWorkflowStatusRequest.class), any())).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/statuses",
                        WS_DEFINITION_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"BUGS","statusOrder":0,"initial":true,"terminal":false}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.workflowDefinitionId").value(WS_DEFINITION_ID.toString()))
                .andExpect(jsonPath("$.statusId").value(WS_STATUS_ID.toString()))
                .andExpect(jsonPath("$.name").value("BUGS"))
                .andExpect(jsonPath("$.statusOrder").value(0))
                .andExpect(jsonPath("$.initial").value(true))
                .andExpect(jsonPath("$.terminal").value(false))
                .andExpect(jsonPath("$.createdAt").value("2026-04-29T10:00:00Z"));
    }

    @Test
    void createWorkflowStatusMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/statuses",
                        WS_DEFINITION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"BUGS","statusOrder":0,"initial":true,"terminal":false}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWorkflowStatusBlankNameReturns400() throws Exception {
        when(writeFacade.createWorkflowStatus(eq(TENANT_ID), eq(WS_DEFINITION_ID),
                any(CreateWorkflowStatusRequest.class), any()))
                .thenThrow(new IllegalArgumentException("name null yoki bo'sh bo'lishi mumkin emas"));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/statuses",
                        WS_DEFINITION_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","statusOrder":0,"initial":false,"terminal":false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void createWorkflowStatusDefinitionNotFoundReturns404() throws Exception {
        when(writeFacade.createWorkflowStatus(eq(TENANT_ID), eq(WS_DEFINITION_ID),
                any(CreateWorkflowStatusRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("WorkflowDefinition", WS_DEFINITION_ID));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/statuses",
                        WS_DEFINITION_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"BUGS","statusOrder":0,"initial":true,"terminal":false}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void createWorkflowStatusDuplicateNameReturns422() throws Exception {
        when(writeFacade.createWorkflowStatus(eq(TENANT_ID), eq(WS_DEFINITION_ID),
                any(CreateWorkflowStatusRequest.class), any()))
                .thenThrow(new BusinessRuleException("DUPLICATE_WORKFLOW_STATUS_NAME",
                        "Workflow definition ichida 'BUGS' nomli status allaqachon mavjud"));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/statuses",
                        WS_DEFINITION_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"BUGS","statusOrder":0,"initial":false,"terminal":false}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_WORKFLOW_STATUS_NAME"));
    }

    @Test
    void createWorkflowStatusDuplicateInitialReturns422() throws Exception {
        when(writeFacade.createWorkflowStatus(eq(TENANT_ID), eq(WS_DEFINITION_ID),
                any(CreateWorkflowStatusRequest.class), any()))
                .thenThrow(new BusinessRuleException("DUPLICATE_INITIAL_STATUS",
                        "Workflow definition uchun boshlang'ich status allaqachon belgilangan "
                                + "(definitionId=" + WS_DEFINITION_ID + ")"));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/statuses",
                        WS_DEFINITION_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"PROCESSING","statusOrder":1,"initial":true,"terminal":false}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_INITIAL_STATUS"));
    }

    @Test
    void createWorkflowStatusNameTooLongReturns400() throws Exception {
        when(writeFacade.createWorkflowStatus(eq(TENANT_ID), eq(WS_DEFINITION_ID),
                any(CreateWorkflowStatusRequest.class), any()))
                .thenThrow(new IllegalArgumentException("name 100 belgidan oshmasligi kerak: 101"));

        String tooLong = "X".repeat(101);
        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/statuses",
                        WS_DEFINITION_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + tooLong + "\",\"statusOrder\":0,"
                                + "\"initial\":true,\"terminal\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void createWorkflowStatusForbiddenWhenAuthorizationFails() throws Exception {
        doThrow(new AccessDeniedException("Bu operatsiya uchun TENANT_CONFIG_WRITE ruxsati talab qilinadi"))
                .when(writeFacade).createWorkflowStatus(eq(TENANT_ID), eq(WS_DEFINITION_ID),
                        any(CreateWorkflowStatusRequest.class), any());

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/statuses",
                        WS_DEFINITION_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"BUGS","statusOrder":0,"initial":true,"terminal":false}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ========== POST /workflow-definitions/{definitionId}/transition-rules endpoint ==========

    private static final UUID TR_DEFINITION_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333331");
    private static final UUID TR_RULE_ID =
            UUID.fromString("66666666-6666-6666-6666-666666666661");
    private static final UUID TR_FROM_STATUS_ID =
            UUID.fromString("55555555-5555-5555-5555-555555555551");
    private static final UUID TR_TO_STATUS_ID =
            UUID.fromString("55555555-5555-5555-5555-555555555552");

    @Test
    void createWorkflowTransitionRuleReturns201WithCreatedResource() throws Exception {
        var view = new TenantConfigWriteFacade.WorkflowTransitionRuleCreatedView(
                TENANT_ID,
                TR_DEFINITION_ID,
                TR_RULE_ID,
                TR_FROM_STATUS_ID,
                TR_TO_STATUS_ID,
                Instant.parse("2026-04-29T10:00:00Z"));

        when(writeFacade.createWorkflowTransitionRule(eq(TENANT_ID), eq(TR_DEFINITION_ID),
                any(CreateWorkflowTransitionRuleRequest.class), any())).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/transition-rules",
                        TR_DEFINITION_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromStatusId\":\"" + TR_FROM_STATUS_ID + "\","
                                + "\"toStatusId\":\"" + TR_TO_STATUS_ID + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.workflowDefinitionId").value(TR_DEFINITION_ID.toString()))
                .andExpect(jsonPath("$.transitionRuleId").value(TR_RULE_ID.toString()))
                .andExpect(jsonPath("$.fromStatusId").value(TR_FROM_STATUS_ID.toString()))
                .andExpect(jsonPath("$.toStatusId").value(TR_TO_STATUS_ID.toString()))
                .andExpect(jsonPath("$.createdAt").value("2026-04-29T10:00:00Z"));
    }

    @Test
    void createWorkflowTransitionRuleMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/transition-rules",
                        TR_DEFINITION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromStatusId\":\"" + TR_FROM_STATUS_ID + "\","
                                + "\"toStatusId\":\"" + TR_TO_STATUS_ID + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWorkflowTransitionRuleMalformedDefinitionIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/transition-rules",
                        "not-a-uuid")
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromStatusId\":\"" + TR_FROM_STATUS_ID + "\","
                                + "\"toStatusId\":\"" + TR_TO_STATUS_ID + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWorkflowTransitionRuleMissingFromStatusIdReturns400() throws Exception {
        when(writeFacade.createWorkflowTransitionRule(eq(TENANT_ID), eq(TR_DEFINITION_ID),
                any(CreateWorkflowTransitionRuleRequest.class), any()))
                .thenThrow(new IllegalArgumentException("fromStatusId null bo'lishi mumkin emas"));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/transition-rules",
                        TR_DEFINITION_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatusId\":\"" + TR_TO_STATUS_ID + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void createWorkflowTransitionRuleDefinitionNotFoundReturns404() throws Exception {
        when(writeFacade.createWorkflowTransitionRule(eq(TENANT_ID), eq(TR_DEFINITION_ID),
                any(CreateWorkflowTransitionRuleRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("WorkflowDefinition", TR_DEFINITION_ID));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/transition-rules",
                        TR_DEFINITION_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromStatusId\":\"" + TR_FROM_STATUS_ID + "\","
                                + "\"toStatusId\":\"" + TR_TO_STATUS_ID + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void createWorkflowTransitionRuleStatusNotFoundReturns404() throws Exception {
        when(writeFacade.createWorkflowTransitionRule(eq(TENANT_ID), eq(TR_DEFINITION_ID),
                any(CreateWorkflowTransitionRuleRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("WorkflowStatus", "fromStatus=" + TR_FROM_STATUS_ID));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/transition-rules",
                        TR_DEFINITION_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromStatusId\":\"" + TR_FROM_STATUS_ID + "\","
                                + "\"toStatusId\":\"" + TR_TO_STATUS_ID + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void createWorkflowTransitionRuleSelfTransitionReturns422() throws Exception {
        when(writeFacade.createWorkflowTransitionRule(eq(TENANT_ID), eq(TR_DEFINITION_ID),
                any(CreateWorkflowTransitionRuleRequest.class), any()))
                .thenThrow(new BusinessRuleException("SELF_TRANSITION_NOT_ALLOWED",
                        "Workflow transition rule fromStatus va toStatus bir xil bo'lishi mumkin emas "
                                + "(statusId=" + TR_FROM_STATUS_ID + ")"));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/transition-rules",
                        TR_DEFINITION_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromStatusId\":\"" + TR_FROM_STATUS_ID + "\","
                                + "\"toStatusId\":\"" + TR_FROM_STATUS_ID + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("SELF_TRANSITION_NOT_ALLOWED"));
    }

    @Test
    void createWorkflowTransitionRuleDuplicateReturns422() throws Exception {
        when(writeFacade.createWorkflowTransitionRule(eq(TENANT_ID), eq(TR_DEFINITION_ID),
                any(CreateWorkflowTransitionRuleRequest.class), any()))
                .thenThrow(new BusinessRuleException("DUPLICATE_WORKFLOW_TRANSITION_RULE",
                        "Workflow definition ichida 'BUGS -> PROCESSING' transition rule allaqachon mavjud"));

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/transition-rules",
                        TR_DEFINITION_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromStatusId\":\"" + TR_FROM_STATUS_ID + "\","
                                + "\"toStatusId\":\"" + TR_TO_STATUS_ID + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_WORKFLOW_TRANSITION_RULE"));
    }

    @Test
    void createWorkflowTransitionRuleForbiddenWhenAuthorizationFails() throws Exception {
        doThrow(new AccessDeniedException("Bu operatsiya uchun TENANT_CONFIG_WRITE ruxsati talab qilinadi"))
                .when(writeFacade).createWorkflowTransitionRule(eq(TENANT_ID), eq(TR_DEFINITION_ID),
                        any(CreateWorkflowTransitionRuleRequest.class), any());

        mockMvc.perform(post("/api/admin/tenant-config/workflow-definitions/{definitionId}/transition-rules",
                        TR_DEFINITION_ID)
                        .param("tenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromStatusId\":\"" + TR_FROM_STATUS_ID + "\","
                                + "\"toStatusId\":\"" + TR_TO_STATUS_ID + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ========== POST /tenants endpoint ==========

    private static final UUID NEW_TENANT_ID =
            UUID.fromString("88888888-8888-8888-8888-888888888881");

    @Test
    void createTenantReturns201WithCreatedResource() throws Exception {
        var view = new TenantConfigWriteFacade.TenantCreatedView(
                NEW_TENANT_ID,
                "Acme Corp",
                "acme",
                "Asia/Tashkent",
                "ACTIVE",
                Instant.parse("2026-04-29T10:00:00Z"));

        when(writeFacade.createTenant(eq(TENANT_ID),
                any(CreateTenantRequest.class), any())).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/tenants")
                        .param("adminContextTenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme Corp","slug":"acme","timezone":"Asia/Tashkent"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(NEW_TENANT_ID.toString()))
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.slug").value("acme"))
                .andExpect(jsonPath("$.timezone").value("Asia/Tashkent"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").value("2026-04-29T10:00:00Z"));
    }

    @Test
    void createTenantMissingAdminContextTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme","slug":"acme"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTenantMalformedAdminContextTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/tenants")
                        .param("adminContextTenantId", "not-a-uuid")
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme","slug":"acme"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTenantBlankNameReturns400() throws Exception {
        when(writeFacade.createTenant(eq(TENANT_ID),
                any(CreateTenantRequest.class), any()))
                .thenThrow(new IllegalArgumentException("name null yoki bo'sh bo'lishi mumkin emas"));

        mockMvc.perform(post("/api/admin/tenant-config/tenants")
                        .param("adminContextTenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","slug":"acme"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void createTenantDuplicateSlugReturns422() throws Exception {
        when(writeFacade.createTenant(eq(TENANT_ID),
                any(CreateTenantRequest.class), any()))
                .thenThrow(new BusinessRuleException("DUPLICATE_TENANT_SLUG",
                        "'acme' slug bilan tenant allaqachon mavjud"));

        mockMvc.perform(post("/api/admin/tenant-config/tenants")
                        .param("adminContextTenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme","slug":"acme"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_TENANT_SLUG"));
    }

    @Test
    void createTenantForbiddenWhenAuthorizationFails() throws Exception {
        doThrow(new AccessDeniedException("Bu operatsiya uchun TENANT_CONFIG_WRITE ruxsati talab qilinadi"))
                .when(writeFacade).createTenant(eq(TENANT_ID),
                        any(CreateTenantRequest.class), any());

        mockMvc.perform(post("/api/admin/tenant-config/tenants")
                        .param("adminContextTenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme","slug":"acme"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ========== POST /users endpoint ==========

    private static final UUID NEW_USER_ID =
            UUID.fromString("99999999-9999-9999-9999-999999999991");

    @Test
    void createAppUserReturns201WithCreatedResource() throws Exception {
        var view = new TenantConfigWriteFacade.AppUserCreatedView(
                NEW_USER_ID,
                123456789L,
                "alice",
                "Alice",
                "ACTIVE",
                Instant.parse("2026-04-29T10:00:00Z"));

        when(writeFacade.createAppUser(eq(TENANT_ID),
                any(CreateAppUserRequest.class), any())).thenReturn(view);

        mockMvc.perform(post("/api/admin/tenant-config/users")
                        .param("adminContextTenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"telegramUserId":123456789,"username":"alice","displayName":"Alice"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(NEW_USER_ID.toString()))
                .andExpect(jsonPath("$.telegramUserId").value(123456789))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.displayName").value("Alice"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").value("2026-04-29T10:00:00Z"));
    }

    @Test
    void createAppUserMissingAdminContextTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"telegramUserId":123456789}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAppUserMalformedAdminContextTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/tenant-config/users")
                        .param("adminContextTenantId", "not-a-uuid")
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"telegramUserId":123456789}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAppUserMissingTelegramUserIdReturns400() throws Exception {
        when(writeFacade.createAppUser(eq(TENANT_ID),
                any(CreateAppUserRequest.class), any()))
                .thenThrow(new IllegalArgumentException("telegramUserId null bo'lishi mumkin emas"));

        mockMvc.perform(post("/api/admin/tenant-config/users")
                        .param("adminContextTenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void createAppUserDuplicateTelegramUserIdReturns422() throws Exception {
        when(writeFacade.createAppUser(eq(TENANT_ID),
                any(CreateAppUserRequest.class), any()))
                .thenThrow(new BusinessRuleException("DUPLICATE_TELEGRAM_USER_ID",
                        "telegramUserId=123456789 bilan foydalanuvchi allaqachon mavjud"));

        mockMvc.perform(post("/api/admin/tenant-config/users")
                        .param("adminContextTenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"telegramUserId":123456789,"username":"alice"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_TELEGRAM_USER_ID"));
    }

    @Test
    void createAppUserForbiddenWhenAuthorizationFails() throws Exception {
        doThrow(new AccessDeniedException("Bu operatsiya uchun TENANT_CONFIG_WRITE ruxsati talab qilinadi"))
                .when(writeFacade).createAppUser(eq(TENANT_ID),
                        any(CreateAppUserRequest.class), any());

        mockMvc.perform(post("/api/admin/tenant-config/users")
                        .param("adminContextTenantId", TENANT_ID.toString())
                        .header(ACTOR_HEADER, ACTOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"telegramUserId":123456789}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }
}
