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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 219a — tenant a'zoligini boshqarish command service'i.
 *
 * <p>TENANT_OWNER / ADMIN o'z tenant'iga xodimlarni invite qilish, rolini
 * o'zgartirish va a'zolikni o'chirish uchun yagona atomik kirish nuqtasi.
 * Phase 199 {@code TenantOnboardingService} pattern'iga amal qiladi:
 * authorize → find-or-create → mutate → audit.</p>
 *
 * <p><strong>Authorization (per-tenant):</strong> har bir command birinchi
 * navbatda {@code MEMBER_MANAGE} ruxsatini tenant doirasida tekshiradi.
 * Ruxsat zanjiri {@link IdentityQueryService#resolvePermissionCodes} orqali
 * hal qilinadi (Membership → Role → Permission). {@code MEMBER_MANAGE} (V2
 * seed, {@code a0000000-…-007}) ADMIN (V6) va TENANT_OWNER (V9) rollariga
 * biriktirilgan — Phase 219a hech qanday yangi permission seed kiritmaydi
 * (fine-grained MEMBER_INVITE/REMOVE/ROLE_CHANGE o'rniga mavjud
 * MEMBER_MANAGE'ni qayta ishlatadi).</p>
 *
 * <p><strong>Boundary:</strong> work item modulidagi
 * {@code OperationalAuthorizationService} ataylab ishlatilmaydi — u
 * boshqa modul (workitem) va faqat work-item-specific permission
 * metodlariga ega. identity.membership identity'ning o'z public query
 * service'idan foydalanadi.</p>
 *
 * <p><strong>Audit:</strong> har bir mutatsiya {@code MEMBERSHIP} aggregate
 * ostida audit qatorini yozadi (MEMBER_INVITED / MEMBER_REMOVED /
 * MEMBER_ROLE_CHANGED), JSON payload bilan.</p>
 *
 * <p><strong>Concurrency:</strong> butun amal yagona {@code @Transactional}
 * boundary ichida — find-or-create + binding + audit atomik. Optimistic
 * locking BaseEntity {@code @Version} orqali.</p>
 */
@Service
@Transactional
public class MembershipCommandService {

    private static final Logger log = LoggerFactory.getLogger(MembershipCommandService.class);

    /** V2 seed permission — ADMIN (V6) va TENANT_OWNER (V9) rollariga biriktirilgan. */
    static final String MEMBER_MANAGE = "MEMBER_MANAGE";

    static final String ACTION_SOURCE = "MEMBER_API";

    /**
     * Invite/role-change orqali tayinlash mumkin bo'lgan rol kodlari.
     * Ownership (TENANT_OWNER) va platform rollari ataylab tashqarida —
     * ular alohida danger-zone oqimlari orqali beriladi.
     */
    static final Set<String> ASSIGNABLE_ROLE_CODES =
            Set.of("ADMIN", "ENGINEER", "TESTER", "VIEWER");

    private final AppUserRepository appUserRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipRoleBindingRepository membershipRoleBindingRepository;
    private final RoleRepository roleRepository;
    private final IdentityQueryService identityQueryService;
    private final AuditService auditService;

    public MembershipCommandService(AppUserRepository appUserRepository,
                                     MembershipRepository membershipRepository,
                                     MembershipRoleBindingRepository membershipRoleBindingRepository,
                                     RoleRepository roleRepository,
                                     IdentityQueryService identityQueryService,
                                     AuditService auditService) {
        this.appUserRepository = appUserRepository;
        this.membershipRepository = membershipRepository;
        this.membershipRoleBindingRepository = membershipRoleBindingRepository;
        this.roleRepository = roleRepository;
        this.identityQueryService = identityQueryService;
        this.auditService = auditService;
    }

    /**
     * Tenant'ga yangi a'zo qo'shadi. Find-or-create AppUser
     * (telegram_user_id bo'yicha): mavjud bo'lsa qayta ishlatiladi, yo'q
     * bo'lsa yaratiladi. Foydalanuvchi shu tenant'da allaqachon a'zo bo'lsa
     * — {@code ALREADY_MEMBER} biznes xatosi.
     *
     * @return yangi yaratilgan Membership identifikatori
     * @throws AccessDeniedException actor MEMBER_MANAGE'ga ega bo'lmasa
     * @throws BusinessRuleException yaroqsiz rol kodi yoki allaqachon a'zo
     */
    public UUID inviteMember(UUID actorUserId, UUID tenantId, InviteMemberRequest request) {
        requireMemberManage(actorUserId, tenantId);

        String roleCode = normalizeRoleCode(request.roleCode());
        Role role = resolveAssignableRole(roleCode);

        AppUser user = appUserRepository.findByTelegramUserId(request.telegramUserId())
                .orElseGet(() -> {
                    AppUser created = new AppUser(request.telegramUserId(), request.displayName());
                    if (hasText(request.username())) {
                        created.setUsername(request.username().strip());
                    }
                    return appUserRepository.save(created);
                });

        if (membershipRepository.existsByTenantIdAndUserId(tenantId, user.getId())) {
            throw new BusinessRuleException("ALREADY_MEMBER",
                    "Foydalanuvchi allaqachon bu tenant a'zosi");
        }

        Membership membership = membershipRepository.save(new Membership(tenantId, user.getId()));
        membershipRoleBindingRepository.save(new MembershipRoleBinding(membership, role));

        auditService.recordEvent(tenantId, "MEMBERSHIP", membership.getId(),
                "MEMBER_INVITED", actorUserId, ACTION_SOURCE, null,
                "{\"telegram_user_id\":" + request.telegramUserId()
                        + ",\"role_code\":\"" + roleCode + "\"}");

        log.info("Member invited: tenant={} membership={} role={} actor={}",
                tenantId, membership.getId(), roleCode, actorUserId);
        return membership.getId();
    }

    /**
     * A'zolikni soft-delete qiladi ({@code status=REMOVED}). O'zini o'chirish
     * mumkin emas. A'zolik topilmasa {@code MEMBER_NOT_FOUND}.
     *
     * @throws AccessDeniedException actor MEMBER_MANAGE'ga ega bo'lmasa
     * @throws BusinessRuleException o'zini o'chirish yoki a'zo topilmasa
     */
    public void removeMember(UUID actorUserId, UUID tenantId, UUID memberUserId) {
        requireMemberManage(actorUserId, tenantId);

        if (memberUserId.equals(actorUserId)) {
            throw new BusinessRuleException("CANNOT_REMOVE_SELF",
                    "O'zingizni a'zolikdan chiqara olmaysiz");
        }

        Membership membership = membershipRepository.findByTenantIdAndUserId(tenantId, memberUserId)
                .orElseThrow(() -> new BusinessRuleException("MEMBER_NOT_FOUND",
                        "A'zo topilmadi"));

        membership.setStatus(MembershipStatus.REMOVED);
        membershipRepository.save(membership);

        auditService.recordEvent(tenantId, "MEMBERSHIP", membership.getId(),
                "MEMBER_REMOVED", actorUserId, ACTION_SOURCE, null,
                "{\"removed_user_id\":\"" + memberUserId + "\"}");

        log.info("Member removed: tenant={} membership={} removedUser={} actor={}",
                tenantId, membership.getId(), memberUserId, actorUserId);
    }

    /**
     * A'zoning rolini almashtiradi: mavjud rol bog'lanish(lar)ini o'chirib,
     * yangi rol bog'lanishini qo'shadi. O'z rolini o'zgartirish mumkin emas.
     *
     * @throws AccessDeniedException actor MEMBER_MANAGE'ga ega bo'lmasa
     * @throws BusinessRuleException o'z rolini o'zgartirish, yaroqsiz rol
     *         kodi yoki a'zo topilmasa
     */
    public void changeRole(UUID actorUserId, UUID tenantId, UUID memberUserId,
                            ChangeRoleRequest request) {
        requireMemberManage(actorUserId, tenantId);

        if (memberUserId.equals(actorUserId)) {
            throw new BusinessRuleException("CANNOT_CHANGE_OWN_ROLE",
                    "O'z rolingizni o'zgartira olmaysiz");
        }

        String newRoleCode = normalizeRoleCode(request.newRoleCode());
        Role newRole = resolveAssignableRole(newRoleCode);

        Membership membership = membershipRepository.findByTenantIdAndUserId(tenantId, memberUserId)
                .orElseThrow(() -> new BusinessRuleException("MEMBER_NOT_FOUND",
                        "A'zo topilmadi"));

        List<MembershipRoleBinding> existing =
                membershipRoleBindingRepository.findByMembershipId(membership.getId());
        String oldRoleCode = existing.isEmpty()
                ? "NONE"
                : existing.get(0).getRole().getCode();

        existing.forEach(membershipRoleBindingRepository::delete);
        membershipRoleBindingRepository.save(new MembershipRoleBinding(membership, newRole));

        auditService.recordEvent(tenantId, "MEMBERSHIP", membership.getId(),
                "MEMBER_ROLE_CHANGED", actorUserId, ACTION_SOURCE, null,
                "{\"old_role_code\":\"" + oldRoleCode
                        + "\",\"new_role_code\":\"" + newRoleCode + "\"}");

        log.info("Member role changed: tenant={} membership={} {} -> {} actor={}",
                tenantId, membership.getId(), oldRoleCode, newRoleCode, actorUserId);
    }

    // ========== Helpers ==========

    private void requireMemberManage(UUID actorUserId, UUID tenantId) {
        if (actorUserId == null) {
            throw new AccessDeniedException("Actor identifikatsiyasi talab qilinadi");
        }
        Set<String> permissions = identityQueryService.resolvePermissionCodes(tenantId, actorUserId);
        if (!permissions.contains(MEMBER_MANAGE)) {
            log.warn("Membership authorization rad etildi: actor={}, tenant={}, kerakli ruxsat={}",
                    actorUserId, tenantId, MEMBER_MANAGE);
            throw new AccessDeniedException(
                    "Bu operatsiya uchun " + MEMBER_MANAGE + " ruxsati talab qilinadi");
        }
    }

    private Role resolveAssignableRole(String roleCode) {
        if (roleCode == null || !ASSIGNABLE_ROLE_CODES.contains(roleCode)) {
            throw new BusinessRuleException("INVALID_ROLE_CODE",
                    "Yaroqsiz rol kodi: " + roleCode);
        }
        return roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new BusinessRuleException("ROLE_NOT_FOUND",
                        "Rol katalog'da topilmadi: " + roleCode));
    }

    private static String normalizeRoleCode(String roleCode) {
        if (roleCode == null) {
            return null;
        }
        return roleCode.strip().toUpperCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
