package com.ai.dev.garage.bot.adapter.in.telegram.command;

public record TelegramCommandContext(Long chatId, Long userId, String username, String text) {
}
