package com.ai.dev.garage.bot.application.service.support;

import com.ai.dev.garage.bot.adapter.out.cursor.CursorCliModelResolver;
import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.application.support.AllowedPathValidator;
import com.ai.dev.garage.bot.domain.ApprovalState;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.ai.dev.garage.bot.domain.Requester;
import com.ai.dev.garage.bot.domain.RiskLevel;
import com.ai.dev.garage.bot.domain.TaskType;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * Validation and entity construction for {@link com.ai.dev.garage.bot.application.service.PlanSessionService#createPlan}
 * so the service method stays below PMD cyclomatic / GodClass thresholds.
 */
public final class PlanCreateSupport {

    private PlanCreateSupport() {
    }

    public static void validatePlanIntent(String intent) {
        if (intent == null || intent.isBlank()) {
            throw new IllegalArgumentException("Plan prompt (intent) must not be empty");
        }
    }

    public static void validatePlanWorkspaceIfPresent(String workspace, AllowedPathValidator allowedPathValidator) {
        if (workspace == null || workspace.isBlank()) {
            return;
        }
        String reason = allowedPathValidator.validationFailureReason(workspace.trim());
        if (reason != null) {
            throw new IllegalArgumentException(reason);
        }
    }

    public static Map<String, Object> buildPlanTaskPayload(
        String intent,
        String workspace,
        String cliModelOverride
    ) {
        Map<String, Object> payload = new HashMap<>();
        if (workspace != null && !workspace.isBlank()) {
            payload.put("workspace", workspace.trim());
        }
        payload.put("input", intent);
        if (cliModelOverride != null && !cliModelOverride.isBlank()) {
            payload.put(CursorCliModelResolver.CLI_MODEL_PAYLOAD_KEY, cliModelOverride.trim());
        }
        return payload;
    }

    public static Job buildNewPlanJobEntity(
        JsonCodec jsonCodec,
        String intent,
        Requester requester,
        Map<String, Object> payload
    ) {
        return Job.builder()
            .intent(intent)
            .requester(Requester.builder()
                .telegramUserId(requester.getTelegramUserId())
                .telegramChatId(requester.getTelegramChatId())
                .telegramUsername(requester.getTelegramUsername())
                .build())
            .taskType(TaskType.PLAN_TASK)
            .riskLevel(RiskLevel.LOW)
            .approvalState(ApprovalState.APPROVED)
            .status(JobStatus.RUNNING)
            .taskPayloadJson(jsonCodec.toJson(payload))
            .startedAt(OffsetDateTime.now(ZoneId.systemDefault()))
            .build();
    }
}
