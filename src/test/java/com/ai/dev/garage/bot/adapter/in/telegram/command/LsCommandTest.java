package com.ai.dev.garage.bot.adapter.in.telegram.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.ai.dev.garage.bot.adapter.in.telegram.NavigationStateStore;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.application.support.AllowedPathValidator;
import com.ai.dev.garage.bot.config.RunnerProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class LsCommandTest {

    @TempDir
    Path tempDir;

    @Mock
    private TelegramBotClient telegramBotClient;

    private NavigationStateStore navigationStateStore;
    private AllowedPathValidator allowedPathValidator;
    private LsCommand lsCommand;

    @BeforeEach
    void setUp() {
        navigationStateStore = new NavigationStateStore();
        RunnerProperties props = new RunnerProperties();
        props.setAllowedNavigationPaths(tempDir.toString());
        allowedPathValidator = new AllowedPathValidator(props);
        lsCommand = new LsCommand(telegramBotClient, navigationStateStore, allowedPathValidator);
    }

    @Test
    void shouldHandleLsCommand() {
        assertThat(lsCommand.canHandle("/ls")).isTrue();
        assertThat(lsCommand.canHandle("/ls foo")).isTrue();
        assertThat(lsCommand.canHandle("/status")).isFalse();
    }

    @Test
    void shouldReplyNoCwdWhenNoneSelected() {
        lsCommand.handle(new TelegramCommandContext(1L, 2L, "u", "/ls"));

        verify(telegramBotClient).sendPlain(eq(1L), argThat(msg -> msg.contains("/nav")));
    }

    @Test
    void shouldShowSubdirectoriesAsButtons() throws Exception {
        Path sub1 = Files.createDirectory(tempDir.resolve("alpha"));
        Path sub2 = Files.createDirectory(tempDir.resolve("beta"));
        Files.createFile(tempDir.resolve("file.txt"));
        Files.createDirectory(tempDir.resolve(".hidden"));

        navigationStateStore.setSelectedPath(1L, 2L, tempDir.toString());

        lsCommand.handle(new TelegramCommandContext(1L, 2L, "u", "/ls"));

        verify(telegramBotClient).sendWithInlineKeyboard(eq(1L), any(String.class),
            argThat((List<List<Map<String, String>>> kb) -> {
                List<String> labels = kb.stream()
                    .flatMap(List::stream)
                    .map(m -> m.get("text"))
                    .toList();
                return labels.contains("alpha") && labels.contains("beta") && !labels.contains("file.txt")
                    && !labels.contains(".hidden");
            }));
    }

    @Test
    void shouldShowNoneWhenNoSubdirectories() throws Exception {
        Files.createFile(tempDir.resolve("only-a-file.txt"));
        navigationStateStore.setSelectedPath(1L, 2L, tempDir.toString());

        lsCommand.handle(new TelegramCommandContext(1L, 2L, "u", "/ls"));

        verify(telegramBotClient).sendPlain(eq(1L), argThat(msg -> msg.contains("(none)")));
    }
}
