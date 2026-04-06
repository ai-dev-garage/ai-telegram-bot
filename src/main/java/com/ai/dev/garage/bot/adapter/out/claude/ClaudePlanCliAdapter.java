package com.ai.dev.garage.bot.adapter.out.claude;

import com.ai.dev.garage.bot.adapter.out.cli.AbstractPlanCliAdapter;
import com.ai.dev.garage.bot.adapter.out.cli.CliStreamParser;
import com.ai.dev.garage.bot.adapter.out.cli.CliWorkspaceResolver;
import com.ai.dev.garage.bot.application.port.out.JobLogAppender;
import com.ai.dev.garage.bot.application.service.AgentQuestionParser;
import com.ai.dev.garage.bot.config.ClaudeCliProperties;
import com.ai.dev.garage.bot.domain.Job;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs Claude Code in print mode for Telegram plan sessions. Uses the same NDJSON stream parsing
 * as {@link com.ai.dev.garage.bot.adapter.out.cursor.CursorPlanCliAdapter} where possible; see
 * {@link CliStreamParser} for supported line shapes (Cursor Agent SDK and Claude stream_event).
 *
 * <p>CLI flags follow
 * <a href="https://code.claude.com/docs/en/cli-reference">Claude Code CLI reference</a> and
 * <a href="https://code.claude.com/docs/en/headless">headless / {@code -p} mode</a>:
 * {@code claude -p "prompt" --output-format stream-json --permission-mode plan}, resume with
 * {@code --resume sessionId} on a follow-up {@code -p} invocation.
 *
 * <p>Bean created by {@link com.ai.dev.garage.bot.config.AgentRuntimeConfiguration}.
 */
public class ClaudePlanCliAdapter extends AbstractPlanCliAdapter {

    private final ClaudeCliProperties claudeCliProperties;

    public ClaudePlanCliAdapter(
        ClaudeCliProperties claudeCliProperties,
        CliWorkspaceResolver workspaceResolver,
        CliStreamParser streamParser,
        AgentQuestionParser questionParser,
        JobLogAppender jobLogAppender
    ) {
        super(workspaceResolver, streamParser, questionParser, jobLogAppender);
        this.claudeCliProperties = claudeCliProperties;
    }

    @Override
    protected String runtimeName() {
        return "Claude";
    }

    @Override
    protected String getWorkspaceProperty() {
        return claudeCliProperties.getWorkspace();
    }

    @Override
    protected String getPlanPromptSuffix() {
        return claudeCliProperties.getPlanPrompt();
    }

    @Override
    protected List<String> buildStartCommand(Job job, String workspace, String prompt) {
        List<String> cmd = new ArrayList<>();
        cmd.add("claude");
        cmd.add("-p");
        cmd.add(prompt);
        appendPlanFlags(cmd);
        return cmd;
    }

    @Override
    protected List<String> buildResumeCommand(Job job, String workspace,
                                              String cliSessionId, String userMessage) {
        List<String> cmd = new ArrayList<>();
        cmd.add("claude");
        cmd.add("-p");
        cmd.add(userMessage);
        appendPlanFlags(cmd);
        cmd.add("--resume");
        cmd.add(cliSessionId);
        return cmd;
    }

    private void appendPlanFlags(List<String> cmd) {
        cmd.add("--output-format");
        cmd.add("stream-json");
        String mode = claudeCliProperties.getPlanPermissionMode();
        if (mode != null && !mode.isBlank()) {
            cmd.add("--permission-mode");
            cmd.add(mode.trim());
        }
        if (claudeCliProperties.isPlanDangerouslySkipPermissions()) {
            cmd.add("--dangerously-skip-permissions");
        }
        if (claudeCliProperties.isPlanIncludePartialMessages()) {
            cmd.add("--include-partial-messages");
        }
        List<String> extra = claudeCliProperties.getPlanExtraArgs();
        if (extra != null) {
            for (String arg : extra) {
                if (arg != null && !arg.isBlank()) {
                    cmd.add(arg.trim());
                }
            }
        }
    }
}
