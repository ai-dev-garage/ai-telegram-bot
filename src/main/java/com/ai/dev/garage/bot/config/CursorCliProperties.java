package com.ai.dev.garage.bot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cursor")
@Getter
@Setter
public class CursorCliProperties {
    private boolean cliInvoke = false;
    private String workspace;
    private String prompt;
    private String planPrompt = "List each question on its own line ending with '?'. " +
        "If you want to offer choices, list them as bullet points below the question.";
}
