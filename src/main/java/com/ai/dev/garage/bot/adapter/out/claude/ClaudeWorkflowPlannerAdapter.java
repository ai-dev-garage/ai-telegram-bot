package com.ai.dev.garage.bot.adapter.out.claude;

import com.ai.dev.garage.bot.adapter.out.cli.AbstractWorkflowPlannerAdapter;
import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.config.ClaudeCliProperties;
import com.ai.dev.garage.bot.config.WorkflowProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Invokes Claude Code CLI ({@code claude -p}) to decompose a user intent into a workflow graph.
 *
 * <p>Bean created by {@link com.ai.dev.garage.bot.config.AgentRuntimeConfiguration}.
 */
public class ClaudeWorkflowPlannerAdapter extends AbstractWorkflowPlannerAdapter {

    private final ClaudeCliProperties claudeCliProperties;

    public ClaudeWorkflowPlannerAdapter(ClaudeCliProperties claudeCliProperties,
                                        WorkflowProperties workflowProperties,
                                        JsonCodec jsonCodec) {
        super(workflowProperties, jsonCodec);
        this.claudeCliProperties = claudeCliProperties;
    }

    @Override
    protected String runtimeName() {
        return "Claude";
    }

    @Override
    protected List<String> buildCommand(String prompt, String workspace) {
        List<String> cmd = new ArrayList<>();
        cmd.add("claude");
        cmd.add("-p");
        cmd.add(prompt);
        cmd.add("--output-format");
        cmd.add("text");
        String mode = claudeCliProperties.getPlanPermissionMode();
        if (mode != null && !mode.isBlank()) {
            cmd.add("--permission-mode");
            cmd.add(mode.trim());
        }
        return cmd;
    }

    @Override
    protected String resolveWorkspace(String workspace) {
        if (workspace != null && !workspace.isBlank()) {
            return workspace.trim();
        }
        String fallback = claudeCliProperties.getWorkspace();
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }
}
