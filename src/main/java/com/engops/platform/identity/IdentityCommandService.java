package com.engops.platform.identity;

import com.engops.platform.audit.AuditService;
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
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final AppUserRepository appUserRepository;
    private final AuditService auditService;
    private final TenantConfigQueryService tenantConfigQueryService;

    public IdentityCommandService(MembershipRepository membershipRepository,
                                   MembershipRoleBindingRepository membershipRoleBindingRepository,
                                   RoleRepository roleRepository,
                                   RolePermissionRepository rolePermissionRepository,
                                   PermissionRepository permissionRepository,
                                   AppUserRepository appUserRepository,
                                   AuditService auditService,
                                   TenantConfigQueryService tenantConfigQueryService) {
        this.membershipRepository = membershipRepository;
        this.membershipRoleBindingRepository = membershipRoleBindingRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
        this.appUserRepository = appUserRepository;
        this.auditService = auditService;
        this.tenantConfigQueryService = tenantConfigQueryService;
    }

    // ========== AppUser operations ==========

    /**
     * Yangi global app_user yaratadi.
     *
     * Caller (TenantConfigWriteFacade) input'larni allaqachon normallashtirgan
     * deb taxmin qilinadi (Phase 115 mini-fix bilan o'rnatilgan pattern):
     * facade boundary'da username/displayName strip + length cap + blank → null
     * konversiyasi bajariladi. Bu service'da takroriy normalizatsiya qilinmaydi —
     * yagona caller pattern'i bo'lsa, defensive in-service normalizatsiya
     * ortiqcha bo'ladi va Phase 115/116/118 bilan zid bo'ladi.
     *
     * Telegram username asosiy identity emas — telegramUserId yagona ishonchli
     * tashqi identifikator (loyiha xavfsizlik qoidasi). DB constraint:
     * UNIQUE on app_user.telegram_user_id.
     *
     * Audit: tenantId argumenti `null` — AppUser global root identity resurs.
     * Bu role catalog (ROLE/CREATED) audit shape bilan bir xil.
     *
     * @param telegramUserId Telegram identifikatori (positive long, unique)
     * @param username normallashgan Telegram username (nullable; facade null/blank
     *                  bo'lsa null qaytaradi, aks holda max 255 bilan strip)
     * @param displayName normallashgan ko'rinish nomi (nullable, max 255)
     * @return yaratilgan AppUser
     * @throws BusinessRuleException agar telegramUserId allaqachon mavjud bo'lsa
     */
    public AppUser createAppUser(Long telegramUserId, String username, String displayName) {
        if (appUserRepository.existsByTelegramUserId(telegramUserId)) {
            throw new BusinessRuleException("DUPLICATE_TELEGRAM_USER_ID",
                    "telegramUserId=" + telegramUserId + " bilan foydalanuvchi allaqachon mavjud");
        }

        AppUser user = new AppUser(telegramUserId, displayName);
        if (username != null) {
            user.setUsername(username);
        }

        try {
            user = appUserRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateTelegramUserIdConstraint(ex)) {
                throw new BusinessRuleException("DUPLICATE_TELEGRAM_USER_ID",
                        "telegramUserId=" + telegramUserId + " bilan foydalanuvchi allaqachon mavjud");
            }
            throw ex;
        }

        auditService.recordEvent(null, "APP_USER", user.getId(),
                "CREATED", null, "ADMIN_API", null, telegramUserId.toString());

        return user;
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

    // ========== Global Role catalog operations ==========

    /**
     * Global rol katalogida yangi rol yaratadi.
     *
     * Validatsiyalar:
     * 1. code unique bo'lishi kerak (application pre-check + DB constraint fallback)
     *
     * Yangi rol default active=true, systemRole=false bilan yaratiladi.
     * Code uppercase normalize qilinadi.
     *
     * @param code rol kodi (unique, uppercase normalize qilinadi)
     * @param name rol nomi
     * @param description ixtiyoriy tavsif (nullable)
     * @return yaratilgan Role
     * @throws BusinessRuleException duplicate code bo'lsa
     */
    public Role createRole(String code, String name, String description) {
        String normalizedCode = code.strip().toUpperCase(java.util.Locale.ROOT);
        String normalizedName = name.strip();
        String normalizedDesc = (description != null && !description.isBlank())
                ? description.strip() : null;

        if (roleRepository.existsByCode(normalizedCode)) {
            throw new BusinessRuleException("DUPLICATE_ROLE_CODE",
                    "'" + normalizedCode + "' kodli rol allaqachon mavjud");
        }

        Role role = new Role(normalizedCode, normalizedName);
        if (normalizedDesc != null) {
            role.setDescription(normalizedDesc);
        }

        try {
            role = roleRepository.save(role);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateRoleCodeConstraint(ex)) {
                throw new BusinessRuleException("DUPLICATE_ROLE_CODE",
                        "'" + normalizedCode + "' kodli rol allaqachon mavjud");
            }
            throw ex;
        }

        auditService.recordEvent(null, "ROLE", role.getId(),
                "CREATED", null, "ADMIN_API", null,
                normalizedCode + " | " + normalizedName);

        return role;
    }

    /**
     * Global rol metadata'sini partial yangilaydi (PATCH semantikasi).
     *
     * Faqat provided=true field'lar yangilanadi.
     * Code va systemRole o'zgartirilmaydi (immutable).
     *
     * @param roleId rol identifikatori
     * @param name yangi nom (faqat nameProvided=true bo'lganda)
     * @param nameProvided name field berilganmi
     * @param description yangi tavsif (faqat descriptionProvided=true bo'lganda)
     * @param descriptionProvided description field berilganmi
     * @return yangilangan Role
     * @throws ResourceNotFoundException rol topilmasa
     */
    public Role updateRole(UUID roleId, String name, boolean nameProvided,
                            String description, boolean descriptionProvided) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));

        if (role.isSystemRole()) {
            throw new BusinessRuleException("SYSTEM_ROLE_UPDATE_FORBIDDEN",
                    "Tizim roli metadata'si o'zgartirilmaydi (roleId=" + roleId + ")");
        }

        String oldName = role.getName();
        String oldDescription = role.getDescription();

        if (nameProvided) {
            role.setName(name.strip());
        }

        if (descriptionProvided) {
            role.setDescription(description != null && !description.isBlank()
                    ? description.strip() : null);
        }

        role = roleRepository.save(role);

        String oldValue = oldName + (oldDescription != null ? " | " + oldDescription : "");
        String newValue = role.getName() + (role.getDescription() != null ? " | " + role.getDescription() : "");

        auditService.recordEvent(null, "ROLE", role.getId(),
                "UPDATED", null, "ADMIN_API", oldValue, newValue);

        return role;
    }

    /**
     * Global rolni aktiv holatga o'tkazadi.
     *
     * Idempotent: allaqachon aktiv bo'lsa, hech narsa o'zgarmaydi.
     *
     * @param roleId rol identifikatori
     * @return yangilangan Role
     * @throws ResourceNotFoundException rol topilmasa
     */
    public Role activateRole(UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));

        if (role.isActive()) {
            return role;
        }

        role.setActive(true);
        role = roleRepository.save(role);

        auditService.recordEvent(null, "ROLE", role.getId(),
                "ACTIVATED", null, "ADMIN_API", "false", "true");

        return role;
    }

    /**
     * Global rolni noaktiv holatga o'tkazadi.
     *
     * Idempotent: allaqachon noaktiv bo'lsa, hech narsa o'zgarmaydi.
     *
     * @param roleId rol identifikatori
     * @return yangilangan Role
     * @throws ResourceNotFoundException rol topilmasa
     */
    public Role deactivateRole(UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));

        if (role.isSystemRole()) {
            throw new BusinessRuleException("SYSTEM_ROLE_DEACTIVATE_FORBIDDEN",
                    "Tizim roli deaktivlashtirilmaydi (roleId=" + roleId + ")");
        }

        if (!role.isActive()) {
            return role;
        }

        role.setActive(false);
        role = roleRepository.save(role);

        auditService.recordEvent(null, "ROLE", role.getId(),
                "DEACTIVATED", null, "ADMIN_API", "true", "false");

        return role;
    }

    /**
     * Global rolni o'chiradi (hard delete).
     *
     * Validatsiyalar:
     * 1. Rol mavjud bo'lishi kerak
     * 2. Tizim roli (systemRole=true) o'chirilmaydi
     * 3. Birorta membership-role binding mavjud bo'lsa, o'chirilmaydi
     *
     * @param roleId rol identifikatori
     * @throws ResourceNotFoundException rol topilmasa
     * @throws BusinessRuleException tizim roli yoki binding mavjud bo'lsa
     */
    public void deleteRole(UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));

        if (role.isSystemRole()) {
            throw new BusinessRuleException("SYSTEM_ROLE_DELETE_FORBIDDEN",
                    "Tizim roli o'chirilmaydi (roleId=" + roleId + ")");
        }

        if (membershipRoleBindingRepository.existsByRoleId(roleId)) {
            throw new BusinessRuleException("ROLE_IN_USE",
                    "Rol hozirda membership'larga tayinlangan, o'chirilmaydi (roleId=" + roleId + ")");
        }

        String oldValue = role.getCode() + " | " + role.getName();

        try {
            roleRepository.delete(role);
            roleRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            if (isRoleReferencedByBindingConstraint(ex)) {
                throw new BusinessRuleException("ROLE_IN_USE",
                        "Rol hozirda membership'larga tayinlangan, o'chirilmaydi (roleId=" + roleId + ")");
            }
            // Phase 94 role-permission write surface qo'shgandan keyin role
            // ham role_permission jadvali tomonidan FK orqali ushlab turilishi
            // mumkin. Phase 78 dagi pre-check membership_role_binding'ni
            // qoplagan, lekin role_permission'ni qoplamagan — admin permission
            // biriktirilgan rolni o'chirmoqchi bo'lsa raw DB exception 500
            // bo'lib chiqib qolardi. Endi shu holat ham clean ROLE_IN_USE 422.
            if (isRoleReferencedByRolePermissionConstraint(ex)) {
                throw new BusinessRuleException("ROLE_IN_USE",
                        "Rolga ruxsatlar biriktirilgan, o'chirilmaydi (roleId=" + roleId + ")");
            }
            throw ex;
        }

        auditService.recordEvent(null, "ROLE", role.getId(),
                "DELETED", null, "ADMIN_API", oldValue, null);
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

    // ========== RolePermission operations ==========

    /**
     * Global rolga ruxsat (permission) tayinlaydi (role-permission binding yaratadi).
     *
     * Validatsiyalar:
     * 1. Rol global katalogda mavjud bo'lishi kerak
     * 2. Ruxsat global katalogda mavjud bo'lishi kerak
     * 3. Shu (role, permission) juftligi uchun binding allaqachon mavjud bo'lmasligi kerak
     *
     * Concurrency: application-level pre-check + DB unique constraint fallback tarjimasi.
     *
     * @param roleId rol identifikatori
     * @param permissionId ruxsat identifikatori
     * @return yaratilgan RolePermission
     * @throws ResourceNotFoundException rol yoki ruxsat topilmasa
     * @throws BusinessRuleException duplicate binding bo'lsa
     */
    public RolePermission assignPermissionToRole(UUID roleId, UUID permissionId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));

        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", permissionId));

        if (rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permissionId)) {
            throw new BusinessRuleException("DUPLICATE_ROLE_PERMISSION",
                    "Rol (id=" + roleId + ") uchun ruxsat (id=" + permissionId
                            + ") allaqachon tayinlangan");
        }

        RolePermission binding = new RolePermission(role, permission);

        try {
            binding = rolePermissionRepository.save(binding);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateRolePermissionConstraint(ex)) {
                throw new BusinessRuleException("DUPLICATE_ROLE_PERMISSION",
                        "Rol (id=" + roleId + ") uchun ruxsat (id=" + permissionId
                                + ") allaqachon tayinlangan");
            }
            throw ex;
        }

        auditService.recordEvent(null, "ROLE_PERMISSION", binding.getId(),
                "CREATED", null, "ADMIN_API", null, permission.getCode());

        return binding;
    }

    /**
     * Global roldan ruxsatni olib tashlaydi (role-permission binding o'chiradi).
     *
     * Validatsiyalar:
     * 1. Rol global katalogda mavjud bo'lishi kerak
     * 2. Ruxsat global katalogda mavjud bo'lishi kerak
     * 3. Binding shu (role, permission) juftligi uchun mavjud bo'lishi kerak
     *
     * @param roleId rol identifikatori
     * @param permissionId ruxsat identifikatori
     * @throws ResourceNotFoundException rol, ruxsat yoki binding topilmasa
     */
    public void unassignPermissionFromRole(UUID roleId, UUID permissionId) {
        roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));

        permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", permissionId));

        RolePermission binding = rolePermissionRepository
                .findByRoleIdAndPermissionId(roleId, permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("RolePermission",
                        "roleId=" + roleId + ",permissionId=" + permissionId));

        UUID bindingId = binding.getId();
        String permissionCode = binding.getPermission() != null ? binding.getPermission().getCode() : null;

        rolePermissionRepository.delete(binding);

        auditService.recordEvent(null, "ROLE_PERMISSION", bindingId,
                "DELETED", null, "ADMIN_API", permissionCode, null);
    }

    /**
     * DataIntegrityViolationException app_user.telegram_user_id unique constraint
     * violation ekanligini tekshiradi.
     *
     * PostgreSQL avtomatik nomi: app_user_telegram_user_id_key (qisqa, kesilmaydi).
     * Pattern truncation'ga ham chidamli — faqat ikkita kalit bo'lakni tekshiradi.
     */
    private static boolean isDuplicateTelegramUserIdConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            return constraintName != null
                    && constraintName.contains("app_user")
                    && constraintName.contains("telegram_user_id");
        }
        return false;
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

    /**
     * DataIntegrityViolationException role o'chirishda membership_role_binding FK
     * reference violation ekanligini tekshiradi.
     */
    private static boolean isRoleReferencedByBindingConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            return constraintName != null
                    && constraintName.contains("membership_role_binding")
                    && constraintName.contains("role_id");
        }
        return false;
    }

    /**
     * DataIntegrityViolationException role o'chirishda role_permission jadvali FK
     * reference violation ekanligini tekshiradi (Phase 94 role-permission write
     * surface'idan kelib chiqadigan inbound FK).
     */
    private static boolean isRoleReferencedByRolePermissionConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            return constraintName != null
                    && constraintName.contains("role_permission")
                    && constraintName.contains("role_id");
        }
        return false;
    }

    /**
     * DataIntegrityViolationException role code unique constraint violation ekanligini tekshiradi.
     */
    private static boolean isDuplicateRoleCodeConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            return constraintName != null
                    && constraintName.contains("role")
                    && constraintName.contains("code");
        }
        return false;
    }

    /**
     * DataIntegrityViolationException role_permission (role_id, permission_id) unique
     * constraint violation ekanligini tekshiradi.
     */
    private static boolean isDuplicateRolePermissionConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            return constraintName != null
                    && constraintName.contains("role_permission")
                    && constraintName.contains("role_id")
                    && constraintName.contains("permission_id");
        }
        return false;
    }
}
