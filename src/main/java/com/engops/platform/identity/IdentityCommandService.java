package com.engops.platform.identity;

import com.engops.platform.audit.AuditService;
import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipRoleBinding;
import com.engops.platform.identity.model.MembershipStatus;
import com.engops.platform.identity.model.Role;
import com.engops.platform.identity.repository.AppUserRepository;
import com.engops.platform.identity.repository.MembershipRepository;
import com.engops.platform.identity.repository.MembershipRoleBindingRepository;
import com.engops.platform.identity.repository.RoleRepository;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Identity moduli uchun buyruq (command) servisi — yozish operatsiyalari.
 *
 * Boshqa modullar (masalan admin) foydalanuvchi va a'zolik yozish
 * operatsiyalarini faqat shu public API orqali bajaradi.
 *
 * Cross-module bog'lanishlar:
 * - AuditService — audit yozish uchun (public API)
 * - TenantConfigQueryService — tenant mavjudligini tekshirish (public API)
 *
 * Muhim:
 * - Bu servis identity module'ning o'z repository'laridan foydalanadi
 * - Read-only operatsiyalar IdentityQueryService orqali
 * - Tenant mavjudligi tenant-config public query API orqali validatsiya qilinadi
 * - Membership tenant-safe lookup findByIdAndTenantId orqali ta'minlanadi
 */
@Service
@Transactional
public class IdentityCommandService {

    private final MembershipRepository membershipRepository;
    private final MembershipRoleBindingRepository membershipRoleBindingRepository;
    private final RoleRepository roleRepository;
    private final AppUserRepository appUserRepository;
    private final AuditService auditService;
    private final TenantConfigQueryService tenantConfigQueryService;

    public IdentityCommandService(MembershipRepository membershipRepository,
                                   MembershipRoleBindingRepository membershipRoleBindingRepository,
                                   RoleRepository roleRepository,
                                   AppUserRepository appUserRepository,
                                   AuditService auditService,
                                   TenantConfigQueryService tenantConfigQueryService) {
        this.membershipRepository = membershipRepository;
        this.membershipRoleBindingRepository = membershipRoleBindingRepository;
        this.roleRepository = roleRepository;
        this.appUserRepository = appUserRepository;
        this.auditService = auditService;
        this.tenantConfigQueryService = tenantConfigQueryService;
    }

    /**
     * Mavjud foydalanuvchi uchun tenantda yangi a'zolik yaratadi.
     *
     * Validatsiyalar:
     * 1. Tenant mavjud bo'lishi kerak
     * 2. Foydalanuvchi global identity katalogida mavjud bo'lishi kerak
     * 3. (tenantId, userId) juftligi uchun membership allaqachon mavjud bo'lmasligi kerak
     *
     * Yangi a'zolik default status ACTIVE bilan yaratiladi.
     *
     * Concurrency: application-level pre-check + DB unique constraint fallback tarjimasi.
     *
     * @param tenantId tenant identifikatori
     * @param userId foydalanuvchi identifikatori
     * @return yaratilgan Membership
     * @throws ResourceNotFoundException tenant yoki foydalanuvchi topilmasa
     * @throws BusinessRuleException duplicate membership bo'lsa
     */
    public Membership createMembership(UUID tenantId, UUID userId) {
        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (membershipRepository.existsByTenantIdAndUserId(tenantId, user.getId())) {
            throw new BusinessRuleException("DUPLICATE_MEMBERSHIP",
                    "Tenant (id=" + tenantId + ") ichida userId=" + user.getId()
                            + " uchun membership allaqachon mavjud");
        }

        Membership membership = new Membership(tenantId, user.getId());

        try {
            membership = membershipRepository.save(membership);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateMembershipConstraint(ex)) {
                throw new BusinessRuleException("DUPLICATE_MEMBERSHIP",
                        "Tenant (id=" + tenantId + ") ichida userId=" + user.getId()
                                + " uchun membership allaqachon mavjud");
            }
            throw ex;
        }

        String newValue = user.getId() + " | " + membership.getStatus().name();
        auditService.recordEvent(tenantId, "MEMBERSHIP", membership.getId(),
                "CREATED", null, "ADMIN_API", null, newValue);

