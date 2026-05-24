package com.engops.platform.identity.membership;

import com.engops.platform.audit.AuditService;
import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipRoleBinding;
import com.engops.platform.identity.model.MembershipStatus;
import com.engops.platform.identity.model.Role;
import com.engops.platform.identity.repository.AppUserRepository;
import com.engops.platform.identity.repository.MembershipRepository;
import com.engops.platform.identity.repository.MembershipRoleBindingRepository;
import com.engops.platform.identity.repository.RoleRepository;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 219a — {@link MembershipCommandService} unit testlari (Mockito).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MembershipCommandServiceTest {

    private static final UUID ACTOR = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID TENANT = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID MEMBER_USER = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final Long TELEGRAM_ID = 555_111_222L;

    @Mock private AppUserRepository appUserRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private MembershipRoleBindingRepository membershipRoleBindingRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private IdentityQueryService identityQueryService;
    @Mock private AuditService auditService;

    @InjectMocks private MembershipCommandService service;

    private void authorized() {
        when(identityQueryService.resolvePermissionCodes(TENANT, ACTOR))
                .thenReturn(Set.of("MEMBER_MANAGE"));
    }

    private void unauthorized() {
        when(identityQueryService.resolvePermissionCodes(TENANT, ACTOR))
                .thenReturn(Set.of());
    }

    private void echoSaves() {
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(i -> i.getArgument(0));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(i -> i.getArgument(0));
    }

    private InviteMemberRequest invite(String roleCode) {
        return new InviteMemberRequest(TELEGRAM_ID, "Sariga", "sariga_tg", roleCode);
    }

    // ===== inviteMember =====

    @Test
    void inviteMember_validRequest_createsAppUserAndMembership() {
        authorized();
        echoSaves();
        when(roleRepository.findByCode("ENGINEER")).thenReturn(Optional.of(new Role("ENGINEER", "Engineer")));
        when(appUserRepository.findByTelegramUserId(TELEGRAM_ID)).thenReturn(Optional.empty());
        when(membershipRepository.existsByTenantIdAndUserId(eq(TENANT), any())).thenReturn(false);

        UUID membershipId = service.inviteMember(ACTOR, TENANT, invite("ENGINEER"));

        assertThat(membershipId).isNotNull();
        verify(appUserRepository).save(any(AppUser.class));
        verify(membershipRepository).save(any(Membership.class));
    }

    @Test
    void inviteMember_existingAppUser_reusesUserCreatesMembership() {
        authorized();
        echoSaves();
        when(roleRepository.findByCode("ENGINEER")).thenReturn(Optional.of(new Role("ENGINEER", "Engineer")));
        AppUser existing = new AppUser(TELEGRAM_ID, "Existing");
        when(appUserRepository.findByTelegramUserId(TELEGRAM_ID)).thenReturn(Optional.of(existing));
        when(membershipRepository.existsByTenantIdAndUserId(TENANT, existing.getId())).thenReturn(false);

        service.inviteMember(ACTOR, TENANT, invite("ENGINEER"));

        verify(appUserRepository, never()).save(any(AppUser.class));
        verify(membershipRepository).save(any(Membership.class));
    }

    @Test
    void inviteMember_alreadyMember_throwsBusinessRule() {
        authorized();
        when(roleRepository.findByCode("ENGINEER")).thenReturn(Optional.of(new Role("ENGINEER", "Engineer")));
        AppUser existing = new AppUser(TELEGRAM_ID, "Existing");
        when(appUserRepository.findByTelegramUserId(TELEGRAM_ID)).thenReturn(Optional.of(existing));
        when(membershipRepository.existsByTenantIdAndUserId(TENANT, existing.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.inviteMember(ACTOR, TENANT, invite("ENGINEER")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("allaqachon");
        verify(membershipRepository, never()).save(any(Membership.class));
        verifyNoInteractions(auditService);
    }

    @Test
    void inviteMember_invalidRoleCode_throwsBusinessRule() {
        authorized();

        assertThatThrownBy(() -> service.inviteMember(ACTOR, TENANT, invite("SUPERUSER")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("rol");
        verifyNoInteractions(appUserRepository, membershipRepository,
                membershipRoleBindingRepository, auditService);
    }

    @Test
    void inviteMember_unauthorizedActor_throwsAccessDenied() {
        unauthorized();

        assertThatThrownBy(() -> service.inviteMember(ACTOR, TENANT, invite("ENGINEER")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(appUserRepository, membershipRepository,
                membershipRoleBindingRepository, roleRepository, auditService);
    }

    @Test
    void inviteMember_persistsRoleBinding() {
        authorized();
        echoSaves();
        when(roleRepository.findByCode("TESTER")).thenReturn(Optional.of(new Role("TESTER", "Tester")));
        when(appUserRepository.findByTelegramUserId(TELEGRAM_ID)).thenReturn(Optional.empty());
        when(membershipRepository.existsByTenantIdAndUserId(eq(TENANT), any())).thenReturn(false);

        service.inviteMember(ACTOR, TENANT, invite("TESTER"));

        verify(membershipRoleBindingRepository).save(any(MembershipRoleBinding.class));
    }

    @Test
    void inviteMember_emitsAuditEvent() {
        authorized();
        echoSaves();
        when(roleRepository.findByCode("ENGINEER")).thenReturn(Optional.of(new Role("ENGINEER", "Engineer")));
        when(appUserRepository.findByTelegramUserId(TELEGRAM_ID)).thenReturn(Optional.empty());
        when(membershipRepository.existsByTenantIdAndUserId(eq(TENANT), any())).thenReturn(false);

        service.inviteMember(ACTOR, TENANT, invite("ENGINEER"));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(auditService).recordEvent(eq(TENANT), eq("MEMBERSHIP"), any(UUID.class),
                eq("MEMBER_INVITED"), eq(ACTOR), eq("MEMBER_API"), isNull(), payload.capture());
        assertThat(payload.getValue()).contains("role_code").contains("ENGINEER")
                .contains("telegram_user_id");
    }

    @Test
    void inviteMember_nullUsername_skipsUsernameSet() {
        authorized();
        echoSaves();
        when(roleRepository.findByCode("VIEWER")).thenReturn(Optional.of(new Role("VIEWER", "Viewer")));
        when(appUserRepository.findByTelegramUserId(TELEGRAM_ID)).thenReturn(Optional.empty());
        when(membershipRepository.existsByTenantIdAndUserId(eq(TENANT), any())).thenReturn(false);

        service.inviteMember(ACTOR, TENANT,
                new InviteMemberRequest(TELEGRAM_ID, "NoUsername", null, "VIEWER"));

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getUsername()).isNull();
    }

    // ===== removeMember =====

    @Test
    void removeMember_validRequest_marksAsRemoved() {
        authorized();
        Membership membership = new Membership(TENANT, MEMBER_USER);
        when(membershipRepository.findByTenantIdAndUserId(TENANT, MEMBER_USER))
                .thenReturn(Optional.of(membership));

        service.removeMember(ACTOR, TENANT, MEMBER_USER);

        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.REMOVED);
        verify(membershipRepository).save(membership);
    }

    @Test
    void removeMember_self_throwsBusinessRule() {
        authorized();

        assertThatThrownBy(() -> service.removeMember(ACTOR, TENANT, ACTOR))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("O'zingizni");
        verify(membershipRepository, never()).findByTenantIdAndUserId(any(), any());
        verifyNoInteractions(auditService);
    }

    @Test
    void removeMember_notFound_throwsBusinessRule() {
        authorized();
        when(membershipRepository.findByTenantIdAndUserId(TENANT, MEMBER_USER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeMember(ACTOR, TENANT, MEMBER_USER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("topilmadi");
        verifyNoInteractions(auditService);
    }

    @Test
    void removeMember_unauthorized_throwsAccessDenied() {
        unauthorized();

        assertThatThrownBy(() -> service.removeMember(ACTOR, TENANT, MEMBER_USER))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(membershipRepository, auditService);
    }

    @Test
    void removeMember_emitsAuditEvent() {
        authorized();
        Membership membership = new Membership(TENANT, MEMBER_USER);
        when(membershipRepository.findByTenantIdAndUserId(TENANT, MEMBER_USER))
                .thenReturn(Optional.of(membership));

        service.removeMember(ACTOR, TENANT, MEMBER_USER);

        verify(auditService).recordEvent(eq(TENANT), eq("MEMBERSHIP"), eq(membership.getId()),
                eq("MEMBER_REMOVED"), eq(ACTOR), eq("MEMBER_API"), isNull(), any(String.class));
    }

    // ===== changeRole =====

    @Test
    void changeRole_validRequest_replacesBinding() {
        authorized();
        when(roleRepository.findByCode("TESTER")).thenReturn(Optional.of(new Role("TESTER", "Tester")));
        Membership membership = new Membership(TENANT, MEMBER_USER);
        when(membershipRepository.findByTenantIdAndUserId(TENANT, MEMBER_USER))
                .thenReturn(Optional.of(membership));
        MembershipRoleBinding old = new MembershipRoleBinding(membership, new Role("VIEWER", "Viewer"));
        when(membershipRoleBindingRepository.findByMembershipId(membership.getId()))
                .thenReturn(List.of(old));

        service.changeRole(ACTOR, TENANT, MEMBER_USER, new ChangeRoleRequest("TESTER"));

        verify(membershipRoleBindingRepository, times(1)).delete(old);
        verify(membershipRoleBindingRepository).save(any(MembershipRoleBinding.class));
    }

    @Test
    void changeRole_self_throwsBusinessRule() {
        authorized();

        assertThatThrownBy(() -> service.changeRole(ACTOR, TENANT, ACTOR, new ChangeRoleRequest("TESTER")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("rol");
        verifyNoInteractions(membershipRepository, auditService);
    }

    @Test
    void changeRole_invalidRoleCode_throwsBusinessRule() {
        authorized();

        assertThatThrownBy(() -> service.changeRole(ACTOR, TENANT, MEMBER_USER, new ChangeRoleRequest("KING")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("rol");
        verifyNoInteractions(membershipRepository, auditService);
    }

    @Test
    void changeRole_memberNotFound_throwsBusinessRule() {
        authorized();
        when(roleRepository.findByCode("TESTER")).thenReturn(Optional.of(new Role("TESTER", "Tester")));
        when(membershipRepository.findByTenantIdAndUserId(TENANT, MEMBER_USER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeRole(ACTOR, TENANT, MEMBER_USER, new ChangeRoleRequest("TESTER")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("topilmadi");
        verifyNoInteractions(auditService);
    }

    @Test
    void changeRole_emitsAuditEventWithOldAndNewRole() {
        authorized();
        when(roleRepository.findByCode("TESTER")).thenReturn(Optional.of(new Role("TESTER", "Tester")));
        Membership membership = new Membership(TENANT, MEMBER_USER);
        when(membershipRepository.findByTenantIdAndUserId(TENANT, MEMBER_USER))
                .thenReturn(Optional.of(membership));
        when(membershipRoleBindingRepository.findByMembershipId(membership.getId()))
                .thenReturn(List.of(new MembershipRoleBinding(membership, new Role("VIEWER", "Viewer"))));

        service.changeRole(ACTOR, TENANT, MEMBER_USER, new ChangeRoleRequest("TESTER"));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(auditService).recordEvent(eq(TENANT), eq("MEMBERSHIP"), eq(membership.getId()),
                eq("MEMBER_ROLE_CHANGED"), eq(ACTOR), eq("MEMBER_API"), isNull(), payload.capture());
        assertThat(payload.getValue())
                .contains("\"old_role_code\":\"VIEWER\"")
                .contains("\"new_role_code\":\"TESTER\"");
    }
}
