package com.ai.dev.garage.bot.adapter.out.cursor;

import com.ai.dev.garage.bot.application.port.out.JobLogAppender;
import com.ai.dev.garage.bot.application.port.out.PlanCliRuntime;
import com.ai.dev.garage.bot.application.port.out.PlanSessionResult;
import com.ai.dev.garage.bot.application.port.out.PlanSessionResult.ParsedMessage;
import com.ai.dev.garage.bot.application.service.AgentQuestionParser;
import com.ai.dev.garage.bot.config.CursorCliProperties;
import com.ai.dev.garage.bot.domain.Job;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

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

    @Override
    public PlanSessionResult startPlan(Job job, String prompt) {
        String workspace = workspaceResolver.resolve(job, cursorCliProperties.getWorkspace());
        String fullPrompt = appendPlanInstructions(prompt);
        List<String> cmd = List.of(
            "cursor", "agent",
            "-p", "--plan", "--trust",
            "--output-format", "stream-json",
            "--workspace", workspace,
            fullPrompt
        );
        return runAndParse(job, cmd);
    }

    @Override
    public PlanSessionResult resumePlan(Job job, String cliSessionId, String userMessage) {
        String workspace = workspaceResolver.resolve(job, cursorCliProperties.getWorkspace());
        List<String> cmd = List.of(
            "cursor", "agent",
            "-p", "--plan", "--trust",
            "--output-format", "stream-json",
            "--workspace", workspace,
            "--resume", cliSessionId,
            userMessage
        );
        return runAndParse(job, cmd);
    }

    private PlanSessionResult runAndParse(Job job, List<String> cmd) {
        jobLogAppender.append(job.getId(), "[Cursor plan CLI starting]");
        log.info("Starting Cursor plan CLI for job {}: {}", job.getId(), String.join(" ", cmd));
        try {
            Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();

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

            boolean hasQuestions = messages.stream()
                .anyMatch(m -> !m.questions().isEmpty());

            return new PlanSessionResult(
                streamResult.sessionId(),
                messages,
                !hasQuestions,
                streamResult.fullText()
            );
        } catch (IOException e) {
            log.error("Failed to start Cursor plan CLI for job {}: {}", job.getId(), e.getMessage(), e);
            jobLogAppender.append(job.getId(), "Failed to start Cursor plan CLI: " + e.getMessage());
            return new PlanSessionResult(null, List.of(), true, "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Cursor plan CLI interrupted for job {}", job.getId(), e);
            return new PlanSessionResult(null, List.of(), true, "");
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
