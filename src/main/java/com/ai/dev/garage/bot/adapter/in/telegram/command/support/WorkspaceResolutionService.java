package com.ai.dev.garage.bot.adapter.in.telegram.command.support;

import com.ai.dev.garage.bot.adapter.in.telegram.NavigationStateStore;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.adapter.in.telegram.command.TelegramCommandContext;
import com.ai.dev.garage.bot.application.support.AllowedPathValidator;
import com.ai.dev.garage.bot.config.CursorCliProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class WorkspaceResolutionService {

    private final NavigationStateStore navigationStateStore;
    private final TelegramBotClient telegramBotClient;
    private final AllowedPathValidator allowedPathValidator;
    private final CursorCliProperties cursorCliProperties;

    public record Resolution(String workspace, String prompt, String cliModel) {
        public Resolution(String workspace, String prompt) {
            this(workspace, prompt, null);
        }
    }

    public Optional<Resolution> resolveWorkspaceAndPrompt(
        TelegramCommandContext ctx,
        String after,
        String commandName
    ) {
        ModelAliasStrip strip = stripLeadingModelAlias(after);
        String remainder = strip.remainder();
        String cliModel = strip.cliModel();

        if (missingUserGoalText(remainder)) {
            telegramBotClient.sendPlain(
                ctx.chatId(),
                "Add your goal after the model or folder token.\nExample: /" + commandName + " @myapp describe what to do"
            );
            return Optional.empty();
        }

        String firstToken = remainder.split("\\s+", 2)[0];
        if (firstToken.startsWith("@") && remainder.length() > firstToken.length()) {
            String folderName = firstToken.substring(1);
            String workspace = Optional.ofNullable(resolveAtFolder(ctx, folderName, commandName)).orElse("");
            if (workspace.isBlank()) {
                return Optional.empty();
            }
            String prompt = remainder.substring(firstToken.length()).trim();
            if (prompt.isBlank()) {
                telegramBotClient.sendPlain(
                    ctx.chatId(),
                    "Add your goal after the folder name.\nExample: /" + commandName + " @myapp what to plan"
                );
                return Optional.empty();
            }
            return Optional.of(new Resolution(workspace, prompt, cliModel));
        }
        String workspace = Optional.ofNullable(requireCwd(ctx, commandName)).orElse("");
        if (workspace.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Resolution(workspace, remainder, cliModel));
    }

    private ModelAliasStrip stripLeadingModelAlias(String after) {
        String rest = after == null ? "" : after.trim();
        if (rest.isBlank()) {
            return new ModelAliasStrip(rest, null);
        }
        String[] parts = rest.split("\\s+", 2);
        String first = parts[0];
        if (!first.startsWith("@") || first.length() < 2) {
            return new ModelAliasStrip(rest, null);
        }
        String aliasKey = first.substring(1);
        if (aliasKey.isBlank()) {
            return new ModelAliasStrip(rest, null);
        }
        String mapped = resolveAlias(cursorCliProperties.getTelegramModelAliases(), aliasKey);
        if (mapped == null) {
            return new ModelAliasStrip(rest, null);
        }
        String remainder = parts.length > 1 ? parts[1].trim() : "";
        return new ModelAliasStrip(remainder, mapped);
    }

    private static String resolveAlias(Map<String, String> aliases, String aliasKey) {
        if (aliases == null || aliases.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> e : aliases.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(aliasKey)) {
                String v = e.getValue();
                return v != null && !v.isBlank() ? v.trim() : null;
            }
        }
        return null;
    }

    private record ModelAliasStrip(String remainder, String cliModel) {
    }

    /**
     * True when there is no real prompt: empty after stripping an optional model alias, or a lone {@code @token}
     * with no following words (folder/model token only).
     */
    private static boolean missingUserGoalText(String remainder) {
        String r = remainder == null ? "" : remainder.trim();
        if (r.isEmpty()) {
            return true;
        }
        if (!r.startsWith("@")) {
            return false;
        }
        String afterAt = r.substring(1);
        return !afterAt.contains(" ") && !afterAt.contains("\n") && !afterAt.contains("\t");
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
