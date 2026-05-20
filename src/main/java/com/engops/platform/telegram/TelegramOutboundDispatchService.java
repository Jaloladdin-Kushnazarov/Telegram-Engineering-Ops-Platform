package com.engops.platform.telegram;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

/**
 * Telegram outbound dispatch uchun application-level orchestration service.
 *
 * Bu servis telegram module'ning outbound execution uchun public entry point'i.
 * TelegramDeliveryCommand'ni qabul qilib, transport-level request'ga assemble qiladi,
 * gateway orqali execute qiladi, va gateway natijasini application-level result'ga
 * tarjima qiladi.
 *
 * Orchestration flow:
 * TelegramDeliveryCommand
 *   -> TelegramSendMessageRequestAssembler.assemble(command)
 *   -> TelegramOutboundGateway.execute(request)
 *   -> TelegramGatewayResult -> TelegramDeliveryResult mapping
 *
 * Phase 189: har bir dispatch chaqiruvi {@code engops.telegram.send.attempts}
 * Micrometer counter'iga bitta increment yozadi. Counter tags low-cardinality
 * bo'lib qoladi (outcome, error, gateway) — tenantId, workItemId, token va
 * boshqa identifier'lar HECH QACHON tag sifatida ishlatilmaydi.
 *
 * Muhim:
 * - Business rule yo'q — faqat orchestration
 * - Rendering yo'q — command allaqachon tayyor
 * - HTTP yo'q — gateway abstraktsiya orqali
 * - Retry yo'q — keyingi phase
 * - Repository access yo'q
 * - Stateless — concurrent-safe
 */
@Service
public class TelegramOutboundDispatchService {

    /** Phase 189 — send attempt counter nomi (low-cardinality). */
    static final String SEND_ATTEMPTS_METER = "engops.telegram.send.attempts";

    private static final String ERROR_NONE = "NONE";
    private static final String GATEWAY_HTTP = "http";
    private static final String GATEWAY_STUB = "stub";
    private static final String GATEWAY_UNKNOWN = "unknown";

    private final TelegramOutboundGateway gateway;
    private final TelegramSendMessageRequestAssembler assembler;
    private final MeterRegistry meterRegistry;
    private final String gatewayTagValue;

    public TelegramOutboundDispatchService(TelegramOutboundGateway gateway,
                                           TelegramSendMessageRequestAssembler assembler,
                                           MeterRegistry meterRegistry) {
        this.gateway = gateway;
        this.assembler = assembler;
        this.meterRegistry = meterRegistry;
        this.gatewayTagValue = resolveGatewayTag(gateway);
    }

    private static String resolveGatewayTag(TelegramOutboundGateway gateway) {
        if (gateway == null) {
            return GATEWAY_UNKNOWN;
        }
        // Bounded mapping — token, URL yoki boshqa identifier metric tag'iga
        // tushmasligi uchun. Ikkala implementatsiya ham shu paketda, shuning
        // uchun instanceof type-safe va class simple-name string compare'dan
        // afzal (rename'ga chidamli).
        if (gateway instanceof HttpTelegramOutboundGateway) {
            return GATEWAY_HTTP;
        }
        if (gateway instanceof StubTelegramOutboundGateway) {
            return GATEWAY_STUB;
        }
        return GATEWAY_UNKNOWN;
    }

    /**
     * TelegramDeliveryCommand'ni orchestrate qiladi:
     * command -> transport request -> gateway execute -> delivery result.
     *
     * @param command outbound delivery command
     * @return application-level delivery natijasi
     * @throws IllegalArgumentException agar command null bo'lsa
     * @throws IllegalStateException agar gateway null result qaytarsa
     */
    public TelegramDeliveryResult dispatch(TelegramDeliveryCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("TelegramDeliveryCommand null bo'lishi mumkin emas");
        }

        TelegramSendMessageRequest request = assembler.assemble(command);

        TelegramGatewayResult gatewayResult;
        try {
            gatewayResult = gateway.execute(request);
        } catch (RuntimeException ex) {
            // Phase 189 — defensive: gateway contract kutilmagan exception
            // tashlamasligi shart, lekin agar tashlasa, metric uchun
            // outcome=EXCEPTION + error=UNKNOWN_ERROR qayd qilamiz va
            // exception caller'ga propagate qilamiz (mavjud xulq-atvor
            // saqlanadi). UNKNOWN_ERROR tag'i NONE'dan afzal — NONE faqat
            // DELIVERED happy path uchun semantikani saqlash uchun.
            recordSendAttempt("EXCEPTION", TelegramGatewayError.UNKNOWN_ERROR.name());
            throw ex;
        }

        if (gatewayResult == null) {
            // null result ham defensive xato — UNKNOWN_ERROR sifatida
            // klassifikatsiya qilamiz va caller'ga IllegalStateException
            // propagate qilamiz (mavjud kontrakt saqlanadi).
            recordSendAttempt("EXCEPTION", TelegramGatewayError.UNKNOWN_ERROR.name());
            throw new IllegalStateException(
                    "TelegramOutboundGateway.execute() null qaytardi — bu hech qachon sodir bo'lmasligi kerak");
        }

        TelegramDeliveryResult result = mapToDeliveryResult(command, gatewayResult);
        recordSendAttempt(result);
        return result;
    }

    private TelegramDeliveryResult mapToDeliveryResult(TelegramDeliveryCommand command,
                                                        TelegramGatewayResult gatewayResult) {
        return switch (gatewayResult.getResultType()) {
            case SUCCESS -> TelegramDeliveryResult.success(
                    command, gatewayResult.getTelegramMessageId());
            case REJECTED -> TelegramDeliveryResult.rejected(
                    command,
                    gatewayResult.getError().name(),
                    gatewayResult.getErrorMessage());
            case FAILED -> TelegramDeliveryResult.failed(
                    command,
                    gatewayResult.getError().name(),
                    gatewayResult.getErrorMessage());
        };
    }

    /**
     * Phase 189 — send attempt counter increment (low-cardinality tags only).
     *
     * <p>Outcome bo'lim {@link TelegramDeliveryResult.DeliveryOutcome} enum
     * nomidan olinadi (DELIVERED / REJECTED / FAILED). Error tag DELIVERED
     * uchun {@code NONE}, aks holda {@code failureCode} (allaqachon
     * {@link TelegramGatewayError} enum name).</p>
     */
    private void recordSendAttempt(TelegramDeliveryResult result) {
        String outcome = result.getDeliveryOutcome().name();
        String error;
        if (result.getDeliveryOutcome() == TelegramDeliveryResult.DeliveryOutcome.DELIVERED) {
            error = ERROR_NONE;
        } else {
            String code = result.getFailureCode();
            error = code == null ? ERROR_NONE : code;
        }
        recordSendAttempt(outcome, error);
    }

    private void recordSendAttempt(String outcome, String error) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(SEND_ATTEMPTS_METER)
                .tag("outcome", outcome)
                .tag("error", error)
                .tag("gateway", gatewayTagValue)
                .register(meterRegistry)
                .increment();
    }
}
