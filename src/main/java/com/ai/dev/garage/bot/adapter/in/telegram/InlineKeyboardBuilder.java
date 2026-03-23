package com.ai.dev.garage.bot.adapter.in.telegram;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluent builder for Telegram inline keyboard markup.
 * Produces the {@code List<List<Map<String, String>>>} structure expected by
 * {@link TelegramBotClient#sendWithInlineKeyboard}.
 */
public final class InlineKeyboardBuilder {

    private final List<List<Map<String, String>>> rows = new ArrayList<>();

    private InlineKeyboardBuilder() {
    }

    @CanIgnoreReturnValue
    public static InlineKeyboardBuilder create() {
        return new InlineKeyboardBuilder();
    }

    /**
     * Add a single-button row.
     */
    @CanIgnoreReturnValue
    public InlineKeyboardBuilder row(String label, String callbackData) {
        Map<String, String> btn = new LinkedHashMap<>();
        btn.put("text", label);
        btn.put("callback_data", callbackData);
        rows.add(List.of(btn));
        return this;
    }

    /**
     * Add a row with multiple buttons side-by-side.
     */
    @CanIgnoreReturnValue
    public InlineKeyboardBuilder row(List<Button> buttons) {
        List<Map<String, String>> row = new ArrayList<>();
        for (Button b : buttons) {
            Map<String, String> btn = new LinkedHashMap<>();
            btn.put("text", b.label());
            btn.put("callback_data", b.callbackData());
            row.add(btn);
        }
        rows.add(row);
        return this;
    }

    public List<List<Map<String, String>>> build() {
        return List.copyOf(rows);
    }

    public record Button(String label, String callbackData) {
    }
}
