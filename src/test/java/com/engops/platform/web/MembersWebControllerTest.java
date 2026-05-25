package com.engops.platform.web;

import com.engops.platform.identity.membership.ChangeRoleRequest;
import com.engops.platform.identity.membership.InviteMemberRequest;
import com.engops.platform.identity.membership.MemberSummary;
import com.engops.platform.identity.membership.MembershipCommandService;
import com.engops.platform.identity.membership.MembershipQueryService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Phase 219b — {@link MembersWebController} @WebMvcTest testlari.
 *
 * <p>Actor SecurityContext'ga RequestAttribute pattern orqali o'rnatiladi
 * (Phase 219a MemberRestControllerTest bilan bir xil). WEB layer
 * exception modeli: AccessDenied/BusinessRule → 200 + inline error
 * fragment; JWT yo'q → 401.</p>
 */
@WebMvcTest(MembersWebController.class)
@Import({SecurityConfig.class, SecurityWebMvcConfig.class})
class MembersWebControllerTest {

    private static final UUID ACTOR = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEMBER_USER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID NEW_MEMBERSHIP = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final String PAGE = "/web/tenants/" + TENANT + "/members";
    private static final String API = "/web/api/tenants/" + TENANT + "/members";

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

    private static MemberSummary member(String name, String role) {
        return new MemberSummary(MEMBER_USER, 555111222L, name, "sariga_tg",
                role, role, "ACTIVE", Instant.parse("2026-05-10T00:00:00Z"));
    }

    // ===== page render =====

    @Test
    void membersPage_returnsBaseLayoutWithContentFragment() throws Exception {
        mockMvc.perform(get(PAGE))
                .andExpect(status().isOk())
                .andExpect(view().name("web/layout/base"))
                .andExpect(model().attribute("contentFragment", "web/members :: content"))
                .andExpect(model().attribute("activeNav", "members"));
    }

    @Test
    void membersPage_addsTenantIdToModel() throws Exception {
        mockMvc.perform(get(PAGE))
                .andExpect(status().isOk())
                .andExpect(model().attribute("tenantId", TENANT));
    }

    // ===== Phase 219c — invite modal UX =====

    @Test
    void membersPage_rendersInviteDialog() throws Exception {
        mockMvc.perform(get(PAGE))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"invite-dialog\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("showModal()")));
    }

