package com.engops.platform.intake;

import com.engops.platform.telegram.TelegramCardDispatchService;
import com.engops.platform.telegram.TelegramCardView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Phase 165 — AFTER_COMMIT semantikasi uchun yengil Spring Boot smoke test.
 *
 * <p>Maqsad: Phase 164 da o'rnatilgan
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} contract'ini
 * Spring framework darajasida uchdan-uchga isbotlash:</p>
 *
 * <ul>
 *   <li><strong>Commit yo'l:</strong> tashqi transaction commit qilingach,
 *       {@link TelegramCardDispatchEventListener} listeneri fire qiladi va
 *       {@link TelegramCardDispatchService#dispatchAttempt} aynan bir marta
 *       chaqiriladi.</li>
 *   <li><strong>Rollback yo'l:</strong> tashqi transaction rollback bo'lsa,
 *       listener umuman fire qilmaydi va dispatchAttempt hech qachon
 *       chaqirilmaydi — ya'ni rolled-back business mutatsiya hech qanday
 *       Telegram message yoki {@code telegram_delivery_attempt} row
 *       yaratmaydi.</li>
 * </ul>
 *
 * <p><strong>Test setup:</strong></p>
 * <ul>
 *   <li>{@code @SpringBootTest} + {@code @ActiveProfiles("test")} — mavjud
 *       test profili (H2 in-memory, create-drop, Flyway o'chirilgan,
 *       bootstrap admin default'da disabled).</li>
 *   <li>{@link TelegramCardDispatchService} {@code @MockBean} bilan
 *       almashtirilgan — listener'ning real dispatch zanjirini
 *       (rendering + outbound + persistence) chaqirmasligi uchun. Real
 *       {@link ProjectionAssembler} va {@code TelegramCardViewService}
 *       ishlatiladi (pure mappers, IO yo'q) — listener'ning real chaqiruv
 *       zanjirini imkon qadar saqlash uchun.</li>
 *   <li>{@link TransactionTemplate} sinov ichida transaction commit/rollback
 *       semantikasini boshqarish uchun.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class TelegramCardDispatchAfterCommitIntegrationTest {

    @MockBean
    private TelegramCardDispatchService telegramCardDispatchService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate txTemplate;

    @BeforeEach
    void setUp() {
        txTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void commitYolidaListenerDispatchAttemptniChaqiradi() {
        TelegramCardDispatchRequested event = newDeliveryReadyEvent("INTAKE", null);

        txTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

        verify(telegramCardDispatchService).dispatchAttempt(any(TelegramCardView.class));
    }

    @Test
    void rollbackYolidaListenerDispatchAttemptniChaqirmaydi() {
        TelegramCardDispatchRequested event = newDeliveryReadyEvent(
                "WORKFLOW_TRANSITION", "PROCESSING");

        txTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            status.setRollbackOnly();
        });

        verify(telegramCardDispatchService, never()).dispatchAttempt(any());
    }

    private TelegramCardDispatchRequested newDeliveryReadyEvent(String sourceFlow,
                                                                  String targetStatusCode) {
        PreparedDeliveryTarget target = new PreparedDeliveryTarget(
                UUID.randomUUID(),                  // tenantId
                UUID.randomUUID(),                  // workItemId
                "BUG-1",                            // workItemCode
                "BUG",                              // workItemType
                "Smoke test bug",                   // title
                targetStatusCode != null ? targetStatusCode : "BUGS",
                null, null,                         // Phase 194 — priority/severity absent
                true,                               // deliveryReady
                UUID.randomUUID(),                  // targetChatBindingId
                42L,                                // targetTopicId
                null);                              // Phase 196 — ownerDisplayLabel absent
        return new TelegramCardDispatchRequested(target, sourceFlow, targetStatusCode);
    }
}
