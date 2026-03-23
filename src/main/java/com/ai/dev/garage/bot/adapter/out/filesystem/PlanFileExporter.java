package com.ai.dev.garage.bot.adapter.out.filesystem;

import com.ai.dev.garage.bot.config.RunnerProperties;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.PlanQuestion;
import com.ai.dev.garage.bot.domain.PlanSession;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlanFileExporter {

    private static final int PLAN_FILE_SLUG_MAX_LEN = 40;
    private static final int YAML_OVERVIEW_MAX_LEN = 200;
    private static final int DERIVE_NAME_MAX_LEN = 60;
    private static final int DERIVE_NAME_KEEP_LEN = 57;
    private static final String ELLIPSIS = "...";

    private final RunnerProperties runnerProperties;
    private final ObjectMapper objectMapper;

    /**
     * Exports a plan as a Cursor-compatible {@code .plan.md} file.
     *
     * @return the absolute path of the written file, or {@code null} on failure
     */
    public String exportPlan(Job job, PlanSession session, List<PlanQuestion> questions) {
        try {
            Path dir = resolveExportDir(job);
            Files.createDirectories(dir);

            String slug = slugify(job.getIntent(), PLAN_FILE_SLUG_MAX_LEN);
            long jobId = job.getId();
            String hex = String.format(Locale.ROOT, "%08x", jobId);
            String filename = "plan_" + slug + "_" + hex + ".plan.md";
            Path file = dir.resolve(filename);

            String content = formatPlanFile(job, session, questions);
            Files.writeString(file, content);

            log.info("Plan exported to {}", file);
            return file.toAbsolutePath().toString();
        } catch (IOException e) {
            log.error("Failed to export plan for job {}: {}", job.getId(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * If the job's workspace has a {@code .cursor} directory, write into
     * {@code <workspace>/.cursor/plans/}. Otherwise fall back to the
     * configured {@code plansExportDir}.
     */
    Path resolveExportDir(Job job) {
        String workspace = extractWorkspace(job);
        if (workspace != null) {
            Path cursorDir = Path.of(workspace).resolve(".cursor");
            if (Files.isDirectory(cursorDir)) {
                log.debug("Using project .cursor/plans/ in workspace {}", workspace);
                return cursorDir.resolve("plans");
            }
        }
        return resolveFallbackDir();
    }

    private Path resolveFallbackDir() {
        String raw = runnerProperties.getPlansExportDir();
        if (raw == null || raw.isBlank()) {
            raw = "~/.ai-dev-garage/plans";
        }
        if (raw.startsWith("~/")) {
            raw = System.getProperty("user.home") + raw.substring(1);
        } else if (Objects.equals(raw, "~")) {
            raw = System.getProperty("user.home");
        }
        return Path.of(raw);
    }

    // Task payload is our own JSON; Map.class is sufficient and avoids a verbose TypeReference here.
    @SuppressWarnings("unchecked")
    private String extractWorkspace(Job job) {
        String payload = job.getTaskPayloadJson();
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(payload, Map.class);
            Object ws = map.get("workspace");
            return ws instanceof String s && !s.isBlank() ? s : null;
        } catch (Exception e) {
            log.debug("Could not parse task payload for workspace: {}", e.getMessage());
            return null;
        }
    }

    private static String formatPlanFile(Job job, PlanSession session, List<PlanQuestion> questions) {
        var sb = new StringBuilder();

        var name = deriveName(job.getIntent());
        var overview = job.getIntent() == null ? "" : truncate(job.getIntent(), YAML_OVERVIEW_MAX_LEN);

        sb.append("---\n");
        sb.append("name: ").append(yamlEscape(name)).append("\n");
        sb.append("overview: ").append(yamlEscape(overview)).append("\n");
        sb.append("todos:\n");
        sb.append("  - id: build\n");
        sb.append("    content: Build this plan\n");
        sb.append("    status: pending\n");
        sb.append("source: telegram-bot\n");
        sb.append("jobId: ").append(job.getId()).append("\n");
        if (job.getCreatedAt() != null) {
            sb.append("createdAt: ").append(job.getCreatedAt()).append("\n");
        }
        sb.append("isProject: false\n");
        sb.append("---\n\n");

        sb.append("# ").append(name).append("\n\n");

        String planText = session.getPlanText();
        if (planText != null && !planText.isBlank()) {
            sb.append(planText).append("\n");
        }

        if (!questions.isEmpty()) {
            sb.append("\n## Q&A History\n");
            Map<Integer, List<PlanQuestion>> byRound = new TreeMap<>();
            for (PlanQuestion q : questions) {
                byRound.computeIfAbsent(q.getRound(), k -> new ArrayList<>()).add(q);
            }
            for (var entry : byRound.entrySet()) {
                sb.append("\n### Round ").append(entry.getKey()).append("\n\n");
                for (PlanQuestion q : entry.getValue()) {
                    sb.append("**Q").append(q.getSeq()).append("**: ").append(q.getQuestionText()).append("\n");
                    String answer = q.getAnswer() != null ? q.getAnswer() : "(unanswered)";
                    sb.append("**A**: ").append(answer).append("\n\n");
                }
            }
        }

        return sb.toString();
    }

    private static String deriveName(String intent) {
        if (intent == null || intent.isBlank()) {
            return "Untitled Plan";
        }
        String name = intent.strip();
        if (name.length() > DERIVE_NAME_MAX_LEN) {
            name = name.substring(0, DERIVE_NAME_KEEP_LEN) + ELLIPSIS;
        }
        return name;
    }

    private static String slugify(String text, int maxLen) {
        if (text == null || text.isBlank()) {
            return "untitled";
        }
        String slug = text.strip().toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
        if (slug.length() > maxLen) {
            slug = slug.substring(0, maxLen).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "untitled" : slug;
    }

    private static String yamlEscape(String s) {
        if (s == null) {
            return "\"\"";
        }
        if (s.contains(":") || s.contains("#") || s.contains("\"") || s.contains("'")
            || s.startsWith("{") || s.startsWith("[")) {
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return s;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - ELLIPSIS.length()) + ELLIPSIS;
    }
}
