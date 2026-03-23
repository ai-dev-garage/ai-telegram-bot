package com.ai.dev.garage.bot.adapter.in.telegram.command;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
public class TelegramCommandDispatcher {

    private final List<TelegramCommand> commandsInOrder;

    public TelegramCommandDispatcher(List<TelegramCommand> commands) {
        List<TelegramCommand> sorted = new ArrayList<>(commands);
        sorted.sort(AnnotationAwareOrderComparator.INSTANCE);
        this.commandsInOrder = List.copyOf(sorted);
    }

    public boolean dispatch(TelegramCommandContext ctx) {
        for (TelegramCommand command : commandsInOrder) {
            if (command.canHandle(ctx.text())) {
                command.handle(ctx);
                return true;
            }
        }
        return false;
    }
}
