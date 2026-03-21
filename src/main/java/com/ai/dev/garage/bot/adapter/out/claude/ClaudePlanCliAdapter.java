package com.ai.dev.garage.bot.adapter.out.claude;

import com.ai.dev.garage.bot.adapter.out.cursor.CliStreamParser;
import com.ai.dev.garage.bot.adapter.out.cursor.CliWorkspaceResolver;
import com.ai.dev.garage.bot.application.port.out.JobLogAppender;
import com.ai.dev.garage.bot.application.port.out.PlanCliRuntime;
import com.ai.dev.garage.bot.application.port.out.PlanSessionResult;
import com.ai.dev.garage.bot.application.port.out.PlanSessionResult.ParsedMessage;
import com.ai.dev.garage.bot.application.service.AgentQuestionParser;
import com.ai.dev.garage.bot.config.ClaudeCliProperties;
import com.ai.dev.garage.bot.domain.Job;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

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
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.runner", name = "agent-runtime", havingValue = "claude")
public class ClaudePlanCliAdapter implements PlanCliRuntime {

    private final ClaudeCliProperties claudeCliProperties;
    private final CliWorkspaceResolver workspaceResolver;
    private final CliStreamParser streamParser;
    private final AgentQuestionParser questionParser;
    private final JobLogAppender jobLogAppender;

    @Override
    public PlanSessionResult startPlan(Job job, String prompt) {
        String fullPrompt = appendPlanInstructions(prompt);
        List<String> cmd = new ArrayList<>();
        cmd.add("claude");
        cmd.add("-p");
        cmd.add(fullPrompt);
        appendPlanFlagsAfterPrompt(cmd);
        return runAndParse(job, cmd);
    }

    @Override
    public PlanSessionResult resumePlan(Job job, String cliSessionId, String userMessage) {
        List<String> cmd = new ArrayList<>();
        cmd.add("claude");
        cmd.add("-p");
        cmd.add(userMessage);
        appendPlanFlagsAfterPrompt(cmd);
        cmd.add("--resume");
        cmd.add(cliSessionId);
        return runAndParse(job, cmd);
    }

    /**
     * Flags after the {@code -p} prompt, per
     * <a href="https://code.claude.com/docs/en/headless">headless mode</a> examples
     * ({@code claude -p "..." --output-format json --resume ...}).
     */
    private void appendPlanFlagsAfterPrompt(List<String> cmd) {
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

    private PlanSessionResult runAndParse(Job job, List<String> cmd) {
        jobLogAppender.append(job.getId(), "[Claude plan CLI starting]");
        log.info("Starting Claude plan CLI for job {}: {}", job.getId(), maskResumeArg(cmd));
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
            String workspaceDir = workspaceResolver.resolve(job, claudeCliProperties.getWorkspace());
            if (workspaceDir != null && !workspaceDir.isBlank()) {
                pb.directory(new File(workspaceDir.trim()));
            }
            Process process = pb.start();

            CliStreamParser.CliStreamResult streamResult = streamParser.parse(process.getInputStream());

            int exitCode = process.waitFor();
            log.info("Claude plan CLI finished for job {} exitCode={} durationMs={}",
                job.getId(), exitCode, streamResult.durationMs());

            jobLogAppender.append(job.getId(),
                "[Claude plan CLI finished — exitCode=" + exitCode + "]");

            List<ParsedMessage> messages = new ArrayList<>();
            for (String text : streamResult.assistantMessages()) {
                var questions = questionParser.parse(text);
                messages.add(new ParsedMessage(text, questions));
            }

            boolean hasQuestions = messages.stream()
                .anyMatch(m -> !m.questions().isEmpty());

            return new PlanSessionResult(
                streamResult.sessionId(),
                messages,
                !hasQuestions,
                streamResult.fullText()
            );
        } catch (IOException e) {
            log.error("Failed to start Claude plan CLI for job {}: {}", job.getId(), e.getMessage(), e);
            jobLogAppender.append(job.getId(), "Failed to start Claude plan CLI: " + e.getMessage());
            return new PlanSessionResult(null, List.of(), true, "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Claude plan CLI interrupted for job {}", job.getId(), e);
            return new PlanSessionResult(null, List.of(), true, "");
        }
    }

    private String appendPlanInstructions(String prompt) {
        String suffix = claudeCliProperties.getPlanPrompt();
        if (suffix == null || suffix.isBlank()) {
            return prompt;
        }
        return prompt + "\n\n" + suffix;
    }

    /** Avoid logging huge prompts; keep resume id visible for debugging. */
    private static String maskResumeArg(List<String> cmd) {
        int resumeIdx = cmd.indexOf("--resume");
        if (resumeIdx < 0 || resumeIdx + 1 >= cmd.size()) {
            return String.join(" ", cmd);
        }
        List<String> copy = new ArrayList<>(cmd);
        copy.set(resumeIdx + 1, "<sessionId>");
        return String.join(" ", copy);
    }
}
