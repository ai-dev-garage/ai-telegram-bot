package com.ai.dev.garage.bot.adapter.in.telegram.command;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

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

    public List<Map<String, String>> botApiCommands() {
        return commands.stream()
            .flatMap(c -> c.botCommands().stream())
            .map(bc -> Map.of("command", bc.command(), "description", bc.menuDescription()))
            .toList();
    }

    public String helpText() {
        String body = commands.stream()
            .flatMap(c -> c.helpLines().stream())
            .collect(Collectors.joining("\n"));
        return HEADER + "\n\n" + body;
    }

    public String unknownInputText() {
        return "Unknown command or message.\n\n" + helpText();
    }
}
