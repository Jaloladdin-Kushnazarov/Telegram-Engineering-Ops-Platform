package com.engops.platform.admin;

import com.engops.platform.infrastructure.security.AuthenticatedActor;
import com.engops.platform.infrastructure.security.SecurityConfig;
import com.engops.platform.infrastructure.security.SecurityWebMvcConfig;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.telegram.TelegramDeliveryAttempt;
import com.engops.platform.telegram.TelegramDeliveryMetricsSnapshot;
import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsView;
import com.engops.platform.telegram.TelegramDeliveryOperation;
import com.engops.platform.telegram.TelegramDeliveryResult;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
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
 * - unauthorized access: 403 qaytariladi
 * - missing authenticated actor: 403 (resolver-level, facade chaqirilmaydi)
 *
 * Phase 128 migratsiyasi: avvalgi {@code X-Actor-User-Id} header endi
 * ishlatilmaydi. Test'lar SecurityContext'ga {@link AuthenticatedActor}
 * principal'ini {@link #withActor(UUID)} {@link RequestPostProcessor} orqali
 * o'rnatadi. {@link SecurityWebMvcConfig} {@code @Import} qilinadi —
 * {@code @WebMvcTest} slice {@code @CurrentActor} resolver'ni faqat shu
 * konfiguratsiya yuklanganida ro'yxatga oladi.
 */
