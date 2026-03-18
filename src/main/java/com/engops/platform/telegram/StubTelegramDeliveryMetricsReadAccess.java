package com.engops.platform.telegram;

import java.util.Optional;
import java.util.UUID;

/**
 * O'chirilgan stub read access — Phase 21'da JpaTelegramDeliveryMetricsReadAccess
 * bilan almashtirildi.
 *
 * @Component olib tashlandi — real adapter endi faol.
 * Test'larda mock sifatida ishlatish mumkin.
 * Klass saqlab qolindi chunki test reference uchun foydali.
 */
public class StubTelegramDeliveryMetricsReadAccess implements TelegramDeliveryMetricsReadAccess {

    @Override
    public Optional<TelegramDeliveryAttempt> findLatestAttempt(UUID tenantId, UUID workItemId) {
        return Optional.empty();
    }
}