        return membership;
    }

    /**
     * A'zolikni aktiv holatga o'tkazadi.
     *
     * Ruxsat etilgan o'tish: SUSPENDED -> ACTIVE.
     * REMOVED terminal holat — aktivlashtirishga ruxsat berilmaydi.
     *
     * Idempotent: allaqachon ACTIVE bo'lsa, hech narsa o'zgarmaydi.
     *
     * Tenant-safe: membership faqat shu tenantga tegishli bo'lsa topiladi.
     *
     * @param tenantId tenant identifikatori
     * @param membershipId a'zolik identifikatori
     * @return yangilangan Membership
     * @throws ResourceNotFoundException agar a'zolik shu tenantda topilmasa
     * @throws BusinessRuleException agar membership REMOVED holatda bo'lsa
     */
    public Membership activateMembership(UUID tenantId, UUID membershipId) {
        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        Membership membership = membershipRepository.findByIdAndTenantId(membershipId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership", membershipId));

        if (membership.getStatus() == MembershipStatus.ACTIVE) {
            return membership;
        }

        if (membership.getStatus() == MembershipStatus.REMOVED) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "REMOVED holatdagi membership aktivlashtirilmaydi (membershipId="
                            + membershipId + ")");
        }

        MembershipStatus oldStatus = membership.getStatus();
        membership.setStatus(MembershipStatus.ACTIVE);
        membership = membershipRepository.save(membership);

        auditService.recordEvent(tenantId, "MEMBERSHIP", membership.getId(),
                "ACTIVATED", null, "ADMIN_API", oldStatus.name(), MembershipStatus.ACTIVE.name());

        return membership;
    }

    /**
     * A'zolikni SUSPENDED holatga o'tkazadi.
     *
     * Ruxsat etilgan o'tish: ACTIVE -> SUSPENDED.
     * REMOVED terminal holat — to'xtatishga ruxsat berilmaydi.
     *
     * Idempotent: allaqachon SUSPENDED bo'lsa, hech narsa o'zgarmaydi.
     *
     * Tenant-safe: membership faqat shu tenantga tegishli bo'lsa topiladi.
     *
     * @param tenantId tenant identifikatori
     * @param membershipId a'zolik identifikatori
     * @return yangilangan Membership
     * @throws ResourceNotFoundException agar a'zolik shu tenantda topilmasa
     * @throws BusinessRuleException agar membership REMOVED holatda bo'lsa
     */
    public Membership suspendMembership(UUID tenantId, UUID membershipId) {
        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        Membership membership = membershipRepository.findByIdAndTenantId(membershipId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership", membershipId));

        if (membership.getStatus() == MembershipStatus.SUSPENDED) {
            return membership;
        }

        if (membership.getStatus() == MembershipStatus.REMOVED) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "REMOVED holatdagi membership to'xtatilmaydi (membershipId="
                            + membershipId + ")");
        }

        MembershipStatus oldStatus = membership.getStatus();
        membership.setStatus(MembershipStatus.SUSPENDED);
        membership = membershipRepository.save(membership);

        auditService.recordEvent(tenantId, "MEMBERSHIP", membership.getId(),
                "SUSPENDED", null, "ADMIN_API", oldStatus.name(), MembershipStatus.SUSPENDED.name());

        return membership;
    }

    /**
     * A'zolikni REMOVED holatga o'tkazadi (lifecycle status transition — hard delete emas).
     *
     * Idempotent: allaqachon REMOVED bo'lsa, hech narsa o'zgarmaydi.
     * Tenant-safe lookup.
     *
     * @param tenantId tenant identifikatori
     * @param membershipId a'zolik identifikatori
     * @return yangilangan Membership
     * @throws ResourceNotFoundException tenant yoki membership topilmasa
     */
    public Membership removeMembership(UUID tenantId, UUID membershipId) {
        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        Membership membership = membershipRepository.findByIdAndTenantId(membershipId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership", membershipId));

        if (membership.getStatus() == MembershipStatus.REMOVED) {
            return membership;
        }

        MembershipStatus oldStatus = membership.getStatus();
        membership.setStatus(MembershipStatus.REMOVED);
        membership = membershipRepository.save(membership);

        auditService.recordEvent(tenantId, "MEMBERSHIP", membership.getId(),
                "REMOVED", null, "ADMIN_API", oldStatus.name(), MembershipStatus.REMOVED.name());

        return membership;
    }

    // ========== MembershipRoleBinding operations ==========

    /**
     * A'zolikka global rolni tayinlaydi (membership-role binding yaratadi).
     *
     * Validatsiyalar:
     * 1. Tenant mavjud bo'lishi kerak
     * 2. Membership shu tenantga tegishli bo'lishi kerak (tenant-safe)
     * 3. Membership REMOVED holatda bo'lmasligi kerak (terminal holat — yangi rol qo'shib bo'lmaydi)
     * 4. Rol global katalogda mavjud bo'lishi kerak
     * 5. Shu (membership, role) juftligi uchun binding allaqachon mavjud bo'lmasligi kerak
     *
     * Concurrency: application-level pre-check + DB unique constraint fallback tarjimasi.
     *
     * @param tenantId tenant identifikatori
     * @param membershipId a'zolik identifikatori
     * @param roleId rol identifikatori
     * @return yaratilgan MembershipRoleBinding
     * @throws ResourceNotFoundException tenant, membership yoki rol topilmasa
     * @throws BusinessRuleException membership REMOVED holatda yoki duplicate binding bo'lsa
     */
    public MembershipRoleBinding assignRoleToMembership(UUID tenantId, UUID membershipId, UUID roleId) {
        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        Membership membership = membershipRepository.findByIdAndTenantId(membershipId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership", membershipId));

        if (membership.getStatus() == MembershipStatus.REMOVED) {
            throw new BusinessRuleException("INVALID_MEMBERSHIP_STATUS",
                    "REMOVED holatdagi membershipga yangi rol tayinlab bo'lmaydi (membershipId="
                            + membershipId + ")");
        }

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));

        if (membershipRoleBindingRepository.existsByMembershipIdAndRoleId(membershipId, roleId)) {
            throw new BusinessRuleException("DUPLICATE_MEMBERSHIP_ROLE",
                    "Membership (id=" + membershipId + ") uchun rol (id=" + roleId
                            + ") allaqachon tayinlangan");
        }

        MembershipRoleBinding binding = new MembershipRoleBinding(membership, role);

        try {
            binding = membershipRoleBindingRepository.save(binding);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateMembershipRoleConstraint(ex)) {
                throw new BusinessRuleException("DUPLICATE_MEMBERSHIP_ROLE",
                        "Membership (id=" + membershipId + ") uchun rol (id=" + roleId
                                + ") allaqachon tayinlangan");
            }
            throw ex;
        }

        auditService.recordEvent(tenantId, "MEMBERSHIP_ROLE_BINDING", binding.getId(),
                "CREATED", null, "ADMIN_API", null, role.getCode());

        return binding;
    }

    /**
     * A'zolikdan rolni olib tashlaydi (membership-role binding o'chiradi).
     *
     * Barcha statuslarda (ACTIVE, SUSPENDED, REMOVED) ruxsat etiladi —
     * REMOVED holatdagi membershiplar uchun cleanup sifatida ishlaydi.
     *
     * Validatsiyalar:
     * 1. Tenant mavjud bo'lishi kerak
     * 2. Membership shu tenantga tegishli bo'lishi kerak (tenant-safe)
     * 3. Binding shu membership kontekstida mavjud bo'lishi kerak
     *
     * @param tenantId tenant identifikatori
     * @param membershipId a'zolik identifikatori
     * @param roleId rol identifikatori
     * @throws ResourceNotFoundException tenant, membership yoki binding topilmasa
     */
    public void unassignRoleFromMembership(UUID tenantId, UUID membershipId, UUID roleId) {
        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        membershipRepository.findByIdAndTenantId(membershipId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership", membershipId));

        MembershipRoleBinding binding = membershipRoleBindingRepository
                .findByMembershipIdAndRoleId(membershipId, roleId)
                .orElseThrow(() -> new ResourceNotFoundException("MembershipRoleBinding",
                        "membershipId=" + membershipId + ",roleId=" + roleId));

        UUID bindingId = binding.getId();
        String roleCode = binding.getRole() != null ? binding.getRole().getCode() : null;

        membershipRoleBindingRepository.delete(binding);

        auditService.recordEvent(tenantId, "MEMBERSHIP_ROLE_BINDING", bindingId,
                "DELETED", null, "ADMIN_API", roleCode, null);
    }

    /**
     * DataIntegrityViolationException membership (tenant_id, user_id) unique
     * constraint violation ekanligini tekshiradi.
     */
    private static boolean isDuplicateMembershipConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            return constraintName != null
                    && constraintName.contains("membership")
                    && constraintName.contains("tenant_id")
                    && constraintName.contains("user_id");
        }
        return false;
    }

    /**
     * DataIntegrityViolationException membership_role_binding (membership_id, role_id) unique
     * constraint violation ekanligini tekshiradi.
     */
    private static boolean isDuplicateMembershipRoleConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            return constraintName != null
                    && constraintName.contains("membership_role_binding")
                    && constraintName.contains("membership_id")
                    && constraintName.contains("role_id");
        }
        return false;
    }
}
