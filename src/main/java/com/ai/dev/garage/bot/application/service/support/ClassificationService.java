package com.ai.dev.garage.bot.application.service.support;

import com.ai.dev.garage.bot.application.port.in.support.IntentClassification;
import com.ai.dev.garage.bot.application.port.out.PolicyProvider;
import com.ai.dev.garage.bot.domain.ApprovalState;
import com.ai.dev.garage.bot.domain.ClassificationResult;
import com.ai.dev.garage.bot.domain.RiskLevel;
import com.ai.dev.garage.bot.domain.TaskType;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClassificationService implements IntentClassification {

    private static final int DEFAULT_SHELL_TIMEOUT_SECONDS = 300;

    private static final Pattern CONFLUENCE_HINT = Pattern.compile(
        "\\bconfluence\\b|create\\s+(a\\s+)?(new\\s+)?page|publish\\s+(to\\s+)?confluence");
    private static final Pattern EXPLICIT_PLAN = Pattern.compile("(?i)^plan\\s+(?<body>.+)$");
    private static final Pattern EXPLICIT_AGENT = Pattern.compile("(?i)^agent\\s+(?<body>.+)$");

    private final PolicyProvider policyProvider;

    @Override
    public ClassificationResult classify(String intent) {
        Map<String, Object> policy = policyProvider.loadPolicy();
        String normalized = intent == null ? "" : intent.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);

        return classifyConfluence(normalized, lower)
            .or(() -> classifyExplicitPlan(normalized))
            .or(() -> classifyExplicitAgent(normalized))
            .orElseGet(() -> classifyShell(normalized, lower, policy));
    }

    private static Optional<ClassificationResult> classifyConfluence(String normalized, String lower) {
        if (!CONFLUENCE_HINT.matcher(lower).find()) {
            return Optional.empty();
        }
        return Optional.of(new ClassificationResult(
            TaskType.AGENT_TASK,
            Map.of("agent_or_command", "confluence", "input", normalized, "context", Map.of()),
            RiskLevel.MEDIUM,
            ApprovalState.APPROVED
        ));
    }

    private static Optional<ClassificationResult> classifyExplicitPlan(String normalized) {
        var m = EXPLICIT_PLAN.matcher(normalized);
        if (!m.matches()) {
            return Optional.empty();
        }
        String body = m.group("body").trim();
        if (body.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ClassificationResult(
            TaskType.PLAN_TASK,
            Map.of("agent_or_command", "plan", "input", body, "context", Map.of()),
            RiskLevel.LOW,
            ApprovalState.APPROVED
        ));
    }

    private static Optional<ClassificationResult> classifyExplicitAgent(String normalized) {
        var m = EXPLICIT_AGENT.matcher(normalized);
        if (!m.matches()) {
            return Optional.empty();
        }
        String body = m.group("body").trim();
        if (body.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ClassificationResult(
            TaskType.AGENT_TASK,
            Map.of("agent_or_command", "agent", "input", body, "context", Map.of()),
            RiskLevel.MEDIUM,
            ApprovalState.APPROVED
        ));
    }

    @SuppressWarnings("unchecked") // YAML policy maps are untyped; nested lists are shell allowlists/patterns
    private static ClassificationResult classifyShell(String normalized, String lower, Map<String, Object> policy) {
        var safeCommands = (List<String>) ((Map<String, Object>) policy.getOrDefault("allowlist_safe", Map.of()))
            .getOrDefault("shell_command", List.of());
        var riskyPatterns = (List<String>) ((Map<String, Object>) policy.getOrDefault(
            "require_approval_patterns", Map.of()))
            .getOrDefault("shell_command", List.of());

        boolean safe = safeCommands.stream()
            .anyMatch(s -> lower.equals(s.toLowerCase(Locale.ROOT))
                || lower.startsWith(s.toLowerCase(Locale.ROOT) + " "));
        boolean restricted = riskyPatterns.stream().anyMatch(lower::contains);

        RiskLevel risk = safe ? RiskLevel.LOW : (restricted ? RiskLevel.HIGH : RiskLevel.MEDIUM);
        ApprovalState approval = safe ? ApprovalState.APPROVED : ApprovalState.PENDING;
        Map<String, Object> shellPayload = new HashMap<>();
        shellPayload.put("command", normalized);
        shellPayload.put("cwd", null);
        shellPayload.put("timeout_seconds", DEFAULT_SHELL_TIMEOUT_SECONDS);

        return new ClassificationResult(
            TaskType.SHELL_COMMAND,
            shellPayload,
            risk,
            approval
        );
    }
}
