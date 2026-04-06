package com.ai.dev.garage.bot.adapter.out.cli;

import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.application.port.out.WorkflowPlannerRuntime;
import com.ai.dev.garage.bot.config.WorkflowProperties;
import com.ai.dev.garage.bot.domain.WorkflowGraph;
import com.ai.dev.garage.bot.domain.exception.WorkflowPlannerException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lombok.extern.slf4j.Slf4j;

/**
 * Template-method base for CLI-based workflow planner adapters. Subclasses supply
 * the command line, workspace resolution, and a human-readable runtime name; the
 * shared process-management, timeout, and parsing logic lives here.
 *
 * @see com.ai.dev.garage.bot.adapter.out.claude.ClaudeWorkflowPlannerAdapter
 * @see com.ai.dev.garage.bot.adapter.out.cursor.CursorWorkflowPlannerAdapter
 */
@Slf4j
public abstract class AbstractWorkflowPlannerAdapter implements WorkflowPlannerRuntime {

    private static final int LOG_INTENT_MAX_LEN = 80;
    private static final int ERROR_OUTPUT_MAX_LEN = 500;
    private static final int OUTPUT_DRAIN_TIMEOUT_SECONDS = 5;

    private final WorkflowProperties workflowProperties;
    private final JsonCodec jsonCodec;

    protected AbstractWorkflowPlannerAdapter(WorkflowProperties workflowProperties, JsonCodec jsonCodec) {
        this.workflowProperties = workflowProperties;
        this.jsonCodec = jsonCodec;
    }

    protected abstract List<String> buildCommand(String prompt, String workspace);

    protected abstract String getWorkspaceFallback();

    protected abstract String runtimeName();

    protected final String resolveWorkspace(String workspace) {
        if (workspace != null && !workspace.isBlank()) {
            return workspace.trim();
        }
        String fallback = getWorkspaceFallback();
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }

    @Override
    public WorkflowGraph decompose(String intent, String workspace) {
        var prompt = WorkflowPlannerPrompt.build(intent, workflowProperties.getMaxSteps());
        List<String> cmd = buildCommand(prompt, workspace);
        log.info("Starting {} workflow planner for intent: {}", runtimeName(), truncate(intent, LOG_INTENT_MAX_LEN));
        String output = runCli(cmd, workspace);
        return WorkflowPlannerJsonParser.parse(output, jsonCodec);
    }

    private String runCli(List<String> cmd, String workspace) {
        try {
            var pb = new ProcessBuilder(cmd).redirectErrorStream(true);
            String dir = resolveWorkspace(workspace);
            if (dir != null) {
                pb.directory(new File(dir));
            }

            Process process = pb.start();

            // Read stdout asynchronously — readAllBytes() blocks until the stream closes.
            // Without this, a hanging process would block readAllBytes() forever and the
            // waitFor() timeout below would never be reached.
            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException ignored) {
                    return "";
                }
            });

            boolean exited = process.waitFor(workflowProperties.getPlannerTimeoutSeconds(), TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
                throw new WorkflowPlannerException(
                    runtimeName() + " planner timed out after " + workflowProperties.getPlannerTimeoutSeconds() + "s");
            }

            // Process has exited — the stream closes quickly; short drain timeout is safe.
            String output = awaitOutput(outputFuture, OUTPUT_DRAIN_TIMEOUT_SECONDS);

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new WorkflowPlannerException(
                    runtimeName() + " planner exited with code " + exitCode + ": " + truncate(output, ERROR_OUTPUT_MAX_LEN));
            }
            return output;
        } catch (IOException e) {
            throw new WorkflowPlannerException("Failed to start " + runtimeName() + " planner CLI: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WorkflowPlannerException(runtimeName() + " planner interrupted", e);
        }
    }

    private static String awaitOutput(CompletableFuture<String> future, int timeoutSeconds) {
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            return "";
        }
    }

    protected static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
