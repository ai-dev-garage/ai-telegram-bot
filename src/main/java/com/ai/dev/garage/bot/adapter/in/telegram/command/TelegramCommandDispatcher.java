package com.ai.dev.garage.bot.adapter.in.telegram.command;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class TelegramCommandDispatcher {

    private final List<TelegramCommand> commands;

    public boolean dispatch(TelegramCommandContext ctx) throws Exception {
        for (TelegramCommand command : commands) {
            if (command.canHandle(ctx.text())) {
                command.handle(ctx);
                return true;
            }
        }
        return false;
    }
}
