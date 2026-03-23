package com.ai.dev.garage.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = "app.cursor")
@Getter
@Setter
public class CursorCliProperties {
    private boolean cliInvoke;
    private String workspace;
    private String prompt;
    private String planPrompt = "List each question on its own line ending with '?'. " +
        "If you want to offer choices, list them as bullet points below the question.";
}
