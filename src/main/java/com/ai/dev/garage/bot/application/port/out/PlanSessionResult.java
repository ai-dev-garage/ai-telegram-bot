package com.ai.dev.garage.bot.application.port.out;

import java.util.List;

/**
 * Structured result from a plan CLI session (start or resume).
 */
public record PlanSessionResult(
    String sessionId,
    List<ParsedMessage> messages,
    boolean completed,
    String fullText
) {

    public record ParsedMessage(String text, List<ParsedQuestion> questions) {
    }

    public record ParsedQuestion(String questionText, List<String> options) {
    }
}
