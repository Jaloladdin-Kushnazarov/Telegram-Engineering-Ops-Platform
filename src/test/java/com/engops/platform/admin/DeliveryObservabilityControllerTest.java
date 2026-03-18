package com.engops.platform.admin;

import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.telegram.TelegramDeliveryAttempt;
import com.engops.platform.telegram.TelegramDeliveryMetricsSnapshot;
import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsFacade;
import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsView;
import com.engops.platform.telegram.TelegramDeliveryOperation;
import com.engops.platform.telegram.TelegramDeliveryResult;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DeliveryObservabilityController @WebMvcTest testlari.
 *
 * Tekshiruvlar:
 * - success path: to'g'ri HTTP status va response body
 * - response mapping: work item metadata + metrics + attempts
 * - empty observability data: valid response
 * - not-found: 404 qaytariladi
 * - invalid historyLimit: 400 qaytariladi
 * - missing required parameter: 400 qaytariladi
 */
@WebMvcTest(DeliveryObservabilityController.class)
class DeliveryObservabilityControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORK_ITEM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String WORK_ITEM_CODE = "BUG-1";
    private static final Instant FIXED_TIME = Instant.parse("2026-03-18T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TelegramDeliveryObservabilityDetailsFacade detailsFacade;

    @Test
    void successPathReturnsCorrectResponse() throws Exception {
        UUID attemptId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID chatBindingId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        TelegramDeliveryAttempt attempt = TelegramDeliveryAttempt.reconstruct(
                attemptId, FIXED_TIME, TENANT_ID, WORK_ITEM_ID,
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                chatBindingId, 42L,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                99001L, null, null);

        TelegramDeliveryMetricsSnapshot snapshot = TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, WORK_ITEM_ID,
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                null, true);

        TelegramDeliveryObservabilityDetailsView details =
                new TelegramDeliveryObservabilityDetailsView(
                        WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                        WorkItemType.BUG, "BUGS",
                        snapshot, List.of(attempt));

        when(detailsFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, 10))
                .thenReturn(details);

        mockMvc.perform(get("/api/admin/delivery-observability/details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", WORK_ITEM_CODE)
                        .param("historyLimit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workItemId").value(WORK_ITEM_ID.toString()))
                .andExpect(jsonPath("$.workItemCode").value(WORK_ITEM_CODE))
                .andExpect(jsonPath("$.title").value("Login xato"))
                .andExpect(jsonPath("$.typeCode").value("BUG"))
                .andExpect(jsonPath("$.currentStatusCode").value("BUGS"))
                .andExpect(jsonPath("$.latestMetrics.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.latestMetrics.workItemId").value(WORK_ITEM_ID.toString()))
                .andExpect(jsonPath("$.latestMetrics.operation").value("SEND_NEW_MESSAGE"))
                .andExpect(jsonPath("$.latestMetrics.deliveryOutcome").value("DELIVERED"))
                .andExpect(jsonPath("$.latestMetrics.success").value(true))
                .andExpect(jsonPath("$.latestMetrics.rejected").value(false))
                .andExpect(jsonPath("$.latestMetrics.failed").value(false))
                .andExpect(jsonPath("$.latestMetrics.hasExternalMessageId").value(true))
                .andExpect(jsonPath("$.latestMetrics.empty").value(false))
                .andExpect(jsonPath("$.recentAttempts", hasSize(1)))
                .andExpect(jsonPath("$.recentAttempts[0].attemptId").value(attemptId.toString()))
                .andExpect(jsonPath("$.recentAttempts[0].attemptedAt").value("2026-03-18T10:00:00Z"))
                .andExpect(jsonPath("$.recentAttempts[0].tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.recentAttempts[0].workItemId").value(WORK_ITEM_ID.toString()))
                .andExpect(jsonPath("$.recentAttempts[0].operation").value("SEND_NEW_MESSAGE"))
                .andExpect(jsonPath("$.recentAttempts[0].targetChatBindingId").value(chatBindingId.toString()))
                .andExpect(jsonPath("$.recentAttempts[0].targetTopicId").value(42))
                .andExpect(jsonPath("$.recentAttempts[0].deliveryOutcome").value("DELIVERED"))
                .andExpect(jsonPath("$.recentAttempts[0].externalMessageId").value(99001))
                .andExpect(jsonPath("$.recentAttempts[0].success").value(true));
    }

    @Test
    void emptyObservabilityDataReturnsValidResponse() throws Exception {
        TelegramDeliveryMetricsSnapshot emptySnapshot =
                TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WORK_ITEM_ID);

        TelegramDeliveryObservabilityDetailsView details =
                new TelegramDeliveryObservabilityDetailsView(
                        WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                        WorkItemType.BUG, "BUGS",
                        emptySnapshot, List.of());

        when(detailsFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, 10))
                .thenReturn(details);

        mockMvc.perform(get("/api/admin/delivery-observability/details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", WORK_ITEM_CODE)
                        .param("historyLimit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workItemId").value(WORK_ITEM_ID.toString()))
                .andExpect(jsonPath("$.latestMetrics.empty").value(true))
                .andExpect(jsonPath("$.latestMetrics.success").value(false))
                .andExpect(jsonPath("$.recentAttempts", hasSize(0)));
    }

    @Test
    void defaultHistoryLimitIsUsed() throws Exception {
        TelegramDeliveryObservabilityDetailsView details =
                new TelegramDeliveryObservabilityDetailsView(
                        WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                        WorkItemType.BUG, "BUGS",
                        TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WORK_ITEM_ID),
                        List.of());

        when(detailsFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, 10))
                .thenReturn(details);

        mockMvc.perform(get("/api/admin/delivery-observability/details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", WORK_ITEM_CODE))
                .andExpect(status().isOk());
    }

    @Test
    void workItemNotFoundReturns404() throws Exception {
        when(detailsFacade.getDetails(TENANT_ID, "NONEXISTENT-99", 10))
                .thenThrow(new ResourceNotFoundException("WorkItem", "NONEXISTENT-99"));

        mockMvc.perform(get("/api/admin/delivery-observability/details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", "NONEXISTENT-99")
                        .param("historyLimit", "10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void invalidHistoryLimitReturns400() throws Exception {
        when(detailsFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, 0))
                .thenThrow(new IllegalArgumentException(
                        "limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        mockMvc.perform(get("/api/admin/delivery-observability/details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", WORK_ITEM_CODE)
                        .param("historyLimit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void missingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/delivery-observability/details")
                        .param("workItemCode", WORK_ITEM_CODE)
                        .param("historyLimit", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingWorkItemCodeReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/delivery-observability/details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("historyLimit", "10"))
                .andExpect(status().isBadRequest());
    }
}
