package com.ai.dev.garage.bot.adapter.in.telegram;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Per Telegram chat + user, the last allowlisted working directory chosen via /navigation.
 */
@Component
public class NavigationStateStore {

    private final Map<String, String> chatUserToPath = new ConcurrentHashMap<>();

    public void setSelectedPath(long chatId, long userId, String normalizedAbsolutePath) {
        chatUserToPath.put(key(chatId, userId), normalizedAbsolutePath);
    }

    public Optional<String> getSelectedPath(long chatId, long userId) {
        return Optional.ofNullable(chatUserToPath.get(key(chatId, userId)));
    }

    public void clear(long chatId, long userId) {
        chatUserToPath.remove(key(chatId, userId));
    }

    private static String key(long chatId, long userId) {
        return chatId + ":" + userId;
    }
}
