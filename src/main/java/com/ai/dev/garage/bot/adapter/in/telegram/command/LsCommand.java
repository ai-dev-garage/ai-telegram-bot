package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.telegram.NavigationStateStore;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.adapter.in.telegram.command.support.DirectoryListingHelper;
import com.ai.dev.garage.bot.application.support.AllowedPathValidator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

@Component
@Order(13)
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class LsCommand implements TelegramCommand {

    private final TelegramBotClient telegramBotClient;
    private final NavigationStateStore navigationStateStore;
    private final AllowedPathValidator allowedPathValidator;

    @Override
    public List<BotCommandInfo> botCommands() {
        return List.of(new BotCommandInfo("ls", "List subdirectories"));
    }

    @Override
    public List<String> helpLines() {
        return List.of("/ls — list subdirectories");
    }

    @Override
    public boolean canHandle(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        return Objects.equals(t, "/ls") || t.startsWith("/ls ");
    }

    @Override
    public void handle(TelegramCommandContext ctx) {
        Optional<String> cwd = navigationStateStore.getSelectedPath(ctx.chatId(), ctx.userId());
        if (cwd.isEmpty()) {
            telegramBotClient.sendPlain(ctx.chatId(), "No directory selected. Use /nav first.");
            return;
        }
        List<List<Map<String, String>>> keyboard = DirectoryListingHelper.buildDirectoryKeyboard(
            cwd.get(), allowedPathValidator);
        if (keyboard.isEmpty()) {
            telegramBotClient.sendPlain(ctx.chatId(),
                DirectoryListingHelper.formatCwdMessage(cwd.get()) + " (none)");
            return;
        }
        telegramBotClient.sendWithInlineKeyboard(ctx.chatId(),
            DirectoryListingHelper.formatCwdMessage(cwd.get()), keyboard);
    }
}
