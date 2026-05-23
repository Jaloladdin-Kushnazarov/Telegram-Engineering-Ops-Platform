package com.engops.platform.telegram;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Phase 200 — bot command registratsiya katalogi.
 *
 * <p>Spring boshlanishida barcha {@link TelegramBotCommand}
 * {@code @Component} bean'larini avtomatik aniqlaydi va case-INSENSITIVE
 * indeks quradi. Ikkita bean bir xil {@code commandName()} qaytarsa,
 * boshlanish vaqtida {@link IllegalStateException} bilan fail-fast bo'ladi
 * — dispatcher'da ambiguity bo'lmaydi.</p>
 *
 * <p>Lookup'lar lowercase normalization orqali — "/Help" va "/help"
 * bir xil command'ga route qilinadi.</p>
 */
@Component
public class TelegramBotCommandRegistry {

    private final Map<String, TelegramBotCommand> commandsByName;

    public TelegramBotCommandRegistry(List<TelegramBotCommand> commands) {
        Map<String, TelegramBotCommand> index = new HashMap<>();
        for (TelegramBotCommand command : commands) {
            String key = normalize(command.commandName());
            TelegramBotCommand previous = index.put(key, command);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate Telegram bot command registered: '" + key
                                + "' (classes: " + previous.getClass().getName()
                                + " va " + command.getClass().getName() + ")");
            }
        }
        this.commandsByName = Map.copyOf(index);
    }

    /**
     * Case-insensitive lookup. Topilmasa {@link Optional#empty()}.
     */
    public Optional<TelegramBotCommand> findByName(String commandName) {
        if (commandName == null || commandName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(commandsByName.get(normalize(commandName)));
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
