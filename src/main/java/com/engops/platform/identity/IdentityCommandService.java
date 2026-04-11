package com.engops.platform.identity;

import com.engops.platform.audit.AuditService;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipRoleBinding;
import com.engops.platform.identity.model.MembershipStatus;
import com.engops.platform.identity.model.Role;
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
    private final AuditService auditService;
    private final TenantConfigQueryService tenantConfigQueryService;

    public IdentityCommandService(MembershipRepository membershipRepository,
                                   MembershipRoleBindingRepository membershipRoleBindingRepository,
                                   RoleRepository roleRepository,
                                   AuditService auditService,
                                   TenantConfigQueryService tenantConfigQueryService) {
        this.membershipRepository = membershipRepository;
        this.membershipRoleBindingRepository = membershipRoleBindingRepository;
        this.roleRepository = roleRepository;
        this.auditService = auditService;
        this.tenantConfigQueryService = tenantConfigQueryService;
    }

    /**
     * A'zolikni aktiv holatga o'tkazadi.
     *
     * Idempotent: allaqachon ACTIVE bo'lsa, hech narsa o'zgarmaydi.
     *
     * Tenant-safe: membership faqat shu tenantga tegishli bo'lsa topiladi.
     *
     * @param tenantId tenant identifikatori
     * @param membershipId a'zolik identifikatori
     * @return yangilangan Membership
     * @throws ResourceNotFoundException agar a'zolik shu tenantda topilmasa
     */
    public Membership activateMembership(UUID tenantId, UUID membershipId) {
        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        Membership membership = membershipRepository.findByIdAndTenantId(membershipId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership", membershipId));

        if (membership.getStatus() == MembershipStatus.ACTIVE) {
            return membership;
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
     * Idempotent: allaqachon SUSPENDED bo'lsa, hech narsa o'zgarmaydi.
     *
     * Tenant-safe: membership faqat shu tenantga tegishli bo'lsa topiladi.
     *
     * @param tenantId tenant identifikatori
     * @param membershipId a'zolik identifikatori
     * @return yangilangan Membership
     * @throws ResourceNotFoundException agar a'zolik shu tenantda topilmasa
     */
    public Membership suspendMembership(UUID tenantId, UUID membershipId) {
        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        Membership membership = membershipRepository.findByIdAndTenantId(membershipId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership", membershipId));

        if (membership.getStatus() == MembershipStatus.SUSPENDED) {
            return membership;
        }

        MembershipStatus oldStatus = membership.getStatus();
        membership.setStatus(MembershipStatus.SUSPENDED);
        membership = membershipRepository.save(membership);

        auditService.recordEvent(tenantId, "MEMBERSHIP", membership.getId(),
                "SUSPENDED", null, "ADMIN_API", oldStatus.name(), MembershipStatus.SUSPENDED.name());

        return membership;
    }

    // ========== MembershipRoleBinding operations ==========

    /**
     * A'zolikka global rolni tayinlaydi (membership-role binding yaratadi).
     *
     * Validatsiyalar:
     * 1. Tenant mavjud bo'lishi kerak
     * 2. Membership shu tenantga tegishli bo'lishi kerak (tenant-safe)
     * 3. Rol global katalogda mavjud bo'lishi kerak
     * 4. Shu (membership, role) juftligi uchun binding allaqachon mavjud bo'lmasligi kerak
     *
     * Concurrency: application-level pre-check + DB unique constraint fallback tarjimasi.
     *
     * @param tenantId tenant identifikatori
     * @param membershipId a'zolik identifikatori
     * @param roleId rol identifikatori
     * @return yaratilgan MembershipRoleBinding
     * @throws ResourceNotFoundException tenant, membership yoki rol topilmasa
     * @throws BusinessRuleException duplicate binding bo'lsa
     */
    public MembershipRoleBinding assignRoleToMembership(UUID tenantId, UUID membershipId, UUID roleId) {
        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        Membership membership = membershipRepository.findByIdAndTenantId(membershipId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership", membershipId));

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
     * Validatsiyalar:
     * 1. Tenant mavjud bo'lishi kerak
     * 2. Membership shu tenantga tegishli bo'lishi kerak (tenant-safe)
     * 3. Binding mavjud bo'lishi kerak
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