@WebMvcTest(DeliveryObservabilityController.class)
@Import({SecurityConfig.class, SecurityWebMvcConfig.class})
class DeliveryObservabilityControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORK_ITEM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ACTOR_USER_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final String WORK_ITEM_CODE = "BUG-1";
    private static final Instant FIXED_TIME = Instant.parse("2026-03-18T10:00:00Z");

    /**
     * Test helper: request attribute orqali SecurityContext'ga
     * {@link AuthenticatedActor} principal'ini o'rnatadi. Spring Security'ning
     * {@code SecurityContextHolderFilter} {@link RequestAttributeSecurityContextRepository}
     * orqali shu attribute'ni o'qiydi va {@link SecurityContextHolder}'ga
     * yuklaydi. Keyin {@code @CurrentActor} resolver principal'ni o'qib
     * controller method'iga {@code actorUserId} sifatida uzatadi.
     *
     * <p>JWT round-trip o'rniga to'g'ridan-to'g'ri Authentication o'rnatish
     * @WebMvcTest slice'ida deterministik — JwtDecoder bean talab qilinmaydi
     * va resolver semantikasi to'liq tekshiriladi.</p>
     *
     * <p>{@code SecurityMockMvcRequestPostProcessors.authentication(...)} ham
     * bir xil natijaga olib keladi, lekin u {@code spring-security-test}
     * dependency'sini talab qiladi — bu phase'da pom.xml o'zgartirilmaydi,
     * shuning uchun bu helper Spring Security ichidagi public API'lardan
     * foydalanib o'sha effektni qo'lda yaratadi.</p>
     */
    private static RequestPostProcessor withActor(UUID actorUserId) {
        return request -> {
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    new AuthenticatedActor(actorUserId, null), null, Collections.emptyList());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            request.setAttribute(
                    RequestAttributeSecurityContextRepository.class.getName()
                            + ".SPRING_SECURITY_CONTEXT",
                    context);
            return request;
        };
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeliveryObservabilityDetailsByCodeFacade detailsByCodeFacade;

    @MockBean
    private DeliveryObservabilitySummaryReadFacade summaryReadFacade;

    @MockBean
    private DeliveryObservabilityDetailsByIdFacade detailsByIdFacade;

    @MockBean
    private DeliveryObservabilitySummaryByStatusFacade summaryByStatusFacade;

    @MockBean
    private DeliveryObservabilitySummaryByOwnerFacade summaryByOwnerFacade;

    @MockBean
    private DeliveryObservabilityDetailsByStatusFacade detailsByStatusFacade;

    @MockBean
    private DeliveryObservabilityDetailsByOwnerFacade detailsByOwnerFacade;

    // ========== Details by-code endpoint tests ==========

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

        when(detailsByCodeFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, 10, ACTOR_USER_ID))
                .thenReturn(details);

        mockMvc.perform(get("/api/admin/delivery-observability/details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", WORK_ITEM_CODE)
                        .param("historyLimit", "10")
                        .with(withActor(ACTOR_USER_ID)))
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

        when(detailsByCodeFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, 10, ACTOR_USER_ID))
                .thenReturn(details);

        mockMvc.perform(get("/api/admin/delivery-observability/details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", WORK_ITEM_CODE)
                        .param("historyLimit", "10")
                        .with(withActor(ACTOR_USER_ID)))
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

        when(detailsByCodeFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, 10, ACTOR_USER_ID))
                .thenReturn(details);

        mockMvc.perform(get("/api/admin/delivery-observability/details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", WORK_ITEM_CODE)
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk());
    }

    @Test
    void workItemNotFoundReturns404() throws Exception {
        when(detailsByCodeFacade.getDetails(eq(TENANT_ID), eq("NONEXISTENT-99"), eq(10), any()))
                .thenThrow(new ResourceNotFoundException("WorkItem", "NONEXISTENT-99"));

        mockMvc.perform(get("/api/admin/delivery-observability/details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", "NONEXISTENT-99")
                        .param("historyLimit", "10")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void invalidHistoryLimitReturns400() throws Exception {
        when(detailsByCodeFacade.getDetails(eq(TENANT_ID), eq(WORK_ITEM_CODE), eq(0), any()))
                .thenThrow(new IllegalArgumentException(
                        "limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        mockMvc.perform(get("/api/admin/delivery-observability/details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", WORK_ITEM_CODE)
                        .param("historyLimit", "0")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void missingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/delivery-observability/details")
                        .param("workItemCode", WORK_ITEM_CODE)
                        .param("historyLimit", "10")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingWorkItemCodeReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/delivery-observability/details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("historyLimit", "10")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    // ========== Summary endpoint tests ==========

    @Test
    void summaryReturnsCorrectResponse() throws Exception {
        TelegramDeliveryMetricsSnapshot snapshot = TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, WORK_ITEM_ID,
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                null, true);

        var item = new DeliveryObservabilitySummaryItem(
                WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                snapshot);

        when(summaryReadFacade.getSummaryList(TENANT_ID, 20, ACTOR_USER_ID))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/admin/delivery-observability/summary")
                        .param("tenantId", TENANT_ID.toString())
                        .param("limit", "20")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].workItemId").value(WORK_ITEM_ID.toString()))
                .andExpect(jsonPath("$.items[0].workItemCode").value(WORK_ITEM_CODE))
                .andExpect(jsonPath("$.items[0].title").value("Login xato"))
                .andExpect(jsonPath("$.items[0].typeCode").value("BUG"))
                .andExpect(jsonPath("$.items[0].currentStatusCode").value("BUGS"))
                .andExpect(jsonPath("$.items[0].latestMetrics.deliveryOutcome").value("DELIVERED"))
                .andExpect(jsonPath("$.items[0].latestMetrics.success").value(true))
                .andExpect(jsonPath("$.items[0].latestMetrics.hasExternalMessageId").value(true))
                .andExpect(jsonPath("$.items[0].latestMetrics.empty").value(false));
    }

    @Test
    void summaryEmptyListReturns200() throws Exception {
        when(summaryReadFacade.getSummaryList(TENANT_ID, 20, ACTOR_USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/delivery-observability/summary")
                        .param("tenantId", TENANT_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void summaryDefaultLimitIsUsed() throws Exception {
        when(summaryReadFacade.getSummaryList(TENANT_ID, 20, ACTOR_USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/delivery-observability/summary")
                        .param("tenantId", TENANT_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk());
    }

    @Test
    void summaryInvalidLimitReturns400() throws Exception {
        when(summaryReadFacade.getSummaryList(eq(TENANT_ID), eq(0), any()))
                .thenThrow(new IllegalArgumentException("limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        mockMvc.perform(get("/api/admin/delivery-observability/summary")
                        .param("tenantId", TENANT_ID.toString())
                        .param("limit", "0")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void summaryMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/delivery-observability/summary")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    // ========== Details by-id endpoint tests ==========

    @Test
    void detailsByIdReturnsCorrectResponse() throws Exception {
        TelegramDeliveryMetricsSnapshot snapshot = TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, WORK_ITEM_ID,
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                null, true);

        UUID attemptId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID chatBindingId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        TelegramDeliveryAttempt attempt = TelegramDeliveryAttempt.reconstruct(
                attemptId, FIXED_TIME, TENANT_ID, WORK_ITEM_ID,
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                chatBindingId, 42L,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                99001L, null, null);

        var details = new TelegramDeliveryObservabilityDetailsView(
                WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                snapshot, List.of(attempt));

        when(detailsByIdFacade.getDetails(TENANT_ID, WORK_ITEM_ID, 10, ACTOR_USER_ID))
                .thenReturn(details);

        mockMvc.perform(get("/api/admin/delivery-observability/details/by-id")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemId", WORK_ITEM_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workItemId").value(WORK_ITEM_ID.toString()))
                .andExpect(jsonPath("$.workItemCode").value(WORK_ITEM_CODE))
                .andExpect(jsonPath("$.title").value("Login xato"))
                .andExpect(jsonPath("$.typeCode").value("BUG"))
                .andExpect(jsonPath("$.currentStatusCode").value("BUGS"))
                .andExpect(jsonPath("$.latestMetrics.success").value(true))
                .andExpect(jsonPath("$.latestMetrics.deliveryOutcome").value("DELIVERED"))
                .andExpect(jsonPath("$.recentAttempts", hasSize(1)))
                .andExpect(jsonPath("$.recentAttempts[0].attemptId").value(attemptId.toString()))
                .andExpect(jsonPath("$.recentAttempts[0].operation").value("SEND_NEW_MESSAGE"))
                .andExpect(jsonPath("$.recentAttempts[0].success").value(true));
    }

    @Test
    void detailsByIdDefaultHistoryLimitIsUsed() throws Exception {
        var details = new TelegramDeliveryObservabilityDetailsView(
                WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WORK_ITEM_ID),
                List.of());

        when(detailsByIdFacade.getDetails(TENANT_ID, WORK_ITEM_ID, 10, ACTOR_USER_ID))
                .thenReturn(details);

        mockMvc.perform(get("/api/admin/delivery-observability/details/by-id")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemId", WORK_ITEM_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workItemId").value(WORK_ITEM_ID.toString()))
                .andExpect(jsonPath("$.latestMetrics.empty").value(true));
    }

    @Test
    void detailsByIdNotFoundReturns404() throws Exception {
        UUID unknownId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        when(detailsByIdFacade.getDetails(eq(unknownId), eq(unknownId), eq(10), any()))
                .thenThrow(new ResourceNotFoundException("WorkItem", unknownId));

        // Use TENANT_ID for the actual request — the mock matches on unknownId
        when(detailsByIdFacade.getDetails(eq(TENANT_ID), eq(unknownId), eq(10), any()))
                .thenThrow(new ResourceNotFoundException("WorkItem", unknownId));

        mockMvc.perform(get("/api/admin/delivery-observability/details/by-id")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemId", unknownId.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void detailsByIdInvalidHistoryLimitReturns400() throws Exception {
        when(detailsByIdFacade.getDetails(eq(TENANT_ID), eq(WORK_ITEM_ID), eq(0), any()))
                .thenThrow(new IllegalArgumentException(
                        "historyLimit 1..50 oralig'ida bo'lishi kerak"));

        mockMvc.perform(get("/api/admin/delivery-observability/details/by-id")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemId", WORK_ITEM_ID.toString())
                        .param("historyLimit", "0")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void detailsByIdMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/delivery-observability/details/by-id")
                        .param("workItemId", WORK_ITEM_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detailsByIdMissingWorkItemIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/delivery-observability/details/by-id")
                        .param("tenantId", TENANT_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    // ========== Summary by-status endpoint tests ==========

    @Test
    void summaryByStatusReturnsCorrectResponse() throws Exception {
        TelegramDeliveryMetricsSnapshot snapshot = TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, WORK_ITEM_ID,
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                null, true);

        var item = new DeliveryObservabilitySummaryItem(
                WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                snapshot);

        when(summaryByStatusFacade.getSummaryList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/admin/delivery-observability/summary/by-status")
                        .param("tenantId", TENANT_ID.toString())
                        .param("statusCode", "BUGS")
                        .param("limit", "20")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].workItemId").value(WORK_ITEM_ID.toString()))
                .andExpect(jsonPath("$.items[0].workItemCode").value(WORK_ITEM_CODE))
                .andExpect(jsonPath("$.items[0].title").value("Login xato"))
                .andExpect(jsonPath("$.items[0].typeCode").value("BUG"))
                .andExpect(jsonPath("$.items[0].currentStatusCode").value("BUGS"))
                .andExpect(jsonPath("$.items[0].latestMetrics.deliveryOutcome").value("DELIVERED"))
                .andExpect(jsonPath("$.items[0].latestMetrics.success").value(true))
                .andExpect(jsonPath("$.items[0].latestMetrics.empty").value(false));
    }

    @Test
    void summaryByStatusDefaultLimitIsUsed() throws Exception {
        when(summaryByStatusFacade.getSummaryList(eq(TENANT_ID), eq("BUGS"), eq(20), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/delivery-observability/summary/by-status")
                        .param("tenantId", TENANT_ID.toString())
                        .param("statusCode", "BUGS")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void summaryByStatusEmptyListReturns200() throws Exception {
        when(summaryByStatusFacade.getSummaryList(eq(TENANT_ID), eq("PROCESSING"), eq(10), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/delivery-observability/summary/by-status")
                        .param("tenantId", TENANT_ID.toString())
                        .param("statusCode", "PROCESSING")
                        .param("limit", "10")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void summaryByStatusInvalidLimitReturns400() throws Exception {
        when(summaryByStatusFacade.getSummaryList(eq(TENANT_ID), eq("BUGS"), eq(0), any()))
                .thenThrow(new IllegalArgumentException(
                        "limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        mockMvc.perform(get("/api/admin/delivery-observability/summary/by-status")
                        .param("tenantId", TENANT_ID.toString())
                        .param("statusCode", "BUGS")
                        .param("limit", "0")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void summaryByStatusMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/delivery-observability/summary/by-status")
                        .param("statusCode", "BUGS")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void summaryByStatusMissingStatusCodeReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/delivery-observability/summary/by-status")
                        .param("tenantId", TENANT_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    // ========== Summary by-owner endpoint tests ==========

    private static final UUID OWNER_USER_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

    @Test
    void summaryByOwnerReturnsCorrectResponse() throws Exception {
        TelegramDeliveryMetricsSnapshot snapshot = TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, WORK_ITEM_ID,
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                null, true);

        var item = new DeliveryObservabilitySummaryItem(
                WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                snapshot);

        when(summaryByOwnerFacade.getSummaryList(TENANT_ID, OWNER_USER_ID, 20, ACTOR_USER_ID))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/admin/delivery-observability/summary/by-owner")
                        .param("tenantId", TENANT_ID.toString())
                        .param("ownerUserId", OWNER_USER_ID.toString())
                        .param("limit", "20")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].workItemId").value(WORK_ITEM_ID.toString()))
                .andExpect(jsonPath("$.items[0].workItemCode").value(WORK_ITEM_CODE))
                .andExpect(jsonPath("$.items[0].title").value("Login xato"))
                .andExpect(jsonPath("$.items[0].typeCode").value("BUG"))
                .andExpect(jsonPath("$.items[0].currentStatusCode").value("BUGS"))
                .andExpect(jsonPath("$.items[0].latestMetrics.deliveryOutcome").value("DELIVERED"))
                .andExpect(jsonPath("$.items[0].latestMetrics.success").value(true))
                .andExpect(jsonPath("$.items[0].latestMetrics.empty").value(false));
    }

    @Test
    void summaryByOwnerDefaultLimitIsUsed() throws Exception {
        when(summaryByOwnerFacade.getSummaryList(eq(TENANT_ID), eq(OWNER_USER_ID), eq(20), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/delivery-observability/summary/by-owner")
                        .param("tenantId", TENANT_ID.toString())
                        .param("ownerUserId", OWNER_USER_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void summaryByOwnerEmptyListReturns200() throws Exception {
        when(summaryByOwnerFacade.getSummaryList(eq(TENANT_ID), eq(OWNER_USER_ID), eq(10), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/delivery-observability/summary/by-owner")
                        .param("tenantId", TENANT_ID.toString())
                        .param("ownerUserId", OWNER_USER_ID.toString())
                        .param("limit", "10")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void summaryByOwnerInvalidLimitReturns400() throws Exception {
        when(summaryByOwnerFacade.getSummaryList(eq(TENANT_ID), eq(OWNER_USER_ID), eq(0), any()))
                .thenThrow(new IllegalArgumentException(
                        "limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        mockMvc.perform(get("/api/admin/delivery-observability/summary/by-owner")
                        .param("tenantId", TENANT_ID.toString())
                        .param("ownerUserId", OWNER_USER_ID.toString())
                        .param("limit", "0")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void summaryByOwnerMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/delivery-observability/summary/by-owner")
                        .param("ownerUserId", OWNER_USER_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void summaryByOwnerMissingOwnerUserIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/delivery-observability/summary/by-owner")
                        .param("tenantId", TENANT_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    // ========== Details by-status endpoint tests ==========

    @Test
    void detailsByStatusReturnsCorrectResponse() throws Exception {
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

        var details = new TelegramDeliveryObservabilityDetailsView(
                WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                snapshot, List.of(attempt));

        when(detailsByStatusFacade.getDetailsList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID))
                .thenReturn(List.of(details));

        mockMvc.perform(get("/api/admin/delivery-observability/details/by-status")
                        .param("tenantId", TENANT_ID.toString())
                        .param("statusCode", "BUGS")
                        .param("limit", "20")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].workItemId").value(WORK_ITEM_ID.toString()))
                .andExpect(jsonPath("$.items[0].workItemCode").value(WORK_ITEM_CODE))
                .andExpect(jsonPath("$.items[0].title").value("Login xato"))
                .andExpect(jsonPath("$.items[0].typeCode").value("BUG"))
                .andExpect(jsonPath("$.items[0].currentStatusCode").value("BUGS"))
                .andExpect(jsonPath("$.items[0].latestMetrics.deliveryOutcome").value("DELIVERED"))
                .andExpect(jsonPath("$.items[0].latestMetrics.success").value(true))
                .andExpect(jsonPath("$.items[0].latestMetrics.empty").value(false))
                .andExpect(jsonPath("$.items[0].recentAttempts", hasSize(1)))
                .andExpect(jsonPath("$.items[0].recentAttempts[0].attemptId").value(attemptId.toString()))
                .andExpect(jsonPath("$.items[0].recentAttempts[0].operation").value("SEND_NEW_MESSAGE"))
                .andExpect(jsonPath("$.items[0].recentAttempts[0].success").value(true));
    }

    @Test
    void detailsByStatusDefaultLimitIsUsed() throws Exception {
        when(detailsByStatusFacade.getDetailsList(eq(TENANT_ID), eq("BUGS"), eq(20), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/delivery-observability/details/by-status")
                        .param("tenantId", TENANT_ID.toString())
                        .param("statusCode", "BUGS")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void detailsByStatusEmptyListReturns200() throws Exception {
        when(detailsByStatusFacade.getDetailsList(eq(TENANT_ID), eq("PROCESSING"), eq(10), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/delivery-observability/details/by-status")
                        .param("tenantId", TENANT_ID.toString())
                        .param("statusCode", "PROCESSING")
                        .param("limit", "10")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void detailsByStatusInvalidLimitReturns400() throws Exception {
        when(detailsByStatusFacade.getDetailsList(eq(TENANT_ID), eq("BUGS"), eq(0), any()))
                .thenThrow(new IllegalArgumentException(
                        "limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        mockMvc.perform(get("/api/admin/delivery-observability/details/by-status")
                        .param("tenantId", TENANT_ID.toString())
                        .param("statusCode", "BUGS")
                        .param("limit", "0")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void detailsByStatusMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/delivery-observability/details/by-status")
                        .param("statusCode", "BUGS")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detailsByStatusMissingStatusCodeReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/delivery-observability/details/by-status")
                        .param("tenantId", TENANT_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    // ========== Details by-owner endpoint tests ==========

    @Test
    void detailsByOwnerReturnsCorrectResponse() throws Exception {
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

        var details = new TelegramDeliveryObservabilityDetailsView(
                WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                snapshot, List.of(attempt));

        when(detailsByOwnerFacade.getDetailsList(TENANT_ID, OWNER_USER_ID, 20, ACTOR_USER_ID))
                .thenReturn(List.of(details));

        mockMvc.perform(get("/api/admin/delivery-observability/details/by-owner")
                        .param("tenantId", TENANT_ID.toString())
                        .param("ownerUserId", OWNER_USER_ID.toString())
                        .param("limit", "20")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].workItemId").value(WORK_ITEM_ID.toString()))
                .andExpect(jsonPath("$.items[0].workItemCode").value(WORK_ITEM_CODE))
                .andExpect(jsonPath("$.items[0].title").value("Login xato"))
                .andExpect(jsonPath("$.items[0].typeCode").value("BUG"))
                .andExpect(jsonPath("$.items[0].currentStatusCode").value("BUGS"))
                .andExpect(jsonPath("$.items[0].latestMetrics.deliveryOutcome").value("DELIVERED"))
                .andExpect(jsonPath("$.items[0].latestMetrics.success").value(true))
                .andExpect(jsonPath("$.items[0].latestMetrics.empty").value(false))
                .andExpect(jsonPath("$.items[0].recentAttempts", hasSize(1)))
                .andExpect(jsonPath("$.items[0].recentAttempts[0].attemptId").value(attemptId.toString()))
                .andExpect(jsonPath("$.items[0].recentAttempts[0].operation").value("SEND_NEW_MESSAGE"))
                .andExpect(jsonPath("$.items[0].recentAttempts[0].success").value(true));
    }

    @Test
    void detailsByOwnerDefaultLimitIsUsed() throws Exception {
        when(detailsByOwnerFacade.getDetailsList(eq(TENANT_ID), eq(OWNER_USER_ID), eq(20), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/delivery-observability/details/by-owner")
                        .param("tenantId", TENANT_ID.toString())
                        .param("ownerUserId", OWNER_USER_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void detailsByOwnerEmptyListReturns200() throws Exception {
        when(detailsByOwnerFacade.getDetailsList(eq(TENANT_ID), eq(OWNER_USER_ID), eq(10), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/delivery-observability/details/by-owner")
                        .param("tenantId", TENANT_ID.toString())
                        .param("ownerUserId", OWNER_USER_ID.toString())
                        .param("limit", "10")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void detailsByOwnerInvalidLimitReturns400() throws Exception {
        when(detailsByOwnerFacade.getDetailsList(eq(TENANT_ID), eq(OWNER_USER_ID), eq(0), any()))
                .thenThrow(new IllegalArgumentException(
                        "limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        mockMvc.perform(get("/api/admin/delivery-observability/details/by-owner")
                        .param("tenantId", TENANT_ID.toString())
                        .param("ownerUserId", OWNER_USER_ID.toString())
                        .param("limit", "0")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void detailsByOwnerMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/delivery-observability/details/by-owner")
                        .param("ownerUserId", OWNER_USER_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detailsByOwnerMissingOwnerUserIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/delivery-observability/details/by-owner")
                        .param("tenantId", TENANT_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    // ========== Authorization tests (controller-level) ==========

    @Test
    void detailsEndpointReturns403WhenAccessDenied() throws Exception {
        when(detailsByCodeFacade.getDetails(eq(TENANT_ID), eq(WORK_ITEM_CODE), eq(10), any()))
                .thenThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"));

        mockMvc.perform(get("/api/admin/delivery-observability/details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", WORK_ITEM_CODE)
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void summaryEndpointReturns403WhenAccessDenied() throws Exception {
        when(summaryReadFacade.getSummaryList(eq(TENANT_ID), eq(20), any()))
                .thenThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"));

        mockMvc.perform(get("/api/admin/delivery-observability/summary")
                        .param("tenantId", TENANT_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void detailsByIdEndpointReturns403WhenAccessDenied() throws Exception {
        when(detailsByIdFacade.getDetails(eq(TENANT_ID), eq(WORK_ITEM_ID), eq(10), any()))
                .thenThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"));

        mockMvc.perform(get("/api/admin/delivery-observability/details/by-id")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemId", WORK_ITEM_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void missingAuthenticatedActorReturns403WithoutReachingFacade() throws Exception {
        // Phase 128: @CurrentActor resolver SecurityContext'da AuthenticatedActor
        // bo'lmaganida AccessDeniedException tashlaydi va GlobalExceptionHandler
        // 403 ACCESS_DENIED qaytaradi. Bu facade chaqirilishidan OLDIN bo'ladi —
        // shuning uchun facade'lar mock'lanmaydi va verifyNoInteractions tasdiqlaydi.
        mockMvc.perform(get("/api/admin/delivery-observability/summary")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        verifyNoInteractions(summaryReadFacade);
        verifyNoInteractions(detailsByCodeFacade);
        verifyNoInteractions(detailsByIdFacade);
        verifyNoInteractions(summaryByStatusFacade);
        verifyNoInteractions(summaryByOwnerFacade);
        verifyNoInteractions(detailsByStatusFacade);
        verifyNoInteractions(detailsByOwnerFacade);
    }
}
