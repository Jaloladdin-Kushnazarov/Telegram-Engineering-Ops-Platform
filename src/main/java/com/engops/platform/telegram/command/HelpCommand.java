package com.engops.platform.telegram.command;

import com.engops.platform.telegram.TelegramBotCommand;
import com.engops.platform.telegram.TelegramBotCommandContext;
import org.springframework.stereotype.Component;

/**
 * Phase 200 — {@code /help} bot komandasi.
 *
 * Mavjud bot buyruqlari ro'yxatini qaytaradi. Phase 200'da ro'yxat
 * qattiq kodlangan; kelajakdagi phase'larda registry'dan dinamik
 * enumeration qo'shilishi mumkin.
 */
@Component
public class HelpCommand implements TelegramBotCommand {

    @Override
    public String commandName() {
        return "/help";
    }

    @Override
    public String execute(TelegramBotCommandContext context) {
        return "Mavjud buyruqlar:\n"
                + "/start — botni boshlash\n"
                + "/help — buyruqlar ro'yxati\n"
                + "/whoami — joriy foydalanuvchi haqida\n"
                + "/onboard — yangi tenant ochish (admin)\n"
                + "/ping — bot tirik tekshiruvi";
    }
}
