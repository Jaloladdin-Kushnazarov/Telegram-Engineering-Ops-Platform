package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * TelegramDeliveryAttemptHistoryFacade testlari.
 *
 * Tekshiruvlar:
 * - happy-path: readAccess'ga delegatsiya va natija qaytarish
 * - bo'sh ro'yxat qaytaradi agar ma'lumot yo'q
 * - tenantId va workItemId va limit to'g'ri uzatiladi
 * - null tenantId rad etiladi
 * - null workItemId rad etiladi
 * - limit < 1 rad etiladi
 * - limit > MAX_LIMIT rad etiladi
 * - limit = 1 qabul qilinadi (pastki chegara)
 * - limit = MAX_LIMIT qabul qilinadi (yuqori chegara)
 */
class TelegramDeliveryAttemptHistoryFacadeTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID WORK_ITEM_ID = UUID.randomUUID();
    private static final Instant FIXED_TIME = Instant.parse("2026-03-18T10:00:00Z");

    private final TelegramDeliveryAttemptHistoryReadAccess readAccess =
            mock(TelegramDeliveryAttemptHistoryReadAccess.class);
    private final TelegramDeliveryAttemptHistoryFacade facade =
            new TelegramDeliveryAttemptHistoryFacade(readAccess);

    @Test
    void delegatesToReadAccessAndReturnsResult() {
        List<TelegramDeliveryAttempt> expected = List.of(buildAttempt());
        when(readAccess.findRecentAttempts(TENANT_ID, WORK_ITEM_ID, 5))
                .thenReturn(expected);

        List<TelegramDeliveryAttempt> result = facade.getRecentAttempts(TENANT_ID, WORK_ITEM_ID, 5);

        assertThat(result).isSameAs(expected);
        verify(readAccess).findRecentAttempts(TENANT_ID, WORK_ITEM_ID, 5);
        verifyNoMoreInteractions(readAccess);
    }

    @Test
    void emptyListReturnedWhenNoData() {
        when(readAccess.findRecentAttempts(TENANT_ID, WORK_ITEM_ID, 10))
                .thenReturn(List.of());

        List<TelegramDeliveryAttempt> result = facade.getRecentAttempts(TENANT_ID, WORK_ITEM_ID, 10);

        assertThat(result).isEmpty();
    }

    @Test
    void nullTenantIdRejected() {
        assertThatThrownBy(() -> facade.getRecentAttempts(null, WORK_ITEM_ID, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId null");

        verifyNoInteractions(readAccess);
    }

    @Test
    void nullWorkItemIdRejected() {
        assertThatThrownBy(() -> facade.getRecentAttempts(TENANT_ID, null, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemId null");

        verifyNoInteractions(readAccess);
    }

    @Test
    void limitZeroRejected() {
        assertThatThrownBy(() -> facade.getRecentAttempts(TENANT_ID, WORK_ITEM_ID, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");

        verifyNoInteractions(readAccess);
    }

    @Test
    void limitNegativeRejected() {
        assertThatThrownBy(() -> facade.getRecentAttempts(TENANT_ID, WORK_ITEM_ID, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");

        verifyNoInteractions(readAccess);
    }

    @Test
    void limitAboveMaxRejected() {
        assertThatThrownBy(() -> facade.getRecentAttempts(
                TENANT_ID, WORK_ITEM_ID, TelegramDeliveryAttemptHistoryFacade.MAX_LIMIT + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");

        verifyNoInteractions(readAccess);
    }

    @Test
    void limitOneAccepted() {
        when(readAccess.findRecentAttempts(TENANT_ID, WORK_ITEM_ID, 1))
                .thenReturn(List.of());

        facade.getRecentAttempts(TENANT_ID, WORK_ITEM_ID, 1);

        verify(readAccess).findRecentAttempts(TENANT_ID, WORK_ITEM_ID, 1);
    }

    @Test
    void limitMaxAccepted() {
        int maxLimit = TelegramDeliveryAttemptHistoryFacade.MAX_LIMIT;
        when(readAccess.findRecentAttempts(TENANT_ID, WORK_ITEM_ID, maxLimit))
                .thenReturn(List.of());

        facade.getRecentAttempts(TENANT_ID, WORK_ITEM_ID, maxLimit);

        verify(readAccess).findRecentAttempts(TENANT_ID, WORK_ITEM_ID, maxLimit);
    }

    private TelegramDeliveryAttempt buildAttempt() {
        return TelegramDeliveryAttempt.reconstruct(
                UUID.randomUUID(), FIXED_TIME,
                TENANT_ID, WORK_ITEM_ID,
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                UUID.randomUUID(), 42L,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                99001L, null, null);
    }
}
