package com.ai.dev.garage.bot.application.port.out;

import java.util.List;

/**
 * Structured result from a plan CLI session (start or resume).
 *
 * @param cliHealthy when {@code false}, {@link #failureDetail()} explains why; persistence must not treat the round as success.
 */
public record PlanSessionResult(
    String sessionId,
    List<ParsedMessage> messages,
    boolean completed,
    String fullText,
    boolean cliHealthy,
    String failureDetail
) {

    /** Backward-compatible constructor: assume CLI succeeded (legacy mocks and happy-path builders). */
    public PlanSessionResult(String sessionId, List<ParsedMessage> messages, boolean completed, String fullText) {
        this(sessionId, messages, completed, fullText, true, null);
    }

    public record ParsedMessage(String text, List<ParsedQuestion> questions) {
    }

    public record ParsedQuestion(String questionText, List<String> options) {
    }
}
