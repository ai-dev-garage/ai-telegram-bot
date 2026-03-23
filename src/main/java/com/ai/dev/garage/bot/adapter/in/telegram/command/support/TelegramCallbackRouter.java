package com.ai.dev.garage.bot.adapter.in.telegram.command.support;

import com.ai.dev.garage.bot.adapter.in.telegram.NavigationStateStore;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.adapter.in.telegram.command.CdCommand;
import com.ai.dev.garage.bot.adapter.in.telegram.command.NavigationCommand;
import com.ai.dev.garage.bot.adapter.in.telegram.command.PlanCallbackHandler;
import com.ai.dev.garage.bot.adapter.in.telegram.command.TodoCommand;
import com.ai.dev.garage.bot.application.support.AllowedPathValidator;
import com.ai.dev.garage.bot.config.RunnerProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongPredicate;
import java.util.function.Predicate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Routes Telegram {@code callback_query} updates (navigation, todos, plans, directory pickers).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class TelegramCallbackRouter {

    private final TelegramBotClient telegramBotClient;
    private final NavigationStateStore navigationStateStore;
    private final RunnerProperties runnerProperties;
    private final AllowedPathValidator allowedPathValidator;
    private final CdCommand cdCommand;
    private final PlanCallbackHandler planCallbackHandler;
    private final TodoCommand todoCommand;

    private List<Map.Entry<Predicate<String>, RouteOp>> callbackRoutes;

    @FunctionalInterface
    private interface RouteOp {
        void run(String callbackQueryId, long chatId, long userId, String data);
    }

    private record ParsedCallback(String queryId, long chatId, long userId, String data) {
    }

    @PostConstruct
    void initCallbackRoutes() {
        callbackRoutes = List.of(
            Map.entry(NavigationCommand.CALLBACK_CLEAR::equals, (id, chatId, userId, d) -> onClearCallback(id, chatId, userId)),
            Map.entry(d -> d.startsWith(DirectoryListingHelper.CD_CALLBACK_PREFIX), this::handleCdCallback),
            Map.entry(
                d -> d.startsWith(PlanCallbackHandler.CALLBACK_PREFIX),
                planCallbackHandler::handleCallback),
            Map.entry(d -> d.startsWith(TodoCommand.CALLBACK_PREFIX), this::handleTodoCallback),
            Map.entry(d -> d.startsWith(NavigationCommand.CALLBACK_PREFIX), this::handleNavCallback)
        );
    }

    // Telegram JSON maps: unchecked cast from Map<String,Object> to nested maps (Telegram API shape).
    @SuppressWarnings("unchecked")
    public void handleCallback(Map<String, Object> callback, LongPredicate isAllowed) {
        Optional<ParsedCallback> parsed = parseCallback(callback);
        if (parsed.isEmpty()) {
            return;
        }
        ParsedCallback p = parsed.get();
        if (!isAllowed.test(p.userId())) {
            telegramBotClient.answerCallbackQuery(p.queryId(), "Not authorized.");
            return;
        }
        log.debug("Telegram callback chatId={} data={}", p.chatId(), p.data());

        for (Map.Entry<Predicate<String>, RouteOp> e : callbackRoutes) {
            if (e.getKey().test(p.data())) {
                e.getValue().run(p.queryId(), p.chatId(), p.userId(), p.data());
                return;
            }
        }
    }

    // Telegram callback_query JSON: nested maps and numeric ids are untyped in the client payload.
    @SuppressWarnings("unchecked")
    private static Optional<ParsedCallback> parseCallback(Map<String, Object> callback) {
        String callbackQueryId = String.valueOf(callback.getOrDefault("id", ""));
        String data = callback.get("data") == null ? "" : String.valueOf(callback.get("data"));
        var from = (Map<String, Object>) callback.get("from");
        var message = (Map<String, Object>) callback.get("message");
        if (from == null || message == null) {
            return Optional.empty();
        }
        var chat = (Map<String, Object>) message.get("chat");
        if (chat == null) {
            return Optional.empty();
        }
        long userId = ((Number) from.get("id")).longValue();
        long chatId = ((Number) chat.get("id")).longValue();
        return Optional.of(new ParsedCallback(callbackQueryId, chatId, userId, data));
    }

    private void onClearCallback(String callbackQueryId, long chatId, long userId) {
        navigationStateStore.clear(chatId, userId);
        telegramBotClient.answerCallbackQuery(callbackQueryId, "Cleared.");
        telegramBotClient.sendPlain(chatId,
            "Working directory cleared. /agent and /run will fall back to config until you pick a folder again.");
    }

    private void handleTodoCallback(String callbackQueryId, long chatId, long userId, String data) {
        String action = data.substring(TodoCommand.CALLBACK_PREFIX.length());
        telegramBotClient.answerCallbackQuery(callbackQueryId, null);
        if (action.startsWith("work:")) {
            String[] parts = action.substring("work:".length()).split(":", 2);
            if (parts.length == 2) {
                String mode = parts[0];
                long todoId = Long.parseLong(parts[1]);
                todoCommand.handleWorkCallback(chatId, userId, todoId, mode);
            }
        } else if (action.startsWith("done:")) {
            long todoId = Long.parseLong(action.substring("done:".length()));
            todoCommand.handleDoneCallback(chatId, todoId);
        } else if (action.startsWith("cancel:")) {
            long todoId = Long.parseLong(action.substring("cancel:".length()));
            todoCommand.handleCancelCallback(chatId, todoId);
        }
    }

    private void handleCdCallback(String callbackQueryId, long chatId, long userId, String data) {
        String arg = data.substring(DirectoryListingHelper.CD_CALLBACK_PREFIX.length());
        var cwd = navigationStateStore.getSelectedPath(chatId, userId);
        if (cwd.isEmpty()) {
            telegramBotClient.answerCallbackQuery(callbackQueryId, "No directory selected.");
            return;
        }
        String result = cdCommand.changeDirectory(cwd.get(), arg);
        if (result == null) {
            String errorMsg = Objects.equals(arg, "..") ? "Already at top of allowed directory." : "Cannot enter: " + arg;
            telegramBotClient.answerCallbackQuery(callbackQueryId, errorMsg);
            return;
        }
        navigationStateStore.setSelectedPath(chatId, userId, result);
        telegramBotClient.answerCallbackQuery(callbackQueryId, DirectoryListingHelper.folderName(result));
        sendDirectoryListing(chatId, result);
    }

    private void handleNavCallback(String callbackQueryId, long chatId, long userId, String data) {
        String idxPart = data.substring(NavigationCommand.CALLBACK_PREFIX.length());
        int idx;
        try {
            idx = Integer.parseInt(idxPart.trim());
        } catch (NumberFormatException e) {
            telegramBotClient.answerCallbackQuery(callbackQueryId, "Invalid selection.");
            return;
        }
        List<String> raw = runnerProperties.listAllowedNavigationPaths();
        if (idx < 0 || idx >= raw.size()) {
            telegramBotClient.answerCallbackQuery(callbackQueryId, "Invalid selection.");
            return;
        }
        String expanded = expandHomeRaw(raw.get(idx));
        try {
            String canonical = Path.of(expanded).toRealPath().toString();
            if (!allowedPathValidator.isAllowedCwd(canonical)) {
                telegramBotClient.answerCallbackQuery(callbackQueryId, "Path not allowed.");
                return;
            }
            navigationStateStore.setSelectedPath(chatId, userId, canonical);
            telegramBotClient.answerCallbackQuery(callbackQueryId, DirectoryListingHelper.folderName(canonical));
            sendDirectoryListing(chatId, canonical);
        } catch (Exception e) {
            telegramBotClient.answerCallbackQuery(callbackQueryId, "Path not available.");
        }
    }

    private void sendDirectoryListing(long chatId, String canonicalPath) {
        List<List<Map<String, String>>> keyboard = DirectoryListingHelper.buildDirectoryKeyboard(
            canonicalPath, allowedPathValidator);
        String message = DirectoryListingHelper.formatCwdMessage(canonicalPath);
        if (keyboard.isEmpty()) {
            telegramBotClient.sendPlain(chatId, message + " (none)");
        } else {
            telegramBotClient.sendWithInlineKeyboard(chatId, message, keyboard);
        }
    }

    private static String expandHomeRaw(String path) {
        if (path == null) {
            return "";
        }
        if (path.startsWith("~/")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        if (Objects.equals(path, "~")) {
            return System.getProperty("user.home");
        }
        return path;
    }
}
