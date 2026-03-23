package com.ai.dev.garage.bot.application.service;

import com.ai.dev.garage.bot.application.port.out.PlanSessionResult.ParsedQuestion;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heuristic parser that extracts questions and their options from agent text output.
 *
 * <p>A question is any non-empty line ending with {@code ?}.
 * Lines immediately following a question that start with {@code - }, {@code * },
 * or a number/letter prefix ({@code 1. }, {@code a) }) are treated as answer options.
 */
@Component
public class AgentQuestionParser {

    private static final Pattern OPTION_LINE = Pattern.compile(
        "^\\s*(?:[-*]|\\d+[.):]|[a-zA-Z][).:])" +
            "\\s+(.+)$"
    );

    public List<ParsedQuestion> parse(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<ParsedQuestion> questions = new ArrayList<>();
        List<String> lines = text.lines().toList();

        Optional<String> currentQuestion = Optional.empty();
        List<String> currentOptions = new ArrayList<>();

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                if (currentQuestion.isPresent()) {
                    questions.add(new ParsedQuestion(currentQuestion.get(), List.copyOf(currentOptions)));
                    currentQuestion = Optional.empty();
                    currentOptions.clear();
                }
                continue;
            }

            if (currentQuestion.isPresent()) {
                Matcher m = OPTION_LINE.matcher(raw);
                if (m.matches()) {
                    currentOptions.add(m.group(1).trim());
                    continue;
                }
                questions.add(new ParsedQuestion(currentQuestion.get(), List.copyOf(currentOptions)));
                currentQuestion = Optional.empty();
                currentOptions.clear();
            }

            if (line.endsWith("?")) {
                currentQuestion = Optional.of(line);
                currentOptions = new ArrayList<>();
            }
        }

        if (currentQuestion.isPresent()) {
            questions.add(new ParsedQuestion(currentQuestion.get(), List.copyOf(currentOptions)));
        }

        return questions;
    }
}
