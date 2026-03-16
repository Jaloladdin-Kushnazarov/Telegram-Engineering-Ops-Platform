package com.engops.platform.telegram;

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

    private final TelegramOutboundGateway gateway;
    private final TelegramSendMessageRequestAssembler assembler;

    public TelegramOutboundDispatchService(TelegramOutboundGateway gateway,
                                           TelegramSendMessageRequestAssembler assembler) {
        this.gateway = gateway;
        this.assembler = assembler;
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

        TelegramGatewayResult gatewayResult = gateway.execute(request);

        if (gatewayResult == null) {
            throw new IllegalStateException(
                    "TelegramOutboundGateway.execute() null qaytardi — bu hech qachon sodir bo'lmasligi kerak");
        }

        return mapToDeliveryResult(command, gatewayResult);
    }

    private TelegramDeliveryResult mapToDeliveryResult(TelegramDeliveryCommand command,
                                                        TelegramGatewayResult gatewayResult) {
        if (gatewayResult.isSuccess()) {
            return TelegramDeliveryResult.success(command, gatewayResult.getTelegramMessageId());
        }

        return TelegramDeliveryResult.failure(
                command,
                gatewayResult.getError().name(),
                gatewayResult.getErrorMessage());
    }
}
