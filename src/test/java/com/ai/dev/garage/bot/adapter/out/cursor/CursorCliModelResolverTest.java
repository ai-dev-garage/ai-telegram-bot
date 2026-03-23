package com.ai.dev.garage.bot.adapter.out.cursor;

import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.config.CursorCliProperties;
import com.ai.dev.garage.bot.domain.Job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CursorCliModelResolverTest {

    @Mock
    private JsonCodec jsonCodec;

    private CursorCliProperties properties;
    private CursorCliModelResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new CursorCliProperties();
        properties.setDefaultModel("auto");
        resolver = new CursorCliModelResolver(jsonCodec, properties);
    }

    @Test
    void shouldUsePayloadCliModelWhenPresent() {
        when(jsonCodec.fromJson(anyString())).thenReturn(Map.of(CursorCliModelResolver.CLI_MODEL_PAYLOAD_KEY, "from-payload"));
        var job = Job.builder().id(1L).taskPayloadJson("{}").build();

        assertThat(resolver.resolveModelForJob(job)).contains("from-payload");
    }

    @Test
    void shouldFallBackToDefaultModelWhenPayloadMissing() {
        when(jsonCodec.fromJson(anyString())).thenReturn(Map.of());
        var job = Job.builder().id(2L).taskPayloadJson("{}").build();

        assertThat(resolver.resolveModelForJob(job)).contains("auto");
    }

    @Test
    void shouldOmitWhenDefaultModelBlank() {
        properties.setDefaultModel("  ");
        when(jsonCodec.fromJson(anyString())).thenReturn(Map.of());
        var job = Job.builder().id(3L).taskPayloadJson("{}").build();

        assertThat(resolver.resolveModelForJob(job)).isEmpty();
    }
}
