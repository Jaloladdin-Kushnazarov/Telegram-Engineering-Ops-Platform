package com.engops.platform.admin;

import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.telegram.TelegramDeliveryMetricsSnapshot;
import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsFacade;
import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsView;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * DeliveryObservabilityDetailsByCodeFacade unit testlari.
 *
 * Tekshiruvlar:
 * - delegation to'g'ri ishlaydi
 * - boundary validation (tenantId null, workItemCode blank)
 * - authorization chaqiriladi
 * - authorization denial short-circuit
 * - validation before authorization ordering
 * - historyLimit to'g'ri uzatiladi
 */
class DeliveryObservabilityDetailsByCodeFacadeTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_USER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID WORK_ITEM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String WORK_ITEM_CODE = "BUG-1";

    private final TelegramDeliveryObservabilityDetailsFacade telegramFacade =
            mock(TelegramDeliveryObservabilityDetailsFacade.class);
    private final AdminAuthorizationService authorizationService =
            mock(AdminAuthorizationService.class);
    private final DeliveryObservabilityDetailsByCodeFacade facade =
            new DeliveryObservabilityDetailsByCodeFacade(telegramFacade, authorizationService);

    @Test
    void delegatesToTelegramFacadeWithCorrectArguments() {
        var expectedView = new TelegramDeliveryObservabilityDetailsView(
                WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WORK_ITEM_ID),
                List.of());

        when(telegramFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, 10))
                .thenReturn(expectedView);

        var result = facade.getDetails(TENANT_ID, WORK_ITEM_CODE, 10, ACTOR_USER_ID);

        assertThat(result).isSameAs(expectedView);

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
        verify(telegramFacade).getDetails(TENANT_ID, WORK_ITEM_CODE, 10);
        verifyNoMoreInteractions(telegramFacade);
    }

    @Test
    void forwardsHistoryLimitCorrectly() {
        var view = new TelegramDeliveryObservabilityDetailsView(
                WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WORK_ITEM_ID),
                List.of());

        when(telegramFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, 30))
                .thenReturn(view);

        facade.getDetails(TENANT_ID, WORK_ITEM_CODE, 30, ACTOR_USER_ID);

        verify(telegramFacade).getDetails(TENANT_ID, WORK_ITEM_CODE, 30);
    }

    @Test
    void throwsIllegalArgumentWhenTenantIdNull() {
        assertThatThrownBy(() -> facade.getDetails(null, WORK_ITEM_CODE, 10, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(authorizationService, telegramFacade);
    }

    @Test
    void throwsIllegalArgumentWhenWorkItemCodeNull() {
        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, null, 10, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemCode");

        verifyNoInteractions(authorizationService, telegramFacade);
    }

    @Test
    void throwsIllegalArgumentWhenWorkItemCodeBlank() {
        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, "  ", 10, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemCode");

        verifyNoInteractions(authorizationService, telegramFacade);
    }

    @Test
    void propagatesInvalidHistoryLimitFromTelegramFacade() {
        when(telegramFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, 0))
                .thenThrow(new IllegalArgumentException(
                        "historyLimit 1..50 oralig'ida bo'lishi kerak"));

        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, WORK_ITEM_CODE, 0, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("historyLimit");
    }

    // ========== Authorization tests ==========

    @Test
    void authorizationCalledWithCorrectArguments() {
        var view = new TelegramDeliveryObservabilityDetailsView(
                WORK_ITEM_ID, WORK_ITEM_CODE, "Login xato",
                WorkItemType.BUG, "BUGS",
                TelegramDeliveryMetricsSnapshot.empty(TENANT_ID, WORK_ITEM_ID),
                List.of());

        when(telegramFacade.getDetails(TENANT_ID, WORK_ITEM_CODE, 10))
                .thenReturn(view);

        facade.getDetails(TENANT_ID, WORK_ITEM_CODE, 10, ACTOR_USER_ID);

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
    }

    @Test
    void authorizationDenialShortCircuitsBusinessDelegation() {
        doThrow(new AccessDeniedException("TENANT_CONFIG_READ ruxsati talab qilinadi"))
                .when(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);

        assertThatThrownBy(() -> facade.getDetails(
                TENANT_ID, WORK_ITEM_CODE, 10, ACTOR_USER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(telegramFacade);
    }

    @Test
    void nullTenantIdSkipsAuthorization() {
        assertThatThrownBy(() -> facade.getDetails(null, WORK_ITEM_CODE, 10, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
    }

    @Test
    void blankWorkItemCodeSkipsAuthorization() {
        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, "", 10, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
    }
}
