package com.ai.dev.garage.bot.application.service.model;

import java.util.Map;

public final class JobResultPayload {

    private JobResultPayload() {
    }

    public static Map<String, Object> result(String summary, int exitCode, String error) {
        if (error == null || error.isBlank()) {
            return Map.of("summary", summary, "exit_code", exitCode);
        }
        return Map.of("summary", summary, "exit_code", exitCode, "error", error);
    }
}
