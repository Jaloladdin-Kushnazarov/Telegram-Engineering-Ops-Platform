package com.engops.platform.telegram.command;

import com.engops.platform.telegram.TelegramBotCommandContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PingCommandTest {

    private final PingCommand command = new PingCommand();

    private TelegramBotCommandContext genericContext() {
        return new TelegramBotCommandContext(
                UUID.randomUUID(), "User",
                UUID.randomUUID(), "acme",
                1L, 1L, "/ping", List.of(), "/ping");
    }

    @Test
    void commandName_isPing() {
        assertThat(command.commandName()).isEqualTo("/ping");
    }

    @Test
    void execute_returnsLowercasePong() {
        assertThat(command.execute(genericContext())).isEqualTo("pong");
    }

    @Test
    void execute_ignoresArguments() {
        TelegramBotCommandContext ctx = new TelegramBotCommandContext(
                UUID.randomUUID(), "User",
                UUID.randomUUID(), "acme",
                1L, 1L, "/ping",
                List.of("foo", "bar"), "/ping foo bar");
        assertThat(command.execute(ctx)).isEqualTo("pong");
    }
}
