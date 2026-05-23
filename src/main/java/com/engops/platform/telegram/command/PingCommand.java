package com.engops.platform.telegram.command;

import com.engops.platform.telegram.TelegramBotCommand;
import com.engops.platform.telegram.TelegramBotCommandContext;
import org.springframework.stereotype.Component;

/**
 * Phase 200 — {@code /ping} bot komandasi.
 *
 * Quick liveness check: doim "pong" qaytaradi. Operator monitoring va
 * uplink tekshiruvi uchun ishlatadi.
 */
@Component
public class PingCommand implements TelegramBotCommand {

    @Override
    public String commandName() {
        return "/ping";
    }

    @Override
    public String execute(TelegramBotCommandContext context) {
        return "pong";
    }
}
