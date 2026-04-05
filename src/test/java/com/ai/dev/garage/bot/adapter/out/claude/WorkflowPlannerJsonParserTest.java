package com.ai.dev.garage.bot.adapter.out.claude;

import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.application.service.support.JsonService;
import com.ai.dev.garage.bot.domain.TaskType;
import com.ai.dev.garage.bot.domain.WorkflowGraph;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowPlannerJsonParserTest {

    private JsonCodec jsonCodec;

    @BeforeEach
    void setUp() {
        jsonCodec = new JsonService(new ObjectMapper());
    }

    @Test
    void shouldParseCleanJsonIntoWorkflowGraph() {
        String json = """
            {
              "version": 1,
              "steps": [
                {
                  "id": "step-1",
                  "label": "Run tests",
                  "taskType": "SHELL_COMMAND",
                  "intent": "mvn test",
                  "critical": false,
                  "dependsOn": []
                }
              ]
            }
            """;

        WorkflowGraph graph = WorkflowPlannerJsonParser.parse(json, jsonCodec);

        assertThat(graph.version()).isEqualTo(1);
        assertThat(graph.steps()).hasSize(1);
        assertThat(graph.steps().get(0).id()).isEqualTo("step-1");
        assertThat(graph.steps().get(0).taskType()).isEqualTo(TaskType.SHELL_COMMAND);
        assertThat(graph.steps().get(0).intent()).isEqualTo("mvn test");
        assertThat(graph.steps().get(0).critical()).isFalse();
    }

    @Test
    void shouldParseJsonWrappedInMarkdownCodeFences() {
        String wrappedJson = """
            Here is the plan:
            ```json
            {
              "version": 1,
              "steps": [
                {
                  "id": "analyze",
                  "label": "Analyze codebase",
                  "taskType": "AGENT_TASK",
                  "intent": "review the project structure",
                  "critical": false,
                  "dependsOn": []
                }
              ]
            }
            ```
            """;

        WorkflowGraph graph = WorkflowPlannerJsonParser.parse(wrappedJson, jsonCodec);

        assertThat(graph.steps()).hasSize(1);
        assertThat(graph.steps().get(0).id()).isEqualTo("analyze");
        assertThat(graph.steps().get(0).taskType()).isEqualTo(TaskType.AGENT_TASK);
    }

    @Test
    void shouldParseJsonWithLeadingAndTrailingText() {
        String rawOutput = "Sure! Here's the execution plan:\n"
            + "{\"version\":1,\"steps\":[{\"id\":\"s1\",\"label\":\"Do it\","
            + "\"taskType\":\"SHELL_COMMAND\",\"intent\":\"echo done\","
            + "\"critical\":false,\"dependsOn\":[]}]}\n"
            + "Let me know if you want changes.";

        WorkflowGraph graph = WorkflowPlannerJsonParser.parse(rawOutput, jsonCodec);

        assertThat(graph.steps()).hasSize(1);
        assertThat(graph.steps().get(0).id()).isEqualTo("s1");
    }

    @Test
    void shouldParseCriticalStepCorrectly() {
        String json = """
            {
              "version": 1,
              "steps": [
                {
                  "id": "deploy",
                  "label": "Deploy to production",
                  "taskType": "SHELL_COMMAND",
                  "intent": "kubectl apply -f prod.yaml",
                  "critical": true,
                  "dependsOn": []
                }
              ]
            }
            """;

        WorkflowGraph graph = WorkflowPlannerJsonParser.parse(json, jsonCodec);

        assertThat(graph.steps().get(0).critical()).isTrue();
    }

    @Test
    void shouldThrowWhenStepsArrayIsMissing() {
        String json = """
            {
              "version": 1
            }
            """;

        assertThatThrownBy(() -> WorkflowPlannerJsonParser.parse(json, jsonCodec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no steps");
    }

    @Test
    void shouldThrowWhenStepsArrayIsEmpty() {
        String json = """
            {
              "version": 1,
              "steps": []
            }
            """;

        assertThatThrownBy(() -> WorkflowPlannerJsonParser.parse(json, jsonCodec))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
