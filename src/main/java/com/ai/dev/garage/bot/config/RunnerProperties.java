package com.ai.dev.garage.bot.config;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.runner")
@Getter
@Setter
public class RunnerProperties {
    /**
     * {@code cursor} or {@code claude} — matches {@code app.runner.agent-runtime} / {@code AGENT_RUNTIME}.
     */
    private String agentRuntime = "cursor";
    private int defaultTimeoutSeconds = 300;
    private long pollIntervalMs = 2000;
    private int maxLogTail = 500;
    private String agentTasksDir;
    private String policyPath;
    private String authToken;
    private String executorId = "mac_mini";
    /**
     * Comma-separated absolute paths (or {@code ~/}...) the bot may use as shell {@code cwd}. Empty = only null
     * cwd allowed (no custom working directory).
     */
    private String allowedNavigationPaths = "";
    /**
     * Prepended to agent task {@code intent} in pending JSON so the IDE agent can verify origin (pair with guardrails rule).
     */
    private String taskAuthSecretPhrase = "";
    /**
     * HMAC key for signing pending agent task payloads. If blank, no {@code signature} field is written.
     */
    private String taskAuthHmacSecret = "";

    /**
     * Value of the {@code source} field in pending task JSON. Agents must accept this id during preflight.
     */
    private String taskSourceId = "ai-telegram-bot";

    /**
     * First line of trusted {@code intent} when {@link #taskAuthSecretPhrase} is set (phrase on second line).
     */
    private String taskTrustMarkerLine = "[TASK_RUNNER_TRUSTED]";

    /**
     * Directory where approved or paused plans are exported as Cursor-compatible {@code .plan.md} files.
     */
    private String plansExportDir = "~/.ai-dev-garage/plans";


    /**
     * Split {@link #allowedNavigationPaths} for allowlist checks and UI.
     */
    public List<String> listAllowedNavigationPaths() {
        if (allowedNavigationPaths == null || allowedNavigationPaths.isBlank()) {
            return List.of();
        }
        return Arrays.stream(allowedNavigationPaths.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }
}
