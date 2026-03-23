package com.ai.dev.garage.bot.application.support;

/**
 * Shared caps for text embedded in Telegram messages or job result payloads.
 */
public final class ContentLengthLimits {

    public static final int TELEGRAM_PLAIN_MESSAGE_SAFE = 3800;

    public static final int JOB_TEXT_SNIPPET_MAX = 4000;

    private ContentLengthLimits() {
    }
}
