package com.ai.dev.garage.bot.adapter.out.cursor.support;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable accumulator while scanning a CLI NDJSON stream.
 */
public final class CliStreamParseState {

    private String sessionId;
    private final List<String> assistantMessages = new ArrayList<>();
    private final StringBuilder fullText = new StringBuilder();
    private final StringBuilder streamDeltaBuffer = new StringBuilder();
    private boolean success;
    private long durationMs;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<String> getAssistantMessages() {
        return assistantMessages;
    }

    public StringBuilder getFullText() {
        return fullText;
    }

    public StringBuilder getStreamDeltaBuffer() {
        return streamDeltaBuffer;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
}
