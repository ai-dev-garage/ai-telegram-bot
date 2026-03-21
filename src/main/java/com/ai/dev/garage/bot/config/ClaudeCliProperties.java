package com.ai.dev.garage.bot.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.claude")
@Getter
@Setter
public class ClaudeCliProperties {

    private boolean cliInvoke = false;

    /** Working directory for the child process (optional). */
    private String workspace;

    /**
     * Prompt or instruction passed to the CLI (appended after {@link #launchCommand}).
     * Adjust {@link #launchCommand} to match your installed Claude Code / CLI.
     */
    private String prompt = "";

    /**
     * Base command plus arguments, executed from {@link #workspace} when set.
     * Example (adjust if your CLI differs): {@code claude -p}
     */
    private List<String> launchCommand = new ArrayList<>(List.of("claude", "-p"));

    /**
     * Appended to the user plan prompt (Telegram /plan flow), same role as {@code app.cursor.plan-prompt}.
     */
    private String planPrompt = "List each question on its own line ending with '?'. "
        + "If you want to offer choices, list them as bullet points below the question.";

    /**
     * Passed to {@code claude -p} as {@code --permission-mode <value>}. Default {@code plan} aligns
     * with exploratory planning; set empty to omit the flag.
     */
    private String planPermissionMode = "plan";

    /**
     * When true, adds {@code --dangerously-skip-permissions} to plan CLI invocations only.
     * Use only in trusted environments; see Claude Code permissions documentation.
     */
    private boolean planDangerouslySkipPermissions = false;

    /**
     * When true, adds {@code --include-partial-messages} so NDJSON may include {@code stream_event}
     * lines; {@link com.ai.dev.garage.bot.adapter.out.cursor.CliStreamParser} accumulates text deltas.
     */
    private boolean planIncludePartialMessages = false;

    /**
     * Extra CLI arguments inserted after standard plan flags (before the prompt on start, before
     * prompt + {@code --resume} on resume). Example: {@code --allowedTools} patterns.
     */
    private List<String> planExtraArgs = new ArrayList<>();
}
