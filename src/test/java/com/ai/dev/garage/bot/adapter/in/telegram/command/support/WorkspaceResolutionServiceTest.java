package com.ai.dev.garage.bot.adapter.in.telegram.command.support;

import com.ai.dev.garage.bot.adapter.in.telegram.NavigationStateStore;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.adapter.in.telegram.command.TelegramCommandContext;
import com.ai.dev.garage.bot.application.support.AllowedPathValidator;
import com.ai.dev.garage.bot.config.CursorCliProperties;
import com.ai.dev.garage.bot.config.RunnerProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceResolutionServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private NavigationStateStore navigationStateStore;

    @Mock
    private TelegramBotClient telegramBotClient;

    private AllowedPathValidator allowedPathValidator;
    private CursorCliProperties cursorCliProperties;
    private WorkspaceResolutionService service;

    @BeforeEach
    void setUp() {
        var runnerProperties = new RunnerProperties();
        runnerProperties.setAllowedNavigationPaths(tempDir.toString());
        allowedPathValidator = new AllowedPathValidator(runnerProperties);
        cursorCliProperties = new CursorCliProperties();
        service = new WorkspaceResolutionService(
            navigationStateStore, telegramBotClient, allowedPathValidator, cursorCliProperties);
    }

    @Test
    void shouldResolveModelAliasThenFolder() throws Exception {
        cursorCliProperties.setTelegramModelAliases(Map.of("sonnet", "cli-sonnet-id"));
        Path sub = Files.createDirectory(tempDir.resolve("myapp"));
        when(navigationStateStore.getSelectedPath(anyLong(), anyLong()))
            .thenReturn(Optional.of(tempDir.toRealPath().toString()));

        var ctx = new TelegramCommandContext(1L, 2L, "u", "/x");
        var r = service.resolveWorkspaceAndPrompt(ctx, "@sonnet @myapp fix it", "plan");

        assertThat(r).isPresent();
        assertThat(r.get().cliModel()).isEqualTo("cli-sonnet-id");
        assertThat(r.get().workspace()).isEqualTo(sub.toRealPath().toString());
        assertThat(r.get().prompt()).isEqualTo("fix it");
    }

    @Test
    void shouldTreatUnknownAtTokenAsFolder() throws Exception {
        Path sub = Files.createDirectory(tempDir.resolve("myapp"));
        when(navigationStateStore.getSelectedPath(anyLong(), anyLong()))
            .thenReturn(Optional.of(tempDir.toRealPath().toString()));

        var ctx = new TelegramCommandContext(1L, 2L, "u", "/x");
        var r = service.resolveWorkspaceAndPrompt(ctx, "@myapp hello", "plan");

        assertThat(r).isPresent();
        assertThat(r.get().cliModel()).isNull();
        assertThat(r.get().workspace()).isEqualTo(sub.toRealPath().toString());
        assertThat(r.get().prompt()).isEqualTo("hello");
    }

    @Test
    void shouldRejectWhenModelAliasHasNoPrompt() {
        cursorCliProperties.setTelegramModelAliases(Map.of("opus", "cli-opus"));

        var ctx = new TelegramCommandContext(1L, 2L, "u", "/x");
        assertThat(service.resolveWorkspaceAndPrompt(ctx, "@opus", "plan")).isEmpty();
        verify(telegramBotClient).sendPlain(eq(1L), argThat(m -> m.contains("goal")));
    }

    @Test
    void shouldRejectWhenOnlySingleAtFolderTokenWithoutPrompt() {
        var ctx = new TelegramCommandContext(1L, 2L, "u", "/x");
        assertThat(service.resolveWorkspaceAndPrompt(ctx, "@myapp", "plan")).isEmpty();
        verify(telegramBotClient).sendPlain(eq(1L), argThat(m -> m.contains("goal")));
    }

    @Test
    void shouldResolveModelAliasOnlyWithCwd() {
        cursorCliProperties.setTelegramModelAliases(Map.of("opus", "cli-opus"));
        when(navigationStateStore.getSelectedPath(anyLong(), anyLong()))
            .thenReturn(Optional.of(tempDir.toString()));

        var ctx = new TelegramCommandContext(1L, 2L, "u", "/x");
        var r = service.resolveWorkspaceAndPrompt(ctx, "@opus refactor auth", "plan");

        assertThat(r).isPresent();
        assertThat(r.get().cliModel()).isEqualTo("cli-opus");
        assertThat(r.get().workspace()).isEqualTo(tempDir.toString());
        assertThat(r.get().prompt()).isEqualTo("refactor auth");
    }
}
