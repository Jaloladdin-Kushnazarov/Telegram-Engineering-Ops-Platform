package com.engops.platform.workflow;

import com.engops.platform.infrastructure.security.AuthenticatedActor;
import com.engops.platform.infrastructure.security.SecurityConfig;
import com.engops.platform.infrastructure.security.SecurityWebMvcConfig;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WorkflowTransitionController @WebMvcTest testlari.
 *
 * Tekshiruvlar:
 * - 200 success: response body to'g'ri map qilinadi
 * - empty body ({}) → service'ga uzatiladi va service'ning mavjud xulqini hurmat qiladi
 * - SAME_STATUS BusinessRuleException → 422 errorCode bilan
 * - INVALID_TRANSITION BusinessRuleException → 422 errorCode bilan
 * - ResourceNotFoundException (work item topilmadi) → 404
 * - Controller WorkflowTransitionService'ga aynan bir marta delegate qiladi va
 *   barcha argumentlar to'g'ri uzatiladi (tenantId, workItemId, targetStatusCode,
 *   actorUserId, actionSource, reason)
 * - Phase 135 migratsiyasi: actor identifikatori @CurrentActor orqali
 *   SecurityContext'dan olinadi; request body'dagi {@code actorUserId}
 *   e'tiborga olinmaydi (spoofing bekor qilingan). Authenticated actor
 *   bo'lmaganida resolver WorkflowTransitionService'ni chaqirmaydan 403
 *   qaytaradi. {@link SecurityWebMvcConfig} {@code @Import} qilinadi —
 *   {@code @CurrentActor} resolver shu konfiguratsiya orqali ro'yxatga olinadi.
 */
