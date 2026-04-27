package com.engops.platform.identity;

import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipRoleBinding;
import com.engops.platform.identity.model.MembershipStatus;
import com.engops.platform.identity.model.Permission;
import com.engops.platform.identity.model.Role;
import com.engops.platform.identity.model.RolePermission;
import com.engops.platform.identity.repository.AppUserRepository;
import com.engops.platform.identity.repository.MembershipRepository;
import com.engops.platform.identity.repository.MembershipRoleBindingRepository;
import com.engops.platform.identity.repository.PermissionRepository;
import com.engops.platform.identity.repository.RolePermissionRepository;
import com.engops.platform.identity.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public IdentityQueryService(AppUserRepository appUserRepository,
                                 MembershipRepository membershipRepository,
                                 MembershipRoleBindingRepository membershipRoleBindingRepository,
                                 RoleRepository roleRepository,
                                 PermissionRepository permissionRepository,
                                 RolePermissionRepository rolePermissionRepository) {
        this.appUserRepository = appUserRepository;
        this.membershipRepository = membershipRepository;
        this.membershipRoleBindingRepository = membershipRoleBindingRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    /**
     * ID bo'yicha foydalanuvchini topadi.
     */
    public Optional<AppUser> findUserById(UUID userId) {
        return appUserRepository.findById(userId);
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
     * Tenantning barcha a'zolarini qaytaradi (barcha statuslar).
     */
    public List<Membership> listAllMembers(UUID tenantId) {
        return membershipRepository.findByTenantId(tenantId);
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
     * Barcha global ruxsatlarni qaytaradi.
     */
    public List<Permission> listAllPermissions() {
        return permissionRepository.findAll();
    }

    /**
     * Code bo'yicha rolni topadi.
     */
    public Optional<Role> findRoleByCode(String code) {
        return roleRepository.findByCode(code);
    }

    /**
     * ID bo'yicha global rolni topadi.
     *
     * Admin moduli role-permission read surface uchun rol header'ini
     * (id, code, name) yig'ish maqsadida ishlatadi.
     */
    public Optional<Role> findRoleById(UUID roleId) {
        return roleRepository.findById(roleId);
    }

    /**
     * Rolga tayinlangan ruxsat (permission) binding'larini qaytaradi.
     *
     * Admin moduli role-permission read surface uchun shu bog'lanishlarni
     * yig'adi va permission'larni katalog tartibida ko'rsatadi.
     */
    public List<RolePermission> findRolePermissions(UUID roleId) {
        return rolePermissionRepository.findByRoleId(roleId);
    }

    /**
     * ID bo'yicha global ruxsatni (permission) topadi.
     *
     * Admin moduli permission-role read surface uchun ruxsat header'ini
     * (id, code) yig'ish maqsadida ishlatadi.
     */
    public Optional<Permission> findPermissionById(UUID permissionId) {
        return permissionRepository.findById(permissionId);
    }

    /**
     * Ruxsatga (permission) tayinlangan rol binding'larini qaytaradi.
     *
     * Admin moduli permission-role read surface uchun shu bog'lanishlarni
     * yig'adi va rol'larni katalog tartibida ko'rsatadi.
     */
    public List<RolePermission> findPermissionRoles(UUID permissionId) {
        return rolePermissionRepository.findByPermissionId(permissionId);
    }

    /**
     * Foydalanuvchining berilgan tenantdagi barcha ruxsat (permission) kodlarini qaytaradi.
     *
     * Zanjir: Membership -> MembershipRoleBinding -> Role -> RolePermission -> Permission.code
     *
     * Agar foydalanuvchining aktiv a'zoligi bo'lmasa, bo'sh set qaytariladi.
     * Faqat aktiv a'zolik uchun ruxsatlar hisoblanadi.
     *
     * @param tenantId tenant identifikatori
     * @param userId foydalanuvchi identifikatori
     * @return ruxsat kodlari seti (bo'sh bo'lishi mumkin)
     */
    public Set<String> resolvePermissionCodes(UUID tenantId, UUID userId) {
        Optional<Membership> membershipOpt = membershipRepository.findByTenantIdAndUserId(tenantId, userId);
        if (membershipOpt.isEmpty() || !membershipOpt.get().isActive()) {
            return Collections.emptySet();
        }

        Membership membership = membershipOpt.get();
        List<MembershipRoleBinding> roleBindings = membershipRoleBindingRepository.findByMembershipId(membership.getId());

        return roleBindings.stream()
                .map(MembershipRoleBinding::getRole)
                .filter(Role::isActive)
                .flatMap(role -> rolePermissionRepository.findByRoleId(role.getId()).stream())
                .map(RolePermission::getPermission)
                .map(permission -> permission.getCode())
                .collect(Collectors.toSet());
    }
}
