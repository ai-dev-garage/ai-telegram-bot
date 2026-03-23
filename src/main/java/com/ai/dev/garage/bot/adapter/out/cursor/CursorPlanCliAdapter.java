package com.ai.dev.garage.bot.adapter.out.cursor;

import com.ai.dev.garage.bot.application.port.out.JobLogAppender;
import com.ai.dev.garage.bot.application.port.out.PlanCliRuntime;
import com.ai.dev.garage.bot.application.port.out.PlanSessionResult;
import com.ai.dev.garage.bot.application.port.out.PlanSessionResult.ParsedMessage;
import com.ai.dev.garage.bot.application.service.AgentQuestionParser;
import com.ai.dev.garage.bot.config.CursorCliProperties;
import com.ai.dev.garage.bot.domain.Job;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.runner", name = "agent-runtime", havingValue = "cursor", matchIfMissing = true)
public class CursorPlanCliAdapter implements PlanCliRuntime {

    private final CursorCliProperties cursorCliProperties;
    private final CliWorkspaceResolver workspaceResolver;
    private final CliStreamParser streamParser;
    private final AgentQuestionParser questionParser;
    private final JobLogAppender jobLogAppender;
    private final CursorCliModelResolver cursorCliModelResolver;

    @Override
    public PlanSessionResult startPlan(Job job, String prompt) {
        var workspace = workspaceResolver.resolve(job, cursorCliProperties.getWorkspace());
        var fullPrompt = appendPlanInstructions(prompt);
        var cmd = buildPlanCommand(job, workspace, false, null, fullPrompt);
        return runAndParse(job, workspace, cmd);
    }

    @Override
    public PlanSessionResult resumePlan(Job job, String cliSessionId, String userMessage) {
        var workspace = workspaceResolver.resolve(job, cursorCliProperties.getWorkspace());
        var cmd = buildPlanCommand(job, workspace, true, cliSessionId, userMessage);
        return runAndParse(job, workspace, cmd);
    }

    private List<String> buildPlanCommand(Job job, String workspace, boolean resume, String cliSessionId, String prompt) {
        List<String> cmd = new ArrayList<>();
        cmd.add(cursorCliProperties.getExecutable());
        List<String> prefix = cursorCliProperties.getPlanPrefixArgs();
        if (prefix != null) {
            for (String segment : prefix) {
                if (segment != null && !segment.isBlank()) {
                    cmd.add(segment.trim());
                }
            }
        }
        cmd.add("--print");
        cmd.add("--plan");
        cmd.add("--trust");
        cmd.add("--output-format");
        cmd.add("stream-json");
        cmd.add("--workspace");
        cmd.add(workspace);
        List<String> extras = cursorCliProperties.getPlanExtraArgs();
        if (extras != null) {
            for (String extra : extras) {
                if (extra != null && !extra.isBlank()) {
                    cmd.add(extra.trim());
                }
            }
        }
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

    private PlanSessionResult runAndParse(Job job, String workspace, List<String> cmd) {
        jobLogAppender.append(job.getId(), "[Cursor plan CLI starting]");
        log.info("Starting Cursor plan CLI for job {}: {}", job.getId(), String.join(" ", cmd));
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
            if (workspace != null && !workspace.isBlank()) {
                pb.directory(new File(workspace.trim()));
            }
            Process process = pb.start();

            CliStreamParser.CliStreamResult streamResult = streamParser.parse(process.getInputStream());

            int exitCode = process.waitFor();
            log.info("Cursor plan CLI finished for job {} exitCode={} durationMs={}",
                job.getId(), exitCode, streamResult.durationMs());

            jobLogAppender.append(job.getId(),
                "[Cursor plan CLI finished — exitCode=" + exitCode + "]");

            List<ParsedMessage> messages = new ArrayList<>();
            for (String text : streamResult.assistantMessages()) {
                var questions = questionParser.parse(text);
                messages.add(new ParsedMessage(text, questions));
            }

            return PlanCliSessionResultMapper.fromStream(
                job.getId(), "Cursor", streamResult, exitCode, messages);
        } catch (IOException e) {
            log.error("Failed to start Cursor plan CLI for job {}: {}", job.getId(), e.getMessage(), e);
            jobLogAppender.append(job.getId(), "Failed to start Cursor plan CLI: " + e.getMessage());
            return new PlanSessionResult(
                null, List.of(), false, "", false, "Failed to start Cursor plan CLI: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Cursor plan CLI interrupted for job {}", job.getId(), e);
            return new PlanSessionResult(
                null, List.of(), false, "", false, "Cursor plan CLI interrupted");
        }
    }

    private String appendPlanInstructions(String prompt) {
        String suffix = cursorCliProperties.getPlanPrompt();
        if (suffix == null || suffix.isBlank()) {
            return prompt;
        }
        return prompt + "\n\n" + suffix;
    }
}
