package com.ai.dev.garage.bot.adapter.out.cli;

import com.ai.dev.garage.bot.adapter.out.agenttask.AgentTaskFileWriter;
import com.ai.dev.garage.bot.application.port.out.AgentTaskRuntime;
import com.ai.dev.garage.bot.application.port.out.JobLogAppender;
import com.ai.dev.garage.bot.domain.Job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static java.util.Locale.ROOT;

/**
 * Template-method base for fire-and-forget CLI agent task adapters. Subclasses supply
 * the command line and runtime-specific configuration; shared process management and
 * log streaming lives here.
 *
 * @see com.ai.dev.garage.bot.adapter.out.cursor.CursorCliAdapter
 * @see com.ai.dev.garage.bot.adapter.out.claude.ClaudeCliAdapter
 */
public abstract class AbstractAgentTaskCliAdapter implements AgentTaskRuntime {

    private static final Logger AGENT_LOG = LoggerFactory.getLogger("com.ai.dev.garage.bot.agent");

    private final AgentTaskFileWriter agentTaskFileWriter;
    private final JobLogAppender jobLogAppender;
    private final CliWorkspaceResolver workspaceResolver;

    protected AbstractAgentTaskCliAdapter(
        AgentTaskFileWriter agentTaskFileWriter,
        JobLogAppender jobLogAppender,
        CliWorkspaceResolver workspaceResolver
    ) {
        this.agentTaskFileWriter = agentTaskFileWriter;
        this.jobLogAppender = jobLogAppender;
        this.workspaceResolver = workspaceResolver;
    }

    protected abstract boolean isCliInvoke();

    protected abstract String getWorkspaceProperty();

    protected abstract String runtimeName();

    protected abstract List<String> buildCommand(Job job, String workspace);

    @Override
    public final boolean startForJob(Job job) {
        agentTaskFileWriter.writePendingTaskFile(job);
        if (!isCliInvoke()) {
            return false;
        }
        String workspace = workspaceResolver.resolve(job, getWorkspaceProperty());
        List<String> cmd = buildCommand(job, workspace);
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
            if (workspace != null && !workspace.isBlank()) {
                pb.directory(new File(workspace.trim()));
            }
            Process process = pb.start();
            jobLogAppender.append(job.getId(), "[" + runtimeName() + " CLI started — agent activity will stream here]");
            new Thread(
                () -> streamLogs(job.getId(), process),
                runtimeName().toLowerCase(ROOT) + "-cli-job-" + job.getId())
                .start();
            return true;
        } catch (IOException e) {
            AGENT_LOG.error("Failed to start {} CLI for job {}: {}", runtimeName(), job.getId(), e.getMessage(), e);
            jobLogAppender.append(job.getId(), "Failed to start " + runtimeName() + " CLI: " + e.getMessage());
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
            jobLogAppender.append(jobId, runtimeName() + " stream error: " + e.getMessage());
        }
    }
}
