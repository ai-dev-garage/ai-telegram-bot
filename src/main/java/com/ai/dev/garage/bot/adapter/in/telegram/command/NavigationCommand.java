package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.telegram.InlineKeyboardBuilder;
import com.ai.dev.garage.bot.adapter.in.telegram.NavigationStateStore;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.adapter.in.telegram.command.support.DirectoryListingHelper;
import com.ai.dev.garage.bot.config.RunnerProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Order(12)
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class NavigationCommand implements TelegramCommand {

    public static final String CALLBACK_PREFIX = "nav:";
    public static final String CALLBACK_CLEAR = "nav:clear";

    private final TelegramBotClient telegramBotClient;
    private final RunnerProperties runnerProperties;
    private final NavigationStateStore navigationStateStore;

    @Override
    public List<BotCommandInfo> botCommands() {
        return List.of(new BotCommandInfo("nav", "Show allowed root folders"));
    }

    @Override
    public List<String> helpLines() {
        return List.of("/nav — pick a root working directory");
    }

    @Override
    public boolean canHandle(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        return Objects.equals(t, "/navigation")
            || Objects.equals(t, "/nav")
            || t.startsWith("/navigation ")
            || t.startsWith("/nav ");
    }

    @Override
    public void handle(TelegramCommandContext ctx) {
        List<String> raw = runnerProperties.listAllowedNavigationPaths();
        log.debug("Navigation handle: chatId={} allowlistSize={}", ctx.chatId(), raw.size());
        if (raw.isEmpty()) {
            log.info("Navigation disabled: empty ALLOWED_NAVIGATION_PATHS chatId={}", ctx.chatId());
            telegramBotClient.sendPlain(
                ctx.chatId(),
                "/navigation is disabled. Set ALLOWED_NAVIGATION_PATHS (comma-separated absolute paths)."
            );
            return;
        }

        List<PathSnapshot> resolved = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            String p = expandHome(raw.get(i));
            try {
                Path real = Path.of(p).toRealPath();
                Path name = real.getFileName();
                String label = name != null ? name.toString() : real.toString();
                resolved.add(new PathSnapshot(i, real.toString(), label));
            } catch (IOException e) {
                log.warn("Navigation: path not resolved (skipped): {} — {}", p, e.getMessage());
            }
        }
        if (resolved.isEmpty()) {
            log.info("Navigation: no resolvable paths chatId={} rawCount={}", ctx.chatId(), raw.size());
            telegramBotClient.sendPlain(ctx.chatId(), "No valid paths from allowlist (directories missing). Check ALLOWED_NAVIGATION_PATHS.");
            return;
        }

        var kb = InlineKeyboardBuilder.create();
        for (PathSnapshot snap : resolved) {
            kb.row(snap.buttonLabel(), CALLBACK_PREFIX + snap.index());
        }
        kb.row("Clear", CALLBACK_CLEAR);

        Optional<String> currentCwd = navigationStateStore.getSelectedPath(ctx.chatId(), ctx.userId());
        String header = currentCwd
            .map(p -> "Current: " + DirectoryListingHelper.folderName(p)
                + "\n\nChoose working directory:")
            .orElse("Choose working directory:");

        log.info("Navigation: sending inline keyboard chatId={} pathButtons={}", ctx.chatId(), resolved.size());
        telegramBotClient.sendWithInlineKeyboard(ctx.chatId(), header, kb.build());
    }

    private static String expandHome(String path) {
        if (path.startsWith("~/")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        if (Objects.equals(path, "~")) {
            return System.getProperty("user.home");
        }
        return path;
    }

    public record PathSnapshot(int index, String canonicalPath, String buttonLabel) {
    }
}
