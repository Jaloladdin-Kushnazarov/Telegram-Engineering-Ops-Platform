package com.engops.platform.platform;

import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.model.TenantStatus;
import com.engops.platform.tenantconfig.repository.TenantRepository;
import com.engops.platform.workitem.OperationalAuthorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 217a — {@link PlatformTenantQueryService} unit testlari.
 *
 * <p>Mocked dependencies: {@link TenantRepository} +
 * {@link OperationalAuthorizationService}. Fokus:</p>
 * <ul>
 *   <li>Authorization birinchi qadamda chaqiriladi (fail fast).</li>
 *   <li>DB so'rovi authorization muvaffaqiyatli bo'lgandan keyingina.</li>
 *   <li>Repository natijasi {@link PlatformTenantSummary}'ga to'g'ri map qilinadi.</li>
 *   <li>Sort {@code createdAt DESC} bilan o'tkazilganligi tasdiqlanadi.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PlatformTenantQueryServiceTest {

    private static final UUID ACTOR_USER_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TENANT_ID_A =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TENANT_ID_B =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private OperationalAuthorizationService authorizationService;

    @InjectMocks
    private PlatformTenantQueryService service;

    // ========== listAllTenants ==========

    @Test
    void listAllTenants_callsAuthorize_withPlatformTenantList() {
        when(tenantRepository.findAll(any(Sort.class)))
                .thenReturn(Collections.emptyList());

        service.listAllTenants(ACTOR_USER_ID);

        verify(authorizationService).authorizeGlobal(ACTOR_USER_ID, "PLATFORM_TENANT_LIST");
    }

    @Test
    void listAllTenants_returnsAllTenants_sortedByCreatedAtDesc() {
        Tenant tA = mockTenant(TENANT_ID_A, "Acme Corp", "acme",
                "UTC", TenantStatus.ACTIVE);
        Tenant tB = mockTenant(TENANT_ID_B, "Beta LLC", "beta",
                "Asia/Tashkent", TenantStatus.SUSPENDED);
        when(tenantRepository.findAll(any(Sort.class))).thenReturn(List.of(tB, tA));

        List<PlatformTenantSummary> result = service.listAllTenants(ACTOR_USER_ID);

        assertThat(result).hasSize(2);
        // Order preserved from repository (Sort applied at query level)
        assertThat(result.get(0).id()).isEqualTo(TENANT_ID_B);
        assertThat(result.get(1).id()).isEqualTo(TENANT_ID_A);

        // Verify Sort direction passed to repository
        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(tenantRepository).findAll(sortCaptor.capture());
        Sort capturedSort = sortCaptor.getValue();
        Sort.Order createdAtOrder = capturedSort.getOrderFor("createdAt");
        assertThat(createdAtOrder).isNotNull();
        assertThat(createdAtOrder.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listAllTenants_emptyResult_returnsEmptyList() {
        when(tenantRepository.findAll(any(Sort.class)))
                .thenReturn(Collections.emptyList());

        List<PlatformTenantSummary> result = service.listAllTenants(ACTOR_USER_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void listAllTenants_authorizeThrows_propagatesAccessDenied() {
        doThrow(new AccessDeniedException("PLATFORM_TENANT_LIST ruxsati yo'q"))
                .when(authorizationService).authorizeGlobal(ACTOR_USER_ID, "PLATFORM_TENANT_LIST");

        assertThatThrownBy(() -> service.listAllTenants(ACTOR_USER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("PLATFORM_TENANT_LIST");
    }

    @Test
    void listAllTenants_doesNotQueryRepository_whenAuthorizeFails() {
        doThrow(new AccessDeniedException("denied"))
                .when(authorizationService).authorizeGlobal(any(), any());

        assertThatThrownBy(() -> service.listAllTenants(ACTOR_USER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(tenantRepository, never()).findAll(any(Sort.class));
        verifyNoInteractions(tenantRepository);
    }

    @Test
    void listAllTenants_mapsTenantFields_correctly() {
        Instant now = Instant.now();
        Tenant t = mockTenant(TENANT_ID_A, "Acme Corp", "acme",
                "Asia/Tashkent", TenantStatus.ACTIVE);
        when(t.getCreatedAt()).thenReturn(now);
        when(t.getUpdatedAt()).thenReturn(now.plusSeconds(60));
        when(tenantRepository.findAll(any(Sort.class))).thenReturn(List.of(t));

        List<PlatformTenantSummary> result = service.listAllTenants(ACTOR_USER_ID);

        assertThat(result).hasSize(1);
        PlatformTenantSummary summary = result.get(0);
        assertThat(summary.id()).isEqualTo(TENANT_ID_A);
        assertThat(summary.name()).isEqualTo("Acme Corp");
        assertThat(summary.slug()).isEqualTo("acme");
        assertThat(summary.timezone()).isEqualTo("Asia/Tashkent");
        assertThat(summary.status()).isEqualTo("ACTIVE");
        assertThat(summary.createdAt()).isEqualTo(now);
        assertThat(summary.updatedAt()).isEqualTo(now.plusSeconds(60));
    }

    // ========== findById ==========

    @Test
    void findById_callsAuthorize() {
        when(tenantRepository.findById(TENANT_ID_A)).thenReturn(Optional.empty());

        service.findById(ACTOR_USER_ID, TENANT_ID_A);

        verify(authorizationService).authorizeGlobal(ACTOR_USER_ID, "PLATFORM_TENANT_LIST");
    }

    @Test
    void findById_returnsTenant_whenExists() {
        Tenant t = mockTenant(TENANT_ID_A, "Acme Corp", "acme",
                "UTC", TenantStatus.ACTIVE);
        when(tenantRepository.findById(TENANT_ID_A)).thenReturn(Optional.of(t));

        Optional<PlatformTenantSummary> result = service.findById(ACTOR_USER_ID, TENANT_ID_A);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(TENANT_ID_A);
        assertThat(result.get().name()).isEqualTo("Acme Corp");
    }

    @Test
    void findById_returnsEmpty_whenNotFound() {
        when(tenantRepository.findById(TENANT_ID_A)).thenReturn(Optional.empty());

        Optional<PlatformTenantSummary> result = service.findById(ACTOR_USER_ID, TENANT_ID_A);

        assertThat(result).isEmpty();
    }

    @Test
    void findById_authorizeThrows_propagatesAccessDenied() {
        doThrow(new AccessDeniedException("denied"))
                .when(authorizationService).authorizeGlobal(ACTOR_USER_ID, "PLATFORM_TENANT_LIST");

        assertThatThrownBy(() -> service.findById(ACTOR_USER_ID, TENANT_ID_A))
                .isInstanceOf(AccessDeniedException.class);

        verify(tenantRepository, never()).findById(any(UUID.class));
    }

    @Test
    void findById_mapsAllFields() {
        Instant created = Instant.parse("2026-01-01T10:00:00Z");
        Instant updated = Instant.parse("2026-01-02T11:00:00Z");
        Tenant t = mockTenant(TENANT_ID_A, "Acme Corp", "acme",
                "America/New_York", TenantStatus.SUSPENDED);
        when(t.getCreatedAt()).thenReturn(created);
        when(t.getUpdatedAt()).thenReturn(updated);
        when(tenantRepository.findById(TENANT_ID_A)).thenReturn(Optional.of(t));

        PlatformTenantSummary s = service.findById(ACTOR_USER_ID, TENANT_ID_A).orElseThrow();

        assertThat(s.id()).isEqualTo(TENANT_ID_A);
        assertThat(s.name()).isEqualTo("Acme Corp");
        assertThat(s.slug()).isEqualTo("acme");
        assertThat(s.timezone()).isEqualTo("America/New_York");
        assertThat(s.status()).isEqualTo("SUSPENDED");
        assertThat(s.createdAt()).isEqualTo(created);
        assertThat(s.updatedAt()).isEqualTo(updated);
    }

    // ========== PlatformTenantSummary.from static factory ==========

    @Test
    void platformTenantSummary_from_mapsAllFields() {
        Instant created = Instant.parse("2026-03-15T08:00:00Z");
        Instant updated = Instant.parse("2026-03-20T08:00:00Z");
        Tenant t = mockTenant(TENANT_ID_B, "Beta LLC", "beta",
                "Europe/London", TenantStatus.ARCHIVED);
        when(t.getCreatedAt()).thenReturn(created);
        when(t.getUpdatedAt()).thenReturn(updated);

        PlatformTenantSummary summary = PlatformTenantSummary.from(t);

        assertThat(summary.id()).isEqualTo(TENANT_ID_B);
        assertThat(summary.name()).isEqualTo("Beta LLC");
        assertThat(summary.slug()).isEqualTo("beta");
        assertThat(summary.timezone()).isEqualTo("Europe/London");
        assertThat(summary.status()).isEqualTo("ARCHIVED");
        assertThat(summary.createdAt()).isEqualTo(created);
        assertThat(summary.updatedAt()).isEqualTo(updated);
    }

    @Test
    void permissionConstant_isExpectedValue() {
        // Catch accidental rename of permission code — V9 seed uses
        // exact string "PLATFORM_TENANT_LIST".
        assertThat(PlatformTenantQueryService.PLATFORM_TENANT_LIST)
                .isEqualTo("PLATFORM_TENANT_LIST");
    }

    @Test
    void listAllTenants_authorizeCalledOnce_evenWhenManyTenants() {
        Tenant tA = mockTenant(TENANT_ID_A, "A", "a", "UTC", TenantStatus.ACTIVE);
        Tenant tB = mockTenant(TENANT_ID_B, "B", "b", "UTC", TenantStatus.ACTIVE);
        when(tenantRepository.findAll(any(Sort.class))).thenReturn(List.of(tA, tB));

        assertThatCode(() -> service.listAllTenants(ACTOR_USER_ID))
                .doesNotThrowAnyException();

        // Authorize chaqirilgan aniq 1 marta — per-tenant check yo'q
        verify(authorizationService, org.mockito.Mockito.times(1))
                .authorizeGlobal(ACTOR_USER_ID, "PLATFORM_TENANT_LIST");
    }

    // ========== Helpers ==========

    private static Tenant mockTenant(UUID id, String name, String slug,
                                      String timezone, TenantStatus status) {
        Tenant t = org.mockito.Mockito.mock(Tenant.class);
        when(t.getId()).thenReturn(id);
        when(t.getName()).thenReturn(name);
        when(t.getSlug()).thenReturn(slug);
        when(t.getTimezone()).thenReturn(timezone);
        when(t.getStatus()).thenReturn(status);
        return t;
    }
}
