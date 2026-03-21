package com.ai.dev.garage.bot.adapter.out.execution;

import com.ai.dev.garage.bot.application.execution.TaskExecutionResult;
import com.ai.dev.garage.bot.application.port.out.JobLogAppender;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Runs shell commands and streams stdout to the job log (single responsibility).
 */
@Component
@RequiredArgsConstructor
public class ShellProcessRunner {

    private final CommandOutputSummarizer summarizer;

    public TaskExecutionResult run(Long jobId, String command, String cwd, JobLogAppender logAppender) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            if (cwd != null && !cwd.isBlank()) {
                pb.directory(new File(cwd));
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.isBlank()) {
                        logAppender.append(jobId, line);
                        out.append(line).append('\n');
                    }
                }
            }
            int code = process.waitFor();
            String summary = summarizer.summarize(out.toString());
            return new TaskExecutionResult(code == 0, summary, code, code == 0 ? null : "Command failed");
        } catch (Exception e) {
            return new TaskExecutionResult(false, "", -1, e.getMessage());
        }
    }
}
