package com.engops.platform.admin;

import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * WorkItemDetailsReadFacade unit testlari.
 *
 * Tekshiruvlar:
 * - authorizeRead to'g'ri tenantId va actorUserId bilan chaqiriladi
 * - authorization denial business delegation'ni short-circuit qiladi
 * - muvaffaqiyatli path WorkItemDetailsFacade'ga delegatsiya qiladi
 * - natija detailsFacade'dan aynan qaytariladi
 */
class WorkItemDetailsReadFacadeTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_USER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID WI_ID_1 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final AdminAuthorizationService authorizationService =
            mock(AdminAuthorizationService.class);
    private final WorkItemDetailsFacade detailsFacade =
            mock(WorkItemDetailsFacade.class);
    private final WorkItemDetailsReadFacade facade =
            new WorkItemDetailsReadFacade(authorizationService, detailsFacade);

    @Test
    void authorizationCalledWithCorrectArguments() {
        var workItem = new WorkItem(TENANT_ID, "BUG-1", WorkItemType.BUG,
                UUID.randomUUID(), "Login xato", "BUGS", null);
        var view = new WorkItemDetailsFacade.WorkItemDetailsView(workItem, List.of());
        when(detailsFacade.getDetails(TENANT_ID, "BUG-1")).thenReturn(view);

        facade.getDetails(TENANT_ID, "BUG-1", ACTOR_USER_ID);

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
    }

    @Test
    void authorizationDenialShortCircuitsDelegation() {
        doThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"))
                .when(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);

        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, "BUG-1", ACTOR_USER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(detailsFacade);
    }

    @Test
    void successPathDelegatesToInnerFacade() {
        var workItem = new WorkItem(TENANT_ID, "BUG-1", WorkItemType.BUG,
                UUID.randomUUID(), "Login xato", "BUGS", null);
        var view = new WorkItemDetailsFacade.WorkItemDetailsView(workItem, List.of());

        when(detailsFacade.getDetails(TENANT_ID, "BUG-1")).thenReturn(view);

        var result = facade.getDetails(TENANT_ID, "BUG-1", ACTOR_USER_ID);

        assertThat(result).isSameAs(view);
        assertThat(result.workItem().getWorkItemCode()).isEqualTo("BUG-1");

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
        verify(detailsFacade).getDetails(TENANT_ID, "BUG-1");
        verifyNoMoreInteractions(authorizationService, detailsFacade);
    }

    @Test
    void nullTenantIdSkipsAuthorization() {
        assertThatThrownBy(() -> facade.getDetails(null, "BUG-1", ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(authorizationService, detailsFacade);
    }

    @Test
    void propagatesValidationErrorFromInnerFacade() {
        when(detailsFacade.getDetails(TENANT_ID, ""))
                .thenThrow(new IllegalArgumentException(
                        "workItemCode bo'sh bo'lishi mumkin emas"));

        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, "", ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemCode");
    }
}
