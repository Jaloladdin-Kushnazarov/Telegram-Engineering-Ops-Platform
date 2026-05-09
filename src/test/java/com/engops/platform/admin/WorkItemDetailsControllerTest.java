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
import com.engops.platform.workitem.model.UpdateType;
import com.engops.platform.workitem.model.Visibility;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import com.engops.platform.workitem.model.WorkItemUpdate;
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

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WorkItemDetailsController @WebMvcTest testlari.
 *
 * Tekshiruvlar:
 * - details success path: to'g'ri HTTP status va response body
 * - details update history ordering: createdAt ASC tartibda
 * - details not-found: 404 qaytariladi
 * - details invalid input: 400 qaytariladi
 * - details missing required parameter: 400 qaytariladi
 * - summary success path: kompakt ro'yxat qaytariladi
 * - summary default limit: 20 ishlatiladi
 * - summary bo'sh ro'yxat: 200 qaytariladi
 * - summary invalid limit: 400 qaytariladi
 * - summary missing tenantId: 400 qaytariladi
 * - support-details success path: combined payload qaytariladi
 * - support-details not-found: 404 qaytariladi
 * - support-details invalid historyLimit: 400 qaytariladi
 * - support-details missing tenantId: 400 qaytariladi
 * - support-details missing workItemCode: 400 qaytariladi
 * - support-details default historyLimit: 10 ishlatiladi
 * - support-summary success path: combined summary ro'yxat qaytariladi
 * - support-summary default limit: 20 ishlatiladi
 * - support-summary bo'sh ro'yxat: 200 qaytariladi
 * - support-summary invalid limit: 400 qaytariladi
 * - support-summary missing tenantId: 400 qaytariladi
 * - support-details/by-id success path: combined payload qaytariladi
 * - support-details/by-id default historyLimit: 10 ishlatiladi
 * - support-details/by-id not-found: 404 qaytariladi
 * - support-details/by-id invalid historyLimit: 400 qaytariladi
 * - support-details/by-id missing tenantId: 400 qaytariladi
 * - support-details/by-id missing workItemId: 400 qaytariladi
 * - details/by-id success path: work item details qaytariladi
 * - details/by-id not-found: 404 qaytariladi
 * - details/by-id missing tenantId: 400 qaytariladi
 * - details/by-id missing workItemId: 400 qaytariladi
 * - by-status success path: status bo'yicha kompakt ro'yxat qaytariladi
 * - by-status default limit: 20 ishlatiladi
 * - by-status bo'sh ro'yxat: 200 qaytariladi
 * - by-status invalid limit: 400 qaytariladi
 * - by-status missing tenantId: 400 qaytariladi
 * - by-status missing statusCode: 400 qaytariladi
 */
