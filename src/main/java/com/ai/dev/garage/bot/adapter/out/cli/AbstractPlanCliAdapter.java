package com.ai.dev.garage.bot.adapter.out.cli;

import com.ai.dev.garage.bot.application.port.out.JobLogAppender;
import com.ai.dev.garage.bot.application.port.out.PlanCliRuntime;
import com.ai.dev.garage.bot.application.port.out.PlanSessionResult;
import com.ai.dev.garage.bot.application.port.out.PlanSessionResult.ParsedMessage;
import com.ai.dev.garage.bot.application.service.AgentQuestionParser;
import com.ai.dev.garage.bot.domain.Job;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * Template-method base for interactive plan session CLI adapters. Subclasses supply
 * command building and runtime-specific configuration; shared process management,
 * stream parsing, and result mapping lives here.
 *
 * @see com.ai.dev.garage.bot.adapter.out.cursor.CursorPlanCliAdapter
 * @see com.ai.dev.garage.bot.adapter.out.claude.ClaudePlanCliAdapter
 */
@Slf4j
public abstract class AbstractPlanCliAdapter implements PlanCliRuntime {

    private final CliWorkspaceResolver workspaceResolver;
    private final CliStreamParser streamParser;
    private final AgentQuestionParser questionParser;
    private final JobLogAppender jobLogAppender;

    protected AbstractPlanCliAdapter(
        CliWorkspaceResolver workspaceResolver,
        CliStreamParser streamParser,
        AgentQuestionParser questionParser,
        JobLogAppender jobLogAppender
    ) {
        this.workspaceResolver = workspaceResolver;
        this.streamParser = streamParser;
        this.questionParser = questionParser;
        this.jobLogAppender = jobLogAppender;
    }

    @Override
    public final PlanSessionResult startPlan(Job job, String prompt) {
        String workspace = workspaceResolver.resolve(job, getWorkspaceProperty());
        String fullPrompt = appendPlanInstructions(prompt);
        List<String> cmd = buildStartCommand(job, workspace, fullPrompt);
        return runAndParse(job, workspace, cmd);
    }

    @Override
    public final PlanSessionResult resumePlan(Job job, String cliSessionId, String userMessage) {
        String workspace = workspaceResolver.resolve(job, getWorkspaceProperty());
        List<String> cmd = buildResumeCommand(job, workspace, cliSessionId, userMessage);
        return runAndParse(job, workspace, cmd);
    }

    protected abstract String runtimeName();

    protected abstract String getWorkspaceProperty();

    protected abstract String getPlanPromptSuffix();

    protected abstract List<String> buildStartCommand(Job job, String workspace, String prompt);

    protected abstract List<String> buildResumeCommand(Job job, String workspace, String cliSessionId, String userMessage);


    private PlanSessionResult runAndParse(Job job, String workspace, List<String> cmd) {
        jobLogAppender.append(job.getId(), "[" + runtimeName() + " plan CLI starting]");
        log.info("Starting {} plan CLI for job {}: {}", runtimeName(), job.getId(), maskResumeArg(cmd));
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
            if (workspace != null && !workspace.isBlank()) {
                pb.directory(new File(workspace.trim()));
            }
            Process process = pb.start();

            CliStreamParser.CliStreamResult streamResult = streamParser.parse(process.getInputStream());

            int exitCode = process.waitFor();
            log.info("{} plan CLI finished for job {} exitCode={} durationMs={}",
                runtimeName(), job.getId(), exitCode, streamResult.durationMs());

            jobLogAppender.append(job.getId(), "[" + runtimeName() + " plan CLI finished — exitCode=" + exitCode + "]");

            List<ParsedMessage> messages = new ArrayList<>();
            for (String text : streamResult.assistantMessages()) {
                var questions = questionParser.parse(text);
                messages.add(new ParsedMessage(text, questions));
            }

            return PlanCliSessionResultMapper.fromStream(job.getId(), runtimeName(), streamResult, exitCode, messages);
        } catch (IOException e) {
            log.error("Failed to start {} plan CLI for job {}: {}", runtimeName(), job.getId(), e.getMessage(), e);
            jobLogAppender.append(job.getId(), "Failed to start " + runtimeName() + " plan CLI: " + e.getMessage());
            return new PlanSessionResult(null, List.of(), false, "", false, "Failed to start " + runtimeName() + " plan CLI: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("{} plan CLI interrupted for job {}", runtimeName(), job.getId(), e);
            return new PlanSessionResult(null, List.of(), false, "", false, runtimeName() + " plan CLI interrupted");
        }
    }

    private String appendPlanInstructions(String prompt) {
        String suffix = getPlanPromptSuffix();
        if (suffix == null || suffix.isBlank()) {
            return prompt;
        }
        return prompt + "\n\n" + suffix;
    }

    protected static String maskResumeArg(List<String> cmd) {
        int resumeIdx = cmd.indexOf("--resume");
        if (resumeIdx < 0 || resumeIdx + 1 >= cmd.size()) {
            return String.join(" ", cmd);
        }
        List<String> copy = new ArrayList<>(cmd);
        copy.set(resumeIdx + 1, "<sessionId>");
        return String.join(" ", copy);
    }
}
