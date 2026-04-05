package com.ai.dev.garage.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = "app.workflow")
@Getter
@Setter
public class WorkflowProperties {

    private boolean enabled = true;

    private int plannerTimeoutSeconds = 120;

    private int stepTimeoutSeconds = 600;

    private int maxSteps = 10;

    /**
     * Which agent runtime handles workflow planning (intent decomposition into execution graph).
     * Only used when {@code app.runner.agent-runtime=hybrid}; ignored in single-agent modes.
     */
    private String plannerRuntime = "claude";

    /**
     * Which agent runtime executes AGENT_TASK steps within a workflow.
     * Only used when {@code app.runner.agent-runtime=hybrid}; ignored in single-agent modes.
     */
    private String executorRuntime = "cursor";
}
