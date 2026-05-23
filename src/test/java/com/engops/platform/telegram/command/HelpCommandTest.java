package com.engops.platform.telegram.command;

import com.engops.platform.telegram.TelegramBotCommandContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HelpCommandTest {

    private final HelpCommand command = new HelpCommand();

    private TelegramBotCommandContext genericContext() {
        return new TelegramBotCommandContext(
                UUID.randomUUID(), "User",
                UUID.randomUUID(), "acme",
                1L, 1L, "/help", List.of(), "/help");
    }

    @Test
    void commandName_isHelp() {
        assertThat(command.commandName()).isEqualTo("/help");
    }

    @Test
    void execute_returnsListOfAllCommands() {
        String reply = command.execute(genericContext());
        assertThat(reply).contains("/start");
        assertThat(reply).contains("/help");
        assertThat(reply).contains("/whoami");
        assertThat(reply).contains("/onboard");
        assertThat(reply).contains("/ping");
    }

    @Test
    void execute_replyUnder4000Chars() {
        String reply = command.execute(genericContext());
        assertThat(reply.length()).isLessThan(4000);
    }
}
