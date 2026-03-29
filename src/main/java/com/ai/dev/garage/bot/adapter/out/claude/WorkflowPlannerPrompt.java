package com.ai.dev.garage.bot.adapter.out.claude;

/**
 * Builds the structured prompt sent to the planner CLI to decompose a user intent
 * into a JSON workflow graph.
 */
public final class WorkflowPlannerPrompt {

    private static final String TEMPLATE = """
        You are a workflow planner. Decompose the following user intent into an ordered \
        list of executable steps. Each step is either a shell command (taskType: SHELL_COMMAND) \
        or an agent task (taskType: AGENT_TASK).

        Rules:
        - Maximum MAX_STEPS steps.
        - Mark destructive or irreversible steps as "critical": true (they require user approval).
        - Analysis, read-only, and test steps should be "critical": false.
        - Each step needs a short unique id (lowercase, alphanumeric + hyphens).
        - The "intent" field for SHELL_COMMAND is the exact command to run.
        - The "intent" field for AGENT_TASK is a detailed instruction for the AI agent.
        - Steps execute sequentially in the order listed.

        Respond with ONLY valid JSON (no markdown fences, no commentary) in this exact format:
        {
          "version": 1,
          "steps": [
            {
              "id": "step-id",
              "label": "Human-readable label",
              "taskType": "AGENT_TASK" or "SHELL_COMMAND",
              "intent": "Detailed instruction or command",
              "critical": false,
              "dependsOn": []
            }
          ]
        }

        User intent: USER_INTENT
        """;

    private WorkflowPlannerPrompt() {
    }

    public static String build(String intent, int maxSteps) {
        return TEMPLATE
            .replace("MAX_STEPS", String.valueOf(maxSteps))
            .replace("USER_INTENT", intent);
    }
}
