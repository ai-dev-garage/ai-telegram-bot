package com.ai.dev.garage.bot.adapter.out.cursor;

import com.ai.dev.garage.bot.adapter.out.cli.AbstractPlanCliAdapter;
import com.ai.dev.garage.bot.adapter.out.cli.CliStreamParser;
import com.ai.dev.garage.bot.adapter.out.cli.CliWorkspaceResolver;
import com.ai.dev.garage.bot.application.port.out.JobLogAppender;
import com.ai.dev.garage.bot.application.service.AgentQuestionParser;
import com.ai.dev.garage.bot.config.CursorCliProperties;
import com.ai.dev.garage.bot.domain.Job;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * Cursor CLI adapter for interactive plan sessions. Bean created by
 * {@link com.ai.dev.garage.bot.config.AgentRuntimeConfiguration}.
 */
public class CursorPlanCliAdapter extends AbstractPlanCliAdapter {

    private final CursorCliProperties cursorCliProperties;
    private final CursorCliModelResolver cursorCliModelResolver;

    @SuppressWarnings("checkstyle:ParameterNumber") // 4 of 6 are pass-through to super
    public CursorPlanCliAdapter(
        CursorCliProperties cursorCliProperties,
        CliWorkspaceResolver workspaceResolver,
        CliStreamParser streamParser,
        AgentQuestionParser questionParser,
        JobLogAppender jobLogAppender,
        CursorCliModelResolver cursorCliModelResolver
    ) {
        super(workspaceResolver, streamParser, questionParser, jobLogAppender);
        this.cursorCliProperties = cursorCliProperties;
        this.cursorCliModelResolver = cursorCliModelResolver;
    }

    @Override
    protected String runtimeName() {
        return "Cursor";
    }

    @Override
    protected String getWorkspaceProperty() {
        return cursorCliProperties.getWorkspace();
    }

    @Override
    protected String getPlanPromptSuffix() {
        return cursorCliProperties.getPlanPrompt();
    }

    @Override
    protected List<String> buildStartCommand(Job job, String workspace, String prompt) {
        return buildPlanCommand(job, workspace, false, null, prompt);
    }

    @Override
    protected List<String> buildResumeCommand(Job job, String workspace, String cliSessionId, String userMessage) {
        return buildPlanCommand(job, workspace, true, cliSessionId, userMessage);
    }

    private List<String> buildPlanCommand(Job job, String workspace, boolean resume, String cliSessionId, String prompt) {
        List<String> cmd = CursorCommandBuilder.baseCommand(cursorCliProperties, "--plan");
        cmd.add("--output-format");
        cmd.add("stream-json");
        cmd.add("--workspace");
        cmd.add(workspace);
        Optional.ofNullable(cursorCliProperties.getPlanExtraArgs())
            .ifPresent(extras -> extras.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .forEach(cmd::add));
        cursorCliModelResolver.resolveModelForJob(job).ifPresent(model -> {
            cmd.add("--model");
            cmd.add(model);
        });
        if (resume) {
            cmd.add("--resume");
            cmd.add(cliSessionId);
        }
        cmd.add(prompt);
        return cmd;
    }
}
