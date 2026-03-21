package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.telegram.NavigationStateStore;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.application.support.AllowedPathValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(14)
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class CdCommand implements TelegramCommand {

    private final TelegramBotClient telegramBotClient;
    private final NavigationStateStore navigationStateStore;
    private final AllowedPathValidator allowedPathValidator;

    @Override
    public List<BotCommandInfo> botCommands() {
        return List.of(new BotCommandInfo("cd", "Enter subdirectory (cd .. to go back)"));
    }

    @Override
    public List<String> helpLines() {
        return List.of("/cd <name> — enter directory (cd .. to go back)");
    }

    @Override
    public boolean canHandle(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        return t.equals("/cd") || t.startsWith("/cd ");
    }

    @Override
    public void handle(TelegramCommandContext ctx) {
        String arg = ctx.text().trim().replaceFirst("^/cd", "").trim();
        if (arg.isBlank()) {
            telegramBotClient.sendPlain(ctx.chatId(), "Usage: /cd <folder> or /cd ..");
            return;
        }
        Optional<String> cwd = navigationStateStore.getSelectedPath(ctx.chatId(), ctx.userId());
        if (cwd.isEmpty()) {
            telegramBotClient.sendPlain(ctx.chatId(), "No directory selected. Use /nav first.");
            return;
        }
        String result = changeDirectory(cwd.get(), arg);
        if (result == null) {
            return;
        }
        navigationStateStore.setSelectedPath(ctx.chatId(), ctx.userId(), result);
        List<List<Map<String, String>>> keyboard = DirectoryListingHelper.buildDirectoryKeyboard(
            result, allowedPathValidator);
        String message = DirectoryListingHelper.formatCwdMessage(result);
        if (keyboard.isEmpty()) {
            telegramBotClient.sendPlain(ctx.chatId(), message + " (none)");
        } else {
            telegramBotClient.sendWithInlineKeyboard(ctx.chatId(), message, keyboard);
        }
    }

    /**
     * Resolve target directory and validate. Returns canonical path or null if not allowed/not found.
     */
    public String changeDirectory(String currentCwd, String arg) {
        if ("..".equals(arg)) {
            Path parent = Path.of(currentCwd).getParent();
            if (parent == null || !allowedPathValidator.isAllowedCwd(parent.toString())) {
                return null;
            }
            try {
                return parent.toRealPath().toString();
            } catch (IOException e) {
                return null;
            }
        }
        Path target = Path.of(currentCwd, arg);
        try {
            Path real = target.toRealPath();
            if (!Files.isDirectory(real)) {
                return null;
            }
            if (!allowedPathValidator.isAllowedCwd(real.toString())) {
                return null;
            }
            return real.toString();
        } catch (IOException e) {
            return null;
        }
    }
}
