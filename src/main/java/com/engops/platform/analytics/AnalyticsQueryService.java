package com.engops.platform.analytics;

import com.engops.platform.admin.AdminAuthorizationService;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.workitem.repository.WorkItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Phase 205 — read-only analytics query service.
 *
 * <p>Aggregate query'lar uchun yagona kirish nuqtasi. Hozircha 3 ta
 * dimension: {@code current_status_code}, {@code type_code},
 * {@code severity_code}. Har bir method:</p>
 * <ol>
 *   <li>{@code tenantId} non-null tekshiradi (INVALID_TENANT_ID).</li>
 *   <li>{@link AdminAuthorizationService#authorizeRead(UUID, UUID)} —
 *       mavjud {@code TENANT_CONFIG_READ} ruxsati tekshiriladi (Phase 205
 *       D3 deviation report'da: {@code WORK_ITEM_READ} permission'i mavjud
 *       emas; loyihaning barcha read facade'lari shu pattern bilan ishlaydi).</li>
 *   <li>Repository aggregate query'sini chaqiradi (Phase 205 D5 — mavjud
 *       {@link WorkItemRepository}'ga additive metodlar qo'shildi).</li>
 *   <li>Bucket'larni count DESC, label ASC bo'yicha tartiblaydi
 *       (Phase 205 D12 — deterministic invariant).</li>
 *   <li>{@code totalCount = sum(bucket.count)} hisoblaydi.</li>
 * </ol>
 *
 * <p>Read-only — hech qanday audit qatori yozilmaydi (loyihaning mavjud
 * query servisi konvensiyasiga muvofiq, Phase 205 D10).</p>
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsQueryService {

    private static final Comparator<AnalyticsBucket> BUCKET_ORDER =
            Comparator.comparingLong(AnalyticsBucket::count).reversed()
                    .thenComparing(AnalyticsBucket::label);

    private final WorkItemRepository workItemRepository;
    private final AdminAuthorizationService adminAuthorizationService;

    public AnalyticsQueryService(WorkItemRepository workItemRepository,
                                  AdminAuthorizationService adminAuthorizationService) {
        this.workItemRepository = workItemRepository;
        this.adminAuthorizationService = adminAuthorizationService;
    }

    public AnalyticsAggregateResult workItemsByStatus(UUID tenantId, UUID actorUserId) {
        requireTenantId(tenantId);
        adminAuthorizationService.authorizeRead(tenantId, actorUserId);
        return aggregate(tenantId,
                workItemRepository.countWorkItemsByCurrentStatusCode(tenantId));
    }

    public AnalyticsAggregateResult workItemsByType(UUID tenantId, UUID actorUserId) {
        requireTenantId(tenantId);
        adminAuthorizationService.authorizeRead(tenantId, actorUserId);
        return aggregate(tenantId,
                workItemRepository.countWorkItemsByTypeCode(tenantId));
    }

    public AnalyticsAggregateResult workItemsBySeverity(UUID tenantId, UUID actorUserId) {
        requireTenantId(tenantId);
        adminAuthorizationService.authorizeRead(tenantId, actorUserId);
        return aggregate(tenantId,
                workItemRepository.countWorkItemsBySeverityCode(tenantId));
    }

    private static void requireTenantId(UUID tenantId) {
        if (tenantId == null) {
            throw new BusinessRuleException("INVALID_TENANT_ID", "tenantId majburiy");
        }
    }

    private static AnalyticsAggregateResult aggregate(
            UUID tenantId, List<AnalyticsBucketProjection> rows) {
        List<AnalyticsBucket> buckets = rows.stream()
                .filter(p -> p != null && p.getLabel() != null)
                .map(p -> new AnalyticsBucket(p.getLabel(), p.getCount()))
                .sorted(BUCKET_ORDER)
                .toList();
        long total = buckets.stream().mapToLong(AnalyticsBucket::count).sum();
        return new AnalyticsAggregateResult(tenantId, total, buckets);
    }
}