    @Test
    void membersPage_rendersInviteButton() throws Exception {
        mockMvc.perform(get(PAGE))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("+ Invite member")));
    }

    @Test
    void membersPage_doesNotRenderInlineInviteFormContainer() throws Exception {
        // Phase 219c — eski sahifa-level form (invite-form-container) modal'ga
        // ko'chirildi; sahifa endi uni render qilmaydi.
        mockMvc.perform(get(PAGE))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("class=\"invite-form-container\""))));
    }

    // ===== GET fragment =====

    @Test
    void membersFragment_authorized_returnsRowsFragment() throws Exception {
        when(queryService.listMembers(ACTOR, TENANT)).thenReturn(List.of(member("Sariga", "ENGINEER")));

        mockMvc.perform(get(API).with(withActor(ACTOR)))
                .andExpect(status().isOk())
                .andExpect(view().name("web/fragments/member-rows :: rows"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sariga")));
    }

    @Test
    void membersFragment_addsMembersAndAssignableRolesToModel() throws Exception {
        when(queryService.listMembers(ACTOR, TENANT)).thenReturn(List.of(member("Sariga", "ENGINEER")));

        mockMvc.perform(get(API).with(withActor(ACTOR)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("members"))
                .andExpect(model().attribute("count", 1))
                .andExpect(model().attribute("assignableRoles",
                        List.of("ADMIN", "ENGINEER", "TESTER", "VIEWER")));
    }

    @Test
    void membersFragment_unauthorized_returnsDeniedFragment() throws Exception {
        doThrow(new AccessDeniedException("denied")).when(queryService).listMembers(ACTOR, TENANT);

        mockMvc.perform(get(API).with(withActor(ACTOR)))
                .andExpect(status().isOk())
                .andExpect(view().name("web/fragments/member-rows-denied :: denied"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("MEMBER_MANAGE")));
    }

    @Test
    void membersFragment_addsErrorMessage_onAccessDenied() throws Exception {
        doThrow(new AccessDeniedException("denied")).when(queryService).listMembers(ACTOR, TENANT);

        mockMvc.perform(get(API).with(withActor(ACTOR)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("error"));
    }

    // ===== POST invite =====

    @Test
    void inviteMember_valid_returnsUpdatedRowsAndSetsHxTrigger() throws Exception {
        when(commandService.inviteMember(eq(ACTOR), eq(TENANT), any(InviteMemberRequest.class)))
                .thenReturn(NEW_MEMBERSHIP);
        when(queryService.listMembers(ACTOR, TENANT)).thenReturn(List.of(member("Sariga", "ENGINEER")));

        mockMvc.perform(post(API).with(withActor(ACTOR))
                        .param("telegramUserId", "555111222")
                        .param("displayName", "Sariga")
                        .param("username", "sariga_tg")
                        .param("roleCode", "ENGINEER"))
                .andExpect(status().isOk())
                .andExpect(view().name("web/fragments/member-rows :: rows"))
                .andExpect(header().string("HX-Trigger", "memberInvited"));
    }

    @Test
    void inviteMember_businessRule_returnsInviteErrorFragment() throws Exception {
        doThrow(new BusinessRuleException("ALREADY_MEMBER", "Foydalanuvchi allaqachon bu tenant a'zosi"))
                .when(commandService).inviteMember(eq(ACTOR), eq(TENANT), any(InviteMemberRequest.class));

        mockMvc.perform(post(API).with(withActor(ACTOR))
                        .param("telegramUserId", "555111222")
                        .param("displayName", "Sariga")
                        .param("roleCode", "ENGINEER"))
                .andExpect(status().isOk())
                .andExpect(view().name("web/fragments/member-rows :: inviteError"))
                .andExpect(header().string("HX-Retarget", "#invite-error"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("allaqachon")));
    }

    @Test
    void inviteMember_emptyUsername_normalizedToNull() throws Exception {
        when(commandService.inviteMember(eq(ACTOR), eq(TENANT), any(InviteMemberRequest.class)))
                .thenReturn(NEW_MEMBERSHIP);
        when(queryService.listMembers(ACTOR, TENANT)).thenReturn(List.of());

        mockMvc.perform(post(API).with(withActor(ACTOR))
                        .param("telegramUserId", "555111222")
                        .param("displayName", "Sariga")
                        .param("username", "   ")
                        .param("roleCode", "ENGINEER"))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<InviteMemberRequest> captor =
                org.mockito.ArgumentCaptor.forClass(InviteMemberRequest.class);
        verify(commandService).inviteMember(eq(ACTOR), eq(TENANT), captor.capture());
        assertThat(captor.getValue().username()).isNull();
    }

    // ===== POST role change =====

    @Test
    void changeRole_valid_returnsUpdatedRows() throws Exception {
        when(queryService.listMembers(ACTOR, TENANT)).thenReturn(List.of(member("Sariga", "TESTER")));

        mockMvc.perform(post(API + "/" + MEMBER_USER + "/role").with(withActor(ACTOR))
                        .param("newRoleCode", "TESTER"))
                .andExpect(status().isOk())
                .andExpect(view().name("web/fragments/member-rows :: rows"));
        verify(commandService).changeRole(eq(ACTOR), eq(TENANT), eq(MEMBER_USER), any(ChangeRoleRequest.class));
    }

    @Test
    void changeRole_businessRule_returnsInviteErrorFragment() throws Exception {
        doThrow(new BusinessRuleException("CANNOT_CHANGE_OWN_ROLE", "O'z rolingizni o'zgartira olmaysiz"))
                .when(commandService).changeRole(eq(ACTOR), eq(TENANT), eq(ACTOR), any(ChangeRoleRequest.class));

        mockMvc.perform(post(API + "/" + ACTOR + "/role").with(withActor(ACTOR))
                        .param("newRoleCode", "TESTER"))
                .andExpect(status().isOk())
                .andExpect(view().name("web/fragments/member-rows :: inviteError"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("rolingizni")));
    }

    // ===== DELETE remove =====

    @Test
    void removeMember_valid_returnsUpdatedRows() throws Exception {
        when(queryService.listMembers(ACTOR, TENANT)).thenReturn(List.of());

        mockMvc.perform(delete(API + "/" + MEMBER_USER).with(withActor(ACTOR)))
                .andExpect(status().isOk())
                .andExpect(view().name("web/fragments/member-rows :: rows"));
        verify(commandService).removeMember(ACTOR, TENANT, MEMBER_USER);
    }

    @Test
    void removeMember_self_returnsInviteErrorFragment() throws Exception {
        doThrow(new BusinessRuleException("CANNOT_REMOVE_SELF", "O'zingizni a'zolikdan chiqara olmaysiz"))
                .when(commandService).removeMember(ACTOR, TENANT, ACTOR);

        mockMvc.perform(delete(API + "/" + ACTOR).with(withActor(ACTOR)))
                .andExpect(status().isOk())
                .andExpect(view().name("web/fragments/member-rows :: inviteError"))
                .andExpect(header().string("HX-Retarget", "#invite-error"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("zolikdan chiqara")));
    }

    // ===== security =====

    @Test
    void anonymous_membersFragment_returns401() throws Exception {
        mockMvc.perform(get(API))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(queryService);
    }
}
