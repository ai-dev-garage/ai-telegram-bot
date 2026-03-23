package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.rest.JobResponseMapper;
import com.ai.dev.garage.bot.adapter.in.rest.dto.JobResponse;
import com.ai.dev.garage.bot.adapter.in.telegram.NavigationStateStore;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.adapter.in.telegram.command.support.WorkspaceResolutionService;
import com.ai.dev.garage.bot.application.port.in.JobManagement;
import com.ai.dev.garage.bot.application.support.AllowedPathValidator;
import com.ai.dev.garage.bot.config.CursorCliProperties;
import com.ai.dev.garage.bot.config.RunnerProperties;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.Requester;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentCommandTest {

    @TempDir
    Path tempDir;

    @Mock
    private JobManagement jobManagement;

    @Mock
    private JobResponseMapper jobResponseMapper;

    @Mock
    private TelegramBotClient telegramBotClient;

    private NavigationStateStore navigationStateStore;
    private RunnerProperties runnerProperties;

    private AgentCommand agentCommand;

    @BeforeEach
    void setUp() {
        navigationStateStore = new NavigationStateStore();
        runnerProperties = new RunnerProperties();
        runnerProperties.setAllowedNavigationPaths(tempDir.toString());
        var allowedPathValidator = new AllowedPathValidator(runnerProperties);
        var cursorCliProperties = new CursorCliProperties();
        var workspaceResolutionService = new WorkspaceResolutionService(
            navigationStateStore, telegramBotClient, allowedPathValidator, cursorCliProperties);
        agentCommand = new AgentCommand(
            jobManagement, jobResponseMapper, telegramBotClient, workspaceResolutionService, runnerProperties
        );
    }

    @Test
    void shouldCallCreateJobWithCwdWhenPromptOnly() {
        navigationStateStore.setSelectedPath(10L, 20L, "/nav/selected");
        var job = Job.builder().id(2L).build();
        when(jobManagement.createJob(eq("agent status"), any(Requester.class), eq("/nav/selected"), isNull()))
            .thenReturn(job);
        when(jobResponseMapper.toResponse(job)).thenReturn(
            JobResponse.builder().jobId("2").status("queued").build()
        );

        agentCommand.handle(new TelegramCommandContext(10L, 20L, "u", "/agent status"));

        verify(jobManagement).createJob(eq("agent status"), any(Requester.class), eq("/nav/selected"), isNull());
    }

    @Test
    void shouldTreatMultiWordInputAsFullPrompt() {
        navigationStateStore.setSelectedPath(10L, 20L, "/nav/selected");
        var job = Job.builder().id(1L).build();
        when(jobManagement.createJob(
            eq("agent please summarize dependencies"), any(Requester.class), eq("/nav/selected"), isNull()))
            .thenReturn(job);
        when(jobResponseMapper.toResponse(job)).thenReturn(
            JobResponse.builder().jobId("1").status("queued").build()
        );

        agentCommand.handle(new TelegramCommandContext(10L, 20L, "u", "/agent please summarize dependencies"));

        verify(jobManagement).createJob(
            eq("agent please summarize dependencies"), any(Requester.class), eq("/nav/selected"), isNull());
    }

    @Test
    void shouldResolveAtFolderRelativeToCwd() throws Exception {
        Path subProject = Files.createDirectory(tempDir.resolve("myapp"));
        navigationStateStore.setSelectedPath(10L, 20L, tempDir.toRealPath().toString());

        var job = Job.builder().id(3L).build();
        when(jobManagement.createJob(
            eq("agent do something"),
            any(Requester.class),
            eq(subProject.toRealPath().toString()),
            isNull())
        ).thenReturn(job);
        when(jobResponseMapper.toResponse(job)).thenReturn(
            JobResponse.builder().jobId("3").status("queued").build()
        );

        agentCommand.handle(new TelegramCommandContext(10L, 20L, "u", "/agent @myapp do something"));

        verify(jobManagement).createJob(
            eq("agent do something"),
            any(Requester.class),
            eq(subProject.toRealPath().toString()),
            isNull()
        );
    }

    @Test
    void shouldReplyErrorWhenAtFolderNotFound() {
        navigationStateStore.setSelectedPath(10L, 20L, tempDir.toString());

        agentCommand.handle(new TelegramCommandContext(10L, 20L, "u", "/agent @nonexistent do stuff"));

        verify(telegramBotClient).sendPlain(eq(10L), argThat(msg -> msg.contains("not found")));
    }

    @Test
    void shouldReplyErrorWhenAtFolderButNoCwd() {
        agentCommand.handle(new TelegramCommandContext(10L, 20L, "u", "/agent @myapp do stuff"));

        verify(telegramBotClient).sendPlain(eq(10L), argThat(msg -> msg.contains("/nav")));
    }

    @Test
    void shouldReplyErrorWhenNoCwdSelected() {
        agentCommand.handle(new TelegramCommandContext(10L, 20L, "u", "/agent do something"));

        verify(telegramBotClient).sendPlain(eq(10L), argThat(msg -> msg.contains("/nav")));
    }

    @Test
    void shouldPassCliModelWhenFirstTokenMatchesTelegramAlias() {
        navigationStateStore.setSelectedPath(10L, 20L, "/nav/selected");
        var cursorProps = new CursorCliProperties();
        cursorProps.getTelegramModelAliases().put("sonnet", "cli-sonnet-x");
        var allowedPathValidator = new AllowedPathValidator(runnerProperties);
        var workspaceResolutionService = new WorkspaceResolutionService(
            navigationStateStore, telegramBotClient, allowedPathValidator, cursorProps);
        var cmd = new AgentCommand(
            jobManagement, jobResponseMapper, telegramBotClient, workspaceResolutionService, runnerProperties);

        var job = Job.builder().id(9L).build();
        when(jobManagement.createJob(
            eq("agent do work"), any(Requester.class), eq("/nav/selected"), eq("cli-sonnet-x")))
            .thenReturn(job);
        when(jobResponseMapper.toResponse(job)).thenReturn(
            JobResponse.builder().jobId("9").status("queued").build()
        );

        cmd.handle(new TelegramCommandContext(10L, 20L, "u", "/agent @sonnet do work"));

        verify(jobManagement).createJob(
            eq("agent do work"), any(Requester.class), eq("/nav/selected"), eq("cli-sonnet-x"));
    }
}
