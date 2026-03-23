package com.ai.dev.garage.bot.adapter.in.telegram;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks when a Telegram chat is expecting a free-text answer to a plan question
 * (i.e. a question with no detected button options).
 */
@Component
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
public class PlanConversationState {

    private final Map<String, PendingAnswer> pending = new ConcurrentHashMap<>();

    public void expectAnswer(Long chatId, Long userId, long sessionId, int round, int seq) {
        pending.put(key(chatId, userId), new PendingAnswer(sessionId, round, seq));
    }

    public Optional<PendingAnswer> consumePending(Long chatId, Long userId) {
        PendingAnswer answer = pending.remove(key(chatId, userId));
        return Optional.ofNullable(answer);
    }

    public boolean hasPending(Long chatId, Long userId) {
        return pending.containsKey(key(chatId, userId));
    }

    public void clear(Long chatId, Long userId) {
        pending.remove(key(chatId, userId));
    }

    private static String key(Long chatId, Long userId) {
        return chatId + ":" + userId;
    }

    public record PendingAnswer(long sessionId, int round, int seq) {
    }
}
