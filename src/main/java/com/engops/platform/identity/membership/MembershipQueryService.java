package com.engops.platform.identity.membership;

import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipRoleBinding;
import com.engops.platform.identity.model.MembershipStatus;
import com.engops.platform.identity.repository.AppUserRepository;
import com.engops.platform.identity.repository.MembershipRepository;
import com.engops.platform.identity.repository.MembershipRoleBindingRepository;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 219a — tenant a'zolari read-model query service'i.
 *
 * <p>Aktiv a'zolar ro'yxatini rol kodi/nomi bilan birga qaytaradi.
 * {@code MEMBER_MANAGE} ruxsati talab qilinadi (Phase 219a member
 * management surface'i admin/owner uchun — listing ham management
 * sirtining bir qismi).</p>
 *
 * <p>{@code @Transactional(readOnly=true)} — {@code spring.jpa.open-in-view=false}
 * sharoitida lazy {@code MembershipRoleBinding.role} proxy'larini xavfsiz
 * traversal qilish uchun.</p>
 */
@Service
@Transactional(readOnly = true)
public class MembershipQueryService {

    static final String MEMBER_MANAGE = "MEMBER_MANAGE";

    private final IdentityQueryService identityQueryService;
    private final MembershipRepository membershipRepository;
    private final AppUserRepository appUserRepository;
    private final MembershipRoleBindingRepository membershipRoleBindingRepository;

    public MembershipQueryService(IdentityQueryService identityQueryService,
                                   MembershipRepository membershipRepository,
                                   AppUserRepository appUserRepository,
                                   MembershipRoleBindingRepository membershipRoleBindingRepository) {
        this.identityQueryService = identityQueryService;
        this.membershipRepository = membershipRepository;
        this.appUserRepository = appUserRepository;
        this.membershipRoleBindingRepository = membershipRoleBindingRepository;
    }

    /**
     * Tenant'ning aktiv a'zolarini joinedAt (created_at) tartibida qaytaradi.
     *
     * @throws AccessDeniedException actor MEMBER_MANAGE'ga ega bo'lmasa
     */
    public List<MemberSummary> listMembers(UUID actorUserId, UUID tenantId) {
        requireMemberManage(actorUserId, tenantId);

        return membershipRepository.findByTenantIdAndStatus(tenantId, MembershipStatus.ACTIVE)
                .stream()
                .map(this::toSummary)
                .sorted(Comparator.comparing(MemberSummary::joinedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private MemberSummary toSummary(Membership membership) {
        AppUser user = appUserRepository.findById(membership.getUserId())
                .orElseThrow(() -> new BusinessRuleException("MEMBER_USER_MISSING",
                        "A'zolik foydalanuvchisi topilmadi: " + membership.getUserId()));

        List<MembershipRoleBinding> bindings =
                membershipRoleBindingRepository.findByMembershipId(membership.getId());
        String roleCode = bindings.isEmpty() ? "NONE" : bindings.get(0).getRole().getCode();
        String roleName = bindings.isEmpty() ? "No role" : bindings.get(0).getRole().getName();

        return new MemberSummary(
                user.getId(),
                user.getTelegramUserId(),
                user.getDisplayName(),
                user.getUsername(),
                roleCode,
                roleName,
                membership.getStatus().name(),
                membership.getCreatedAt());
    }

    private void requireMemberManage(UUID actorUserId, UUID tenantId) {
        if (actorUserId == null) {
            throw new AccessDeniedException("Actor identifikatsiyasi talab qilinadi");
        }
        Set<String> permissions = identityQueryService.resolvePermissionCodes(tenantId, actorUserId);
        if (!permissions.contains(MEMBER_MANAGE)) {
            throw new AccessDeniedException(
                    "Bu operatsiya uchun " + MEMBER_MANAGE + " ruxsati talab qilinadi");
        }
    }
}
