package com.ai.dev.garage.bot.adapter.in.telegram;

import com.ai.dev.garage.bot.adapter.in.telegram.command.TelegramCommandRegistry;
import com.ai.dev.garage.bot.adapter.in.telegram.command.support.TelegramCallbackRouter;
import com.ai.dev.garage.bot.adapter.in.telegram.command.support.TelegramMessageHandler;
import com.ai.dev.garage.bot.config.TelegramProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Inbound adapter: long-polling loop. Delegates command handling to {@link TelegramMessageHandler}
 * and callbacks to {@link TelegramCallbackRouter}.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class TelegramPollingBot {

    private static final int GET_UPDATES_TIMEOUT_SECONDS = 20;

    private final TelegramProperties telegramProperties;
    private final TelegramBotClient telegramBotClient;
    private final TelegramCommandRegistry commandRegistry;
    private final TelegramMessageHandler messageHandler;
    private final TelegramCallbackRouter callbackRouter;
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
    public void poll() {
        var response = telegramBotClient.getUpdates(offset, GET_UPDATES_TIMEOUT_SECONDS);
        var updates = TelegramBotClient.extractUpdates(response);
        for (var update : updates) {
            var updateId = (Number) update.get("update_id");
            if (updateId != null) {
                offset = updateId.longValue() + 1;
            }
            handleUpdate(update);
        }
    }

    @SuppressWarnings("unchecked") // Telegram update JSON: nested maps from heterogeneous keys
    private void handleUpdate(Map<String, Object> update) {
        var callback = (Map<String, Object>) update.get("callback_query");
        if (callback != null) {
            callbackRouter.handleCallback(callback, this::isAllowed);
            return;
        }

        var message = (Map<String, Object>) update.get("message");
        if (message == null) {
            return;
        }
        String text = String.valueOf(message.getOrDefault("text", "")).trim();
        var from = (Map<String, Object>) message.get("from");
        var chat = (Map<String, Object>) message.get("chat");
        if (from == null || chat == null) {
            return;
        }
        Long userId = ((Number) from.get("id")).longValue();
        Long chatId = ((Number) chat.get("id")).longValue();
        String username = from.get("username") == null ? null : String.valueOf(from.get("username"));

        messageHandler.handleTextMessage(chatId, userId, username, text, this::isAllowed);
    }

    private boolean isAllowed(Long userId) {
        return allowedUsers.isEmpty() || allowedUsers.contains(userId);
    }
}
