package com.engops.platform.telegram;

import org.springframework.stereotype.Component;

/**
 * TelegramDeliveryAttempt'dan TelegramDeliveryMetricsSnapshot'ga
 * pure mapping assembler.
 *
 * Bu assembler faqat attempt'dagi ma'lumotlarni metrics-friendly
 * snapshot shaklga o'tkazadi. Hech qanday side-effect yo'q:
 * - repository access yo'q
 * - logging yo'q
 * - metrics emission yo'q
 * - hidden default yo'q
 *
 * Stateless @Component — thread-safe va immutable natija qaytaradi.
 */
@Component
public class TelegramDeliveryMetricsAssembler {

    /**
     * TelegramDeliveryAttempt'dan metrics snapshot yaratadi.
     *
     * @param attempt delivery attempt trace record
     * @return metrics-friendly snapshot
     * @throws IllegalArgumentException agar attempt null bo'lsa
     */
    public TelegramDeliveryMetricsSnapshot assemble(TelegramDeliveryAttempt attempt) {
        if (attempt == null) {
            throw new IllegalArgumentException("attempt null bo'lishi mumkin emas");
        }

        return TelegramDeliveryMetricsSnapshot.of(
                attempt.getTenantId(),
                attempt.getWorkItemId(),
                attempt.getOperation(),
                attempt.getDeliveryOutcome(),
                attempt.getFailureCode(),
                attempt.getExternalMessageId() != null);
    }
}
