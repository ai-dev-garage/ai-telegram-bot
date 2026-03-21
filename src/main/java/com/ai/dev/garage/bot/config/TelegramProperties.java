package com.ai.dev.garage.bot.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.telegram")
@Getter
@Setter
public class TelegramProperties {
    private boolean enabled = false;
    private String token;
    private String allowedUserIds;
    private long pollingIntervalMs = 2000;

    public Set<Long> parsedAllowedUserIds() {
        if (allowedUserIds == null || allowedUserIds.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(allowedUserIds.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .map(Long::valueOf)
            .collect(Collectors.toSet());
    }
}
