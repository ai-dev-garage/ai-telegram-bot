package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.telegram.NavigationStateStore;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

@Component
@Order(15)
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PwdCommand implements TelegramCommand {

    private final TelegramBotClient telegramBotClient;
    private final NavigationStateStore navigationStateStore;

    @Override
    public List<BotCommandInfo> botCommands() {
        return List.of(new BotCommandInfo("pwd", "Show full path of current directory"));
    }

    @Override
    public List<String> helpLines() {
        return List.of("/pwd — show full path of current directory");
    }

    @Override
    public boolean canHandle(String text) {
        return text != null && Objects.equals(text.trim(), "/pwd");
    }

    @Override
    public void handle(TelegramCommandContext ctx) {
        Optional<String> cwd = navigationStateStore.getSelectedPath(ctx.chatId(), ctx.userId());
        if (cwd.isEmpty()) {
            telegramBotClient.sendPlain(ctx.chatId(), "No directory selected. Use /nav first.");
            return;
        }
        telegramBotClient.sendPlain(ctx.chatId(), cwd.get());
    }
}
