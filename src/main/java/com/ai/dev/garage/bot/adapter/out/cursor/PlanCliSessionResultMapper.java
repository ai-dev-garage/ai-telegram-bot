package com.ai.dev.garage.bot.adapter.out.cursor;

import com.ai.dev.garage.bot.application.port.out.PlanSessionResult;
import com.ai.dev.garage.bot.application.port.out.PlanSessionResult.ParsedMessage;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * Turns {@link CliStreamParser} output plus process exit code into a {@link PlanSessionResult},
 * marking {@link PlanSessionResult#cliHealthy()} false when the round must not be stored as success.
 */
@Slf4j
public final class PlanCliSessionResultMapper {

    private PlanCliSessionResultMapper() {
    }

    public static PlanSessionResult fromStream(
        long jobId,
        String cliLabel,
        CliStreamParser.CliStreamResult streamResult,
        int exitCode,
        List<ParsedMessage> messages) {

        boolean hasQuestions = messages.stream().anyMatch(m -> !m.questions().isEmpty());
        boolean hasAssistantOutput = !streamResult.assistantMessages().isEmpty()
            || (streamResult.fullText() != null && !streamResult.fullText().isBlank());
        boolean streamReportedFailure = streamResult.resultLineSeen() && !streamResult.success();
        boolean cliOk = exitCode == 0 && !streamReportedFailure && (hasAssistantOutput || hasQuestions);

        if (cliOk) {
            return new PlanSessionResult(
                streamResult.sessionId(),
                messages,
                !hasQuestions,
                streamResult.fullText()
            );
        }

        String detail = buildFailureDetail(exitCode, streamResult, streamReportedFailure, !hasAssistantOutput);
        log.warn("{} plan CLI unhealthy for job {}: {}", cliLabel, jobId, detail);
        return new PlanSessionResult(
            streamResult.sessionId(),
            List.of(),
            false,
            "",
            false,
            detail
        );
    }

    private static String buildFailureDetail(
        int exitCode,
        CliStreamParser.CliStreamResult streamResult,
        boolean streamReportedFailure,
        boolean missingAssistantOutput) {
        String core;
        if (exitCode != 0) {
            core = "process exited with code " + exitCode;
        } else if (streamReportedFailure) {
            core = "CLI reported an error in the result event";
        } else if (missingAssistantOutput) {
            core = "CLI produced no assistant output (check PATH, CLI install, and job logs)";
        } else {
            core = "CLI run rejected (unexpected state)";
        }
        String preview = streamResult.unparseablePreview();
        if (preview != null && !preview.isBlank()) {
            return core + ". Raw output preview: " + preview;
        }
        return core;
    }
}
