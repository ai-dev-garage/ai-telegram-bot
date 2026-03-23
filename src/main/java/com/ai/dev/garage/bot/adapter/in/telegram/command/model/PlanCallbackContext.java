package com.ai.dev.garage.bot.adapter.in.telegram.command.model;

/**
 * Telegram callback payload for plan:* actions (after {@code answerCallbackQuery}).
 */
public record PlanCallbackContext(Long chatId, Long userId, String data) {
}
