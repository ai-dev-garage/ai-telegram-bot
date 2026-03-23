package com.ai.dev.garage.bot.adapter.in.telegram.command.support;

import com.ai.dev.garage.bot.adapter.in.telegram.NavigationStateStore;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.adapter.in.telegram.command.TelegramCommandContext;
import com.ai.dev.garage.bot.application.support.AllowedPathValidator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class WorkspaceResolutionService {

    private final NavigationStateStore navigationStateStore;
    private final TelegramBotClient telegramBotClient;
    private final AllowedPathValidator allowedPathValidator;

    public record Resolution(String workspace, String prompt) {
    }

    public Optional<Resolution> resolveWorkspaceAndPrompt(
        TelegramCommandContext ctx,
        String after,
        String commandName
    ) {
        String firstToken = after.split("\\s+", 2)[0];
        if (firstToken.startsWith("@") && after.length() > firstToken.length()) {
            String folderName = firstToken.substring(1);
            String workspace = Optional.ofNullable(resolveAtFolder(ctx, folderName, commandName)).orElse("");
            if (workspace.isBlank()) {
                return Optional.empty();
            }
            String prompt = after.substring(firstToken.length()).trim();
            return Optional.of(new Resolution(workspace, prompt));
        }
        String workspace = Optional.ofNullable(requireCwd(ctx, commandName)).orElse("");
        if (workspace.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Resolution(workspace, after));
    }

    private String requireCwd(TelegramCommandContext ctx, String commandName) {
        Optional<String> cwd = navigationStateStore.getSelectedPath(ctx.chatId(), ctx.userId());
        if (cwd.isEmpty() || cwd.get().isBlank()) {
            telegramBotClient.sendPlain(
                ctx.chatId(),
                "No working folder selected. Use /nav to pick one, or:\n/" + commandName + " @folder <prompt>"
            );
            return null;
        }
        return cwd.get();
    }

    private String resolveAtFolder(TelegramCommandContext ctx, String folderName, String commandName) {
        Optional<String> cwd = navigationStateStore.getSelectedPath(ctx.chatId(), ctx.userId());
        if (cwd.isEmpty()) {
            telegramBotClient.sendPlain(
                ctx.chatId(),
                "No working folder selected. Use /nav first, then /" + commandName + " @folder <prompt>."
            );
            return null;
        }
        try {
            Path target = Path.of(cwd.get(), folderName).toRealPath();
            if (!Files.isDirectory(target)) {
                telegramBotClient.sendPlain(ctx.chatId(), "Not a directory: " + folderName);
                return null;
            }
            if (!allowedPathValidator.isAllowedCwd(target.toString())) {
                telegramBotClient.sendPlain(ctx.chatId(), "Folder not under allowed path: " + folderName);
                return null;
            }
            return target.toString();
        } catch (IOException e) {
            telegramBotClient.sendPlain(ctx.chatId(), "Folder not found: " + folderName);
            return null;
        }
    }
}
