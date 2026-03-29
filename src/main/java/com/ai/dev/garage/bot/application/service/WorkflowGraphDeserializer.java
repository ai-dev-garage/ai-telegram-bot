package com.ai.dev.garage.bot.application.service;

import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.domain.TaskType;
import com.ai.dev.garage.bot.domain.WorkflowGraph;
import com.ai.dev.garage.bot.domain.WorkflowStep;

import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;

/**
 * Shared utility for deserializing a JSON workflow graph map into a {@link WorkflowGraph}.
 *
 * <p>Used by both:
 * <ul>
 *   <li>{@code WorkflowOrchestrationService} — reads the graph stored in the job's task payload</li>
 *   <li>{@code WorkflowPlannerJsonParser} (adapter) — reads the graph produced by a planner CLI</li>
 * </ul>
 *
 * <p>This class is intentionally in the application layer so that adapter-layer parsers can import
 * it without violating the hexagonal architecture (adapters may depend on application layer).
 */
@UtilityClass
public class WorkflowGraphDeserializer {

    /**
     * Deserialize a JSON string into a {@link WorkflowGraph}.
     *
     * @param json     the JSON string representing a workflow graph object
     * @param jsonCodec codec used to parse the JSON string into a raw map
     * @return the deserialized graph, or {@code null} if {@code steps} is absent or empty
     */
    @SuppressWarnings("unchecked") // JSON codec returns untyped maps; safe cast to List<Map>
    public static WorkflowGraph fromJson(String json, JsonCodec jsonCodec) {
        Map<String, Object> raw = jsonCodec.fromJson(json);
        int version = raw.containsKey("version") ? ((Number) raw.get("version")).intValue() : 1;
        var rawSteps = (List<Map<String, Object>>) raw.get("steps");
        if (rawSteps == null || rawSteps.isEmpty()) {
            return null;
        }
        List<WorkflowStep> steps = rawSteps.stream()
            .map(WorkflowGraphDeserializer::parseStep)
            .toList();
        return new WorkflowGraph(version, steps);
    }

    /**
     * Parse a single step map into a {@link WorkflowStep}.
     */
    @SuppressWarnings("unchecked") // JSON codec returns untyped maps; safe cast to List<String>
    public static WorkflowStep parseStep(Map<String, Object> raw) {
        var id = (String) raw.get("id");
        var label = (String) raw.get("label");
        var taskTypeStr = (String) raw.get("taskType");
        var intent = (String) raw.get("intent");
        boolean critical = raw.get("critical") instanceof Boolean b && b;
        List<String> dependsOn = raw.containsKey("dependsOn")
            ? (List<String>) raw.get("dependsOn")
            : List.of();
        return new WorkflowStep(id, label, TaskType.valueOf(taskTypeStr),
            intent, critical, dependsOn);
    }
}
