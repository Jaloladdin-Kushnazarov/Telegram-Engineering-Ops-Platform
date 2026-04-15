package com.engops.platform.admin;

import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * WorkItemSupportDetailsReadFacade unit testlari.
 *
 * Tekshiruvlar:
 * - authorizeRead to'g'ri tenantId va actorUserId bilan chaqiriladi
 * - authorization denial business delegation'ni short-circuit qiladi
 * - muvaffaqiyatli path WorkItemSupportDetailsFacade'ga delegatsiya qiladi
 * - natija supportDetailsFacade'dan aynan qaytariladi
 */
class WorkItemSupportDetailsReadFacadeTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_USER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID WI_ID_1 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final AdminAuthorizationService authorizationService =
            mock(AdminAuthorizationService.class);
    private final WorkItemSupportDetailsFacade supportDetailsFacade =
            mock(WorkItemSupportDetailsFacade.class);
    private final WorkItemSupportDetailsReadFacade facade =
            new WorkItemSupportDetailsReadFacade(authorizationService, supportDetailsFacade);

    @Test
    void authorizationCalledWithCorrectArguments() {
        var view = mock(WorkItemSupportDetailsFacade.WorkItemSupportDetailsView.class);
        when(supportDetailsFacade.getDetails(TENANT_ID, "BUG-1", 10)).thenReturn(view);

        facade.getDetails(TENANT_ID, "BUG-1", 10, ACTOR_USER_ID);

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
    }

    @Test
    void authorizationDenialShortCircuitsDelegation() {
        doThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"))
                .when(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);

        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, "BUG-1", 10, ACTOR_USER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(supportDetailsFacade);
    }

    @Test
    void successPathDelegatesToInnerFacade() {
        var view = mock(WorkItemSupportDetailsFacade.WorkItemSupportDetailsView.class);

        when(supportDetailsFacade.getDetails(TENANT_ID, "BUG-1", 10)).thenReturn(view);

        var result = facade.getDetails(TENANT_ID, "BUG-1", 10, ACTOR_USER_ID);

        assertThat(result).isSameAs(view);

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
        verify(supportDetailsFacade).getDetails(TENANT_ID, "BUG-1", 10);
        verifyNoMoreInteractions(authorizationService, supportDetailsFacade);
    }

    @Test
    void nullTenantIdSkipsAuthorization() {
        assertThatThrownBy(() -> facade.getDetails(null, "BUG-1", 10, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(authorizationService, supportDetailsFacade);
    }

    @Test
    void propagatesValidationErrorFromInnerFacade() {
        when(supportDetailsFacade.getDetails(TENANT_ID, "", 10))
                .thenThrow(new IllegalArgumentException(
                        "workItemCode bo'sh bo'lishi mumkin emas"));

        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, "", 10, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemCode");
    }
}
