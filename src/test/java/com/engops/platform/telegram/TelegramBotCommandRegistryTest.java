package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelegramBotCommandRegistryTest {

    private static final class FakeCommand implements TelegramBotCommand {
        private final String name;
        FakeCommand(String name) { this.name = name; }
        @Override public String commandName() { return name; }
        @Override public String execute(TelegramBotCommandContext context) { return "ok"; }
    }

    @Test
    void lookupCaseInsensitive_resolvesSameCommand() {
        TelegramBotCommand help = new FakeCommand("/help");
        TelegramBotCommandRegistry registry = new TelegramBotCommandRegistry(List.of(help));

        assertThat(registry.findByName("/help")).contains(help);
        assertThat(registry.findByName("/Help")).contains(help);
        assertThat(registry.findByName("/HELP")).contains(help);
    }

    @Test
    void unknownCommand_returnsEmpty() {
        TelegramBotCommandRegistry registry = new TelegramBotCommandRegistry(
                List.of(new FakeCommand("/help")));

        assertThat(registry.findByName("/start")).isEmpty();
        assertThat(registry.findByName(null)).isEmpty();
        assertThat(registry.findByName("")).isEmpty();
        assertThat(registry.findByName("   ")).isEmpty();
    }

    @Test
    void duplicateCommandName_atConstructionThrowsIllegalState() {
        assertThatThrownBy(() -> new TelegramBotCommandRegistry(
                List.of(new FakeCommand("/help"), new FakeCommand("/help"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void duplicateCommandName_caseInsensitiveAlsoThrows() {
        assertThatThrownBy(() -> new TelegramBotCommandRegistry(
                List.of(new FakeCommand("/help"), new FakeCommand("/Help"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void emptyCommandList_resolvesNothing() {
        TelegramBotCommandRegistry registry = new TelegramBotCommandRegistry(List.of());
        assertThat(registry.findByName("/anything")).isEmpty();
    }
}
