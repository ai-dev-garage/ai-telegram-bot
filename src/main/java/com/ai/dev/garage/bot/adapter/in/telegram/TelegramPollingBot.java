package com.ai.dev.garage.bot.adapter.in.telegram;

import com.ai.dev.garage.bot.adapter.in.telegram.command.CdCommand;
import com.ai.dev.garage.bot.adapter.in.telegram.command.DirectoryListingHelper;
import com.ai.dev.garage.bot.adapter.in.telegram.command.NavigationCommand;
import com.ai.dev.garage.bot.adapter.in.telegram.command.PlanCallbackHandler;
import com.ai.dev.garage.bot.adapter.in.telegram.command.TelegramCommandContext;
import com.ai.dev.garage.bot.adapter.in.telegram.command.TelegramCommandDispatcher;
import com.ai.dev.garage.bot.adapter.in.telegram.command.TelegramCommandRegistry;
import com.ai.dev.garage.bot.adapter.in.telegram.command.TodoCommand;
import com.ai.dev.garage.bot.application.support.AllowedPathValidator;
import com.ai.dev.garage.bot.config.RunnerProperties;
import com.ai.dev.garage.bot.config.TelegramProperties;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter: long-polling loop. Delegates command handling to {@link TelegramCommandDispatcher} (Command pattern).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class TelegramPollingBot {

    private final TelegramProperties telegramProperties;
    private final TelegramBotClient telegramBotClient;
    private final TelegramCommandDispatcher commandDispatcher;
    private final NavigationStateStore navigationStateStore;
    private final RunnerProperties runnerProperties;
    private final AllowedPathValidator allowedPathValidator;
    private final CdCommand cdCommand;
    private final PlanCallbackHandler planCallbackHandler;
    private final PlanConversationState planConversationState;
    private final TodoCommand todoCommand;
    private final TelegramCommandRegistry commandRegistry;
    private Set<Long> allowedUsers;
    private long offset;

    @PostConstruct
    void validate() {
        if (telegramProperties.getToken() == null || telegramProperties.getToken().isBlank()) {
            throw new IllegalStateException("TELEGRAM_BOT_TOKEN must be configured when telegram is enabled");
        }
        allowedUsers = telegramProperties.parsedAllowedUserIds();
        telegramBotClient.setMyCommands(commandRegistry.botApiCommands());
    }

    @Scheduled(fixedDelayString = "${app.telegram.polling-interval-ms:2000}")
    @SuppressWarnings("unchecked")
    public void poll() {
        Map<String, Object> response = telegramBotClient.getUpdates(offset, 20);
        List<Map<String, Object>> updates = TelegramBotClient.extractUpdates(response);
        for (Map<String, Object> update : updates) {
            Number updateId = (Number) update.get("update_id");
            if (updateId != null) {
                offset = updateId.longValue() + 1;
            }
            handleUpdate(update);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleUpdate(Map<String, Object> update) {
        Map<String, Object> callback = (Map<String, Object>) update.get("callback_query");
        if (callback != null) {
            handleCallback(callback);
            return;
        }

        Map<String, Object> message = (Map<String, Object>) update.get("message");
        if (message == null) {
            return;
        }
        String text = String.valueOf(message.getOrDefault("text", "")).trim();
        Map<String, Object> from = (Map<String, Object>) message.get("from");
        Map<String, Object> chat = (Map<String, Object>) message.get("chat");
        if (from == null || chat == null) {
            return;
        }
        Long userId = ((Number) from.get("id")).longValue();
        Long chatId = ((Number) chat.get("id")).longValue();
        String username = from.get("username") == null ? null : String.valueOf(from.get("username"));

        if (!isAllowed(userId)) {
            telegramBotClient.sendPlain(chatId, "Not authorized.");
            return;
        }
        try {
            log.debug("Telegram message chatId={} userId={} text={}", chatId, userId, abbreviateForLog(text));

            if (!text.startsWith("/") && planConversationState.hasPending(chatId, userId)) {
                planCallbackHandler.handleFreeTextAnswer(chatId, userId, text);
                return;
            }

            TelegramCommandContext ctx = new TelegramCommandContext(chatId, userId, username, text);
            boolean handled = commandDispatcher.dispatch(ctx);
            if (handled) {
                log.debug("Telegram command handled chatId={}", chatId);
            } else if (!text.isBlank()) {
                log.debug("Telegram no command matched chatId={} text={}", chatId, abbreviateForLog(text));
                telegramBotClient.sendPlain(chatId, commandRegistry.unknownInputText());
            }
        } catch (Exception e) {
            log.warn("Telegram command error chatId={}: {}", chatId, e.getMessage());
            String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            telegramBotClient.sendPlain(chatId, "Error: " + detail);
        }
    }

    private static String abbreviateForLog(String text) {
        if (text == null) {
            return "";
        }
        String t = text.replace('\n', ' ');
        return t.length() <= 120 ? t : t.substring(0, 117) + "…";
    }

    @SuppressWarnings("unchecked")
    private void handleCallback(Map<String, Object> callback) {
        String callbackQueryId = String.valueOf(callback.getOrDefault("id", ""));
        String data = callback.get("data") == null ? "" : String.valueOf(callback.get("data"));
        Map<String, Object> from = (Map<String, Object>) callback.get("from");
        Map<String, Object> message = (Map<String, Object>) callback.get("message");
        if (from == null || message == null) {
            return;
        }
        Map<String, Object> chat = (Map<String, Object>) message.get("chat");
        if (chat == null) {
            return;
        }
        Long userId = ((Number) from.get("id")).longValue();
        Long chatId = ((Number) chat.get("id")).longValue();
        if (!isAllowed(userId)) {
            telegramBotClient.answerCallbackQuery(callbackQueryId, "Not authorized.");
            return;
        }
        log.debug("Telegram callback chatId={} data={}", chatId, data);

        if (NavigationCommand.CALLBACK_CLEAR.equals(data)) {
            navigationStateStore.clear(chatId, userId);
            telegramBotClient.answerCallbackQuery(callbackQueryId, "Cleared.");
            telegramBotClient.sendPlain(chatId,
                "Working directory cleared. /agent and /run will fall back to config until you pick a folder again.");
            return;
        }

        if (data.startsWith(DirectoryListingHelper.CD_CALLBACK_PREFIX)) {
            handleCdCallback(callbackQueryId, chatId, userId, data);
            return;
        }

        if (data.startsWith(PlanCallbackHandler.CALLBACK_PREFIX)) {
            planCallbackHandler.handleCallback(callbackQueryId, chatId, userId, data);
            return;
        }

        if (data.startsWith(TodoCommand.CALLBACK_PREFIX)) {
            handleTodoCallback(callbackQueryId, chatId, userId, data);
            return;
        }

        if (data.startsWith(NavigationCommand.CALLBACK_PREFIX)) {
            handleNavCallback(callbackQueryId, chatId, userId, data);
        }
    }

    private void handleTodoCallback(String callbackQueryId, Long chatId, Long userId, String data) {
        String action = data.substring(TodoCommand.CALLBACK_PREFIX.length());
        telegramBotClient.answerCallbackQuery(callbackQueryId, null);
        if (action.startsWith("work:")) {
            String[] parts = action.substring(5).split(":", 2);
            if (parts.length == 2) {
                String mode = parts[0];
                long todoId = Long.parseLong(parts[1]);
                todoCommand.handleWorkCallback(chatId, userId, todoId, mode);
            }
        } else if (action.startsWith("done:")) {
            long todoId = Long.parseLong(action.substring(5));
            todoCommand.handleDoneCallback(chatId, todoId);
        } else if (action.startsWith("cancel:")) {
            long todoId = Long.parseLong(action.substring(7));
            todoCommand.handleCancelCallback(chatId, todoId);
        }
    }

    private void handleCdCallback(String callbackQueryId, Long chatId, Long userId, String data) {
        String arg = data.substring(DirectoryListingHelper.CD_CALLBACK_PREFIX.length());
        var cwd = navigationStateStore.getSelectedPath(chatId, userId);
        if (cwd.isEmpty()) {
            telegramBotClient.answerCallbackQuery(callbackQueryId, "No directory selected.");
            return;
        }
        String result = cdCommand.changeDirectory(cwd.get(), arg);
        if (result == null) {
            String errorMsg = "..".equals(arg) ? "Already at top of allowed directory." : "Cannot enter: " + arg;
            telegramBotClient.answerCallbackQuery(callbackQueryId, errorMsg);
            return;
        }
        navigationStateStore.setSelectedPath(chatId, userId, result);
        telegramBotClient.answerCallbackQuery(callbackQueryId, DirectoryListingHelper.folderName(result));
        sendDirectoryListing(chatId, result);
    }

    private void handleNavCallback(String callbackQueryId, Long chatId, Long userId, String data) {
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
            String canonical = java.nio.file.Path.of(expanded).toRealPath().toString();
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

    private void sendDirectoryListing(Long chatId, String canonicalPath) {
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
        if ("~".equals(path)) {
            return System.getProperty("user.home");
        }
        return path;
    }

    private boolean isAllowed(Long userId) {
        return allowedUsers.isEmpty() || allowedUsers.contains(userId);
    }
}
