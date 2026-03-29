package com.ai.dev.garage.bot.adapter.out.cursor;

import com.ai.dev.garage.bot.adapter.out.agenttask.AgentTaskFileWriter;
import com.ai.dev.garage.bot.application.port.out.AgentTaskRuntime;
import com.ai.dev.garage.bot.application.port.out.JobLogAppender;
import com.ai.dev.garage.bot.config.CursorCliProperties;
import com.ai.dev.garage.bot.domain.Job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cursor CLI adapter for agent task execution. Bean created by
 * {@link com.ai.dev.garage.bot.config.AgentRuntimeConfiguration}.
 */
@Slf4j
@RequiredArgsConstructor
public class CursorCliAdapter implements AgentTaskRuntime {
    private static final Logger AGENT_LOG = LoggerFactory.getLogger("com.ai.dev.garage.bot.agent");

    private final AgentTaskFileWriter agentTaskFileWriter;
    private final CursorCliProperties cursorCliProperties;
    private final JobLogAppender jobLogAppender;
    private final CliWorkspaceResolver workspaceResolver;
    private final CursorCliModelResolver cursorCliModelResolver;

    @Override
    public boolean startForJob(Job job) {
        agentTaskFileWriter.writePendingTaskFile(job);
        if (!cursorCliProperties.isCliInvoke()) {
            return false;
        }
        String workspace = workspaceResolver.resolve(job, cursorCliProperties.getWorkspace());
        try {
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
            cmd.add("--force");
            cmd.add("--trust");
            cursorCliModelResolver.resolveModelForJob(job).ifPresent(model -> {
                cmd.add("--model");
                cmd.add(model);
            });
            cmd.add("--workspace");
            cmd.add(workspace);
            cmd.add(cursorCliProperties.getPrompt());
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
            if (workspace != null && !workspace.isBlank()) {
                pb.directory(new File(workspace.trim()));
            }
            Process process = pb.start();
            jobLogAppender.append(job.getId(), "[Cursor CLI started — agent activity will stream here]");
            new Thread(() -> streamLogs(job.getId(), process), "cursor-cli-job-" + job.getId()).start();
            return true;
        } catch (IOException e) {
            AGENT_LOG.error("Failed to start Cursor CLI for job {}: {}", job.getId(), e.getMessage(), e);
            jobLogAppender.append(job.getId(), "Failed to start Cursor CLI: " + e.getMessage());
            return false;
        }
    }

    private void streamLogs(Long jobId, Process process) {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while (line != null) {
                if (!line.isBlank()) {
                    jobLogAppender.append(jobId, line);
                }
                line = reader.readLine();
            }
        } catch (IOException e) {
            jobLogAppender.append(jobId, "Cursor stream error: " + e.getMessage());
        }
    }
}
