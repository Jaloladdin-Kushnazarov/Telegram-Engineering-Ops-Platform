package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TelegramDeliveryMetricsQuery invariant testlari.
 *
 * Tekshiruvlar:
 * - to'g'ri yaratilgan query field'lari
 * - null tenantId rad etiladi
 * - null workItemId rad etiladi
 */
class TelegramDeliveryMetricsQueryTest {

    @Test
    void queryCreatedCorrectly() {
        UUID tenantId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();

        TelegramDeliveryMetricsQuery query = new TelegramDeliveryMetricsQuery(tenantId, workItemId);

        assertThat(query.getTenantId()).isEqualTo(tenantId);
        assertThat(query.getWorkItemId()).isEqualTo(workItemId);
    }

    @Test
    void nullTenantIdRejected() {
        assertThatThrownBy(() -> new TelegramDeliveryMetricsQuery(null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId null");
    }

    @Test
    void nullWorkItemIdRejected() {
        assertThatThrownBy(() -> new TelegramDeliveryMetricsQuery(UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemId null");
    }
}
