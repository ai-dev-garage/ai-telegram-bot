package com.ai.dev.garage.bot.adapter.out.cli.support;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable accumulator while scanning a CLI NDJSON stream.
 */
public final class CliStreamParseState {

    private static final int UNPARSEABLE_PREVIEW_MAX_CHARS = 800;

    private String sessionId;
    private final List<String> assistantMessages = new ArrayList<>();
    private final StringBuilder fullText = new StringBuilder();
    private final StringBuilder streamDeltaBuffer = new StringBuilder();
    private final StringBuilder unparseablePreview = new StringBuilder();
    private boolean resultLineSeen;
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

    public boolean isResultLineSeen() {
        return resultLineSeen;
    }

    public void setResultLineSeen(boolean resultLineSeen) {
        this.resultLineSeen = resultLineSeen;
    }

    public String getUnparseablePreview() {
        return unparseablePreview.toString();
    }

    /**
     * Append a non-NDJSON line (e.g. shell error text) for operator diagnostics, capped in size.
     */
    public void appendUnparseableLine(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        if (unparseablePreview.length() >= UNPARSEABLE_PREVIEW_MAX_CHARS) {
            return;
        }
        int remaining = UNPARSEABLE_PREVIEW_MAX_CHARS - unparseablePreview.length();
        String chunk = line.length() > remaining ? line.substring(0, remaining) + "…" : line;
        if (!unparseablePreview.isEmpty()) {
            unparseablePreview.append('\n');
        }
        unparseablePreview.append(chunk);
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
}
