package com.ai.dev.garage.bot.adapter.out.claude;

import com.ai.dev.garage.bot.adapter.out.agenttask.AgentTaskFileWriter;
import com.ai.dev.garage.bot.adapter.out.cursor.CliWorkspaceResolver;
import com.ai.dev.garage.bot.application.port.out.AgentTaskRuntime;
import com.ai.dev.garage.bot.application.port.out.JobLogAppender;
import com.ai.dev.garage.bot.config.ClaudeCliProperties;
import com.ai.dev.garage.bot.domain.Job;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.runner", name = "agent-runtime", havingValue = "claude")
public class ClaudeCliAdapter implements AgentTaskRuntime {
    private static final Logger agentLog = LoggerFactory.getLogger("com.ai.dev.garage.bot.agent");

    private final AgentTaskFileWriter agentTaskFileWriter;
    private final ClaudeCliProperties claudeCliProperties;
    private final JobLogAppender jobLogAppender;
    private final CliWorkspaceResolver workspaceResolver;

    @Override
    public boolean startForJob(Job job) {
        agentTaskFileWriter.writePendingTaskFile(job);
        if (!claudeCliProperties.isCliInvoke()) {
            return false;
        }
        List<String> cmd = new ArrayList<>();
        if (claudeCliProperties.getLaunchCommand() != null) {
            cmd.addAll(claudeCliProperties.getLaunchCommand());
        }
        cmd.add(claudeCliProperties.getPrompt());
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
            String workspaceDir = workspaceResolver.resolve(job, claudeCliProperties.getWorkspace());
            if (workspaceDir != null && !workspaceDir.isBlank()) {
                pb.directory(new java.io.File(workspaceDir.trim()));
            }
            Process process = pb.start();
            jobLogAppender.append(job.getId(), "[Claude CLI started — agent activity will stream here]");
            new Thread(() -> streamLogs(job.getId(), process), "claude-cli-job-" + job.getId()).start();
            return true;
        } catch (IOException e) {
            agentLog.error("Failed to start Claude CLI for job {}: {}", job.getId(), e.getMessage(), e);
            jobLogAppender.append(job.getId(), "Failed to start Claude CLI: " + e.getMessage());
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
            jobLogAppender.append(jobId, "Claude stream error: " + e.getMessage());
        }
    }
}
