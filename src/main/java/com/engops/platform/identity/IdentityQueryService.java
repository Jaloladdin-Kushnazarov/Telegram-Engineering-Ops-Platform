package com.engops.platform.identity;

import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipRoleBinding;
import com.engops.platform.identity.model.MembershipStatus;
import com.engops.platform.identity.model.Role;
import com.engops.platform.identity.repository.AppUserRepository;
import com.engops.platform.identity.repository.MembershipRepository;
import com.engops.platform.identity.repository.MembershipRoleBindingRepository;
import com.engops.platform.identity.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Identity moduli uchun so'rov (query) servisi.
 * Boshqa modullar foydalanuvchi va a'zolik ma'lumotlarini shu servis orqali oladi.
 *
 * Bu servis faqat o'qish operatsiyalarini bajaradi.
 */
@Service
@Transactional(readOnly = true)
public class IdentityQueryService {

    private final AppUserRepository appUserRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipRoleBindingRepository membershipRoleBindingRepository;
    private final RoleRepository roleRepository;

    public IdentityQueryService(AppUserRepository appUserRepository,
                                 MembershipRepository membershipRepository,
                                 MembershipRoleBindingRepository membershipRoleBindingRepository,
                                 RoleRepository roleRepository) {
        this.appUserRepository = appUserRepository;
        this.membershipRepository = membershipRepository;
        this.membershipRoleBindingRepository = membershipRoleBindingRepository;
        this.roleRepository = roleRepository;
    }

    /**
     * Telegram user ID bo'yicha foydalanuvchini topadi.
     */
    public Optional<AppUser> findUserByTelegramUserId(Long telegramUserId) {
        return appUserRepository.findByTelegramUserId(telegramUserId);
    }

    /**
     * Foydalanuvchining berilgan tenantda aktiv a'zoligi borligini tekshiradi.
     */
    public boolean hasActiveMembership(UUID tenantId, UUID userId) {
        return membershipRepository.findByTenantIdAndUserId(tenantId, userId)
                .map(Membership::isActive)
                .orElse(false);
    }

    /**
     * Tenantning barcha aktiv a'zolarini qaytaradi.
     */
    public List<Membership> listActiveMembers(UUID tenantId) {
        return membershipRepository.findByTenantIdAndStatus(tenantId, MembershipStatus.ACTIVE);
    }

    /**
     * Tenant va foydalanuvchi uchun a'zolikni topadi.
     */
    public Optional<Membership> findMembership(UUID tenantId, UUID userId) {
        return membershipRepository.findByTenantIdAndUserId(tenantId, userId);
    }

    /**
     * A'zolikka tayinlangan rollarni qaytaradi.
     */
    public List<MembershipRoleBinding> getMembershipRoles(UUID membershipId) {
        return membershipRoleBindingRepository.findByMembershipId(membershipId);
    }

    /**
     * Barcha global rollarni qaytaradi.
     */
    public List<Role> listAllRoles() {
        return roleRepository.findAll();
    }

    /**
     * Code bo'yicha rolni topadi.
     */
    public Optional<Role> findRoleByCode(String code) {
        return roleRepository.findByCode(code);
    }
}