@WebMvcTest(WorkItemDetailsController.class)
@Import({SecurityConfig.class, SecurityWebMvcConfig.class})
class WorkItemDetailsControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORK_ITEM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WORKFLOW_DEF_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OWNER_USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID AUTHOR_USER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String WORK_ITEM_CODE = "BUG-1";
    private static final UUID ACTOR_USER_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");

    /**
     * Phase 129 helper: SecurityContext'ga {@link AuthenticatedActor} principal'ini
     * request attribute orqali o'rnatadi. Spring Security'ning
     * {@code SecurityContextHolderFilter} {@link RequestAttributeSecurityContextRepository}
     * orqali shu attribute'ni o'qiydi va keyin {@code @CurrentActor} resolver
     * principal'ni controller {@code actorUserId} parametriga uzatadi.
     *
     * <p>{@code SecurityMockMvcRequestPostProcessors.authentication(...)} ham
     * shu effektni beradi, lekin {@code spring-security-test} dependency'sini
     * talab qiladi — bu phase'da {@code pom.xml} o'zgartirilmaydi, shuning uchun
     * Spring Security ichidagi public {@code RequestAttributeSecurityContextRepository}
     * API'sidan foydalanib effekt qo'lda yaratiladi.</p>
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
    private WorkItemDetailsReadFacade detailsReadFacade;

    @MockBean
    private WorkItemSummaryReadFacade summaryReadFacade;

    @MockBean
    private WorkItemSupportDetailsReadFacade supportDetailsReadFacade;

    @MockBean
    private WorkItemSupportSummaryFacade supportSummaryFacade;

    @MockBean
    private WorkItemSupportDetailsByIdFacade supportDetailsByIdFacade;

    @MockBean
    private WorkItemDetailsByIdFacade detailsByIdFacade;

    @MockBean
    private WorkItemSummaryByStatusReadFacade summaryByStatusReadFacade;

    @MockBean
    private WorkItemSummaryByOwnerReadFacade summaryByOwnerReadFacade;

    @MockBean
    private WorkItemSupportSummaryByStatusFacade supportSummaryByStatusFacade;

    @MockBean
    private WorkItemSupportSummaryByOwnerFacade supportSummaryByOwnerFacade;

    @MockBean
    private WorkItemSupportDetailsByStatusFacade supportDetailsByStatusFacade;

    @MockBean
    private WorkItemSupportDetailsByOwnerFacade supportDetailsByOwnerFacade;

    @Test
    void successPathReturnsCorrectResponse() throws Exception {
        WorkItem workItem = new WorkItem(
                TENANT_ID, WORK_ITEM_CODE, WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Login xato", "BUGS", OWNER_USER_ID);
        workItem.setPriorityCode("HIGH");
        workItem.setSeverityCode("CRITICAL");
        workItem.setEnvironmentCode("PRODUCTION");
        workItem.setSourceService("auth-service");
        workItem.setCorrelationKey("corr-123");
        workItem.assignOwner(OWNER_USER_ID);

        WorkItemUpdate update = new WorkItemUpdate(
                TENANT_ID, workItem.getId(), AUTHOR_USER_ID,
                UpdateType.COMMENT, "Tekshirilmoqda");

        var view = new WorkItemDetailsFacade.WorkItemDetailsView(workItem, List.of(update));
        when(detailsReadFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, ACTOR_USER_ID)).thenReturn(view);

        mockMvc.perform(get("/api/admin/work-items/details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", WORK_ITEM_CODE)
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workItemId").value(workItem.getId().toString()))
                .andExpect(jsonPath("$.workItemCode").value(WORK_ITEM_CODE))
                .andExpect(jsonPath("$.title").value("Login xato"))
                .andExpect(jsonPath("$.typeCode").value("BUG"))
                .andExpect(jsonPath("$.currentStatusCode").value("BUGS"))
                .andExpect(jsonPath("$.priorityCode").value("HIGH"))
                .andExpect(jsonPath("$.severityCode").value("CRITICAL"))
                .andExpect(jsonPath("$.environmentCode").value("PRODUCTION"))
                .andExpect(jsonPath("$.sourceService").value("auth-service"))
                .andExpect(jsonPath("$.correlationKey").value("corr-123"))
                .andExpect(jsonPath("$.currentOwnerUserId").value(OWNER_USER_ID.toString()))
                .andExpect(jsonPath("$.reopenedCount").value(0))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.updates", hasSize(1)))
                .andExpect(jsonPath("$.updates[0].updateId").value(update.getId().toString()))
                .andExpect(jsonPath("$.updates[0].tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.updates[0].workItemId").value(workItem.getId().toString()))
                .andExpect(jsonPath("$.updates[0].authorUserId").value(AUTHOR_USER_ID.toString()))
                .andExpect(jsonPath("$.updates[0].updateTypeCode").value("COMMENT"))
                .andExpect(jsonPath("$.updates[0].body").value("Tekshirilmoqda"))
                .andExpect(jsonPath("$.updates[0].visibilityCode").value("INTERNAL"));
    }

    @Test
    void emptyUpdateHistoryReturnsValidResponse() throws Exception {
        WorkItem workItem = new WorkItem(
                TENANT_ID, WORK_ITEM_CODE, WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Login xato", "BUGS", OWNER_USER_ID);

        var view = new WorkItemDetailsFacade.WorkItemDetailsView(workItem, List.of());
        when(detailsReadFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, ACTOR_USER_ID)).thenReturn(view);

        mockMvc.perform(get("/api/admin/work-items/details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", WORK_ITEM_CODE)
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workItemId").value(workItem.getId().toString()))
                .andExpect(jsonPath("$.updates", hasSize(0)));
    }

    @Test
    void multipleUpdatesReturnedInOrder() throws Exception {
        WorkItem workItem = new WorkItem(
                TENANT_ID, WORK_ITEM_CODE, WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Login xato", "BUGS", OWNER_USER_ID);

        WorkItemUpdate update1 = new WorkItemUpdate(
                TENANT_ID, workItem.getId(), AUTHOR_USER_ID,
                UpdateType.COMMENT, "Birinchi izoh");
        WorkItemUpdate update2 = new WorkItemUpdate(
                TENANT_ID, workItem.getId(), AUTHOR_USER_ID,
                UpdateType.STATUS_CHANGE, "PROCESSING ga o'tkazildi");

        var view = new WorkItemDetailsFacade.WorkItemDetailsView(
                workItem, List.of(update1, update2));
        when(detailsReadFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, ACTOR_USER_ID)).thenReturn(view);

        mockMvc.perform(get("/api/admin/work-items/details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", WORK_ITEM_CODE)
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updates", hasSize(2)))
                .andExpect(jsonPath("$.updates[0].updateTypeCode").value("COMMENT"))
                .andExpect(jsonPath("$.updates[0].body").value("Birinchi izoh"))
                .andExpect(jsonPath("$.updates[1].updateTypeCode").value("STATUS_CHANGE"))
                .andExpect(jsonPath("$.updates[1].body").value("PROCESSING ga o'tkazildi"));
    }

    @Test
    void workItemNotFoundReturns404() throws Exception {
        when(detailsReadFacade.getDetails(eq(TENANT_ID), eq("NONEXISTENT-99"), any()))
                .thenThrow(new ResourceNotFoundException("WorkItem", "NONEXISTENT-99"));

        mockMvc.perform(get("/api/admin/work-items/details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", "NONEXISTENT-99")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void invalidWorkItemCodeReturns400() throws Exception {
        when(detailsReadFacade.getDetails(eq(TENANT_ID), eq(""), any()))
                .thenThrow(new IllegalArgumentException(
                        "workItemCode null yoki bo'sh bo'lishi mumkin emas"));

        mockMvc.perform(get("/api/admin/work-items/details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", "")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/details")
                        .param("workItemCode", WORK_ITEM_CODE)
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingWorkItemCodeReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/details")
                        .param("tenantId", TENANT_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    // ========== Summary endpoint tests ==========

    @Test
    void summaryReturnsCorrectResponse() throws Exception {
        var item = new WorkItemSummaryItem(
                WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                "HIGH", "CRITICAL", OWNER_USER_ID,
                java.time.Instant.parse("2026-03-18T10:00:00Z"),
                java.time.Instant.parse("2026-03-18T11:00:00Z"),
                null, 0, false);

        when(summaryReadFacade.getSummaryList(TENANT_ID, 20, ACTOR_USER_ID)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/admin/work-items/summary")
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
                .andExpect(jsonPath("$.items[0].priorityCode").value("HIGH"))
                .andExpect(jsonPath("$.items[0].severityCode").value("CRITICAL"))
                .andExpect(jsonPath("$.items[0].currentOwnerUserId").value(OWNER_USER_ID.toString()))
                .andExpect(jsonPath("$.items[0].openedAt").value("2026-03-18T10:00:00Z"))
                .andExpect(jsonPath("$.items[0].lastTransitionAt").value("2026-03-18T11:00:00Z"))
                .andExpect(jsonPath("$.items[0].reopenedCount").value(0))
                .andExpect(jsonPath("$.items[0].archived").value(false));
    }

    @Test
    void summaryDefaultLimitIsUsed() throws Exception {
        when(summaryReadFacade.getSummaryList(TENANT_ID, 20, ACTOR_USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/work-items/summary")
                        .param("tenantId", TENANT_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void summaryEmptyListReturns200() throws Exception {
        when(summaryReadFacade.getSummaryList(TENANT_ID, 10, ACTOR_USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/work-items/summary")
                        .param("tenantId", TENANT_ID.toString())
                        .param("limit", "10")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void summaryInvalidLimitReturns400() throws Exception {
        when(summaryReadFacade.getSummaryList(eq(TENANT_ID), eq(0), any()))
                .thenThrow(new IllegalArgumentException(
                        "limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        mockMvc.perform(get("/api/admin/work-items/summary")
                        .param("tenantId", TENANT_ID.toString())
                        .param("limit", "0")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void summaryMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/summary")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    // ========== Support details endpoint tests ==========

    @Test
    void supportDetailsReturnsCorrectCombinedResponse() throws Exception {
        WorkItem workItem = new WorkItem(
                TENANT_ID, WORK_ITEM_CODE, WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Login xato", "BUGS", OWNER_USER_ID);
        workItem.setPriorityCode("HIGH");
        workItem.assignOwner(OWNER_USER_ID);

        WorkItemUpdate update = new WorkItemUpdate(
                TENANT_ID, workItem.getId(), AUTHOR_USER_ID,
                UpdateType.COMMENT, "Tekshirilmoqda");

        var workItemView = new WorkItemDetailsFacade.WorkItemDetailsView(
                workItem, List.of(update));

        UUID attemptId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        UUID chatBindingId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        Instant attemptTime = Instant.parse("2026-03-18T10:00:00Z");

        TelegramDeliveryAttempt attempt = TelegramDeliveryAttempt.reconstruct(
                attemptId, attemptTime, TENANT_ID, WORK_ITEM_ID,
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                chatBindingId, 42L,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                99001L, null, null);

        TelegramDeliveryMetricsSnapshot snapshot = TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, WORK_ITEM_ID,
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                null, true);

        var observabilityView = new TelegramDeliveryObservabilityDetailsView(
                WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                snapshot, List.of(attempt));

        var supportView = new WorkItemSupportDetailsFacade.WorkItemSupportDetailsView(
                workItemView, observabilityView);

        when(supportDetailsReadFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, 10, ACTOR_USER_ID))
                .thenReturn(supportView);

        mockMvc.perform(get("/api/admin/work-items/support-details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", WORK_ITEM_CODE)
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                // workItem section
                .andExpect(jsonPath("$.workItem.workItemId").value(workItem.getId().toString()))
                .andExpect(jsonPath("$.workItem.workItemCode").value(WORK_ITEM_CODE))
                .andExpect(jsonPath("$.workItem.title").value("Login xato"))
                .andExpect(jsonPath("$.workItem.typeCode").value("BUG"))
                .andExpect(jsonPath("$.workItem.currentStatusCode").value("BUGS"))
                .andExpect(jsonPath("$.workItem.priorityCode").value("HIGH"))
                .andExpect(jsonPath("$.workItem.currentOwnerUserId").value(OWNER_USER_ID.toString()))
                .andExpect(jsonPath("$.workItem.updates", hasSize(1)))
                .andExpect(jsonPath("$.workItem.updates[0].updateTypeCode").value("COMMENT"))
                .andExpect(jsonPath("$.workItem.updates[0].body").value("Tekshirilmoqda"))
                // deliveryObservability section
                .andExpect(jsonPath("$.deliveryObservability.workItemId").value(WORK_ITEM_ID.toString()))
                .andExpect(jsonPath("$.deliveryObservability.workItemCode").value(WORK_ITEM_CODE))
                .andExpect(jsonPath("$.deliveryObservability.latestMetrics.success").value(true))
                .andExpect(jsonPath("$.deliveryObservability.latestMetrics.deliveryOutcome").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveryObservability.recentAttempts", hasSize(1)))
                .andExpect(jsonPath("$.deliveryObservability.recentAttempts[0].attemptId").value(attemptId.toString()))
                .andExpect(jsonPath("$.deliveryObservability.recentAttempts[0].operation").value("SEND_NEW_MESSAGE"))
                .andExpect(jsonPath("$.deliveryObservability.recentAttempts[0].deliveryOutcome").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveryObservability.recentAttempts[0].success").value(true));
    }

    @Test
    void supportDetailsDefaultHistoryLimitIsUsed() throws Exception {
        WorkItem workItem = new WorkItem(
                TENANT_ID, WORK_ITEM_CODE, WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Login xato", "BUGS", OWNER_USER_ID);

        var workItemView = new WorkItemDetailsFacade.WorkItemDetailsView(workItem, List.of());

        TelegramDeliveryMetricsSnapshot snapshot =
                TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WORK_ITEM_ID);
        var observabilityView = new TelegramDeliveryObservabilityDetailsView(
                WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                snapshot, List.of());

        var supportView = new WorkItemSupportDetailsFacade.WorkItemSupportDetailsView(
                workItemView, observabilityView);

        // default historyLimit=10 ishlatilishi kerak
        when(supportDetailsReadFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, 10, ACTOR_USER_ID))
                .thenReturn(supportView);

        mockMvc.perform(get("/api/admin/work-items/support-details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", WORK_ITEM_CODE)
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workItem.workItemId").value(workItem.getId().toString()))
                .andExpect(jsonPath("$.deliveryObservability.latestMetrics.empty").value(true));
    }

    @Test
    void supportDetailsNotFoundReturns404() throws Exception {
        when(supportDetailsReadFacade.getDetails(eq(TENANT_ID), eq("NONEXISTENT-99"), eq(10), any()))
                .thenThrow(new ResourceNotFoundException("WorkItem", "NONEXISTENT-99"));

        mockMvc.perform(get("/api/admin/work-items/support-details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", "NONEXISTENT-99")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void supportDetailsInvalidHistoryLimitReturns400() throws Exception {
        when(supportDetailsReadFacade.getDetails(eq(TENANT_ID), eq(WORK_ITEM_CODE), eq(0), any()))
                .thenThrow(new IllegalArgumentException(
                        "historyLimit 1..50 oralig'ida bo'lishi kerak"));

        mockMvc.perform(get("/api/admin/work-items/support-details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", WORK_ITEM_CODE)
                        .param("historyLimit", "0")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void supportDetailsMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/support-details")
                        .param("workItemCode", WORK_ITEM_CODE)
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void supportDetailsMissingWorkItemCodeReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/support-details")
                        .param("tenantId", TENANT_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    // ========== Support summary endpoint tests ==========

    @Test
    void supportSummaryReturnsCorrectCombinedResponse() throws Exception {
        var wiSummary = new WorkItemSummaryItem(
                WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                "HIGH", "CRITICAL", OWNER_USER_ID,
                Instant.parse("2026-03-18T10:00:00Z"),
                Instant.parse("2026-03-18T11:00:00Z"),
                null, 0, false);

        TelegramDeliveryMetricsSnapshot snapshot = TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, WORK_ITEM_ID,
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                null, true);

        var delSummary = new DeliveryObservabilitySummaryItem(
                WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                snapshot);

        var composedItem = new WorkItemSupportSummaryItem(wiSummary, delSummary);

        when(supportSummaryFacade.getSummaryList(TENANT_ID, 20, ACTOR_USER_ID))
                .thenReturn(List.of(composedItem));

        mockMvc.perform(get("/api/admin/work-items/support-summary")
                        .param("tenantId", TENANT_ID.toString())
                        .param("limit", "20")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                // workItem section
                .andExpect(jsonPath("$.items[0].workItem.workItemId").value(WORK_ITEM_ID.toString()))
                .andExpect(jsonPath("$.items[0].workItem.workItemCode").value(WORK_ITEM_CODE))
                .andExpect(jsonPath("$.items[0].workItem.title").value("Login xato"))
                .andExpect(jsonPath("$.items[0].workItem.typeCode").value("BUG"))
                .andExpect(jsonPath("$.items[0].workItem.currentStatusCode").value("BUGS"))
                .andExpect(jsonPath("$.items[0].workItem.priorityCode").value("HIGH"))
                .andExpect(jsonPath("$.items[0].workItem.severityCode").value("CRITICAL"))
                .andExpect(jsonPath("$.items[0].workItem.currentOwnerUserId").value(OWNER_USER_ID.toString()))
                .andExpect(jsonPath("$.items[0].workItem.reopenedCount").value(0))
                .andExpect(jsonPath("$.items[0].workItem.archived").value(false))
                // deliveryObservability section
                .andExpect(jsonPath("$.items[0].deliveryObservability.workItemId").value(WORK_ITEM_ID.toString()))
                .andExpect(jsonPath("$.items[0].deliveryObservability.workItemCode").value(WORK_ITEM_CODE))
                .andExpect(jsonPath("$.items[0].deliveryObservability.latestMetrics.success").value(true))
                .andExpect(jsonPath("$.items[0].deliveryObservability.latestMetrics.deliveryOutcome").value("DELIVERED"))
                .andExpect(jsonPath("$.items[0].deliveryObservability.latestMetrics.empty").value(false));
    }

    @Test
    void supportSummaryDefaultLimitIsUsed() throws Exception {
        when(supportSummaryFacade.getSummaryList(TENANT_ID, 20, ACTOR_USER_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/work-items/support-summary")
                        .param("tenantId", TENANT_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void supportSummaryEmptyListReturns200() throws Exception {
        when(supportSummaryFacade.getSummaryList(TENANT_ID, 10, ACTOR_USER_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/work-items/support-summary")
                        .param("tenantId", TENANT_ID.toString())
                        .param("limit", "10")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void supportSummaryInvalidLimitReturns400() throws Exception {
        when(supportSummaryFacade.getSummaryList(eq(TENANT_ID), eq(0), any()))
                .thenThrow(new IllegalArgumentException(
                        "limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        mockMvc.perform(get("/api/admin/work-items/support-summary")
                        .param("tenantId", TENANT_ID.toString())
                        .param("limit", "0")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void supportSummaryMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/support-summary")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    // ========== Support details by-id endpoint tests ==========

    @Test
    void supportDetailsByIdReturnsCorrectCombinedResponse() throws Exception {
        WorkItem workItem = new WorkItem(
                TENANT_ID, WORK_ITEM_CODE, WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Login xato", "BUGS", OWNER_USER_ID);
        workItem.setPriorityCode("HIGH");
        workItem.assignOwner(OWNER_USER_ID);

        // Semantic consistency: ikkala section bir xil workItemId va workItemCode ishlatadi
        UUID consistentWorkItemId = workItem.getId();

        WorkItemUpdate update = new WorkItemUpdate(
                TENANT_ID, consistentWorkItemId, AUTHOR_USER_ID,
                UpdateType.COMMENT, "Tekshirilmoqda");

        var workItemView = new WorkItemDetailsFacade.WorkItemDetailsView(
                workItem, List.of(update));

        TelegramDeliveryMetricsSnapshot snapshot = TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, consistentWorkItemId,
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                null, true);

        var observabilityView = new TelegramDeliveryObservabilityDetailsView(
                consistentWorkItemId, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                snapshot, List.of());

        var supportView = new WorkItemSupportDetailsFacade.WorkItemSupportDetailsView(
                workItemView, observabilityView);

        when(supportDetailsByIdFacade.getDetails(TENANT_ID, consistentWorkItemId, 10, ACTOR_USER_ID))
                .thenReturn(supportView);

        mockMvc.perform(get("/api/admin/work-items/support-details/by-id")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemId", consistentWorkItemId.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                // workItem section
                .andExpect(jsonPath("$.workItem.workItemId").value(consistentWorkItemId.toString()))
                .andExpect(jsonPath("$.workItem.workItemCode").value(WORK_ITEM_CODE))
                .andExpect(jsonPath("$.workItem.title").value("Login xato"))
                .andExpect(jsonPath("$.workItem.typeCode").value("BUG"))
                .andExpect(jsonPath("$.workItem.priorityCode").value("HIGH"))
                .andExpect(jsonPath("$.workItem.updates", hasSize(1)))
                .andExpect(jsonPath("$.workItem.updates[0].updateTypeCode").value("COMMENT"))
                // deliveryObservability section
                .andExpect(jsonPath("$.deliveryObservability.workItemId").value(consistentWorkItemId.toString()))
                .andExpect(jsonPath("$.deliveryObservability.workItemCode").value(WORK_ITEM_CODE))
                .andExpect(jsonPath("$.deliveryObservability.latestMetrics.success").value(true))
                .andExpect(jsonPath("$.deliveryObservability.latestMetrics.deliveryOutcome").value("DELIVERED"));
    }

    @Test
    void supportDetailsByIdDefaultHistoryLimitIsUsed() throws Exception {
        WorkItem workItem = new WorkItem(
                TENANT_ID, WORK_ITEM_CODE, WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Login xato", "BUGS", OWNER_USER_ID);

        UUID consistentWorkItemId = workItem.getId();

        var workItemView = new WorkItemDetailsFacade.WorkItemDetailsView(workItem, List.of());

        TelegramDeliveryMetricsSnapshot snapshot =
                TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, consistentWorkItemId);
        var observabilityView = new TelegramDeliveryObservabilityDetailsView(
                consistentWorkItemId, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                snapshot, List.of());

        var supportView = new WorkItemSupportDetailsFacade.WorkItemSupportDetailsView(
                workItemView, observabilityView);

        // default historyLimit=10 ishlatilishi kerak
        when(supportDetailsByIdFacade.getDetails(TENANT_ID, consistentWorkItemId, 10, ACTOR_USER_ID))
                .thenReturn(supportView);

        mockMvc.perform(get("/api/admin/work-items/support-details/by-id")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemId", consistentWorkItemId.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                // Ikkala section bir xil workItemId ishlatishini isbotlash
                .andExpect(jsonPath("$.workItem.workItemId").value(consistentWorkItemId.toString()))
                .andExpect(jsonPath("$.deliveryObservability.workItemId").value(consistentWorkItemId.toString()))
                .andExpect(jsonPath("$.deliveryObservability.latestMetrics.empty").value(true));
    }

    @Test
    void supportDetailsByIdNotFoundReturns404() throws Exception {
        UUID unknownId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        when(supportDetailsByIdFacade.getDetails(eq(TENANT_ID), eq(unknownId), eq(10), any()))
                .thenThrow(new ResourceNotFoundException("WorkItem", unknownId));

        mockMvc.perform(get("/api/admin/work-items/support-details/by-id")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemId", unknownId.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void supportDetailsByIdInvalidHistoryLimitReturns400() throws Exception {
        when(supportDetailsByIdFacade.getDetails(eq(TENANT_ID), eq(WORK_ITEM_ID), eq(0), any()))
                .thenThrow(new IllegalArgumentException(
                        "historyLimit 1..50 oralig'ida bo'lishi kerak"));

        mockMvc.perform(get("/api/admin/work-items/support-details/by-id")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemId", WORK_ITEM_ID.toString())
                        .param("historyLimit", "0")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void supportDetailsByIdMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/support-details/by-id")
                        .param("workItemId", WORK_ITEM_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void supportDetailsByIdMissingWorkItemIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/support-details/by-id")
                        .param("tenantId", TENANT_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    // ========== Details by-id endpoint tests ==========

    @Test
    void detailsByIdReturnsCorrectResponse() throws Exception {
        WorkItem workItem = new WorkItem(
                TENANT_ID, WORK_ITEM_CODE, WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Login xato", "BUGS", OWNER_USER_ID);
        workItem.setPriorityCode("HIGH");
        workItem.setSeverityCode("CRITICAL");
        workItem.assignOwner(OWNER_USER_ID);

        UUID consistentId = workItem.getId();

        WorkItemUpdate update = new WorkItemUpdate(
                TENANT_ID, consistentId, AUTHOR_USER_ID,
                UpdateType.COMMENT, "Tekshirilmoqda");

        var view = new WorkItemDetailsFacade.WorkItemDetailsView(workItem, List.of(update));

        when(detailsByIdFacade.getDetails(TENANT_ID, consistentId, ACTOR_USER_ID))
                .thenReturn(view);

        mockMvc.perform(get("/api/admin/work-items/details/by-id")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemId", consistentId.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workItemId").value(consistentId.toString()))
                .andExpect(jsonPath("$.workItemCode").value(WORK_ITEM_CODE))
                .andExpect(jsonPath("$.title").value("Login xato"))
                .andExpect(jsonPath("$.typeCode").value("BUG"))
                .andExpect(jsonPath("$.currentStatusCode").value("BUGS"))
                .andExpect(jsonPath("$.priorityCode").value("HIGH"))
                .andExpect(jsonPath("$.severityCode").value("CRITICAL"))
                .andExpect(jsonPath("$.currentOwnerUserId").value(OWNER_USER_ID.toString()))
                .andExpect(jsonPath("$.reopenedCount").value(0))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.updates", hasSize(1)))
                .andExpect(jsonPath("$.updates[0].updateTypeCode").value("COMMENT"))
                .andExpect(jsonPath("$.updates[0].body").value("Tekshirilmoqda"));
    }

    @Test
    void detailsByIdNotFoundReturns404() throws Exception {
        UUID unknownId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        when(detailsByIdFacade.getDetails(eq(TENANT_ID), eq(unknownId), any()))
                .thenThrow(new ResourceNotFoundException("WorkItem", unknownId));

        mockMvc.perform(get("/api/admin/work-items/details/by-id")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemId", unknownId.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void detailsByIdMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/details/by-id")
                        .param("workItemId", WORK_ITEM_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detailsByIdMissingWorkItemIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/details/by-id")
                        .param("tenantId", TENANT_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    // ========== By-status endpoint tests ==========

    @Test
    void byStatusReturnsCorrectResponse() throws Exception {
        var item = new WorkItemSummaryItem(
                WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                "HIGH", "CRITICAL", OWNER_USER_ID,
                Instant.parse("2026-03-18T10:00:00Z"),
                Instant.parse("2026-03-18T11:00:00Z"),
                null, 0, false);

        when(summaryByStatusReadFacade.getSummaryList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/admin/work-items/by-status")
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
                .andExpect(jsonPath("$.items[0].priorityCode").value("HIGH"))
                .andExpect(jsonPath("$.items[0].severityCode").value("CRITICAL"))
                .andExpect(jsonPath("$.items[0].currentOwnerUserId").value(OWNER_USER_ID.toString()));
    }

    @Test
    void byStatusDefaultLimitIsUsed() throws Exception {
        when(summaryByStatusReadFacade.getSummaryList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/work-items/by-status")
                        .param("tenantId", TENANT_ID.toString())
                        .param("statusCode", "BUGS")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void byStatusEmptyListReturns200() throws Exception {
        when(summaryByStatusReadFacade.getSummaryList(TENANT_ID, "PROCESSING", 10, ACTOR_USER_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/work-items/by-status")
                        .param("tenantId", TENANT_ID.toString())
                        .param("statusCode", "PROCESSING")
                        .param("limit", "10")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void byStatusInvalidLimitReturns400() throws Exception {
        when(summaryByStatusReadFacade.getSummaryList(eq(TENANT_ID), eq("BUGS"), eq(0), any()))
                .thenThrow(new IllegalArgumentException(
                        "limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        mockMvc.perform(get("/api/admin/work-items/by-status")
                        .param("tenantId", TENANT_ID.toString())
                        .param("statusCode", "BUGS")
                        .param("limit", "0")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void byStatusMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/by-status")
                        .param("statusCode", "BUGS")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void byStatusMissingStatusCodeReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/by-status")
                        .param("tenantId", TENANT_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    // ========== By-owner endpoint tests ==========

    @Test
    void byOwnerReturnsCorrectResponse() throws Exception {
        var item = new WorkItemSummaryItem(
                WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                "HIGH", "CRITICAL", OWNER_USER_ID,
                Instant.parse("2026-03-18T10:00:00Z"),
                Instant.parse("2026-03-18T11:00:00Z"),
                null, 0, false);

        when(summaryByOwnerReadFacade.getSummaryList(TENANT_ID, OWNER_USER_ID, 20, ACTOR_USER_ID))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/admin/work-items/by-owner")
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
                .andExpect(jsonPath("$.items[0].priorityCode").value("HIGH"))
                .andExpect(jsonPath("$.items[0].currentOwnerUserId").value(OWNER_USER_ID.toString()));
    }

    @Test
    void byOwnerDefaultLimitIsUsed() throws Exception {
        when(summaryByOwnerReadFacade.getSummaryList(TENANT_ID, OWNER_USER_ID, 20, ACTOR_USER_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/work-items/by-owner")
                        .param("tenantId", TENANT_ID.toString())
                        .param("ownerUserId", OWNER_USER_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void byOwnerEmptyListReturns200() throws Exception {
        when(summaryByOwnerReadFacade.getSummaryList(TENANT_ID, OWNER_USER_ID, 10, ACTOR_USER_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/work-items/by-owner")
                        .param("tenantId", TENANT_ID.toString())
                        .param("ownerUserId", OWNER_USER_ID.toString())
                        .param("limit", "10")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void byOwnerInvalidLimitReturns400() throws Exception {
        when(summaryByOwnerReadFacade.getSummaryList(eq(TENANT_ID), eq(OWNER_USER_ID), eq(0), any()))
                .thenThrow(new IllegalArgumentException(
                        "limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        mockMvc.perform(get("/api/admin/work-items/by-owner")
                        .param("tenantId", TENANT_ID.toString())
                        .param("ownerUserId", OWNER_USER_ID.toString())
                        .param("limit", "0")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void byOwnerMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/by-owner")
                        .param("ownerUserId", OWNER_USER_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void byOwnerMissingOwnerUserIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/by-owner")
                        .param("tenantId", TENANT_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    // ========== Support summary by-status endpoint tests ==========

    @Test
    void supportSummaryByStatusReturnsCorrectCombinedResponse() throws Exception {
        var wiSummary = new WorkItemSummaryItem(
                WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                "HIGH", "CRITICAL", OWNER_USER_ID,
                Instant.parse("2026-03-18T10:00:00Z"),
                Instant.parse("2026-03-18T11:00:00Z"),
                null, 0, false);

        TelegramDeliveryMetricsSnapshot snapshot = TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, WORK_ITEM_ID,
                com.engops.platform.telegram.TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                com.engops.platform.telegram.TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                null, true);

        var delSummary = new DeliveryObservabilitySummaryItem(
                WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                snapshot);

        var composedItem = new WorkItemSupportSummaryItem(wiSummary, delSummary);

        when(supportSummaryByStatusFacade.getSummaryList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID))
                .thenReturn(List.of(composedItem));

        mockMvc.perform(get("/api/admin/work-items/support-summary/by-status")
                        .param("tenantId", TENANT_ID.toString())
                        .param("statusCode", "BUGS")
                        .param("limit", "20")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].workItem.workItemId").value(WORK_ITEM_ID.toString()))
                .andExpect(jsonPath("$.items[0].workItem.workItemCode").value(WORK_ITEM_CODE))
                .andExpect(jsonPath("$.items[0].workItem.typeCode").value("BUG"))
                .andExpect(jsonPath("$.items[0].workItem.currentStatusCode").value("BUGS"))
                .andExpect(jsonPath("$.items[0].workItem.priorityCode").value("HIGH"))
                .andExpect(jsonPath("$.items[0].deliveryObservability.workItemId").value(WORK_ITEM_ID.toString()))
                .andExpect(jsonPath("$.items[0].deliveryObservability.latestMetrics.success").value(true))
                .andExpect(jsonPath("$.items[0].deliveryObservability.latestMetrics.deliveryOutcome").value("DELIVERED"))
                .andExpect(jsonPath("$.items[0].deliveryObservability.latestMetrics.empty").value(false));
    }

    @Test
    void supportSummaryByStatusDefaultLimitIsUsed() throws Exception {
        when(supportSummaryByStatusFacade.getSummaryList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/work-items/support-summary/by-status")
                        .param("tenantId", TENANT_ID.toString())
                        .param("statusCode", "BUGS")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void supportSummaryByStatusEmptyListReturns200() throws Exception {
        when(supportSummaryByStatusFacade.getSummaryList(TENANT_ID, "PROCESSING", 10, ACTOR_USER_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/work-items/support-summary/by-status")
                        .param("tenantId", TENANT_ID.toString())
                        .param("statusCode", "PROCESSING")
                        .param("limit", "10")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void supportSummaryByStatusInvalidLimitReturns400() throws Exception {
        when(supportSummaryByStatusFacade.getSummaryList(eq(TENANT_ID), eq("BUGS"), eq(0), any()))
                .thenThrow(new IllegalArgumentException(
                        "limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        mockMvc.perform(get("/api/admin/work-items/support-summary/by-status")
                        .param("tenantId", TENANT_ID.toString())
                        .param("statusCode", "BUGS")
                        .param("limit", "0")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void supportSummaryByStatusMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/support-summary/by-status")
                        .param("statusCode", "BUGS")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void supportSummaryByStatusMissingStatusCodeReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/support-summary/by-status")
                        .param("tenantId", TENANT_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    // ========== Support summary by-owner endpoint tests ==========

    @Test
    void supportSummaryByOwnerReturnsCorrectCombinedResponse() throws Exception {
        var wiSummary = new WorkItemSummaryItem(
                WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                "HIGH", "CRITICAL", OWNER_USER_ID,
                Instant.parse("2026-03-18T10:00:00Z"),
                Instant.parse("2026-03-18T11:00:00Z"),
                null, 0, false);

        TelegramDeliveryMetricsSnapshot snapshot = TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, WORK_ITEM_ID,
                com.engops.platform.telegram.TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                com.engops.platform.telegram.TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                null, true);

        var delSummary = new DeliveryObservabilitySummaryItem(
                WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                snapshot);

        var composedItem = new WorkItemSupportSummaryItem(wiSummary, delSummary);

        when(supportSummaryByOwnerFacade.getSummaryList(TENANT_ID, OWNER_USER_ID, 20, ACTOR_USER_ID))
                .thenReturn(List.of(composedItem));

        mockMvc.perform(get("/api/admin/work-items/support-summary/by-owner")
                        .param("tenantId", TENANT_ID.toString())
                        .param("ownerUserId", OWNER_USER_ID.toString())
                        .param("limit", "20")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].workItem.workItemId").value(WORK_ITEM_ID.toString()))
                .andExpect(jsonPath("$.items[0].workItem.workItemCode").value(WORK_ITEM_CODE))
                .andExpect(jsonPath("$.items[0].workItem.typeCode").value("BUG"))
                .andExpect(jsonPath("$.items[0].workItem.currentStatusCode").value("BUGS"))
                .andExpect(jsonPath("$.items[0].workItem.currentOwnerUserId").value(OWNER_USER_ID.toString()))
                .andExpect(jsonPath("$.items[0].deliveryObservability.workItemId").value(WORK_ITEM_ID.toString()))
                .andExpect(jsonPath("$.items[0].deliveryObservability.latestMetrics.success").value(true))
                .andExpect(jsonPath("$.items[0].deliveryObservability.latestMetrics.deliveryOutcome").value("DELIVERED"))
                .andExpect(jsonPath("$.items[0].deliveryObservability.latestMetrics.empty").value(false));
    }

    @Test
    void supportSummaryByOwnerDefaultLimitIsUsed() throws Exception {
        when(supportSummaryByOwnerFacade.getSummaryList(TENANT_ID, OWNER_USER_ID, 20, ACTOR_USER_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/work-items/support-summary/by-owner")
                        .param("tenantId", TENANT_ID.toString())
                        .param("ownerUserId", OWNER_USER_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void supportSummaryByOwnerEmptyListReturns200() throws Exception {
        when(supportSummaryByOwnerFacade.getSummaryList(TENANT_ID, OWNER_USER_ID, 10, ACTOR_USER_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/work-items/support-summary/by-owner")
                        .param("tenantId", TENANT_ID.toString())
                        .param("ownerUserId", OWNER_USER_ID.toString())
                        .param("limit", "10")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void supportSummaryByOwnerInvalidLimitReturns400() throws Exception {
        when(supportSummaryByOwnerFacade.getSummaryList(eq(TENANT_ID), eq(OWNER_USER_ID), eq(0), any()))
                .thenThrow(new IllegalArgumentException(
                        "limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        mockMvc.perform(get("/api/admin/work-items/support-summary/by-owner")
                        .param("tenantId", TENANT_ID.toString())
                        .param("ownerUserId", OWNER_USER_ID.toString())
                        .param("limit", "0")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void supportSummaryByOwnerMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/support-summary/by-owner")
                        .param("ownerUserId", OWNER_USER_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void supportSummaryByOwnerMissingOwnerUserIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/support-summary/by-owner")
                        .param("tenantId", TENANT_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    // ========== Support details by-status endpoint tests ==========

    @Test
    void supportDetailsByStatusReturnsCorrectCombinedResponse() throws Exception {
        WorkItem workItem = new WorkItem(
                TENANT_ID, WORK_ITEM_CODE, WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Login xato", "BUGS", OWNER_USER_ID);
        workItem.setPriorityCode("HIGH");
        workItem.setSeverityCode("CRITICAL");
        workItem.assignOwner(OWNER_USER_ID);

        // Semantic consistency: ikkala section bir xil workItemId va workItemCode ishlatadi
        UUID consistentWorkItemId = workItem.getId();

        WorkItemUpdate update = new WorkItemUpdate(
                TENANT_ID, consistentWorkItemId, AUTHOR_USER_ID,
                UpdateType.COMMENT, "Tekshirilmoqda");

        var workItemView = new WorkItemDetailsFacade.WorkItemDetailsView(
                workItem, List.of(update));

        UUID attemptId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        UUID chatBindingId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        Instant attemptTime = Instant.parse("2026-03-18T10:00:00Z");

        TelegramDeliveryAttempt attempt = TelegramDeliveryAttempt.reconstruct(
                attemptId, attemptTime, TENANT_ID, consistentWorkItemId,
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                chatBindingId, 42L,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                99001L, null, null);

        TelegramDeliveryMetricsSnapshot snapshot = TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, consistentWorkItemId,
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                null, true);

        var observabilityView = new TelegramDeliveryObservabilityDetailsView(
                consistentWorkItemId, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                snapshot, List.of(attempt));

        var supportView = new WorkItemSupportDetailsFacade.WorkItemSupportDetailsView(
                workItemView, observabilityView);

        when(supportDetailsByStatusFacade.getDetailsList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID))
                .thenReturn(List.of(supportView));

        mockMvc.perform(get("/api/admin/work-items/support-details/by-status")
                        .param("tenantId", TENANT_ID.toString())
                        .param("statusCode", "BUGS")
                        .param("limit", "20")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                // workItem section — full details level
                .andExpect(jsonPath("$.items[0].workItem.workItemId").value(consistentWorkItemId.toString()))
                .andExpect(jsonPath("$.items[0].workItem.workItemCode").value(WORK_ITEM_CODE))
                .andExpect(jsonPath("$.items[0].workItem.title").value("Login xato"))
                .andExpect(jsonPath("$.items[0].workItem.typeCode").value("BUG"))
                .andExpect(jsonPath("$.items[0].workItem.currentStatusCode").value("BUGS"))
                .andExpect(jsonPath("$.items[0].workItem.priorityCode").value("HIGH"))
                .andExpect(jsonPath("$.items[0].workItem.severityCode").value("CRITICAL"))
                .andExpect(jsonPath("$.items[0].workItem.currentOwnerUserId").value(OWNER_USER_ID.toString()))
                .andExpect(jsonPath("$.items[0].workItem.reopenedCount").value(0))
                .andExpect(jsonPath("$.items[0].workItem.archived").value(false))
                .andExpect(jsonPath("$.items[0].workItem.updates", hasSize(1)))
                .andExpect(jsonPath("$.items[0].workItem.updates[0].updateTypeCode").value("COMMENT"))
                .andExpect(jsonPath("$.items[0].workItem.updates[0].body").value("Tekshirilmoqda"))
                // deliveryObservability section — details level with recent attempts
                .andExpect(jsonPath("$.items[0].deliveryObservability.workItemId").value(consistentWorkItemId.toString()))
                .andExpect(jsonPath("$.items[0].deliveryObservability.workItemCode").value(WORK_ITEM_CODE))
                .andExpect(jsonPath("$.items[0].deliveryObservability.latestMetrics.success").value(true))
                .andExpect(jsonPath("$.items[0].deliveryObservability.latestMetrics.deliveryOutcome").value("DELIVERED"))
                .andExpect(jsonPath("$.items[0].deliveryObservability.recentAttempts", hasSize(1)))
                .andExpect(jsonPath("$.items[0].deliveryObservability.recentAttempts[0].attemptId").value(attemptId.toString()))
                .andExpect(jsonPath("$.items[0].deliveryObservability.recentAttempts[0].operation").value("SEND_NEW_MESSAGE"))
                .andExpect(jsonPath("$.items[0].deliveryObservability.recentAttempts[0].deliveryOutcome").value("DELIVERED"))
                .andExpect(jsonPath("$.items[0].deliveryObservability.recentAttempts[0].success").value(true))
                // Identity consistency: same workItemId across both sections
                .andExpect(jsonPath("$.items[0].workItem.workItemId").value(consistentWorkItemId.toString()))
                .andExpect(jsonPath("$.items[0].deliveryObservability.workItemId").value(consistentWorkItemId.toString()));
    }

    @Test
    void supportDetailsByStatusDefaultLimitIsUsed() throws Exception {
        when(supportDetailsByStatusFacade.getDetailsList(TENANT_ID, "BUGS", 20, ACTOR_USER_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/work-items/support-details/by-status")
                        .param("tenantId", TENANT_ID.toString())
                        .param("statusCode", "BUGS")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void supportDetailsByStatusEmptyListReturns200() throws Exception {
        when(supportDetailsByStatusFacade.getDetailsList(TENANT_ID, "PROCESSING", 10, ACTOR_USER_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/work-items/support-details/by-status")
                        .param("tenantId", TENANT_ID.toString())
                        .param("statusCode", "PROCESSING")
                        .param("limit", "10")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void supportDetailsByStatusInvalidLimitReturns400() throws Exception {
        when(supportDetailsByStatusFacade.getDetailsList(eq(TENANT_ID), eq("BUGS"), eq(0), any()))
                .thenThrow(new IllegalArgumentException(
                        "limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        mockMvc.perform(get("/api/admin/work-items/support-details/by-status")
                        .param("tenantId", TENANT_ID.toString())
                        .param("statusCode", "BUGS")
                        .param("limit", "0")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void supportDetailsByStatusMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/support-details/by-status")
                        .param("statusCode", "BUGS")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void supportDetailsByStatusMissingStatusCodeReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/support-details/by-status")
                        .param("tenantId", TENANT_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    // ========== Support details by-owner endpoint tests ==========

    @Test
    void supportDetailsByOwnerReturnsCorrectCombinedResponse() throws Exception {
        WorkItem workItem = new WorkItem(
                TENANT_ID, WORK_ITEM_CODE, WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Login xato", "BUGS", OWNER_USER_ID);
        workItem.setPriorityCode("HIGH");
        workItem.setSeverityCode("CRITICAL");
        workItem.assignOwner(OWNER_USER_ID);

        UUID consistentWorkItemId = workItem.getId();

        WorkItemUpdate update = new WorkItemUpdate(
                TENANT_ID, consistentWorkItemId, AUTHOR_USER_ID,
                UpdateType.COMMENT, "Tekshirilmoqda");

        var workItemView = new WorkItemDetailsFacade.WorkItemDetailsView(
                workItem, List.of(update));

        UUID attemptId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        UUID chatBindingId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        Instant attemptTime = Instant.parse("2026-03-18T10:00:00Z");

        TelegramDeliveryAttempt attempt = TelegramDeliveryAttempt.reconstruct(
                attemptId, attemptTime, TENANT_ID, consistentWorkItemId,
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                chatBindingId, 42L,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                99001L, null, null);

        TelegramDeliveryMetricsSnapshot snapshot = TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, consistentWorkItemId,
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                null, true);

        var observabilityView = new TelegramDeliveryObservabilityDetailsView(
                consistentWorkItemId, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                snapshot, List.of(attempt));

        var supportView = new WorkItemSupportDetailsFacade.WorkItemSupportDetailsView(
                workItemView, observabilityView);

        when(supportDetailsByOwnerFacade.getDetailsList(TENANT_ID, OWNER_USER_ID, 20, ACTOR_USER_ID))
                .thenReturn(List.of(supportView));

        mockMvc.perform(get("/api/admin/work-items/support-details/by-owner")
                        .param("tenantId", TENANT_ID.toString())
                        .param("ownerUserId", OWNER_USER_ID.toString())
                        .param("limit", "20")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                // workItem section — full details level
                .andExpect(jsonPath("$.items[0].workItem.workItemId").value(consistentWorkItemId.toString()))
                .andExpect(jsonPath("$.items[0].workItem.workItemCode").value(WORK_ITEM_CODE))
                .andExpect(jsonPath("$.items[0].workItem.title").value("Login xato"))
                .andExpect(jsonPath("$.items[0].workItem.typeCode").value("BUG"))
                .andExpect(jsonPath("$.items[0].workItem.currentStatusCode").value("BUGS"))
                .andExpect(jsonPath("$.items[0].workItem.priorityCode").value("HIGH"))
                .andExpect(jsonPath("$.items[0].workItem.severityCode").value("CRITICAL"))
                .andExpect(jsonPath("$.items[0].workItem.currentOwnerUserId").value(OWNER_USER_ID.toString()))
                .andExpect(jsonPath("$.items[0].workItem.reopenedCount").value(0))
                .andExpect(jsonPath("$.items[0].workItem.archived").value(false))
                .andExpect(jsonPath("$.items[0].workItem.updates", hasSize(1)))
                .andExpect(jsonPath("$.items[0].workItem.updates[0].updateTypeCode").value("COMMENT"))
                .andExpect(jsonPath("$.items[0].workItem.updates[0].body").value("Tekshirilmoqda"))
                // deliveryObservability section — details level with recent attempts
                .andExpect(jsonPath("$.items[0].deliveryObservability.workItemId").value(consistentWorkItemId.toString()))
                .andExpect(jsonPath("$.items[0].deliveryObservability.workItemCode").value(WORK_ITEM_CODE))
                .andExpect(jsonPath("$.items[0].deliveryObservability.latestMetrics.success").value(true))
                .andExpect(jsonPath("$.items[0].deliveryObservability.latestMetrics.deliveryOutcome").value("DELIVERED"))
                .andExpect(jsonPath("$.items[0].deliveryObservability.recentAttempts", hasSize(1)))
                .andExpect(jsonPath("$.items[0].deliveryObservability.recentAttempts[0].attemptId").value(attemptId.toString()))
                .andExpect(jsonPath("$.items[0].deliveryObservability.recentAttempts[0].operation").value("SEND_NEW_MESSAGE"))
                .andExpect(jsonPath("$.items[0].deliveryObservability.recentAttempts[0].deliveryOutcome").value("DELIVERED"))
                .andExpect(jsonPath("$.items[0].deliveryObservability.recentAttempts[0].success").value(true))
                // Identity consistency: same workItemId across both sections
                .andExpect(jsonPath("$.items[0].workItem.workItemId").value(consistentWorkItemId.toString()))
                .andExpect(jsonPath("$.items[0].deliveryObservability.workItemId").value(consistentWorkItemId.toString()));
    }

    @Test
    void supportDetailsByOwnerDefaultLimitIsUsed() throws Exception {
        when(supportDetailsByOwnerFacade.getDetailsList(TENANT_ID, OWNER_USER_ID, 20, ACTOR_USER_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/work-items/support-details/by-owner")
                        .param("tenantId", TENANT_ID.toString())
                        .param("ownerUserId", OWNER_USER_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void supportDetailsByOwnerEmptyListReturns200() throws Exception {
        when(supportDetailsByOwnerFacade.getDetailsList(TENANT_ID, OWNER_USER_ID, 10, ACTOR_USER_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/work-items/support-details/by-owner")
                        .param("tenantId", TENANT_ID.toString())
                        .param("ownerUserId", OWNER_USER_ID.toString())
                        .param("limit", "10")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void supportDetailsByOwnerInvalidLimitReturns400() throws Exception {
        when(supportDetailsByOwnerFacade.getDetailsList(eq(TENANT_ID), eq(OWNER_USER_ID), eq(0), any()))
                .thenThrow(new IllegalArgumentException(
                        "limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        mockMvc.perform(get("/api/admin/work-items/support-details/by-owner")
                        .param("tenantId", TENANT_ID.toString())
                        .param("ownerUserId", OWNER_USER_ID.toString())
                        .param("limit", "0")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void supportDetailsByOwnerMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/support-details/by-owner")
                        .param("ownerUserId", OWNER_USER_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void supportDetailsByOwnerMissingOwnerUserIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/support-details/by-owner")
                        .param("tenantId", TENANT_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());
    }

    // ========== Authorization tests ==========

    @Test
    void summaryEndpointReturns403WhenAccessDenied() throws Exception {
        when(summaryReadFacade.getSummaryList(eq(TENANT_ID), eq(20), any()))
                .thenThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"));

        mockMvc.perform(get("/api/admin/work-items/summary")
                        .param("tenantId", TENANT_ID.toString())
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void detailsEndpointReturns403WhenAccessDenied() throws Exception {
        when(detailsReadFacade.getDetails(eq(TENANT_ID), eq(WORK_ITEM_CODE), any()))
                .thenThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"));

        mockMvc.perform(get("/api/admin/work-items/details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", WORK_ITEM_CODE)
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void supportDetailsEndpointReturns403WhenAccessDenied() throws Exception {
        when(supportDetailsReadFacade.getDetails(eq(TENANT_ID), eq(WORK_ITEM_CODE), eq(10), any()))
                .thenThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"));

        mockMvc.perform(get("/api/admin/work-items/support-details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", WORK_ITEM_CODE)
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void missingAuthenticatedActorReturns401UnauthorizedEnvelopeWithoutReachingFacade()
            throws Exception {
        // Phase 129 missing-actor coverage. Phase 146'da reject layer Spring
        // Security filter chain'iga ko'chdi; Phase 148'da default fallback
        // JsonAuthenticationEntryPoint bilan almashtirildi — anonymous principal
        // /api/** authenticated qoidasini buzganda 401 + UNAUTHORIZED envelope
        // (ApiErrorResponse) qaytariladi. Facade'ga yetmaslik invariant'i
        // saqlanadi.
        mockMvc.perform(get("/api/admin/work-items/summary")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verifyNoInteractions(detailsReadFacade);
        verifyNoInteractions(summaryReadFacade);
        verifyNoInteractions(supportDetailsReadFacade);
        verifyNoInteractions(supportSummaryFacade);
        verifyNoInteractions(supportDetailsByIdFacade);
        verifyNoInteractions(detailsByIdFacade);
        verifyNoInteractions(summaryByStatusReadFacade);
        verifyNoInteractions(summaryByOwnerReadFacade);
        verifyNoInteractions(supportSummaryByStatusFacade);
        verifyNoInteractions(supportSummaryByOwnerFacade);
        verifyNoInteractions(supportDetailsByStatusFacade);
        verifyNoInteractions(supportDetailsByOwnerFacade);
    }
}
