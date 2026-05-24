package com.engops.platform.platform;

import com.engops.platform.tenantconfig.repository.TenantRepository;
import com.engops.platform.workitem.OperationalAuthorizationService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 217a — PLATFORM_OWNER scope tenant read service.
 *
 * <p>Platform darajasidagi cross-tenant ko'rish uchun ajratilgan service.
 * Per-tenant {@link com.engops.platform.tenantconfig.TenantConfigQueryService}
 * tenant ichidagi konfiguratsiya uchun mo'ljallangan (per-tenant
 * authorization). Bu service esa BARCHA tenantlarni qaytaradi va
 * {@code PLATFORM_TENANT_LIST} global permission orqali himoyalanadi.</p>
 *
 * <p><strong>Authorization model:</strong> har bir public metod
 * {@link OperationalAuthorizationService#authorizeGlobal(UUID, String)}'ni
 * birinchi qadamda chaqiradi. Ruxsat yo'q bo'lsa
 * {@link com.engops.platform.sharedkernel.exception.AccessDeniedException}
 * tashlanadi va DB so'rovi umuman bajarilmaydi (fail fast).</p>
 *
 * <p><strong>Phase 216 RBAC cutover bilan integratsiya:</strong>
 * {@code authorizeGlobal} endi faqat {@code AppUserRoleBinding} (V9 jadval)
 * orqali platform-level rollarni tekshiradi. PLATFORM_OWNER role V9'da
 * seed qilingan va {@code PLATFORM_TENANT_LIST} ruxsatiga ega.</p>
 *
 * <p>Phase 217b'da REST/HTMX controller (PlatformWebController) shu
 * service'ni ishlatib platform admin sahifasini render qiladi.</p>
 */
@Service
@Transactional(readOnly = true)
public class PlatformTenantQueryService {

    public static final String PLATFORM_TENANT_LIST = "PLATFORM_TENANT_LIST";

    private final TenantRepository tenantRepository;
    private final OperationalAuthorizationService authorizationService;

    public PlatformTenantQueryService(TenantRepository tenantRepository,
                                       OperationalAuthorizationService authorizationService) {
        this.tenantRepository = tenantRepository;
        this.authorizationService = authorizationService;
    }

    /**
     * Barcha tenantlarni {@code createdAt DESC} tartibida qaytaradi
     * (eng yangi tenant birinchi).
     *
     * @param actorUserId joriy actor identifikatori (JWT'dan kelgan)
     * @return barcha tenantlar uchun {@link PlatformTenantSummary} ro'yxati
     *         (bo'sh bo'lishi mumkin agar DB'da hech qanday tenant yo'q bo'lsa)
     * @throws com.engops.platform.sharedkernel.exception.AccessDeniedException
     *         agar actor'da {@code PLATFORM_TENANT_LIST} ruxsati bo'lmasa
     */
    public List<PlatformTenantSummary> listAllTenants(UUID actorUserId) {
        authorizationService.authorizeGlobal(actorUserId, PLATFORM_TENANT_LIST);
        return tenantRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(PlatformTenantSummary::from)
                .toList();
    }

    /**
     * Berilgan ID bo'yicha bitta tenantni qaytaradi.
     *
     * @param actorUserId joriy actor identifikatori
     * @param tenantId qidirilayotgan tenant identifikatori
     * @return {@link Optional} mavjud tenant uchun, aks holda {@code Optional.empty()}
     * @throws com.engops.platform.sharedkernel.exception.AccessDeniedException
     *         agar actor'da {@code PLATFORM_TENANT_LIST} ruxsati bo'lmasa
     */
    public Optional<PlatformTenantSummary> findById(UUID actorUserId, UUID tenantId) {
        authorizationService.authorizeGlobal(actorUserId, PLATFORM_TENANT_LIST);
        return tenantRepository.findById(tenantId)
                .map(PlatformTenantSummary::from);
    }
}
