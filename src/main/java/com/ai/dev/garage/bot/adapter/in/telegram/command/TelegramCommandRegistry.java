package com.ai.dev.garage.bot.adapter.in.telegram.command;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

/**
 * Aggregates {@link TelegramCommand#botCommands()} and {@link TelegramCommand#helpLines()}
 * from all registered commands so that bot-menu registration and /start help text
 * stay in sync automatically when new commands are added.
 */
@Component
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class TelegramCommandRegistry {

    private static final String HEADER = "Remote runner bot.";

    private final List<TelegramCommand> commands;

    /**
     * Payload for Telegram {@code setMyCommands}: stable {@link LinkedHashMap} entries ({@code command} then
     * {@code description}) and handler beans sorted by {@link org.springframework.core.annotation.Order}.
     */
    public List<Map<String, String>> botApiCommands() {
        return sortedCommands().stream()
            .flatMap(c -> c.botCommands().stream())
            .map(TelegramCommandRegistry::toBotCommandMap)
            .toList();
    }

    public String helpText() {
        String body = sortedCommands().stream()
            .flatMap(c -> c.helpLines().stream())
            .collect(Collectors.joining("\n"));
        return HEADER + "\n\n" + body;
    }

    public String unknownInputText() {
        return "Unknown command or message.\n\n" + helpText();
    }

    private List<TelegramCommand> sortedCommands() {
        List<TelegramCommand> sorted = new ArrayList<>(commands);
        sorted.sort(AnnotationAwareOrderComparator.INSTANCE);
        return sorted;
    }

    private static Map<String, String> toBotCommandMap(TelegramCommand.BotCommandInfo bc) {
        Map<String, String> row = new LinkedHashMap<>(2);
        row.put("command", bc.command());
        row.put("description", bc.menuDescription());
        return row;
    }
}
