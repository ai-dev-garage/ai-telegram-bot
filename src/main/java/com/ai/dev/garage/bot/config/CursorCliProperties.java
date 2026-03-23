package com.ai.dev.garage.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = "app.cursor")
@Getter
@Setter
public class CursorCliProperties {
    /**
     * Executable or absolute path for the Cursor CLI (first argument before {@code agent}).
     * Use when the JVM process does not inherit the same {@code PATH} as an interactive shell.
     */
    private String executable = "cursor";
    private boolean cliInvoke;
    private String workspace;
    private String prompt;
    private String planPrompt = "List each question on its own line ending with '?'. " +
        "If you want to offer choices, list them as bullet points below the question.";

    /**
     * Tokens after {@link #executable} and before plan flags. Default {@code [agent]} gives
     * {@code cursor agent --print …}. For docs-style {@code agent agent …}, set {@link #executable} to the
     * {@code agent} binary and keep one {@code agent} entry here (argv becomes {@code agent agent --print …}).
     */
    private List<String> planPrefixArgs = new ArrayList<>(List.of("agent"));

    /**
     * Extra argv after standard plan flags (before the prompt; before {@code --resume} + prompt on resume).
     */
    private List<String> planExtraArgs = new ArrayList<>();

    /**
     * Passed to Cursor CLI as {@code --model} for plan and agent invocations when job payload has no
     * {@code cliModel}. Default {@code auto}; leave empty to omit {@code --model} and use the CLI default.
     */
    private String defaultModel = "auto";

    /**
     * Telegram {@code /plan} / {@code /agent} first token {@code @alias} (case-insensitive key) → exact
     * {@code --model} argument for the CLI. Authoritative ids: Telegram {@code /models} (uses {@code --list-models}) or local CLI.
     */
    private Map<String, String> telegramModelAliases = new LinkedHashMap<>();
}
