package com.ai.dev.garage.bot.domain;

import java.util.Map;

public record ClassificationResult(
    TaskType taskType,
    Map<String, Object> taskPayload,
    RiskLevel riskLevel,
    ApprovalState approvalState
) {
}
