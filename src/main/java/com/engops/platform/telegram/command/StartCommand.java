package com.engops.platform.telegram.command;

import com.engops.platform.telegram.TelegramBotCommand;
import com.engops.platform.telegram.TelegramBotCommandContext;
import org.springframework.stereotype.Component;

/**
 * Phase 200 — {@code /start} bot komandasi.
 *
 * Botni birinchi marta ishlatayotgan ro'yxatdan o'tgan foydalanuvchi
 * uchun salomlashish + /help eslatmasi.
 */
@Component
public class StartCommand implements TelegramBotCommand {

    @Override
    public String commandName() {
        return "/start";
    }

    @Override
    public String execute(TelegramBotCommandContext context) {
        return "Salom, " + context.actorDisplayName() + "!\n"
                + "Engineering Ops platformasiga xush kelibsiz. "
                + "Mavjud buyruqlar ro'yxati uchun /help yozing.";
    }
}
