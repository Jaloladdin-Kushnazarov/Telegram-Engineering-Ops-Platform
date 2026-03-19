package com.engops.platform.admin;

import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.workitem.model.UpdateType;
import com.engops.platform.workitem.model.Visibility;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import com.engops.platform.workitem.model.WorkItemUpdate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
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
 */
@WebMvcTest(WorkItemDetailsController.class)
class WorkItemDetailsControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORK_ITEM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WORKFLOW_DEF_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OWNER_USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID AUTHOR_USER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String WORK_ITEM_CODE = "BUG-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkItemDetailsFacade detailsFacade;

    @MockBean
    private WorkItemSummaryFacade summaryFacade;

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
        when(detailsFacade.getDetails(TENANT_ID, WORK_ITEM_CODE)).thenReturn(view);

        mockMvc.perform(get("/api/admin/work-items/details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", WORK_ITEM_CODE))
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
        when(detailsFacade.getDetails(TENANT_ID, WORK_ITEM_CODE)).thenReturn(view);

        mockMvc.perform(get("/api/admin/work-items/details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", WORK_ITEM_CODE))
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
        when(detailsFacade.getDetails(TENANT_ID, WORK_ITEM_CODE)).thenReturn(view);

        mockMvc.perform(get("/api/admin/work-items/details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", WORK_ITEM_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updates", hasSize(2)))
                .andExpect(jsonPath("$.updates[0].updateTypeCode").value("COMMENT"))
                .andExpect(jsonPath("$.updates[0].body").value("Birinchi izoh"))
                .andExpect(jsonPath("$.updates[1].updateTypeCode").value("STATUS_CHANGE"))
                .andExpect(jsonPath("$.updates[1].body").value("PROCESSING ga o'tkazildi"));
    }

    @Test
    void workItemNotFoundReturns404() throws Exception {
        when(detailsFacade.getDetails(TENANT_ID, "NONEXISTENT-99"))
                .thenThrow(new ResourceNotFoundException("WorkItem", "NONEXISTENT-99"));

        mockMvc.perform(get("/api/admin/work-items/details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", "NONEXISTENT-99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void invalidWorkItemCodeReturns400() throws Exception {
        when(detailsFacade.getDetails(TENANT_ID, ""))
                .thenThrow(new IllegalArgumentException(
                        "workItemCode null yoki bo'sh bo'lishi mumkin emas"));

        mockMvc.perform(get("/api/admin/work-items/details")
                        .param("tenantId", TENANT_ID.toString())
                        .param("workItemCode", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/details")
                        .param("workItemCode", WORK_ITEM_CODE))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingWorkItemCodeReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/details")
                        .param("tenantId", TENANT_ID.toString()))
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

        when(summaryFacade.getSummaryList(TENANT_ID, 20)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/admin/work-items/summary")
                        .param("tenantId", TENANT_ID.toString())
                        .param("limit", "20"))
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
        when(summaryFacade.getSummaryList(TENANT_ID, 20)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/work-items/summary")
                        .param("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void summaryEmptyListReturns200() throws Exception {
        when(summaryFacade.getSummaryList(TENANT_ID, 10)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/work-items/summary")
                        .param("tenantId", TENANT_ID.toString())
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void summaryInvalidLimitReturns400() throws Exception {
        when(summaryFacade.getSummaryList(TENANT_ID, 0))
                .thenThrow(new IllegalArgumentException(
                        "limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        mockMvc.perform(get("/api/admin/work-items/summary")
                        .param("tenantId", TENANT_ID.toString())
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void summaryMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(get("/api/admin/work-items/summary"))
                .andExpect(status().isBadRequest());
    }
}
