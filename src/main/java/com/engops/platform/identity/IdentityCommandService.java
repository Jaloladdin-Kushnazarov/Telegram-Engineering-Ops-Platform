package com.engops.platform.identity;

import com.engops.platform.audit.AuditService;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipStatus;
import com.engops.platform.identity.repository.MembershipRepository;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
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
    private final AuditService auditService;
    private final TenantConfigQueryService tenantConfigQueryService;

    public IdentityCommandService(MembershipRepository membershipRepository,
                                   AuditService auditService,
                                   TenantConfigQueryService tenantConfigQueryService) {
        this.membershipRepository = membershipRepository;
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
}
