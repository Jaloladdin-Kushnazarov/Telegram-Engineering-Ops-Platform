package com.engops.platform.workitem.repository;

import com.engops.platform.analytics.AnalyticsBucketProjection;
import com.engops.platform.workitem.model.WorkItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * WorkItem uchun repository. Barcha so'rovlar tenant-scoped.
 */
@Repository
public interface WorkItemRepository extends JpaRepository<WorkItem, UUID> {

    Optional<WorkItem> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * Phase 173 — workItemId bo'yicha faqat tenantId qaytaruvchi tor
     * cross-tenant lookup. Telegram callback orchestration uchun ishlatiladi:
     * inbound {@code callback_data} faqat {@code workItemId}'ni olib keladi
     * va tenantId server-side derive qilinishi shart (callback_data hech
     * qachon authoritative tenantId tashimaydi). Qaytarilgan tenantId
     * keyin majburiy ravishda {@code IdentityQueryService.hasActiveMembership(...)}
     * va {@code OperationalAuthorizationService.authorizeTransition(...)}
     * orqali re-check qilinadi, shuning uchun bu lookup'ning o'zi
     * cross-tenant leak xavfini keltirib chiqarmaydi — derive qilingan
     * tenantda a'zo bo'lmagan foydalanuvchi keyingi qadamda to'siladi.
     *
     * <p>Ataylab faqat tenantId UUID qaytariladi (to'liq WorkItem emas) —
     * keng cross-tenant WorkItem yuklash boundary surface'i ochilmaydi.</p>
     */
    @Query("select w.tenantId from WorkItem w where w.id = :workItemId")
    Optional<UUID> findTenantIdById(@Param("workItemId") UUID workItemId);

    Optional<WorkItem> findByTenantIdAndWorkItemCode(UUID tenantId, String workItemCode);

    List<WorkItem> findByTenantIdAndCurrentStatusCode(UUID tenantId, String statusCode);

    List<WorkItem> findByTenantIdAndTypeCode(UUID tenantId, com.engops.platform.workitem.model.WorkItemType typeCode);

    List<WorkItem> findByTenantIdAndCurrentOwnerUserId(UUID tenantId, UUID ownerUserId);

    List<WorkItem> findByTenantIdAndArchivedFalse(UUID tenantId);

    /**
     * Tenant uchun aktiv work item'larni openedAt DESC, id DESC tartibda qaytaradi.
     *
     * Deterministic ordering:
     * - openedAt DESC: eng yangi ochilgan birinchi
     * - id DESC: bir xil openedAt bo'lganda deterministic tie-breaker
     *
     * Pageable orqali natija soni cheklanadi (limit).
     *
     * @param tenantId tenant identifikatori
     * @param pageable limit uchun PageRequest
     * @return aktiv work item'lar, openedAt DESC, id DESC
     */
    List<WorkItem> findByTenantIdAndArchivedFalseOrderByOpenedAtDescIdDesc(
            UUID tenantId, Pageable pageable);

    /**
     * Tenant uchun berilgan statusdagi aktiv work item'larni deterministic tartibda qaytaradi.
     *
     * Filtering: tenantId + currentStatusCode + archived=false
     * Ordering: openedAt DESC, id DESC
     * Pageable orqali natija soni cheklanadi (limit).
     *
     * @param tenantId tenant identifikatori
     * @param statusCode holat kodi (masalan "BUGS", "PROCESSING")
     * @param pageable limit uchun PageRequest
     * @return aktiv work item'lar, openedAt DESC, id DESC
     */
    List<WorkItem> findByTenantIdAndCurrentStatusCodeAndArchivedFalseOrderByOpenedAtDescIdDesc(
            UUID tenantId, String statusCode, Pageable pageable);

    /**
     * Tenant uchun berilgan owner'dagi aktiv work item'larni deterministic tartibda qaytaradi.
     *
     * Filtering: tenantId + currentOwnerUserId + archived=false
     * Ordering: openedAt DESC, id DESC
     * Pageable orqali natija soni cheklanadi (limit).
     */
    List<WorkItem> findByTenantIdAndCurrentOwnerUserIdAndArchivedFalseOrderByOpenedAtDescIdDesc(
            UUID tenantId, UUID ownerUserId, Pageable pageable);

    /**
     * Tenant ichida berilgan turdagi work item'lar sonini qaytaradi.
     * Code generatsiya uchun ishlatiladi.
     */
    @Query("SELECT COUNT(w) FROM WorkItem w WHERE w.tenantId = :tenantId AND w.typeCode = :typeCode")
    long countByTenantIdAndTypeCode(UUID tenantId, com.engops.platform.workitem.model.WorkItemType typeCode);

    // ========== Phase 205: analytics aggregates ==========

    /**
     * Phase 205 — tenant uchun work item'larni currentStatusCode bo'yicha
     * group qiladi va har bucket uchun count qaytaradi. JPQL alias
     * {@code AS label / AS count} {@link AnalyticsBucketProjection} interface
     * projection'iga map qilinadi. Tartiblash service tomonida
     * (count DESC, label ASC determinizm uchun).
     */
    @Query("SELECT w.currentStatusCode AS label, COUNT(w) AS count "
            + "FROM WorkItem w WHERE w.tenantId = :tenantId "
            + "GROUP BY w.currentStatusCode")
    List<AnalyticsBucketProjection> countWorkItemsByCurrentStatusCode(
            @Param("tenantId") UUID tenantId);

    /**
     * Phase 205 — tenant uchun work item'larni typeCode bo'yicha group qiladi.
     * Enum {@link com.engops.platform.workitem.model.WorkItemType} string
     * sifatida qaytariladi (BUG / INCIDENT / TASK).
     */
    @Query("SELECT CAST(w.typeCode AS string) AS label, COUNT(w) AS count "
            + "FROM WorkItem w WHERE w.tenantId = :tenantId "
            + "GROUP BY w.typeCode")
    List<AnalyticsBucketProjection> countWorkItemsByTypeCode(
            @Param("tenantId") UUID tenantId);

    /**
     * Phase 205 — tenant uchun work item'larni severityCode bo'yicha group
     * qiladi. {@code severityCode IS NULL} bo'lgan rows EKSKLUD qilinadi —
     * severity belgilanmagan work item'lar bucket'da ko'rinmaydi (Phase 205 D2).
     */
    @Query("SELECT w.severityCode AS label, COUNT(w) AS count "
            + "FROM WorkItem w WHERE w.tenantId = :tenantId "
            + "AND w.severityCode IS NOT NULL "
            + "GROUP BY w.severityCode")
    List<AnalyticsBucketProjection> countWorkItemsBySeverityCode(
            @Param("tenantId") UUID tenantId);
}
