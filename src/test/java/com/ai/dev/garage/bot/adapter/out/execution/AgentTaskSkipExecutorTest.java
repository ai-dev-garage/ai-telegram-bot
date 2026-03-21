package com.ai.dev.garage.bot.adapter.out.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.dev.garage.bot.application.execution.TaskExecutionContext;
import com.ai.dev.garage.bot.application.execution.TaskExecutionResult;
import com.ai.dev.garage.bot.application.port.out.JobLogAppender;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.TaskType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentTaskSkipExecutorTest {

    private final AgentTaskSkipExecutor executor = new AgentTaskSkipExecutor();

    @Mock
    private JobLogAppender logAppender;

    @Test
    void shouldReturnTrueWhenSupportsAgentTask() {
        assertThat(executor.supports(TaskType.AGENT_TASK)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = TaskType.class, mode = Mode.EXCLUDE, names = "AGENT_TASK")
    void shouldReturnFalseWhenSupportsNonAgentTaskType(TaskType taskType) {
        assertThat(executor.supports(taskType)).isFalse();
    }

    @Test
    void shouldReturnFailureWhenExecuteInvoked() {
        Job job = Job.builder().id(7L).build();
        TaskExecutionContext ctx = new TaskExecutionContext(7L, logAppender);

        TaskExecutionResult result = executor.execute(job, ctx);

        assertThat(result.success()).isFalse();
        assertThat(result.exitCode()).isEqualTo(-1);
        assertThat(result.error()).contains("Agent task should be handled by Cursor flow");
    }
}
