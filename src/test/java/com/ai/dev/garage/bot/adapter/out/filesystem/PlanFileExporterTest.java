package com.ai.dev.garage.bot.adapter.out.filesystem;

import com.ai.dev.garage.bot.config.RunnerProperties;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.PlanQuestion;
import com.ai.dev.garage.bot.domain.PlanSession;
import com.ai.dev.garage.bot.domain.PlanState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PlanFileExporterTest {

    @TempDir
    Path tempDir;

    private RunnerProperties runnerProperties;
    private PlanFileExporter exporter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        runnerProperties = new RunnerProperties();
        runnerProperties.setPlansExportDir(tempDir.toString());
        exporter = new PlanFileExporter(runnerProperties, objectMapper);
    }

    private static Job testJob(long id, String intent) {
        return Job.builder()
            .id(id)
            .intent(intent)
            .createdAt(OffsetDateTime.parse("2026-01-15T10:30:00Z"))
            .build();
    }

    private static Job jobWithWorkspace(long id, String intent, String workspace) {
        Job job = testJob(id, intent);
        job.setTaskPayloadJson("{\"workspace\":\"" + workspace + "\",\"input\":\"" + intent + "\"}");
        return job;
    }

    private static PlanSession testSession(PlanState state, String planText) {
        return PlanSession.builder()
            .id(1L)
            .jobId(42L)
            .state(state)
            .planText(planText)
            .round(2)
            .build();
    }

    private static PlanQuestion testQuestion(PlanSession session, int round, int seq, String text, String answer) {
        return PlanQuestion.builder()
            .planSession(session)
            .round(round)
            .seq(seq)
            .questionText(text)
            .answer(answer)
            .build();
    }

    @Nested
    class ExportPlan {

        @Test
        void shouldWriteFileWithFrontmatterAndPlanText() throws IOException {
            Job job = testJob(42L, "Build a REST API for user management");
            PlanSession session = testSession(PlanState.PLAN_READY, "## Architecture\nUse Spring Boot with PostgreSQL.");

            String path = exporter.exportPlan(job, session, List.of());

            assertThat(path).isNotNull();
            String content = Files.readString(Path.of(path));
            assertThat(content).startsWith("---\n");
            assertThat(content).contains("name: Build a REST API for user management");
            assertThat(content).contains("source: telegram-bot");
            assertThat(content).contains("jobId: 42");
            assertThat(content).contains("isProject: false");
            assertThat(content).contains("## Architecture\nUse Spring Boot with PostgreSQL.");
        }

        @Test
        void shouldIncludeQaHistoryGroupedByRound() throws IOException {
            Job job = testJob(42L, "Build auth");
            PlanSession session = testSession(PlanState.PLAN_READY, "Plan text here.");
            var questions = List.of(
                testQuestion(session, 1, 1, "What auth provider?", "OAuth2"),
                testQuestion(session, 1, 2, "Multi-tenant?", "Yes"),
                testQuestion(session, 2, 1, "Support MFA?", "TOTP only")
            );

            var path = exporter.exportPlan(job, session, questions);

            var content = Files.readString(Path.of(path));
            assertThat(content).contains("## Q&A History");
            assertThat(content).contains("### Round 1");
            assertThat(content).contains("**Q1**: What auth provider?");
            assertThat(content).contains("**A**: OAuth2");
            assertThat(content).contains("**Q2**: Multi-tenant?");
            assertThat(content).contains("### Round 2");
            assertThat(content).contains("**Q1**: Support MFA?");
            assertThat(content).contains("**A**: TOTP only");
        }

        @Test
        void shouldHandleUnansweredQuestions() throws IOException {
            Job job = testJob(7L, "Plan something");
            PlanSession session = testSession(PlanState.PAUSED, "Draft plan.");
            var questions = List.of(
                testQuestion(session, 1, 1, "Open question?", null)
            );

            var path = exporter.exportPlan(job, session, questions);

            var content = Files.readString(Path.of(path));
            assertThat(content).contains("**A**: (unanswered)");
        }

        @Test
        void shouldGenerateSlugBasedFilename() {
            Job job = testJob(99L, "Build a REST API for user management");
            PlanSession session = testSession(PlanState.PLAN_READY, "Plan.");

            String path = exporter.exportPlan(job, session, List.of());

            assertThat(path).isNotNull();
            String filename = Optional.ofNullable(Path.of(path).getFileName())
                .map(Path::toString)
                .orElseThrow(() -> new AssertionError("expected file name segment"));
            assertThat(filename).startsWith("plan_build-a-rest-api-for-user-management");
            assertThat(filename).endsWith(".plan.md");
        }

        @Test
        void shouldCreateDirectoryWhenMissing() {
            Path nested = tempDir.resolve("deeply/nested/dir");
            runnerProperties.setPlansExportDir(nested.toString());

            Job job = testJob(1L, "Test");
            PlanSession session = testSession(PlanState.PLAN_READY, "Plan.");

            String path = exporter.exportPlan(job, session, List.of());

            assertThat(path).isNotNull();
            assertThat(Path.of(path)).exists();
        }

        @Test
        void shouldEscapeSpecialCharsInYamlName() throws IOException {
            Job job = testJob(1L, "Fix: authentication #issues for \"admin\" users");
            PlanSession session = testSession(PlanState.PLAN_READY, "Plan.");

            String path = exporter.exportPlan(job, session, List.of());

            String content = Files.readString(Path.of(path));
            assertThat(content).contains("name: \"Fix: authentication #issues for \\\"admin\\\" users\"");
        }

        @Test
        void shouldOmitQaHistorySectionWhenNoQuestions() throws IOException {
            Job job = testJob(1L, "Simple plan");
            PlanSession session = testSession(PlanState.PLAN_READY, "Do the thing.");

            String path = exporter.exportPlan(job, session, List.of());

            String content = Files.readString(Path.of(path));
            assertThat(content).doesNotContain("## Q&A History");
        }
    }

    @Nested
    class ResolveExportDir {

        @Test
        void shouldUseProjectCursorPlansWhenCursorDirExists() throws IOException {
            Path projectDir = tempDir.resolve("my-project");
            Files.createDirectories(projectDir.resolve(".cursor"));

            Job job = jobWithWorkspace(1L, "Plan", projectDir.toString());

            Path resolved = exporter.resolveExportDir(job);

            assertThat(resolved).isEqualTo(projectDir.resolve(".cursor/plans"));
        }

        @Test
        void shouldFallBackWhenWorkspaceHasNoCursorDir() {
            Path projectDir = tempDir.resolve("bare-project");

            Job job = jobWithWorkspace(1L, "Plan", projectDir.toString());

            Path resolved = exporter.resolveExportDir(job);

            assertThat(resolved).isEqualTo(Path.of(tempDir.toString()));
        }

        @Test
        void shouldFallBackWhenNoWorkspaceInPayload() {
            Job job = testJob(1L, "Plan");

            Path resolved = exporter.resolveExportDir(job);

            assertThat(resolved).isEqualTo(Path.of(tempDir.toString()));
        }

        @Test
        void shouldFallBackWhenPayloadIsNull() {
            Job job = testJob(1L, "Plan");
            job.setTaskPayloadJson(null);

            Path resolved = exporter.resolveExportDir(job);

            assertThat(resolved).isEqualTo(Path.of(tempDir.toString()));
        }

        @Test
        void shouldWriteToProjectCursorPlansOnExport() throws IOException {
            Path projectDir = tempDir.resolve("cursor-project");
            Files.createDirectories(projectDir.resolve(".cursor"));

            Job job = jobWithWorkspace(42L, "Build auth", projectDir.toString());
            PlanSession session = testSession(PlanState.PLAN_READY, "Plan text.");

            String path = exporter.exportPlan(job, session, List.of());

            assertThat(path).isNotNull();
            assertThat(Path.of(path).getParent()).isEqualTo(projectDir.resolve(".cursor/plans"));
            assertThat(Path.of(path)).exists();
        }
    }
}
