package com.ai.dev.garage.bot.adapter.in.telegram.command;

import java.util.List;

public interface TelegramCommand {

    record BotCommandInfo(String command, String menuDescription) {}

    boolean canHandle(String text);

    void handle(TelegramCommandContext ctx);

    default List<BotCommandInfo> botCommands() {
        return List.of();
    }

    default List<String> helpLines() {
        return List.of();
    }
}
