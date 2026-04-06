package com.ai.dev.garage.bot.adapter.out.cursor;

import com.ai.dev.garage.bot.adapter.out.cli.AbstractWorkflowPlannerAdapter;
import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.config.CursorCliProperties;
import com.ai.dev.garage.bot.config.WorkflowProperties;

import java.util.List;

/**
 * Invokes Cursor CLI ({@code cursor agent --print --force --trust}) to decompose a user intent
 * into a workflow graph.
 *
 * <p>Bean created by {@link com.ai.dev.garage.bot.config.AgentRuntimeConfiguration}.
 */
public class CursorWorkflowPlannerAdapter extends AbstractWorkflowPlannerAdapter {

    private final CursorCliProperties cursorCliProperties;

    public CursorWorkflowPlannerAdapter(CursorCliProperties cursorCliProperties, WorkflowProperties workflowProperties, JsonCodec jsonCodec) {
        super(workflowProperties, jsonCodec);
        this.cursorCliProperties = cursorCliProperties;
    }

    @Override
    protected String runtimeName() {
        return "Cursor";
    }

    @Override
    protected List<String> buildCommand(String prompt, String workspace) {
        List<String> cmd = CursorCommandBuilder.baseCommand(cursorCliProperties, "--force");
        if (workspace != null && !workspace.isBlank()) {
            cmd.add("--workspace");
            cmd.add(workspace.trim());
        }
        cmd.add(prompt);
        return cmd;
    }

    @Override
    protected String getWorkspaceFallback() {
        return cursorCliProperties.getWorkspace();
    }
}
