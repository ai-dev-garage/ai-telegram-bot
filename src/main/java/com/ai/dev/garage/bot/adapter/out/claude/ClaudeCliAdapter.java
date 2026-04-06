package com.ai.dev.garage.bot.adapter.out.claude;

import com.ai.dev.garage.bot.adapter.out.agenttask.AgentTaskFileWriter;
import com.ai.dev.garage.bot.adapter.out.cli.AbstractAgentTaskCliAdapter;
import com.ai.dev.garage.bot.adapter.out.cli.CliWorkspaceResolver;
import com.ai.dev.garage.bot.application.port.out.JobLogAppender;
import com.ai.dev.garage.bot.config.ClaudeCliProperties;
import com.ai.dev.garage.bot.domain.Job;

import java.util.ArrayList;
import java.util.List;

/**
 * Claude Code CLI adapter for agent task execution. Bean created by
 * {@link com.ai.dev.garage.bot.config.AgentRuntimeConfiguration}.
 */
public class ClaudeCliAdapter extends AbstractAgentTaskCliAdapter {

    private final ClaudeCliProperties claudeCliProperties;

    public ClaudeCliAdapter(
        AgentTaskFileWriter agentTaskFileWriter,
        ClaudeCliProperties claudeCliProperties,
        JobLogAppender jobLogAppender,
        CliWorkspaceResolver workspaceResolver
    ) {
        super(agentTaskFileWriter, jobLogAppender, workspaceResolver);
        this.claudeCliProperties = claudeCliProperties;
    }

    @Override
    protected boolean isCliInvoke() {
        return claudeCliProperties.isCliInvoke();
    }

    @Override
    protected String getWorkspaceProperty() {
        return claudeCliProperties.getWorkspace();
    }

    @Override
    protected String runtimeName() {
        return "Claude";
    }

    @Override
    protected List<String> buildCommand(Job job, String workspace) {
        List<String> cmd = new ArrayList<>();
        if (claudeCliProperties.getLaunchCommand() != null) {
            cmd.addAll(claudeCliProperties.getLaunchCommand());
        }
        cmd.add(claudeCliProperties.getPrompt());
        return cmd;
    }
}
