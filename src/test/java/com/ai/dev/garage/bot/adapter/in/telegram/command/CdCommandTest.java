package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.telegram.NavigationStateStore;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.application.support.AllowedPathValidator;
import com.ai.dev.garage.bot.config.RunnerProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CdCommandTest {

    @TempDir
    Path tempDir;

    @Mock
    private TelegramBotClient telegramBotClient;

    private NavigationStateStore navigationStateStore;
    private AllowedPathValidator allowedPathValidator;
    private CdCommand cdCommand;

    @BeforeEach
    void setUp() {
        navigationStateStore = new NavigationStateStore();
        var props = new RunnerProperties();
        props.setAllowedNavigationPaths(tempDir.toString());
        allowedPathValidator = new AllowedPathValidator(props);
        cdCommand = new CdCommand(telegramBotClient, navigationStateStore, allowedPathValidator);
    }

    @Test
    void shouldHandleCdCommand() {
        assertThat(cdCommand.canHandle("/cd")).isTrue();
        assertThat(cdCommand.canHandle("/cd foo")).isTrue();
        assertThat(cdCommand.canHandle("/cancel")).isFalse();
    }

    @Test
    void shouldReplyNoCwdWhenNoneSelected() {
        cdCommand.handle(new TelegramCommandContext(1L, 2L, "u", "/cd sub"));

        verify(telegramBotClient).sendPlain(eq(1L), argThat(msg -> msg.contains("/nav")));
    }

    @Test
    void shouldEnterChildDirectory() throws Exception {
        Path child = Files.createDirectory(tempDir.resolve("child"));
        navigationStateStore.setSelectedPath(1L, 2L, tempDir.toString());

        cdCommand.handle(new TelegramCommandContext(1L, 2L, "u", "/cd child"));

        assertThat(navigationStateStore.getSelectedPath(1L, 2L))
            .isPresent()
            .hasValue(child.toRealPath().toString());
    }

    @Test
    void shouldGoUpWithDotDot() throws Exception {
        Path child = Files.createDirectory(tempDir.resolve("child"));
        navigationStateStore.setSelectedPath(1L, 2L, child.toRealPath().toString());

        cdCommand.handle(new TelegramCommandContext(1L, 2L, "u", "/cd .."));

        assertThat(navigationStateStore.getSelectedPath(1L, 2L))
            .isPresent()
            .hasValue(tempDir.toRealPath().toString());
    }

    @Test
    void shouldBlockGoingAboveAllowlistRoot() {
        navigationStateStore.setSelectedPath(1L, 2L, tempDir.toString());

        String result = cdCommand.changeDirectory(tempDir.toString(), "..");

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullForNonExistentChild() {
        String result = cdCommand.changeDirectory(tempDir.toString(), "nonexistent");

        assertThat(result).isNull();
    }

    @Test
    void shouldShowUsageWhenNoArgument() {
        cdCommand.handle(new TelegramCommandContext(1L, 2L, "u", "/cd"));

        verify(telegramBotClient).sendPlain(eq(1L), argThat(msg -> msg.contains("Usage")));
    }
}
