package com.ai.dev.garage.bot.adapter.out.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.dev.garage.bot.application.execution.TaskExecutionContext;
import com.ai.dev.garage.bot.application.execution.TaskExecutionResult;
import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.application.support.AllowedPathValidator;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.TaskType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShellTaskExecutorTest {

    @Mock
    private JsonCodec jsonCodec;
    @Mock
    private ShellProcessRunner shellProcessRunner;
    @Mock
    private AllowedPathValidator allowedPathValidator;
    @InjectMocks
    private ShellTaskExecutor shellTaskExecutor;

    @Test
    void shouldRejectExecutionWhenCwdDisallowed() {
        when(jsonCodec.fromJson("{}")).thenReturn(Map.of("command", "pwd", "cwd", "/bad"));
        when(allowedPathValidator.validationFailureReason("/bad")).thenReturn("not allowed");
        Job job = Job.builder().id(1L).taskPayloadJson("{}").build();
        TaskExecutionContext ctx = new TaskExecutionContext(1L, (jid, line) -> { });
        TaskExecutionResult result = shellTaskExecutor.execute(job, ctx);
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("not allowed");
        verify(shellProcessRunner, never()).run(any(), any(), any(), any());
    }

    @Test
    void shouldRunProcessWhenCwdAllowed() {
        when(jsonCodec.fromJson("{}")).thenReturn(Map.of("command", "pwd", "cwd", "/ok"));
        when(allowedPathValidator.validationFailureReason("/ok")).thenReturn(null);
        when(shellProcessRunner.run(eq(1L), eq("pwd"), eq("/ok"), any())).thenReturn(
            new TaskExecutionResult(true, "ok", 0, null)
        );
        Job job = Job.builder().id(1L).taskPayloadJson("{}").build();
        TaskExecutionContext ctx = new TaskExecutionContext(1L, (jid, line) -> { });
        TaskExecutionResult result = shellTaskExecutor.execute(job, ctx);
        assertThat(result.success()).isTrue();
    }
}
