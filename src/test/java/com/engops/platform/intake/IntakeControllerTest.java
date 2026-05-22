package com.engops.platform.intake;

import com.engops.platform.infrastructure.security.AuthenticatedActor;
import com.engops.platform.infrastructure.security.SecurityConfig;
import com.engops.platform.infrastructure.security.SecurityWebMvcConfig;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IntakeController @WebMvcTest testlari.
 *
 * Tekshiruvlar:
 * - 201 success: response body to'g'ri map qilinadi (routing fields ham)
 * - empty body ({}) → IntakeApplicationService.validateCommand orqali 422
 * - INTAKE_VALIDATION (BusinessRuleException) → 422 errorCode bilan
 * - ResourceNotFoundException → 404
 * - Noto'g'ri typeCode → 400 (controller'da WorkItemType.valueOf)
 * - Controller IntakeApplicationService'ga aynan bir marta delegate qiladi va
 *   IntakeCommand field'lari to'g'ri map qilinadi
 * - Phase 134 migratsiyasi: yaratuvchi identifikatori @CurrentActor orqali
 *   SecurityContext'dan olinadi; request body'dagi {@code createdByUserId}
 *   e'tiborga olinmaydi (spoofing bekor qilingan). Authenticated actor
 *   bo'lmaganida resolver IntakeApplicationService'ni chaqirmaydan 403
 *   qaytaradi. {@link SecurityWebMvcConfig} {@code @Import} qilinadi —
 *   {@code @CurrentActor} resolver shu konfiguratsiya orqali ro'yxatga olinadi.
 */
@WebMvcTest(IntakeController.class)
@Import({SecurityConfig.class, SecurityWebMvcConfig.class})
class IntakeControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORKFLOW_DEFINITION_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222221");
    /**
     * Phase 134: bu UUID endi faqat request JSON body'ga "spoof attempt"
     * sifatida yuboriladi. Controller uni jim e'tiborsiz qoldirishi kerak;
     * IntakeCommand'ga {@link #ACTOR_USER_ID} tushadi. Happy-path mapping
     * test'i bu farqni isbotlaydi.
     */
    private static final UUID CREATED_BY_USER_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    /**
     * Phase 134: SecurityContext'dagi authenticated actor — controller
     * IntakeCommand.createdByUserId sifatida shu qiymatni ishlatishi kerak.
     * {@link #CREATED_BY_USER_ID}'dan ataylab farqli — body field e'tiborga
     * olinmasligini tasdiqlash uchun.
     */
    private static final UUID ACTOR_USER_ID =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID WORK_ITEM_ID =
            UUID.fromString("77777777-7777-7777-7777-777777777771");
    private static final UUID ROUTING_RULE_ID =
            UUID.fromString("66666666-6666-6666-6666-666666666661");
    private static final UUID TOPIC_BINDING_ID =
            UUID.fromString("66666666-6666-6666-6666-666666666662");
    private static final UUID CHAT_BINDING_ID =
            UUID.fromString("66666666-6666-6666-6666-666666666663");

    /**
     * Phase 134 helper: request attribute orqali SecurityContext'ga
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
     * API'sidan foydalanib effekt qo'lda yaratiladi (Phase 128/129/131/132
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
    private IntakeApplicationService intakeApplicationService;

    @Test
    void submitReturns201AndMapsResponseIncludingRoutingFields() throws Exception {
        IntakeResult result = new IntakeResult(
                WORK_ITEM_ID,
                "BUG-1",
                "BUG",
                "Login broken",
                "BUGS",
                WORKFLOW_DEFINITION_ID,
                TENANT_ID,
                null, null,
                true,
                ROUTING_RULE_ID,
                TOPIC_BINDING_ID,
                CHAT_BINDING_ID,
                42L);

        when(intakeApplicationService.submit(any(IntakeCommand.class))).thenReturn(result);

        mockMvc.perform(post("/api/intake/work-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "typeCode":"BUG",
                                  "title":"Login broken",
                                  "description":"500 error",
                                  "workflowDefinitionId":"%s",
                                  "initialStatusCode":"BUGS",
                                  "createdByUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID, WORKFLOW_DEFINITION_ID, CREATED_BY_USER_ID))
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.workItemId").value(WORK_ITEM_ID.toString()))
                .andExpect(jsonPath("$.workItemCode").value("BUG-1"))
                .andExpect(jsonPath("$.typeCode").value("BUG"))
                .andExpect(jsonPath("$.title").value("Login broken"))
                .andExpect(jsonPath("$.currentStatusCode").value("BUGS"))
                .andExpect(jsonPath("$.workflowDefinitionId").value(WORKFLOW_DEFINITION_ID.toString()))
                .andExpect(jsonPath("$.routingPrepared").value(true))
                .andExpect(jsonPath("$.matchedRoutingRuleId").value(ROUTING_RULE_ID.toString()))
                .andExpect(jsonPath("$.targetTopicBindingId").value(TOPIC_BINDING_ID.toString()))
                .andExpect(jsonPath("$.targetChatBindingId").value(CHAT_BINDING_ID.toString()))
                .andExpect(jsonPath("$.targetTopicId").value(42));
    }

    @Test
    void submitOmitsRoutingFieldsWhenRoutingNotPrepared() throws Exception {
        IntakeResult result = new IntakeResult(
                WORK_ITEM_ID,
                "TASK-1",
                "TASK",
                "Cleanup logs",
                "OPEN",
                WORKFLOW_DEFINITION_ID,
                TENANT_ID,
                null, null,
                false,
                null, null, null, null);

        when(intakeApplicationService.submit(any(IntakeCommand.class))).thenReturn(result);

        mockMvc.perform(post("/api/intake/work-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "typeCode":"TASK",
                                  "title":"Cleanup logs",
                                  "createdByUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID, CREATED_BY_USER_ID))
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.routingPrepared").value(false))
                .andExpect(jsonPath("$.matchedRoutingRuleId").doesNotExist())
                .andExpect(jsonPath("$.targetTopicBindingId").doesNotExist())
                .andExpect(jsonPath("$.targetChatBindingId").doesNotExist())
                .andExpect(jsonPath("$.targetTopicId").doesNotExist());
    }

    @Test
    void submitDelegatesToApplicationServiceWithMappedCommandUsingAuthenticatedActor() throws Exception {
        // Phase 134: body'dagi createdByUserId (CREATED_BY_USER_ID) ataylab
        // ACTOR_USER_ID'dan farqli yuborilgan. Controller body field'ni
        // e'tiborga olmasligi va IntakeCommand.createdByUserId sifatida
        // SecurityContext'dagi ACTOR_USER_ID'ni ishlatishi kerak. Bu spoofing
        // hujumi to'sib qo'yilganini tasdiqlovchi asosiy assertion.
        IntakeResult result = new IntakeResult(
                WORK_ITEM_ID, "BUG-2", "BUG", "Title", "BUGS",
                WORKFLOW_DEFINITION_ID, TENANT_ID,
                null, null,
                false, null, null, null, null);
        when(intakeApplicationService.submit(any(IntakeCommand.class))).thenReturn(result);

        mockMvc.perform(post("/api/intake/work-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "typeCode":"BUG",
                                  "title":"Title",
                                  "description":"Body",
                                  "workflowDefinitionId":"%s",
                                  "initialStatusCode":"BUGS",
                                  "createdByUserId":"%s",
                                  "actionSource":"TELEGRAM"
                                }
                                """.formatted(TENANT_ID, WORKFLOW_DEFINITION_ID, CREATED_BY_USER_ID))
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isCreated());

        ArgumentCaptor<IntakeCommand> captor = ArgumentCaptor.forClass(IntakeCommand.class);
        verify(intakeApplicationService, times(1)).submit(captor.capture());
        IntakeCommand command = captor.getValue();
        assertThat(command.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(command.getTypeCode()).isEqualTo(WorkItemType.BUG);
        assertThat(command.getTitle()).isEqualTo("Title");
        assertThat(command.getDescription()).isEqualTo("Body");
        assertThat(command.getWorkflowDefinitionId()).isEqualTo(WORKFLOW_DEFINITION_ID);
        assertThat(command.getInitialStatusCode()).isEqualTo("BUGS");
        assertThat(command.getCreatedByUserId()).isEqualTo(ACTOR_USER_ID);
        assertThat(command.getCreatedByUserId()).isNotEqualTo(CREATED_BY_USER_ID);
        assertThat(command.getActionSource()).isEqualTo("TELEGRAM");
    }

    @Test
    void submitInvalidTypeCodeReturns400WithoutDelegating() throws Exception {
        mockMvc.perform(post("/api/intake/work-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "typeCode":"FEATURE",
                                  "title":"Some title",
                                  "createdByUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID, CREATED_BY_USER_ID))
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));

        verifyNoInteractions(intakeApplicationService);
    }

    @Test
    void submitIntakeValidationBubblesAs422() throws Exception {
        when(intakeApplicationService.submit(any(IntakeCommand.class)))
                .thenThrow(new BusinessRuleException("INTAKE_VALIDATION",
                        "title bo'sh bo'lishi mumkin emas"));

        mockMvc.perform(post("/api/intake/work-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "typeCode":"BUG",
                                  "title":"",
                                  "createdByUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID, CREATED_BY_USER_ID))
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INTAKE_VALIDATION"));
    }

    @Test
    void submitResourceNotFoundBubblesAs404() throws Exception {
        when(intakeApplicationService.submit(any(IntakeCommand.class)))
                .thenThrow(new ResourceNotFoundException("WorkflowDefinition", WORKFLOW_DEFINITION_ID));

        mockMvc.perform(post("/api/intake/work-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "typeCode":"BUG",
                                  "title":"Login broken",
                                  "workflowDefinitionId":"%s",
                                  "createdByUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID, WORKFLOW_DEFINITION_ID, CREATED_BY_USER_ID))
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void submitEmptyJsonBodyDelegatesAndBubblesValidation422() throws Exception {
        // Empty {} — Jackson barcha field'larni null qiladi.
        // Controller IntakeCommand'ni tuzadi va submit'ga uzatadi.
        // IntakeApplicationService.validateCommand "tenantId majburiy" → 422 INTAKE_VALIDATION.
        // Phase 134: authenticated actor mavjud bo'lishi shart, aks holda
        // resolver service chaqirilishidan oldin 403 qaytaradi va validation
        // semantikasi yo'qoladi.
        when(intakeApplicationService.submit(any(IntakeCommand.class)))
                .thenThrow(new BusinessRuleException("INTAKE_VALIDATION", "tenantId majburiy"));

        mockMvc.perform(post("/api/intake/work-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INTAKE_VALIDATION"));
    }

    // ========== Phase 134: missing-actor 403 coverage ==========

    /**
     * Phase 134 yangi qoplama: SecurityContext'da {@code AuthenticatedActor}
     * yo'q bo'lganda himoyalangan {@code /api/**} so'rovi rad etilishi va
     * IntakeApplicationService'ga umuman yetmasligi kerak.
     *
     * <p>Phase 146'da reject layer Spring Security filter chain'iga ko'chdi:
     * {@code SecurityConfig} {@code /api/**}'ni {@code authenticated()} deb
     * belgilaydi va @WebMvcTest slice'da JwtDecoder bean yo'q. Phase 148'dan
     * keyin {@code http.exceptionHandling} default fallback'ni
     * {@code JsonAuthenticationEntryPoint} bilan almashtiradi: anonymous
     * principal {@code authenticated()} qoidasini buzganda 401 + UNAUTHORIZED
     * envelope ({@code ApiErrorResponse}) qaytariladi. Facade'ga yetmaslik
     * invariant'i saqlanadi.</p>
     */
    @Test
    void missingAuthenticatedActorOnSubmitReturns401UnauthorizedEnvelopeWithoutReachingService()
            throws Exception {
        mockMvc.perform(post("/api/intake/work-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "typeCode":"BUG",
                                  "title":"Login broken",
                                  "createdByUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID, CREATED_BY_USER_ID)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verifyNoInteractions(intakeApplicationService);
    }

    /**
     * Phase 139: authenticated actor mavjud, lekin tenant'da WORK_ITEM_CREATE
     * ruxsati yo'q — IntakeApplicationService AccessDeniedException tashlaydi
     * va GlobalExceptionHandler 403 ACCESS_DENIED ga aylantiradi. Controller
     * surface'i shu envelope'ni saqlaydi.
     */
    @Test
    void submitReturns403WhenServiceThrowsAccessDenied() throws Exception {
        when(intakeApplicationService.submit(any(IntakeCommand.class)))
                .thenThrow(new AccessDeniedException(
                        "Bu operatsiya uchun WORK_ITEM_CREATE ruxsati talab qilinadi"));

        mockMvc.perform(post("/api/intake/work-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "typeCode":"BUG",
                                  "title":"Login broken",
                                  "createdByUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID, CREATED_BY_USER_ID))
                        .with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }
}
