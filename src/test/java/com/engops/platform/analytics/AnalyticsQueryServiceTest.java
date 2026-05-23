package com.engops.platform.analytics;

import com.engops.platform.admin.AdminAuthorizationService;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.workitem.repository.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 205 — {@link AnalyticsQueryService} unit testlari. Repository va
 * AdminAuthorizationService mock; sorting determinism, NULL severity
 * exclusion, validation, va authorization yo'llari qoplanadi.
 */
class AnalyticsQueryServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final WorkItemRepository workItemRepository = mock(WorkItemRepository.class);
    private final AdminAuthorizationService adminAuthorizationService =
            mock(AdminAuthorizationService.class);

    private final AnalyticsQueryService service = new AnalyticsQueryService(
            workItemRepository, adminAuthorizationService);

    private static AnalyticsBucketProjection bucket(String label, long count) {
        AnalyticsBucketProjection p = mock(AnalyticsBucketProjection.class);
        when(p.getLabel()).thenReturn(label);
        when(p.getCount()).thenReturn(count);
        return p;
    }

    // ========== Happy paths (one per endpoint) ==========

    @Test
    void workItemsByStatus_happyPath_returnsSortedBucketsAndTotalCount() {
        // Build mocks BEFORE outer when()...thenReturn() to avoid Mockito
        // "unfinished stubbing" detection (nested when() calls inside
        // List.of(...) argument expressions confuse the framework).
        AnalyticsBucketProjection b1 = bucket("RESOLVED", 23);
        AnalyticsBucketProjection b2 = bucket("REPORTED", 12);
        AnalyticsBucketProjection b3 = bucket("IN_PROGRESS", 4);
        when(workItemRepository.countWorkItemsByCurrentStatusCode(TENANT_ID))
                .thenReturn(List.of(b1, b2, b3));

        AnalyticsAggregateResult result = service.workItemsByStatus(TENANT_ID, ACTOR_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.totalCount()).isEqualTo(39);
        assertThat(result.buckets()).extracting(AnalyticsBucket::label)
                .containsExactly("RESOLVED", "REPORTED", "IN_PROGRESS");
        verify(adminAuthorizationService).authorizeRead(TENANT_ID, ACTOR_ID);
    }

    @Test
    void workItemsByType_happyPath_returnsAllTypeBucketsAndTotal() {
        AnalyticsBucketProjection b1 = bucket("BUG", 10);
        AnalyticsBucketProjection b2 = bucket("INCIDENT", 5);
        AnalyticsBucketProjection b3 = bucket("TASK", 3);
        when(workItemRepository.countWorkItemsByTypeCode(TENANT_ID))
                .thenReturn(List.of(b1, b2, b3));

        AnalyticsAggregateResult result = service.workItemsByType(TENANT_ID, ACTOR_ID);

        assertThat(result.totalCount()).isEqualTo(18);
        assertThat(result.buckets()).hasSize(3);
        assertThat(result.buckets().get(0).label()).isEqualTo("BUG");
    }

    @Test
    void workItemsBySeverity_happyPath_returnsSortedBuckets() {
        AnalyticsBucketProjection b1 = bucket("HIGH", 7);
        AnalyticsBucketProjection b2 = bucket("CRITICAL", 3);
        AnalyticsBucketProjection b3 = bucket("MEDIUM", 2);
        when(workItemRepository.countWorkItemsBySeverityCode(TENANT_ID))
                .thenReturn(List.of(b1, b2, b3));

        AnalyticsAggregateResult result = service.workItemsBySeverity(TENANT_ID, ACTOR_ID);

        assertThat(result.totalCount()).isEqualTo(12);
        assertThat(result.buckets()).extracting(AnalyticsBucket::label)
                .containsExactly("HIGH", "CRITICAL", "MEDIUM");
    }

    // ========== Sorting determinism ==========

    @Test
    void bucketOrdering_sortsByCountDescendingThenLabelAscending() {
        AnalyticsBucketProjection b1 = bucket("ZEBRA", 5);
        AnalyticsBucketProjection b2 = bucket("APPLE", 5);
        AnalyticsBucketProjection b3 = bucket("MANGO", 8);
        AnalyticsBucketProjection b4 = bucket("BANANA", 5);
        when(workItemRepository.countWorkItemsByCurrentStatusCode(TENANT_ID))
                .thenReturn(List.of(b1, b2, b3, b4));

        AnalyticsAggregateResult result = service.workItemsByStatus(TENANT_ID, ACTOR_ID);

        assertThat(result.buckets()).extracting(AnalyticsBucket::label)
                .containsExactly("MANGO", "APPLE", "BANANA", "ZEBRA");
    }

    @Test
    void bucketOrdering_outputStableAcrossUnorderedInput() {
        AnalyticsBucketProjection b1 = bucket("LOW", 1);
        AnalyticsBucketProjection b2 = bucket("HIGH", 7);
        AnalyticsBucketProjection b3 = bucket("CRITICAL", 3);
        AnalyticsBucketProjection b4 = bucket("MEDIUM", 7);
        when(workItemRepository.countWorkItemsByCurrentStatusCode(TENANT_ID))
                .thenReturn(List.of(b1, b2, b3, b4));

        AnalyticsAggregateResult result = service.workItemsByStatus(TENANT_ID, ACTOR_ID);

        assertThat(result.buckets()).extracting(AnalyticsBucket::label)
                .containsExactly("HIGH", "MEDIUM", "CRITICAL", "LOW");
    }

    // ========== Empty tenant ==========

    @Test
    void emptyTenant_returnsZeroTotalAndEmptyBuckets() {
        when(workItemRepository.countWorkItemsByCurrentStatusCode(TENANT_ID))
                .thenReturn(List.of());

        AnalyticsAggregateResult result = service.workItemsByStatus(TENANT_ID, ACTOR_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.totalCount()).isEqualTo(0);
        assertThat(result.buckets()).isEmpty();
    }

    // ========== NULL severity exclusion ==========

    @Test
    void severityWithNullLabelRow_isFilteredOutEvenIfReturned() {
        // Repository query SHOULD already exclude NULL via WHERE clause,
        // but the service layer also drops null labels defensively.
        AnalyticsBucketProjection nullLabel = mock(AnalyticsBucketProjection.class);
        when(nullLabel.getLabel()).thenReturn(null);
        when(nullLabel.getCount()).thenReturn(99L);
        AnalyticsBucketProjection highBucket = bucket("HIGH", 4);

        when(workItemRepository.countWorkItemsBySeverityCode(TENANT_ID))
                .thenReturn(List.of(highBucket, nullLabel));

        AnalyticsAggregateResult result = service.workItemsBySeverity(TENANT_ID, ACTOR_ID);

        assertThat(result.buckets()).hasSize(1);
        assertThat(result.buckets().get(0).label()).isEqualTo("HIGH");
        assertThat(result.totalCount()).isEqualTo(4); // 99 NOT included
    }

    // ========== Validation ==========

    @Test
    void nullTenantId_throwsBusinessRule_INVALID_TENANT_ID_byStatus() {
        assertThatThrownBy(() -> service.workItemsByStatus(null, ACTOR_ID))
                .isInstanceOf(BusinessRuleException.class)
                .matches(e -> "INVALID_TENANT_ID".equals(
                        ((BusinessRuleException) e).getErrorCode()));
        verifyNoInteractions(workItemRepository);
        verifyNoInteractions(adminAuthorizationService);
    }

    @Test
    void nullTenantId_throwsBusinessRule_INVALID_TENANT_ID_byType() {
        assertThatThrownBy(() -> service.workItemsByType(null, ACTOR_ID))
                .isInstanceOf(BusinessRuleException.class)
                .matches(e -> "INVALID_TENANT_ID".equals(
                        ((BusinessRuleException) e).getErrorCode()));
    }

    @Test
    void nullTenantId_throwsBusinessRule_INVALID_TENANT_ID_bySeverity() {
        assertThatThrownBy(() -> service.workItemsBySeverity(null, ACTOR_ID))
                .isInstanceOf(BusinessRuleException.class)
                .matches(e -> "INVALID_TENANT_ID".equals(
                        ((BusinessRuleException) e).getErrorCode()));
    }

    // ========== Authorization ==========

    @Test
    void unauthorizedActor_throwsAccessDenied_byStatus_noRepositoryCall() {
        doThrow(new AccessDeniedException("TENANT_CONFIG_READ talab qilinadi"))
                .when(adminAuthorizationService).authorizeRead(TENANT_ID, ACTOR_ID);

        assertThatThrownBy(() -> service.workItemsByStatus(TENANT_ID, ACTOR_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workItemRepository);
    }

    @Test
    void unauthorizedActor_throwsAccessDenied_byType() {
        doThrow(new AccessDeniedException("denied"))
                .when(adminAuthorizationService).authorizeRead(TENANT_ID, ACTOR_ID);

        assertThatThrownBy(() -> service.workItemsByType(TENANT_ID, ACTOR_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unauthorizedActor_throwsAccessDenied_bySeverity() {
        doThrow(new AccessDeniedException("denied"))
                .when(adminAuthorizationService).authorizeRead(TENANT_ID, ACTOR_ID);

        assertThatThrownBy(() -> service.workItemsBySeverity(TENANT_ID, ACTOR_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ========== Edge cases ==========

    @Test
    void singleBucket_returnsThatBucketAndItsCountAsTotal() {
        AnalyticsBucketProjection b = bucket("BUGS", 42);
        when(workItemRepository.countWorkItemsByCurrentStatusCode(TENANT_ID))
                .thenReturn(List.of(b));

        AnalyticsAggregateResult result = service.workItemsByStatus(TENANT_ID, ACTOR_ID);

        assertThat(result.buckets()).hasSize(1);
        assertThat(result.totalCount()).isEqualTo(42);
        assertThat(result.buckets().get(0).count()).isEqualTo(42);
    }

    @Test
    void buckets_immutable_via_resultRecordCompactCtor() {
        AnalyticsBucketProjection b = bucket("BUG", 1);
        when(workItemRepository.countWorkItemsByTypeCode(TENANT_ID))
                .thenReturn(List.of(b));
        AnalyticsAggregateResult result = service.workItemsByType(TENANT_ID, ACTOR_ID);
        // List.copyOf result — modification attempt must throw.
        assertThatThrownBy(() -> result.buckets().add(new AnalyticsBucket("X", 0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
