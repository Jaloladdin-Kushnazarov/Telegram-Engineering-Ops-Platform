package com.engops.platform.identity.membership;

import com.engops.platform.infrastructure.security.AuthenticatedActor;
import com.engops.platform.infrastructure.security.SecurityConfig;
import com.engops.platform.infrastructure.security.SecurityWebMvcConfig;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 219a — {@link MemberRestController} @WebMvcTest testlari.
 *
 * <p>Actor SecurityContext'ga RequestAttribute pattern orqali o'rnatiladi
 * (TenantOnboardingControllerTest bilan bir xil). Exception mapping:
 * AccessDenied → 403, BusinessRule → 422, autentifikatsiyasiz → 401.</p>
 */
@WebMvcTest(MemberRestController.class)
@Import({SecurityConfig.class, SecurityWebMvcConfig.class})
class MemberRestControllerTest {

    private static final UUID ACTOR = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEMBER_USER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID NEW_MEMBERSHIP = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final String BASE = "/api/tenants/" + TENANT + "/members";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MembershipCommandService commandService;

    @MockBean
    private MembershipQueryService queryService;

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

    // ===== GET list =====

    @Test
    void list_authorized_returns200WithMembers() throws Exception {
        when(queryService.listMembers(ACTOR, TENANT)).thenReturn(List.of(
                new MemberSummary(MEMBER_USER, 555L, "Sariga", "sariga_tg",
                        "ENGINEER", "Engineer", "ACTIVE", Instant.parse("2026-05-10T00:00:00Z"))));

        mockMvc.perform(get(BASE).with(withActor(ACTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roleCode").value("ENGINEER"))
                .andExpect(jsonPath("$[0].displayName").value("Sariga"));
    }

    @Test
    void list_unauthorized_returns403() throws Exception {
        doThrow(new AccessDeniedException("Bu operatsiya uchun MEMBER_MANAGE ruxsati talab qilinadi"))
                .when(queryService).listMembers(ACTOR, TENANT);

        mockMvc.perform(get(BASE).with(withActor(ACTOR)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void list_anonymous_returns401() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(queryService);
    }

    // ===== POST invite =====

    @Test
    void invite_valid_returns201WithMembershipId() throws Exception {
        when(commandService.inviteMember(eq(ACTOR), eq(TENANT), any(InviteMemberRequest.class)))
                .thenReturn(NEW_MEMBERSHIP);

        mockMvc.perform(post(BASE).with(withActor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"telegramUserId":555111222,"displayName":"Sariga",
                                 "username":"sariga_tg","roleCode":"ENGINEER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.membershipId").value(NEW_MEMBERSHIP.toString()));
    }

    @Test
    void invite_invalidRole_returns422() throws Exception {
        doThrow(new BusinessRuleException("INVALID_ROLE_CODE", "Yaroqsiz rol kodi: KING"))
                .when(commandService).inviteMember(eq(ACTOR), eq(TENANT), any(InviteMemberRequest.class));

        mockMvc.perform(post(BASE).with(withActor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"telegramUserId":555,"displayName":"X","roleCode":"KING"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ROLE_CODE"));
    }

    @Test
    void invite_alreadyMember_returns422() throws Exception {
        doThrow(new BusinessRuleException("ALREADY_MEMBER", "Foydalanuvchi allaqachon bu tenant a'zosi"))
                .when(commandService).inviteMember(eq(ACTOR), eq(TENANT), any(InviteMemberRequest.class));

        mockMvc.perform(post(BASE).with(withActor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"telegramUserId":555,"displayName":"X","roleCode":"ENGINEER"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_MEMBER"));
    }

    @Test
    void invite_unauthorized_returns403() throws Exception {
        doThrow(new AccessDeniedException("Bu operatsiya uchun MEMBER_MANAGE ruxsati talab qilinadi"))
                .when(commandService).inviteMember(eq(ACTOR), eq(TENANT), any(InviteMemberRequest.class));

        mockMvc.perform(post(BASE).with(withActor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"telegramUserId":555,"displayName":"X","roleCode":"ENGINEER"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void invite_malformedBody_returnsErrorStatusNot200() throws Exception {
        int code = mockMvc.perform(post(BASE).with(withActor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andReturn().getResponse().getStatus();
        assertThat(code).isGreaterThanOrEqualTo(400);
        verifyNoInteractions(commandService);
    }

    // ===== POST role change =====

    @Test
    void changeRole_valid_returns204() throws Exception {
        mockMvc.perform(post(BASE + "/" + MEMBER_USER + "/role").with(withActor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newRoleCode":"TESTER"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void changeRole_self_returns422() throws Exception {
        doThrow(new BusinessRuleException("CANNOT_CHANGE_OWN_ROLE", "O'z rolingizni o'zgartira olmaysiz"))
                .when(commandService).changeRole(eq(ACTOR), eq(TENANT), eq(ACTOR), any(ChangeRoleRequest.class));

        mockMvc.perform(post(BASE + "/" + ACTOR + "/role").with(withActor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newRoleCode":"TESTER"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("CANNOT_CHANGE_OWN_ROLE"));
    }

    @Test
    void changeRole_invalidRole_returns422() throws Exception {
        doThrow(new BusinessRuleException("INVALID_ROLE_CODE", "Yaroqsiz rol kodi: KING"))
                .when(commandService).changeRole(eq(ACTOR), eq(TENANT), eq(MEMBER_USER), any(ChangeRoleRequest.class));

        mockMvc.perform(post(BASE + "/" + MEMBER_USER + "/role").with(withActor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newRoleCode":"KING"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ROLE_CODE"));
    }

    // ===== DELETE remove =====

    @Test
    void remove_valid_returns204() throws Exception {
        mockMvc.perform(delete(BASE + "/" + MEMBER_USER).with(withActor(ACTOR)))
                .andExpect(status().isNoContent());
    }

    @Test
    void remove_self_returns422() throws Exception {
        doThrow(new BusinessRuleException("CANNOT_REMOVE_SELF", "O'zingizni a'zolikdan chiqara olmaysiz"))
                .when(commandService).removeMember(ACTOR, TENANT, ACTOR);

        mockMvc.perform(delete(BASE + "/" + ACTOR).with(withActor(ACTOR)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("CANNOT_REMOVE_SELF"));
    }

    @Test
    void remove_anonymous_returns401() throws Exception {
        mockMvc.perform(delete(BASE + "/" + MEMBER_USER))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(commandService);
    }
}
