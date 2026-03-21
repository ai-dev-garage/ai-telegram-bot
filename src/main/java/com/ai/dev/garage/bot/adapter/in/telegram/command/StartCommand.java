package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
public class StartCommand implements TelegramCommand {

    private final TelegramBotClient telegramBotClient;
    private final TelegramCommandRegistry commandRegistry;

    /**
     * {@link TelegramCommandRegistry} depends on all {@link TelegramCommand} beans; {@code @Lazy} on
     * this parameter breaks the cycle (Lombok {@code @RequiredArgsConstructor} does not reliably
     * copy {@code @Lazy} onto the generated constructor).
     */
    public StartCommand(
            TelegramBotClient telegramBotClient, @Lazy TelegramCommandRegistry commandRegistry) {
        this.telegramBotClient = telegramBotClient;
        this.commandRegistry = commandRegistry;
    }

    @Override
    public List<BotCommandInfo> botCommands() {
        return List.of(new BotCommandInfo("start", "Help"));
    }

    @Override
    public boolean canHandle(String text) {
        return text.startsWith("/start");
    }

    @Override
    public void handle(TelegramCommandContext ctx) {
        telegramBotClient.sendPlain(ctx.chatId(), commandRegistry.helpText());
    }
}
