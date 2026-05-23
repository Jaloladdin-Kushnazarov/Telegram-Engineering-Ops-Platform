package com.engops.platform.telegram.command;

import com.engops.platform.telegram.TelegramBotCommandContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WhoAmICommandTest {

    private final WhoAmICommand command = new WhoAmICommand();

    @Test
    void commandName_isWhoami() {
        assertThat(command.commandName()).isEqualTo("/whoami");
    }

    @Test
    void execute_returnsActorDisplayNameAndTelegramIdAndTenantSlug() {
        TelegramBotCommandContext ctx = new TelegramBotCommandContext(
                UUID.randomUUID(), "Demo Admin",
                UUID.randomUUID(), "acme",
                123456789L, -1001234567890L,
                "/whoami", List.of(), "/whoami");
        String reply = command.execute(ctx);

        assertThat(reply).contains("Foydalanuvchi: Demo Admin");
        assertThat(reply).contains("Telegram ID: 123456789");
        assertThat(reply).contains("Tenant: acme");
        assertThat(reply).contains("Membership: ACTIVE");
    }

    @Test
    void execute_handlesNullTenantSlug() {
        TelegramBotCommandContext ctx = new TelegramBotCommandContext(
                UUID.randomUUID(), "User",
                UUID.randomUUID(), null,
                1L, 1L, "/whoami", List.of(), "/whoami");
        String reply = command.execute(ctx);
        assertThat(reply).contains("Tenant: null");
    }
}
