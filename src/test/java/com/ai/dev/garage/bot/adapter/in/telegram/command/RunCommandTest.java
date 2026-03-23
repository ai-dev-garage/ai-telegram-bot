package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.rest.JobResponseMapper;
import com.ai.dev.garage.bot.adapter.in.rest.dto.JobResponse;
import com.ai.dev.garage.bot.adapter.in.telegram.NavigationStateStore;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.application.port.in.JobManagement;
import com.ai.dev.garage.bot.config.RunnerProperties;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.Requester;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunCommandTest {

    @Mock
    private JobManagement jobManagement;
    @Mock
    private JobResponseMapper jobResponseMapper;
    @Mock
    private TelegramBotClient telegramBotClient;

    private NavigationStateStore navigationStateStore;
    private RunCommand runCommand;
    private RunnerProperties runnerProperties;

    @BeforeEach
    void setUp() {
        navigationStateStore = new NavigationStateStore();
        runnerProperties = new RunnerProperties();
        runnerProperties.setAgentRuntime("cursor");
        runCommand = new RunCommand(
            jobManagement, jobResponseMapper, telegramBotClient, navigationStateStore, runnerProperties);
    }

    @Test
    void shouldSendClassifyingMessageWithCursorHintWhenAgentRuntimeIsCursor() {
        Job job = mock(Job.class);
        when(jobManagement.createJob(anyString(), any(Requester.class), nullable(String.class))).thenReturn(job);
        when(jobResponseMapper.toResponse(any(Job.class))).thenReturn(JobResponse.builder()
            .jobId("7")
            .status("queued")
            .taskType("shell")
            .build());

        runCommand.handle(new TelegramCommandContext(1L, 2L, "u", "/run echo hi"));

        verify(telegramBotClient).sendPlain(eq(1L), argThat(msg ->
            msg.contains("Job #7 received. Classifying")
                && msg.contains("Cursor")
                && msg.contains("Process pending agent task")));
    }

    @Test
    void shouldSendClaudeHintWhenAgentRuntimeIsClaude() {
        runnerProperties.setAgentRuntime("claude");
        Job job = mock(Job.class);
        when(jobManagement.createJob(anyString(), any(Requester.class), nullable(String.class))).thenReturn(job);
        when(jobResponseMapper.toResponse(any(Job.class))).thenReturn(JobResponse.builder()
            .jobId("3")
            .status("queued")
            .taskType("shell")
            .build());

        runCommand.handle(new TelegramCommandContext(1L, 2L, "u", "/run task"));

        verify(telegramBotClient).sendPlain(eq(1L), argThat(msg ->
            msg.contains("Claude Code") && msg.contains("Process pending agent task")));
    }
}
