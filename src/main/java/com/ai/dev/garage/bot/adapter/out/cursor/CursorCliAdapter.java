package com.ai.dev.garage.bot.adapter.out.cursor;

import com.ai.dev.garage.bot.adapter.out.agenttask.AgentTaskFileWriter;
import com.ai.dev.garage.bot.adapter.out.cli.AbstractAgentTaskCliAdapter;
import com.ai.dev.garage.bot.adapter.out.cli.CliWorkspaceResolver;
import com.ai.dev.garage.bot.application.port.out.JobLogAppender;
import com.ai.dev.garage.bot.config.CursorCliProperties;
import com.ai.dev.garage.bot.domain.Job;

import java.util.List;

/**
 * Cursor CLI adapter for agent task execution. Bean created by
 * {@link com.ai.dev.garage.bot.config.AgentRuntimeConfiguration}.
 */
public class CursorCliAdapter extends AbstractAgentTaskCliAdapter {

    private final CursorCliProperties cursorCliProperties;
    private final CursorCliModelResolver cursorCliModelResolver;

    public CursorCliAdapter(
        AgentTaskFileWriter agentTaskFileWriter,
        CursorCliProperties cursorCliProperties,
        JobLogAppender jobLogAppender,
        CliWorkspaceResolver workspaceResolver,
        CursorCliModelResolver cursorCliModelResolver
    ) {
        super(agentTaskFileWriter, jobLogAppender, workspaceResolver);
        this.cursorCliProperties = cursorCliProperties;
        this.cursorCliModelResolver = cursorCliModelResolver;
    }

    @Override
    protected boolean isCliInvoke() {
        return cursorCliProperties.isCliInvoke();
    }

    @Override
    protected String getWorkspaceProperty() {
        return cursorCliProperties.getWorkspace();
    }

    @Override
    protected String runtimeName() {
        return "Cursor";
    }

    @Override
    protected List<String> buildCommand(Job job, String workspace) {
        List<String> cmd = CursorCommandBuilder.baseCommand(cursorCliProperties, "--force");
        cursorCliModelResolver.resolveModelForJob(job).ifPresent(model -> {
            cmd.add("--model");
            cmd.add(model);
        });
        cmd.add("--workspace");
        cmd.add(workspace);
        cmd.add(cursorCliProperties.getPrompt());
        return cmd;
    }
}
