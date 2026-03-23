package com.ai.dev.garage.bot.adapter.in.telegram.command.support;

import com.ai.dev.garage.bot.adapter.in.telegram.PlanConversationState;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.adapter.in.telegram.command.PlanCallbackHandler;
import com.ai.dev.garage.bot.adapter.in.telegram.command.TelegramCommandContext;
import com.ai.dev.garage.bot.adapter.in.telegram.command.TelegramCommandDispatcher;
import com.ai.dev.garage.bot.adapter.in.telegram.command.TelegramCommandRegistry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.LongPredicate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles Telegram {@code message} updates (text commands and plan free-text answers).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class TelegramMessageHandler {

    private static final int LOG_DEBUG_TEXT_MAX_CHARS = 120;
    private static final int LOG_DEBUG_TEXT_PREFIX_CHARS = 117;

    private final TelegramBotClient telegramBotClient;
    private final TelegramCommandDispatcher commandDispatcher;
    private final TelegramCommandRegistry commandRegistry;
    private final PlanConversationState planConversationState;
    private final PlanCallbackHandler planCallbackHandler;

    public void handleTextMessage(
        Long chatId,
        Long userId,
        String username,
        String text,
        LongPredicate isAllowed) {

        if (!isAllowed.test(userId)) {
            telegramBotClient.sendPlain(chatId, "Not authorized.");
            return;
        }
        try {
            log.debug("Telegram message chatId={} userId={} text={}", chatId, userId, abbreviateForLog(text));

            if (!text.startsWith("/") && planConversationState.hasPending(chatId, userId)) {
                planCallbackHandler.handleFreeTextAnswer(chatId, userId, text);
                return;
            }

            var ctx = new TelegramCommandContext(chatId, userId, username, text);
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
        return t.length() <= LOG_DEBUG_TEXT_MAX_CHARS ? t : t.substring(0, LOG_DEBUG_TEXT_PREFIX_CHARS) + "…";
    }
}
