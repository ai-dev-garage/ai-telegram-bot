package com.ai.dev.garage.bot.adapter.in.telegram.command;

/**
 * Shared user-visible strings for Telegram job actions.
 */
final class JobTelegramMessages {

    private JobTelegramMessages() {
    }

    static String formatApproveRejectError(Exception e) {
        String m = e.getMessage();
        if (m != null && m.contains("not awaiting approval")) {
            return m + "\n\nAgent tasks are auto-approved when created — /approve only applies to jobs "
                + "waiting on human approval (e.g. higher-risk shell). "
                + "If an agent job stays queued, use \"Process pending agent task\" in Cursor/Claude or check "
                + "runner logs; /status <id> shows details.";
        }
        return m != null ? m : e.getClass().getSimpleName();
    }
}
