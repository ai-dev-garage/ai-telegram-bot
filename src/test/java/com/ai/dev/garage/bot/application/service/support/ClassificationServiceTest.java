package com.ai.dev.garage.bot.application.service.support;

import com.ai.dev.garage.bot.application.port.out.PolicyProvider;
import com.ai.dev.garage.bot.domain.ApprovalState;
import com.ai.dev.garage.bot.domain.RiskLevel;
import com.ai.dev.garage.bot.domain.TaskType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassificationServiceTest {

    @Mock
    private PolicyProvider policyProvider;

    @InjectMocks
    private ClassificationService classificationService;

    @BeforeEach
    void setUp() {
        when(policyProvider.loadPolicy()).thenReturn(
            Map.of(
                "allowlist_safe", Map.of("shell_command", List.of("git status", "echo")),
                "require_approval_patterns", Map.of("shell_command", List.of("sudo", "rm -rf"))
            )
        );
    }

    @Test
    void shouldClassifyAsLowRiskApprovedShellWhenOnAllowlist() {
        var r = classificationService.classify("git status");
        assertThat(r.taskType()).isEqualTo(TaskType.SHELL_COMMAND);
        assertThat(r.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(r.approvalState()).isEqualTo(ApprovalState.APPROVED);
    }

    @Test
    void shouldClassifyAsAgentTaskWhenConfluenceIntent() {
        var r = classificationService.classify("create a new page on confluence");
        assertThat(r.taskType()).isEqualTo(TaskType.AGENT_TASK);
        assertThat(r.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void shouldClassifyAsAgentTaskWhenExplicitAgentPrefix() {
        var r = classificationService.classify("agent brief status of the projects");
        assertThat(r.taskType()).isEqualTo(TaskType.AGENT_TASK);
        assertThat(r.taskPayload()).containsEntry("agent_or_command", "agent");
        assertThat(r.taskPayload().get("input")).isEqualTo("brief status of the projects");
        assertThat(r.approvalState()).isEqualTo(ApprovalState.APPROVED);
    }

    @Test
    void shouldClassifyAsHighRiskShellWhenMatchesRiskyPattern() {
        var r = classificationService.classify("sudo apt update");
        assertThat(r.taskType()).isEqualTo(TaskType.SHELL_COMMAND);
        assertThat(r.riskLevel()).isEqualTo(RiskLevel.HIGH);
    }
}
