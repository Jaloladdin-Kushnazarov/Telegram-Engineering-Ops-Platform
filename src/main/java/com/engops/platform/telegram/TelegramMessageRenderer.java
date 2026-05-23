package com.engops.platform.telegram;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * TelegramCardView'dan transport-level TelegramMessage hosil qiluvchi renderer.
 *
 * Bu component pure rendering qiladi:
 * 1. cardView.renderPayload dan message text yig'adi
 * 2. cardView.actions dan inline keyboard hosil qiladi
 *
 * Muhim:
 * - Repository access yo'q — pure rendering
 * - Side effect yo'q
 * - Business rule yo'q
 * - Authorization yo'q — faqat view render
 * - Stateless — concurrent-safe
 * - Telegram Bot API ishlatilmaydi — faqat internal model
 */
@Component
public class TelegramMessageRenderer {

    /**
     * TelegramCardView'dan tayyor TelegramMessage hosil qiladi.
     *
     * @param cardView render payload + action'lar
     * @return transport-level message model
     * @throws IllegalArgumentException agar cardView null bo'lsa
     */
    public TelegramMessage render(TelegramCardView cardView) {
        if (cardView == null) {
            throw new IllegalArgumentException("TelegramCardView null bo'lishi mumkin emas");
        }

        TelegramRenderPayload renderPayload = cardView.getRenderPayload();

        String text = buildMessageText(renderPayload);
        List<TelegramInlineKeyboardRow> keyboard = buildKeyboard(cardView.getActions());

        return new TelegramMessage(
                renderPayload.getTenantId(),
                renderPayload.getWorkItemId(),
                text,
                keyboard,
                renderPayload.getTargetChatBindingId(),
                renderPayload.getTargetTopicId());
    }

    /**
     * Phase 194 / Phase 196 — base 3-line card text optionally followed by
     * {@code Priority: <code>}, {@code Severity: <code>} and/or
     * {@code Owner: <displayName>} lines (in that stable order).
     *
     * <p>Each optional line is appended <em>only</em> when the corresponding
     * field on the {@link TelegramRenderPayload} is non-null and non-blank.
     * Blank strings are treated like absent values; no empty-label line is
     * ever rendered. When ALL THREE optional fields are absent the output is
     * byte-for-byte identical to the pre-Phase-194 format, preserving Phase
     * 179 NOT_MODIFIED behavior for unchanged work items.</p>
     *
     * <p><strong>Owner is always rendered as the pre-resolved display label
     * String</strong> (Phase 196 contract). The raw owner UUID is never
     * carried into this renderer and never written to the rendered text —
     * resolution happens publisher-side via {@code IdentityQueryService} in
     * intake / workflow services, never in the telegram module.</p>
     *
     * <p>No Markdown or HTML — plain text only. No {@code parse_mode}.
     * Rendered text is never logged (token-leak guard pattern).</p>
     */
    private String buildMessageText(TelegramRenderPayload renderPayload) {
        StringBuilder sb = new StringBuilder()
                .append(renderPayload.getHeaderLine()).append('\n')
                .append(renderPayload.getDisplayTitle()).append('\n')
                .append(renderPayload.getStatusLine());
        if (isPresent(renderPayload.getPriorityCode())) {
            sb.append('\n').append("Priority: ").append(renderPayload.getPriorityCode());
        }
        if (isPresent(renderPayload.getSeverityCode())) {
            sb.append('\n').append("Severity: ").append(renderPayload.getSeverityCode());
        }
        if (isPresent(renderPayload.getOwnerDisplayLabel())) {
            sb.append('\n').append("Owner: ").append(renderPayload.getOwnerDisplayLabel());
        }
        return sb.toString();
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private List<TelegramInlineKeyboardRow> buildKeyboard(List<TelegramCardAction> actions) {
        return actions.stream()
                .map(this::toKeyboardRow)
                .toList();
    }

    private TelegramInlineKeyboardRow toKeyboardRow(TelegramCardAction action) {
        TelegramInlineKeyboardButton button = new TelegramInlineKeyboardButton(
                action.getLabel(),
                action.getCallbackData());
        return new TelegramInlineKeyboardRow(List.of(button));
    }
}
