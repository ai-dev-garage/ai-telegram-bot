package com.ai.dev.garage.bot.adapter.out.cursor;

import com.ai.dev.garage.bot.config.CursorCliProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs {@code <executable> <planPrefixArgs...> --list-models} to list model ids. Uses the global
 * {@code --list-models} flag (see Cursor CLI parameters) instead of the {@code agent models} subcommand so
 * listing does not start an agent session or bill model usage.
 *
 * <p>Bean created by {@link com.ai.dev.garage.bot.config.AgentRuntimeConfiguration}.
 */
@Slf4j
@RequiredArgsConstructor
public class CursorAgentModelsCliRunner {

    private static final int TIMEOUT_SECONDS = 60;

    private final CursorCliProperties cursorCliProperties;

    /**
     * Runs the Cursor CLI models subcommand and returns process output.
     *
     * @return combined stdout/stderr; empty string if process produced no bytes
     */
    public String runListModels() throws IOException, InterruptedException {
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
        cmd.add("--list-models");
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process process = pb.start();
        byte[] bytes = process.getInputStream().readAllBytes();
        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("cursor list-models timed out after " + TIMEOUT_SECONDS + "s");
        }
        String text = new String(bytes, StandardCharsets.UTF_8).trim();
        if (text.isEmpty() && process.exitValue() != 0) {
            return "(no output, exitCode=" + process.exitValue() + ")";
        }
        return text;
    }
}
