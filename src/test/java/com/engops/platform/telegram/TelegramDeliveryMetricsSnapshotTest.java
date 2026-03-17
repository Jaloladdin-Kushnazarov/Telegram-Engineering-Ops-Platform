package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TelegramDeliveryMetricsSnapshot invariant testlari.
 *
 * Tekshiruvlar:
 * - DELIVERED snapshot flag'lari to'g'ri
 * - REJECTED snapshot flag'lari to'g'ri
 * - FAILED snapshot flag'lari to'g'ri
 * - DELIVERED + failureCode bo'lsa rad etiladi
 * - REJECTED + failureCode yo'q bo'lsa rad etiladi
 * - FAILED + failureCode yo'q bo'lsa rad etiladi
 * - null tenantId rad etiladi
 * - null workItemId rad etiladi
 * - null operation rad etiladi
 * - null deliveryOutcome rad etiladi
 * - REJECTED + blank failureCode rad etiladi
 * - FAILED + blank failureCode rad etiladi
 */
class TelegramDeliveryMetricsSnapshotTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID WORK_ITEM_ID = UUID.randomUUID();
    private static final TelegramDeliveryOperation OPERATION = TelegramDeliveryOperation.SEND_NEW_MESSAGE;

    @Test
    void deliveredSnapshotFlagsCorrect() {
        TelegramDeliveryMetricsSnapshot snapshot = TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, WORK_ITEM_ID, OPERATION,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                null, true);

        assertThat(snapshot.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(snapshot.getWorkItemId()).isEqualTo(WORK_ITEM_ID);
        assertThat(snapshot.getOperation()).isEqualTo(OPERATION);
        assertThat(snapshot.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED);
        assertThat(snapshot.isSuccess()).isTrue();
        assertThat(snapshot.isRejected()).isFalse();
        assertThat(snapshot.isFailed()).isFalse();
        assertThat(snapshot.getFailureCode()).isNull();
        assertThat(snapshot.hasExternalMessageId()).isTrue();
    }

    @Test
    void rejectedSnapshotFlagsCorrect() {
        TelegramDeliveryMetricsSnapshot snapshot = TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, WORK_ITEM_ID, OPERATION,
                TelegramDeliveryResult.DeliveryOutcome.REJECTED,
                "INVALID_REQUEST", false);

        assertThat(snapshot.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.REJECTED);
        assertThat(snapshot.isSuccess()).isFalse();
        assertThat(snapshot.isRejected()).isTrue();
        assertThat(snapshot.isFailed()).isFalse();
        assertThat(snapshot.getFailureCode()).isEqualTo("INVALID_REQUEST");
        assertThat(snapshot.hasExternalMessageId()).isFalse();
    }

    @Test
    void failedSnapshotFlagsCorrect() {
        TelegramDeliveryMetricsSnapshot snapshot = TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, WORK_ITEM_ID, OPERATION,
                TelegramDeliveryResult.DeliveryOutcome.FAILED,
                "NETWORK_ERROR", false);

        assertThat(snapshot.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.FAILED);
        assertThat(snapshot.isSuccess()).isFalse();
        assertThat(snapshot.isRejected()).isFalse();
        assertThat(snapshot.isFailed()).isTrue();
        assertThat(snapshot.getFailureCode()).isEqualTo("NETWORK_ERROR");
        assertThat(snapshot.hasExternalMessageId()).isFalse();
    }

    @Test
    void deliveredWithFailureCodeRejected() {
        assertThatThrownBy(() -> TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, WORK_ITEM_ID, OPERATION,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                "SOME_CODE", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DELIVERED");
    }

    @Test
    void rejectedWithoutFailureCodeRejected() {
        assertThatThrownBy(() -> TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, WORK_ITEM_ID, OPERATION,
                TelegramDeliveryResult.DeliveryOutcome.REJECTED,
                null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("REJECTED");
    }

    @Test
    void failedWithoutFailureCodeRejected() {
        assertThatThrownBy(() -> TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, WORK_ITEM_ID, OPERATION,
                TelegramDeliveryResult.DeliveryOutcome.FAILED,
                null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FAILED");
    }

    @Test
    void nullTenantIdRejected() {
        assertThatThrownBy(() -> TelegramDeliveryMetricsSnapshot.of(
                null, WORK_ITEM_ID, OPERATION,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId null");
    }

    @Test
    void nullWorkItemIdRejected() {
        assertThatThrownBy(() -> TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, null, OPERATION,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemId null");
    }

    @Test
    void nullOperationRejected() {
        assertThatThrownBy(() -> TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, WORK_ITEM_ID, null,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operation null");
    }

    @Test
    void nullDeliveryOutcomeRejected() {
        assertThatThrownBy(() -> TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, WORK_ITEM_ID, OPERATION,
                null,
                null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deliveryOutcome null");
    }

    @Test
    void rejectedWithBlankFailureCodeRejected() {
        assertThatThrownBy(() -> TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, WORK_ITEM_ID, OPERATION,
                TelegramDeliveryResult.DeliveryOutcome.REJECTED,
                "   ", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("REJECTED");
    }

    @Test
    void failedWithBlankFailureCodeRejected() {
        assertThatThrownBy(() -> TelegramDeliveryMetricsSnapshot.of(
                TENANT_ID, WORK_ITEM_ID, OPERATION,
                TelegramDeliveryResult.DeliveryOutcome.FAILED,
                "", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FAILED");
    }
}
