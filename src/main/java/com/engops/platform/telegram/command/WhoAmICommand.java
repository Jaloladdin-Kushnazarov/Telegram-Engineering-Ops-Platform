package com.engops.platform.telegram.command;

import com.engops.platform.telegram.TelegramBotCommand;
import com.engops.platform.telegram.TelegramBotCommandContext;
import org.springframework.stereotype.Component;

/**
 * Phase 200 — {@code /whoami} bot komandasi.
 *
 * Joriy actor + resolved tenant + Telegram identity haqida bounded
 * ma'lumotni qaytaradi. Operator o'z konteksti to'g'ri ekanini tezda
 * tekshirish uchun ishlatadi.
 */
@Component
public class WhoAmICommand implements TelegramBotCommand {

    @Override
    public String commandName() {
        return "/whoami";
    }

    @Override
    public String execute(TelegramBotCommandContext context) {
        return "Foydalanuvchi: " + context.actorDisplayName() + "\n"
                + "Telegram ID: " + context.telegramUserId() + "\n"
                + "Tenant: " + context.tenantSlug() + "\n"
                + "Membership: ACTIVE";
    }
}