@WebMvcTest(WorkflowTransitionController.class)
@Import({SecurityConfig.class, SecurityWebMvcConfig.class})
class WorkflowTransitionControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORK_ITEM_ID =
            UUID.fromString("77777777-7777-7777-7777-777777777771");
    /**
     * Phase 135: SecurityContext'dagi authenticated actor — controller
     * WorkflowTransitionService.transition(...)'ga shu qiymatni uzatishi kerak.
     * {@link #SPOOFED_BODY_ACTOR_USER_ID}'dan ataylab farqli — request body'dagi
     * actorUserId field'i e'tiborga olinmasligini tasdiqlash uchun.
     */
    private static final UUID ACTOR_USER_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    /**
     * Phase 135: bu UUID faqat request JSON body'ga "spoof attempt" sifatida
     * yuboriladi. Controller uni jim e'tiborsiz qoldirishi kerak; service'ga
     * {@link #ACTOR_USER_ID} uzatiladi. Happy-path delegation test'i bu farqni
     * isbotlaydi.
     */
    private static final UUID SPOOFED_BODY_ACTOR_USER_ID =
            UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID WORKFLOW_DEFINITION_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222221");
    private static final UUID OWNER_USER_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaab");

    /**
     * Phase 135 helper: request attribute orqali SecurityContext'ga
     * {@link AuthenticatedActor} principal'ini o'rnatadi. Spring Security'ning
     * {@code SecurityContextHolderFilter} {@link RequestAttributeSecurityContextRepository}
     * orqali shu attribute'ni o'qiydi va {@link SecurityContextHolder}'ga
     * yuklaydi. Keyin {@code @CurrentActor} resolver principal'ni o'qib
     * controller method'iga {@code actorUserId} sifatida uzatadi.
     *
     * <p>{@code SecurityMockMvcRequestPostProcessors.authentication(...)} ham
     * shu effektni beradi, lekin {@code spring-security-test} dependency'sini
     * talab qiladi — bu phase'da {@code pom.xml} o'zgartirilmaydi, shuning
     * uchun Spring Security ichidagi public {@link RequestAttributeSecurityContextRepository}
     * API'sidan foydalanib effekt qo'lda yaratiladi (Phase 128/129/131/132/134
     * pattern'i bilan bir xil).</p>
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
    private WorkflowTransitionService workflowTransitionService;

    private WorkItem successWorkItem(String status) {
        WorkItem wi = org.mockito.Mockito.mock(WorkItem.class);
        when(wi.getId()).thenReturn(WORK_ITEM_ID);
        when(wi.getTenantId()).thenReturn(TENANT_ID);
        when(wi.getWorkItemCode()).thenReturn("BUG-1");
        when(wi.getTypeCode()).thenReturn(WorkItemType.BUG);
        when(wi.getTitle()).thenReturn("Login broken");
        when(wi.getCurrentStatusCode()).thenReturn(status);
        when(wi.getWorkflowDefinitionId()).thenReturn(WORKFLOW_DEFINITION_ID);
        when(wi.getCurrentOwnerUserId()).thenReturn(OWNER_USER_ID);
        when(wi.getLastTransitionAt()).thenReturn(Instant.parse("2026-04-29T10:00:00Z"));
        when(wi.getResolvedAt()).thenReturn(null);
        when(wi.getReopenedCount()).thenReturn(0);
        when(wi.getUpdatedAt()).thenReturn(Instant.parse("2026-04-29T10:00:00Z"));
        return wi;
    }

    @Test
    void transitionReturns200AndMapsResponseBody() throws Exception {
        WorkItem wi = successWorkItem("PROCESSING");
        when(workflowTransitionService.transition(
                eq(TENANT_ID), eq(WORK_ITEM_ID), eq("PROCESSING"),
                eq(ACTOR_USER_ID), eq("MANUAL"), any()))
                .thenReturn(wi);

        mockMvc.perform(post("/api/work-items/{workItemId}/transitions", WORK_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "targetStatusCode":"PROCESSING",
                                  "actorUserId":"%s",
                                  "actionSource":"MANUAL",
                                  "reason":"started investigation"
                                }
                                """.formatted(TENANT_ID, ACTOR_USER_ID))
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.workItemId").value(WORK_ITEM_ID.toString()))
                .andExpect(jsonPath("$.workItemCode").value("BUG-1"))
                .andExpect(jsonPath("$.typeCode").value("BUG"))
                .andExpect(jsonPath("$.title").value("Login broken"))
                .andExpect(jsonPath("$.currentStatusCode").value("PROCESSING"))
                .andExpect(jsonPath("$.workflowDefinitionId").value(WORKFLOW_DEFINITION_ID.toString()))
                .andExpect(jsonPath("$.currentOwnerUserId").value(OWNER_USER_ID.toString()))
                .andExpect(jsonPath("$.lastTransitionAt").value("2026-04-29T10:00:00Z"))
                .andExpect(jsonPath("$.reopenedCount").value(0))
                .andExpect(jsonPath("$.updatedAt").value("2026-04-29T10:00:00Z"))
                // resolvedAt null — JsonInclude(NON_NULL) tushiradi
                .andExpect(jsonPath("$.resolvedAt").doesNotExist());
    }

    @Test
    void transitionDelegatesExactlyOnceWithAuthenticatedActorIgnoringSpoofedBody() throws Exception {
        // Phase 135: body'dagi actorUserId (SPOOFED_BODY_ACTOR_USER_ID) ataylab
        // ACTOR_USER_ID'dan farqli yuborilgan. Controller body field'ni
        // e'tiborga olmasligi va WorkflowTransitionService.transition(...)'ga
        // SecurityContext'dagi ACTOR_USER_ID'ni uzatishi kerak. Bu spoofing
        // hujumi to'sib qo'yilganini tasdiqlovchi asosiy assertion.
        WorkItem wi = successWorkItem("PROCESSING");
        when(workflowTransitionService.transition(
                eq(TENANT_ID), eq(WORK_ITEM_ID), eq("PROCESSING"),
                eq(ACTOR_USER_ID), eq("TELEGRAM"), eq("started investigation")))
                .thenReturn(wi);

        mockMvc.perform(post("/api/work-items/{workItemId}/transitions", WORK_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "targetStatusCode":"PROCESSING",
                                  "actorUserId":"%s",
                                  "actionSource":"TELEGRAM",
                                  "reason":"started investigation"
                                }
                                """.formatted(TENANT_ID, SPOOFED_BODY_ACTOR_USER_ID))
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk());

        // Service ACTOR_USER_ID bilan chaqirilishi shart, SPOOFED_BODY_ACTOR_USER_ID bilan emas.
        verify(workflowTransitionService, times(1)).transition(
                eq(TENANT_ID), eq(WORK_ITEM_ID), eq("PROCESSING"),
                eq(ACTOR_USER_ID), eq("TELEGRAM"), eq("started investigation"));
        // Negative assertion: spoofed body UUID hech qachon service'ga uzatilmasligi shart.
        verify(workflowTransitionService, times(0)).transition(
                any(), any(), any(),
                eq(SPOOFED_BODY_ACTOR_USER_ID), any(), any());
    }

    @Test
    void transitionEmptyBodyReturns400WithoutDelegating() throws Exception {
        // Empty {} → barcha field'lar null. REST boundary tenantId null'da
        // 400 BAD_REQUEST qaytaradi va service hech qachon chaqirilmaydi.
        // Phase 135: authenticated actor mavjud bo'lishi shart, aks holda
        // resolver controller validation'idan oldin 403 qaytaradi.
        mockMvc.perform(post("/api/work-items/{workItemId}/transitions", WORK_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));

        verifyNoInteractions(workflowTransitionService);
    }

    @Test
    void transitionMissingTenantIdReturns400WithoutDelegating() throws Exception {
        mockMvc.perform(post("/api/work-items/{workItemId}/transitions", WORK_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetStatusCode":"PROCESSING",
                                  "actorUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(ACTOR_USER_ID))
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));

        verifyNoInteractions(workflowTransitionService);
    }

    @Test
    void transitionBlankTargetStatusCodeReturns400WithoutDelegating() throws Exception {
        mockMvc.perform(post("/api/work-items/{workItemId}/transitions", WORK_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "targetStatusCode":"   ",
                                  "actorUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID, ACTOR_USER_ID))
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));

        verifyNoInteractions(workflowTransitionService);
    }

    @Test
    void transitionBlankActionSourceReturns400WithoutDelegating() throws Exception {
        mockMvc.perform(post("/api/work-items/{workItemId}/transitions", WORK_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "targetStatusCode":"PROCESSING",
                                  "actorUserId":"%s",
                                  "actionSource":"  "
                                }
                                """.formatted(TENANT_ID, ACTOR_USER_ID))
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));

        verifyNoInteractions(workflowTransitionService);
    }

    @Test
    void transitionMalformedWorkItemIdReturns400() throws Exception {
        // @PathVariable UUID conversion fails before @CurrentActor resolves —
        // withActor mavjudligidan qat'i nazar 400 qaytariladi. Lekin Phase 135
        // pattern bilan moslashtirib withActor qo'shamiz: ham authenticated
        // request, ham malformed path tekshiruvi bir testda namoyish qilinsin.
        mockMvc.perform(post("/api/work-items/{workItemId}/transitions", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "targetStatusCode":"PROCESSING",
                                  "actorUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID, ACTOR_USER_ID))
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(workflowTransitionService);
    }

    @Test
    void transitionSameStatusBubblesAs422() throws Exception {
        when(workflowTransitionService.transition(
                eq(TENANT_ID), eq(WORK_ITEM_ID), eq("BUGS"),
                eq(ACTOR_USER_ID), eq("MANUAL"), any()))
                .thenThrow(new BusinessRuleException("SAME_STATUS",
                        "Work item allaqachon 'BUGS' holatida"));

        mockMvc.perform(post("/api/work-items/{workItemId}/transitions", WORK_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "targetStatusCode":"BUGS",
                                  "actorUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID, ACTOR_USER_ID))
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("SAME_STATUS"));
    }

    @Test
    void transitionInvalidTransitionBubblesAs422() throws Exception {
        when(workflowTransitionService.transition(
                eq(TENANT_ID), eq(WORK_ITEM_ID), eq("FIXED"),
                eq(ACTOR_USER_ID), eq("MANUAL"), any()))
                .thenThrow(new BusinessRuleException("INVALID_TRANSITION",
                        "'BUGS' dan 'FIXED' ga o'tish ruxsat etilmagan"));

        mockMvc.perform(post("/api/work-items/{workItemId}/transitions", WORK_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "targetStatusCode":"FIXED",
                                  "actorUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID, ACTOR_USER_ID))
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TRANSITION"));
    }

    @Test
    void transitionWorkItemNotFoundBubblesAs404() throws Exception {
        when(workflowTransitionService.transition(
                eq(TENANT_ID), eq(WORK_ITEM_ID), eq("PROCESSING"),
                eq(ACTOR_USER_ID), eq("MANUAL"), any()))
                .thenThrow(new ResourceNotFoundException("WorkItem", WORK_ITEM_ID));

        mockMvc.perform(post("/api/work-items/{workItemId}/transitions", WORK_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "targetStatusCode":"PROCESSING",
                                  "actorUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID, ACTOR_USER_ID))
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ========== Phase 135: missing-actor 403 coverage ==========

    /**
     * Phase 135 yangi qoplama: SecurityContext'da {@code AuthenticatedActor}
     * yo'q bo'lganda {@code @CurrentActor} resolver controller body'ga ham,
     * WorkflowTransitionService'ga ham yetib bormay 403 ACCESS_DENIED
     * qaytarishi kerak. Bu eski {@code transitionMissingActorUserIdReturns400}
     * test'ining o'rnini bosadi — endi body'dagi {@code actorUserId} jim
     * e'tiborga olinmaydi va REST darajasidagi 400 kontrakti yo'q. Pattern
     * Phase 128/129/131/132/134 missing-actor qoplamasi bilan bir xil.
     *
     * <p>Body'da {@code actorUserId} ataylab ko'rsatilgan — uning mavjudligi
     * 403 javobga ta'sir qilmasligini ko'rsatish uchun (spoofing yo'lga
     * qo'yilmaydi, autentifikatsiya yetishmasligi har doim eng birinchi
     * to'siq bo'lib qoladi).</p>
     */
    @Test
    void missingAuthenticatedActorOnTransitionReturns403WithoutReachingService() throws Exception {
        // Phase 146: filter-chain reject ({@code SecurityConfig} {@code /api/**}
        // authenticated qoidasi) controller advice'gacha yetmaydi, shuning uchun
        // {@code GlobalExceptionHandler}'ning {@code errorCode=ACCESS_DENIED}
        // envelope'i emit qilinmaydi. 403 status va service'ga yetmaslik
        // invariantlari saqlanadi.
        mockMvc.perform(post("/api/work-items/{workItemId}/transitions", WORK_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "targetStatusCode":"PROCESSING",
                                  "actorUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID, SPOOFED_BODY_ACTOR_USER_ID)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(workflowTransitionService);
    }

    /**
     * Phase 139: authenticated actor mavjud, lekin tenant'da WORK_ITEM_TRANSITION
     * ruxsati yo'q — WorkflowTransitionService AccessDeniedException tashlaydi
     * va GlobalExceptionHandler 403 ACCESS_DENIED ga aylantiradi. Controller
     * surface'i shu envelope'ni saqlaydi.
     */
    @Test
    void transitionReturns403WhenServiceThrowsAccessDenied() throws Exception {
        when(workflowTransitionService.transition(
                eq(TENANT_ID), eq(WORK_ITEM_ID), eq("PROCESSING"),
                eq(ACTOR_USER_ID), eq("MANUAL"), any()))
                .thenThrow(new AccessDeniedException(
                        "Bu operatsiya uchun WORK_ITEM_TRANSITION ruxsati talab qilinadi"));

        mockMvc.perform(post("/api/work-items/{workItemId}/transitions", WORK_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "targetStatusCode":"PROCESSING",
                                  "actorUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID, ACTOR_USER_ID))
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }
}
