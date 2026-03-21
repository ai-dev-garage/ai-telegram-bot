package com.ai.dev.garage.bot;

import com.ai.dev.garage.bot.config.ClaudeCliProperties;
import com.ai.dev.garage.bot.config.CursorCliProperties;
import com.ai.dev.garage.bot.config.RunnerProperties;
import com.ai.dev.garage.bot.config.TelegramProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
    RunnerProperties.class,
    CursorCliProperties.class,
    ClaudeCliProperties.class,
    TelegramProperties.class
})
public class RunnerApplication {
    public static void main(String[] args) {
        SpringApplication.run(RunnerApplication.class, args);
    }
}
