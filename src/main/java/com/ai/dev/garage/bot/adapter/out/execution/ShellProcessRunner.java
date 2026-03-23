package com.ai.dev.garage.bot.adapter.out.execution;

import com.ai.dev.garage.bot.application.execution.model.TaskExecutionResult;
import com.ai.dev.garage.bot.application.port.out.JobLogAppender;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import lombok.RequiredArgsConstructor;

/**
 * Runs shell commands and streams stdout to the job log (single responsibility).
 */
@Component
@RequiredArgsConstructor
public class ShellProcessRunner {

    private final CommandOutputSummarizer summarizer;

    public TaskExecutionResult run(Long jobId, String command, String cwd, JobLogAppender logAppender) {
        try {
            var pb = new ProcessBuilder("sh", "-c", command);
            if (cwd != null && !cwd.isBlank()) {
                pb.directory(new File(cwd));
            }
            pb.redirectErrorStream(true);
            var process = pb.start();
            var out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = br.readLine();
                while (line != null) {
                    if (!line.isBlank()) {
                        logAppender.append(jobId, line);
                        out.append(line).append('\n');
                    }
                    line = br.readLine();
                }
            }
            int code = process.waitFor();
            String summary = summarizer.summarize(out.toString());
            return new TaskExecutionResult(code == 0, summary, code, code == 0 ? null : "Command failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new TaskExecutionResult(false, "", -1, e.getMessage());
        } catch (Exception e) {
            return new TaskExecutionResult(false, "", -1, e.getMessage());
        }
    }
}
