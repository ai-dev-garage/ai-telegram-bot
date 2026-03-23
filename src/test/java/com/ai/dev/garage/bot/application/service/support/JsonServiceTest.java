package com.ai.dev.garage.bot.application.service.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonServiceTest {

    private JsonService jsonService;

    @BeforeEach
    void setUp() {
        jsonService = new JsonService(new ObjectMapper());
    }

    @Test
    void shouldRoundTripMapWhenToJsonAndFromJson() {
        var original = Map.of("k", "v", "n", 1);
        String json = jsonService.toJson(original);
        assertThat(jsonService.fromJson(json)).containsEntry("k", "v").containsEntry("n", 1);
    }

    @Test
    void shouldReturnEmptyMapWhenFromJsonBlankOrNull() {
        assertThat(jsonService.fromJson("")).isEmpty();
        assertThat(jsonService.fromJson(null)).isEmpty();
    }

    @Test
    void shouldSerializeEmptyJsonObjectWhenMapEmpty() {
        assertThat(jsonService.toJson(Map.of())).isEqualTo("{}");
    }
}
