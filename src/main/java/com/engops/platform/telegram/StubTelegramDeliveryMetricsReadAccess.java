package com.engops.platform.telegram;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Vaqtinchalik stub read access — real persistence integration yo'q.
 *
 * Bu bean TelegramDeliveryMetricsReadAccess port'ining placeholder implementation'i.
 * Keyingi phase'da real repository adapter shu bean'ni almashtiradi.
 * Almashtirishda shu stub'ni o'chirib, yangi @Component qo'yiladi —
 * StubTelegramOutboundGateway bilan bir xil pattern.
 *
 * Hozirgi holat:
 * - Har doim Optional.empty() qaytaradi (hech qanday attempt topilmadi)
 * - App startup va autowiring muammosini bartaraf etadi
 * - Exception tashlamaydi
 *
 * Eslatma: @ConditionalOnMissingBean ishlatilmaydi chunki mavjud
 * stub pattern (StubTelegramOutboundGateway) shu yondashuvni qo'llamaydi.
 * Loyiha bo'ylab bir xillik saqlanadi.
 */
@Component
public class StubTelegramDeliveryMetricsReadAccess implements TelegramDeliveryMetricsReadAccess {

    @Override
    public Optional<TelegramDeliveryAttempt> findLatestAttempt(UUID tenantId, UUID workItemId) {
        return Optional.empty();
    }
}
