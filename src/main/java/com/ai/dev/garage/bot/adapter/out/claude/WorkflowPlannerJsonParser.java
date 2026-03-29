package com.ai.dev.garage.bot.adapter.out.claude;

import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.application.service.WorkflowGraphDeserializer;
import com.ai.dev.garage.bot.domain.WorkflowGraph;
import com.ai.dev.garage.bot.domain.WorkflowStep;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;

/**
 * Parses the planner CLI output (expected to be a JSON workflow graph) into a {@link WorkflowGraph}.
 * Handles common LLM quirks like markdown code fences around the JSON.
 *
 * <p>Step parsing is delegated to {@link WorkflowGraphDeserializer} to avoid duplication with the
 * service-layer graph reader.
 */
@UtilityClass
public class WorkflowPlannerJsonParser {

    private static final Pattern JSON_BLOCK = Pattern.compile(
        "```(?:json)?\\s*\\n?(\\{.*?})\\s*```", Pattern.DOTALL);

    @SuppressWarnings("unchecked") // JSON codec returns untyped maps; safe cast to List<Map>
    public static WorkflowGraph parse(String rawOutput, JsonCodec jsonCodec) {
        String json = extractJson(rawOutput);
        Map<String, Object> raw = jsonCodec.fromJson(json);

        int version = raw.containsKey("version") ? ((Number) raw.get("version")).intValue() : 1;

        var rawSteps = (List<Map<String, Object>>) raw.get("steps");
        if (rawSteps == null || rawSteps.isEmpty()) {
            throw new IllegalArgumentException("Planner returned no steps");
        }

        List<WorkflowStep> steps = rawSteps.stream()
            .map(WorkflowGraphDeserializer::parseStep)
            .toList();

        return new WorkflowGraph(version, steps);
    }

    /**
     * Extract JSON from the raw CLI output. If the model wrapped it in markdown fences,
     * strip them. Otherwise assume the entire output is JSON.
     */
    private static String extractJson(String raw) {
        String trimmed = raw.strip();
        Matcher m = JSON_BLOCK.matcher(trimmed);
        if (m.find()) {
            return m.group(1).strip();
        }
        // Try to find the first { and last } for resilience
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }
}
