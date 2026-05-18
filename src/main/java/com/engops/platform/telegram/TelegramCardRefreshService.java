package com.engops.platform.telegram;

import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.TelegramChatBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 177 — thin composer service for future Telegram in-place card refresh.
 *
 * <p>Bu service uchta primitivni bog'laydi:</p>
 * <ol>
 *   <li>{@link TelegramDeliveryAttemptHistoryReadAccess#findLatestDeliveredSendMessage(UUID, UUID)}
 *       orqali "current active Telegram card" namzodini topadi (eng so'nggi
 *       {@code DELIVERED} + {@code SEND_NEW_MESSAGE} attempt).</li>
 *   <li>{@link TenantConfigQueryService#findChatBindingById(UUID, UUID)}
 *       orqali storage'dagi {@code target_chat_binding_id}'ni Telegram
 *       numeric {@code chat_id}'ga aylantiradi.</li>
 *   <li>{@link TelegramOutboundGateway#editMessageText(TelegramEditMessageTextRequest)}
 *       chaqirilib, Telegram'dagi mavjud xabar joyida tahrirlanadi.</li>
 * </ol>
 *
 * <p><strong>Phase 177 wiring:</strong> bu service hech qanday production
 * yo'lida chaqirilmaydi. AFTER_COMMIT card dispatch listener,
 * {@link com.engops.platform.intake.TelegramCallbackActionExecutionService},
 * webhook controller — birortasi bu service'ni invoke qilmaydi. U faqat
 * primitiv sifatida foundation/test sirti. Production wiring Phase 178
 * da edit-first/send-as-fallback siyosati bilan birga bo'ladi.</p>
 *
 * <p><strong>Boundary'lar:</strong></p>
 * <ul>
 *   <li>Bu service {@code workflow} modulini import qilmaydi
 *       ({@code ModuleBoundaryTest} qoidasi).</li>
 *   <li>{@code identity} va {@code workitem} modulini ham import qilmaydi.</li>
 *   <li>Hech qanday state mutation bajarmaydi.</li>
 *   <li>{@code telegram_delivery_attempt}'ga edit attempt yozmaydi
 *       (Phase 178 wiring bilan birga kelishi mumkin).</li>
 *   <li>Class-level {@code @Transactional} EMAS. Collaborator tx
 *       chegaralari (read-access read-only) ishlatiladi.</li>
 *   <li>Retry yo'q.</li>
 * </ul>
 *
 * <p><strong>Fail-soft kontrakt:</strong> bu service exception
 * tashlamaydi. Har bir holat {@link TelegramEditMessageTextResult}
 * sifatida qaytariladi:</p>
 * <ul>
 *   <li>Prior delivered attempt topilmasa → {@code REJECTED} +
 *       {@code INVALID_REQUEST} (no active card).</li>
 *   <li>Attempt'da {@code external_message_id} {@code null} bo'lsa
 *       (defensive) → {@code REJECTED}.</li>
 *   <li>Chat binding mavjud bo'lmasa yoki {@code chat_id} resolve
 *       qilinmasa → {@code REJECTED}.</li>
 *   <li>Gateway natijasi (success/rejected/failed) o'zgartirilmasdan
 *       qaytariladi.</li>
 *   <li>Read access yoki tenant config kutilmagan
 *       {@link RuntimeException} tashlasa → {@code FAILED} +
 *       {@code UNKNOWN_ERROR} (bounded log faqat
 *       {@code exceptionType}; message log'ga chiqarilmaydi).</li>
 *   <li>Gateway kutilmagan {@link RuntimeException} tashlasa
 *       (gateway contract'iga ko'ra bo'lmasligi shart, lekin
 *       defense-in-depth) → {@code FAILED} + {@code UNKNOWN_ERROR}.</li>
 * </ul>
 *
 * <p><strong>Tenant isolation:</strong> har bir DB lookup tenantId
 * argumenti bilan filtrlanadi. Read access {@code (tenantId, workItemId)}
 * juftligi bo'yicha; {@link TenantConfigQueryService#findChatBindingById(UUID, UUID)}
 * tenant-safe; gateway resolved numeric chat_id ustida ishlaydi —
 * cross-tenant leak mumkin emas.</p>
 *
 * <p><strong>Logging hygiene:</strong> faqat bounded metadata
 * ({@code resultType}, {@code error.name()}, {@code exceptionType})
 * log qilinadi. Hech qachon yozilmaydi: bot token, full URL, full
 * payload, {@code text} qiymati, exception message, full callback_data.</p>
 */
@Service
public class TelegramCardRefreshService {

    private static final Logger log = LoggerFactory.getLogger(TelegramCardRefreshService.class);

    private final TelegramDeliveryAttemptHistoryReadAccess historyReadAccess;
    private final TenantConfigQueryService tenantConfigQueryService;
    private final TelegramOutboundGateway gateway;

    public TelegramCardRefreshService(TelegramDeliveryAttemptHistoryReadAccess historyReadAccess,
                                       TenantConfigQueryService tenantConfigQueryService,
                                       TelegramOutboundGateway gateway) {
        this.historyReadAccess = historyReadAccess;
        this.tenantConfigQueryService = tenantConfigQueryService;
        this.gateway = gateway;
    }

    /**
     * Mavjud Telegram card'ni joyida tahrirlashga harakat qiladi (best-effort).
     *
     * <p>Caller (Phase 178 wiring) yangi rendered text va keyboard'ni
     * tashqaridan beradi — bu service rendering qilmaydi. {@code keyboard}
     * {@code null} yoki bo'sh ro'yxat bo'lishi mumkin (request normalizatsiya
     * qiladi).</p>
     *
     * @param tenantId tenant identifikatori
     * @param workItemId work item identifikatori
     * @param text yangi message matni (plain text, parse_mode yo'q)
     * @param keyboard ixtiyoriy yangi inline keyboard
     * @return strukturali natija (har holat fail-soft)
     */
    public TelegramEditMessageTextResult refresh(UUID tenantId,
                                                   UUID workItemId,
                                                   String text,
                                                   List<TelegramInlineKeyboardRow> keyboard) {
        if (tenantId == null || workItemId == null) {
            log.info("Telegram card refresh skip resultType=REJECTED reason=missing-ids");
            return TelegramEditMessageTextResult.rejected(
                    TelegramGatewayError.INVALID_REQUEST,
                    "tenantId or workItemId null");
        }
        if (text == null || text.isBlank()) {
            log.info("Telegram card refresh skip resultType=REJECTED reason=blank-text");
            return TelegramEditMessageTextResult.rejected(
                    TelegramGatewayError.INVALID_REQUEST,
                    "text null or blank");
        }

        Optional<TelegramDeliveryAttempt> latestOpt;
        try {
            latestOpt = historyReadAccess.findLatestDeliveredSendMessage(tenantId, workItemId);
        } catch (RuntimeException ex) {
            log.warn("Telegram card refresh failed resultType=READ_FAILURE exceptionType={}",
                    ex.getClass().getSimpleName());
            return TelegramEditMessageTextResult.failed(
                    TelegramGatewayError.UNKNOWN_ERROR,
                    "Active card read failed");
        }

        if (latestOpt.isEmpty()) {
            log.info("Telegram card refresh skip resultType=REJECTED reason=no-active-card");
            return TelegramEditMessageTextResult.rejected(
                    TelegramGatewayError.INVALID_REQUEST,
                    "no active card found");
        }

        TelegramDeliveryAttempt latest = latestOpt.get();
        Long messageId = latest.getExternalMessageId();
        if (messageId == null) {
            // Defensive: DELIVERED row external_message_id null bo'lmasligi
            // kutiladi, lekin DB invariant nullable shuni cheklab qo'ymaydi.
            log.warn("Telegram card refresh skip resultType=REJECTED reason=null-external-message-id");
            return TelegramEditMessageTextResult.rejected(
                    TelegramGatewayError.INVALID_REQUEST,
                    "active card has no external message id");
        }

        UUID chatBindingId = latest.getTargetChatBindingId();
        if (chatBindingId == null) {
            log.warn("Telegram card refresh skip resultType=REJECTED reason=null-chat-binding");
            return TelegramEditMessageTextResult.rejected(
                    TelegramGatewayError.INVALID_REQUEST,
                    "active card has no chat binding");
        }

        long chatId;
        try {
            Optional<TelegramChatBinding> bindingOpt =
                    tenantConfigQueryService.findChatBindingById(tenantId, chatBindingId);
            if (bindingOpt.isEmpty()) {
                log.info("Telegram card refresh skip resultType=REJECTED reason=chat-binding-missing");
                return TelegramEditMessageTextResult.rejected(
                        TelegramGatewayError.INVALID_REQUEST,
                        "chat binding not found");
            }
            chatId = bindingOpt.get().getChatId();
        } catch (RuntimeException ex) {
            log.warn("Telegram card refresh failed resultType=BINDING_LOOKUP_FAILURE exceptionType={}",
                    ex.getClass().getSimpleName());
            return TelegramEditMessageTextResult.failed(
                    TelegramGatewayError.UNKNOWN_ERROR,
                    "Chat binding lookup failed");
        }

        TelegramEditMessageTextRequest request;
        try {
            request = new TelegramEditMessageTextRequest(chatId, messageId, text, keyboard);
        } catch (IllegalArgumentException ex) {
            log.warn("Telegram card refresh skip resultType=REJECTED reason=invalid-request exceptionType={}",
                    ex.getClass().getSimpleName());
            return TelegramEditMessageTextResult.rejected(
                    TelegramGatewayError.INVALID_REQUEST,
                    "invalid edit request");
        }

        TelegramEditMessageTextResult result;
        try {
            result = gateway.editMessageText(request);
        } catch (RuntimeException ex) {
            log.warn("Telegram card refresh failed resultType=GATEWAY_UNEXPECTED exceptionType={}",
                    ex.getClass().getSimpleName());
            return TelegramEditMessageTextResult.failed(
                    TelegramGatewayError.UNKNOWN_ERROR,
                    "Gateway threw unexpected exception");
        }

        if (result == null) {
            log.warn("Telegram card refresh failed resultType=NULL_RESULT");
            return TelegramEditMessageTextResult.failed(
                    TelegramGatewayError.UNKNOWN_ERROR,
                    "Gateway returned null");
        }

        if (result.isSuccess()) {
            log.info("Telegram card refresh resultType=SUCCESS");
        } else {
            log.warn("Telegram card refresh resultType={} error={}",
                    result.getResultType(),
                    result.getError() == null ? null : result.getError().name());
        }
        return result;
    }
}
