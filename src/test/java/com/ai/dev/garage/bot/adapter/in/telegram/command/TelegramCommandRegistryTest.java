package com.ai.dev.garage.bot.adapter.in.telegram.command;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramCommandRegistryTest {

    @Test
    void shouldIncludeModelsCommandInSetMyCommandsPayload() {
        TelegramCommand models = new ModelsOnlyStub();
        TelegramCommand other = new OtherStub();
        var registry = new TelegramCommandRegistry(List.of(other, models));

        assertThat(registry.botApiCommands())
            .extracting(m -> m.get("command"))
            .contains("models");
    }

    @Test
    void shouldOrderBotMenuBySpringOrderAnnotation() {
        var registry = new TelegramCommandRegistry(List.of(new ZebraMenuStub(), new AlphaMenuStub()));

        assertThat(registry.botApiCommands())
            .extracting(m -> m.get("command"))
            .containsExactly("alpha", "zebra");
    }

    /** Minimal stand-in; only {@link #botCommands()} is used for menu payload. */
    private static final class ModelsOnlyStub implements TelegramCommand {
        @Override
        public boolean canHandle(String text) {
            return false;
        }

        @Override
        public void handle(TelegramCommandContext ctx) {
        }

        @Override
        public List<BotCommandInfo> botCommands() {
            return List.of(new BotCommandInfo("models", "List models"));
        }
    }

    private static final class OtherStub implements TelegramCommand {
        @Override
        public boolean canHandle(String text) {
            return false;
        }

        @Override
        public void handle(TelegramCommandContext ctx) {
        }

        @Override
        public List<BotCommandInfo> botCommands() {
            return List.of(new BotCommandInfo("other", "Other"));
        }
    }

    @Order(10)
    private static final class AlphaMenuStub implements TelegramCommand {
        @Override
        public boolean canHandle(String text) {
            return false;
        }

        @Override
        public void handle(TelegramCommandContext ctx) {
        }

        @Override
        public List<BotCommandInfo> botCommands() {
            return List.of(new BotCommandInfo("alpha", "a"));
        }
    }

    @Order(100)
    private static final class ZebraMenuStub implements TelegramCommand {
        @Override
        public boolean canHandle(String text) {
            return false;
        }

        @Override
        public void handle(TelegramCommandContext ctx) {
        }

        @Override
        public List<BotCommandInfo> botCommands() {
            return List.of(new BotCommandInfo("zebra", "z"));
        }
    }
}
