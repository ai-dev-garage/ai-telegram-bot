package com.ai.dev.garage.bot.adapter.out.cursor;

import com.ai.dev.garage.bot.adapter.out.agenttask.AgentTaskFileWriter;
import com.ai.dev.garage.bot.application.port.out.AgentTaskRuntime;
import com.ai.dev.garage.bot.application.port.out.JobLogAppender;
import com.ai.dev.garage.bot.config.CursorCliProperties;
import com.ai.dev.garage.bot.domain.Job;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.runner", name = "agent-runtime", havingValue = "cursor", matchIfMissing = true)
public class CursorCliAdapter implements AgentTaskRuntime {
    private static final Logger agentLog = LoggerFactory.getLogger("com.ai.dev.garage.bot.agent");

    private final AgentTaskFileWriter agentTaskFileWriter;
    private final CursorCliProperties cursorCliProperties;
    private final JobLogAppender jobLogAppender;
    private final CliWorkspaceResolver workspaceResolver;

    @Override
    public boolean startForJob(Job job) {
        agentTaskFileWriter.writePendingTaskFile(job);
        if (!cursorCliProperties.isCliInvoke()) {
            return false;
        }
        String workspace = workspaceResolver.resolve(job, cursorCliProperties.getWorkspace());
        try {
            Process process = new ProcessBuilder(
                    "cursor",
                    "agent",
                    "-p",
                    "--force",
                    "--trust",
                    "--workspace",
                    workspace,
                    cursorCliProperties.getPrompt())
                .redirectErrorStream(true)
                .start();
            jobLogAppender.append(job.getId(), "[Cursor CLI started — agent activity will stream here]");
            new Thread(() -> streamLogs(job.getId(), process), "cursor-cli-job-" + job.getId()).start();
            return true;
        } catch (IOException e) {
            agentLog.error("Failed to start Cursor CLI for job {}: {}", job.getId(), e.getMessage(), e);
            jobLogAppender.append(job.getId(), "Failed to start Cursor CLI: " + e.getMessage());
            return false;
        }
    }

    private void streamLogs(Long jobId, Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    jobLogAppender.append(jobId, line);
                }
            }
        } catch (IOException e) {
            jobLogAppender.append(jobId, "Cursor stream error: " + e.getMessage());
        }
    }
}
