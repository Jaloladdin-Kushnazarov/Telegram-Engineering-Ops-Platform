package com.engops.platform.telegram;

import java.util.List;

/**
 * Telegram card uchun to'liq ko'rinish contract — render payload + actionlar.
 *
 * Bu DTO future Telegram adapter uchun yagona kirish nuqtasi:
 * - renderPayload: message text uchun kerakli barcha ma'lumotlar
 * - actions: card ustidagi ko'rinadigan actionlar (future inline keyboard buttons)
 *
 * Adapter shu contract'ni qabul qilib:
 * 1. renderPayload'dan message text hosil qiladi
 * 2. actions'dan inline keyboard hosil qiladi
 * 3. message + keyboard ni Telegram API orqali yuboradi
 *
 * Hozir faqat contract — actual rendering va sending keyingi phase'larda.
 *
 * actions bo'sh list bo'lishi mumkin — bu valid holat
 * (masalan INCIDENT/TASK uchun hali MVP action'lar aniqlanmagan).
 */
public class TelegramCardView {

    private final TelegramRenderPayload renderPayload;
    private final List<TelegramCardAction> actions;

    public TelegramCardView(TelegramRenderPayload renderPayload,
                             List<TelegramCardAction> actions) {
        if (renderPayload == null) {
            throw new IllegalArgumentException("TelegramRenderPayload null bo'lishi mumkin emas");
        }
        this.renderPayload = renderPayload;
        this.actions = actions != null ? List.copyOf(actions) : List.of();
    }

    public TelegramRenderPayload getRenderPayload() { return renderPayload; }
    public List<TelegramCardAction> getActions() { return actions; }

    public boolean hasActions() { return !actions.isEmpty(); }
}
