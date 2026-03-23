package com.ai.dev.garage.bot.adapter.in.telegram.command.support;

import com.ai.dev.garage.bot.adapter.in.telegram.InlineKeyboardBuilder;
import com.ai.dev.garage.bot.domain.PlanSession;

import java.util.List;
import java.util.Locale;

/**
 * Inline keyboards and short text helpers for plan Telegram flows.
 */
public final class PlanCallbackKeyboards {

    private static final String ELLIPSIS = "...";

    private PlanCallbackKeyboards() {
    }

    public static String truncateForTelegram(String s, int max) {
        if (s == null) {
            return "(empty plan)";
        }
        return s.length() <= max ? s : s.substring(0, max - ELLIPSIS.length()) + ELLIPSIS;
    }

    public static InlineKeyboardBuilder planDetailKeyboard(long jobId, long sessionId, boolean paused, String prefix) {
        var kb = InlineKeyboardBuilder.create();
        if (paused) {
            kb.row(List.of(
                    new InlineKeyboardBuilder.Button("Build", prefix + "approve:" + jobId),
                    new InlineKeyboardBuilder.Button("Resume", prefix + "unpause:" + jobId)
                ))
                .row(List.of(
                    new InlineKeyboardBuilder.Button("Adjust", prefix + "adjust:" + jobId),
                    new InlineKeyboardBuilder.Button("Cancel", prefix + "reject:" + jobId)
                ));
        } else {
            kb.row(List.of(
                    new InlineKeyboardBuilder.Button("Build", prefix + "approve:" + jobId),
                    new InlineKeyboardBuilder.Button("Adjust", prefix + "adjust:" + jobId)
                ))
                .row(List.of(
                    new InlineKeyboardBuilder.Button("Pause", prefix + "pause:" + jobId),
                    new InlineKeyboardBuilder.Button("Cancel", prefix + "reject:" + jobId)
                ));
        }
        return kb.row(List.of(
            new InlineKeyboardBuilder.Button("Q&A History", prefix + "qa:" + sessionId),
            new InlineKeyboardBuilder.Button("All Plans", "plan:list")
        ));
    }

    public static String formatState(PlanSession s) {
        return switch (s.getState()) {
            case PLANNING -> "analyzing...";
            case AWAITING_INPUT -> "awaiting your input";
            case PLAN_READY -> "plan ready";
            case PAUSED -> "paused";
            default -> s.getState().name().toLowerCase(Locale.ROOT);
        };
    }
}
