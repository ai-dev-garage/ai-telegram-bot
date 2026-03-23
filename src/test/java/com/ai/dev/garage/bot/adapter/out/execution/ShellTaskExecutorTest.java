package com.ai.dev.garage.bot.adapter.out.execution;

import com.ai.dev.garage.bot.application.execution.model.TaskExecutionContext;
import com.ai.dev.garage.bot.application.execution.model.TaskExecutionResult;
import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.application.support.AllowedPathValidator;
import com.ai.dev.garage.bot.domain.Job;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        var job = Job.builder().id(1L).taskPayloadJson("{}").build();
        var ctx = new TaskExecutionContext(1L, (jid, line) -> {
        });
        var result = shellTaskExecutor.execute(job, ctx);
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
        var job = Job.builder().id(1L).taskPayloadJson("{}").build();
        var ctx = new TaskExecutionContext(1L, (jid, line) -> {
        });
        var result = shellTaskExecutor.execute(job, ctx);
        assertThat(result.success()).isTrue();
    }
}
