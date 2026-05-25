package com.engops.platform.admin;

import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * WorkItemSummaryReadFacade unit testlari.
 *
 * Tekshiruvlar:
 * - authorizeRead to'g'ri tenantId va actorUserId bilan chaqiriladi
 * - authorization denial business delegation'ni short-circuit qiladi
 * - muvaffaqiyatli path WorkItemSummaryFacade'ga delegatsiya qiladi
 * - natija summaryFacade'dan aynan qaytariladi
 */
class WorkItemSummaryReadFacadeTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_USER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID WI_ID_1 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final AdminAuthorizationService authorizationService =
            mock(AdminAuthorizationService.class);
    private final WorkItemSummaryFacade summaryFacade =
            mock(WorkItemSummaryFacade.class);
    private final WorkItemSummaryReadFacade facade =
            new WorkItemSummaryReadFacade(authorizationService, summaryFacade);

    @Test
    void authorizationCalledWithCorrectArguments() {
        when(summaryFacade.getSummaryList(TENANT_ID, 20)).thenReturn(List.of());

        facade.getSummaryList(TENANT_ID, 20, ACTOR_USER_ID);

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
    }

    @Test
    void authorizationDenialShortCircuitsDelegation() {
        doThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"))
                .when(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);

        assertThatThrownBy(() -> facade.getSummaryList(TENANT_ID, 20, ACTOR_USER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(summaryFacade);
    }

    @Test
    void successPathDelegatesToInnerFacade() {
        var item = new WorkItemSummaryItem(
                WI_ID_1, "BUG-1", "Login xato",
                WorkItemType.BUG, "BUGS", null, null, null,
                null,
                Instant.parse("2026-03-18T10:00:00Z"), null, null, 0, false);

        when(summaryFacade.getSummaryList(TENANT_ID, 20)).thenReturn(List.of(item));

        var result = facade.getSummaryList(TENANT_ID, 20, ACTOR_USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).workItemId()).isEqualTo(WI_ID_1);
        assertThat(result.get(0).workItemCode()).isEqualTo("BUG-1");

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
        verify(summaryFacade).getSummaryList(TENANT_ID, 20);
        verifyNoMoreInteractions(authorizationService, summaryFacade);
    }

    @Test
    void successPath_ownerDisplayNamePassedThrough() {
        // Phase 220a — read facade pass-through: inner facade resolve qilgan
        // ownerDisplayName o'zgarmasdan qaytariladi.
        var item = new WorkItemSummaryItem(
                WI_ID_1, "BUG-1", "Login xato",
                WorkItemType.BUG, "BUGS", null, null, null,
                "Sariga",
                Instant.parse("2026-03-18T10:00:00Z"), null, null, 0, false);
        when(summaryFacade.getSummaryList(TENANT_ID, 20)).thenReturn(List.of(item));

        var result = facade.getSummaryList(TENANT_ID, 20, ACTOR_USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).ownerDisplayName()).isEqualTo("Sariga");
    }

    @Test
    void emptyResultReturnedAsIs() {
        when(summaryFacade.getSummaryList(TENANT_ID, 10)).thenReturn(List.of());

        var result = facade.getSummaryList(TENANT_ID, 10, ACTOR_USER_ID);

        assertThat(result).isEmpty();
        verify(summaryFacade).getSummaryList(TENANT_ID, 10);
    }

    @Test
    void nullTenantIdSkipsAuthorization() {
        assertThatThrownBy(() -> facade.getSummaryList(null, 20, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(authorizationService, summaryFacade);
    }

    @Test
    void propagatesValidationErrorFromInnerFacade() {
        when(summaryFacade.getSummaryList(TENANT_ID, 0))
                .thenThrow(new IllegalArgumentException(
                        "limit 1..50 oralig'ida bo'lishi kerak, berilgan: 0"));

        assertThatThrownBy(() -> facade.getSummaryList(TENANT_ID, 0, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }
}
