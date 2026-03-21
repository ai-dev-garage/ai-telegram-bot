package com.ai.dev.garage.bot.adapter.out.execution;

import org.springframework.stereotype.Component;

@Component
public class CommandOutputSummarizer {

    public String summarize(String output) {
        String trimmed = output == null ? "" : output.trim();
        if (trimmed.isBlank()) {
            return "(no output)";
        }
        String[] lines = trimmed.split("\n");
        int from = Math.max(0, lines.length - 5);
        String summary = String.join("\n", java.util.Arrays.copyOfRange(lines, from, lines.length));
        return summary.length() > 500 ? summary.substring(0, 500) + "..." : summary;
    }
}
