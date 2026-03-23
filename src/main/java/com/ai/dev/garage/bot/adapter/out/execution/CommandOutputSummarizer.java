package com.ai.dev.garage.bot.adapter.out.execution;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CommandOutputSummarizer {

    private static final int TAIL_LINE_COUNT = 5;
    private static final int MAX_SUMMARY_CHARS = 500;

    public String summarize(String output) {
        String trimmed = output == null ? "" : output.trim();
        if (trimmed.isBlank()) {
            return "(no output)";
        }
        List<String> lines = trimmed.lines().toList();
        int from = Math.max(0, lines.size() - TAIL_LINE_COUNT);
        String summary = String.join("\n", lines.subList(from, lines.size()));
        return summary.length() > MAX_SUMMARY_CHARS ? summary.substring(0, MAX_SUMMARY_CHARS) + "..." : summary;
    }
}
