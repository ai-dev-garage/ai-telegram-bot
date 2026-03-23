package com.ai.dev.garage.bot.adapter.out.cursor;

import com.ai.dev.garage.bot.application.port.out.PlanSessionResult;
import com.ai.dev.garage.bot.application.port.out.PlanSessionResult.ParsedMessage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanCliSessionResultMapperTest {

    @Test
    void shouldMarkUnhealthyWhenExitNonZeroAndNoOutput() {
        var stream = new CliStreamParser.CliStreamResult(
            null, List.of(), "", false, false, 0, "command not found");
        PlanSessionResult out = PlanCliSessionResultMapper.fromStream(1L, "Cursor", stream, 127, List.of());

        assertThat(out.cliHealthy()).isFalse();
        assertThat(out.failureDetail()).contains("exited with code 127");
        assertThat(out.failureDetail()).contains("command not found");
    }

    @Test
    void shouldMarkUnhealthyWhenResultEventReportsError() {
        var stream = new CliStreamParser.CliStreamResult(
            "sid", List.of("x"), "x", true, false, 1, "");
        PlanSessionResult out = PlanCliSessionResultMapper.fromStream(
            2L, "Claude", stream, 0, List.of(new ParsedMessage("x", List.of())));

        assertThat(out.cliHealthy()).isFalse();
        assertThat(out.failureDetail()).contains("result event");
    }

    @Test
    void shouldReturnHealthyWhenAssistantTextPresentWithoutResultLine() {
        var stream = new CliStreamParser.CliStreamResult(
            null, List.of("hello"), "hello", false, false, 0, "");
        PlanSessionResult out = PlanCliSessionResultMapper.fromStream(
            3L, "X", stream, 0, List.of(new ParsedMessage("hello", List.of())));

        assertThat(out.cliHealthy()).isTrue();
        assertThat(out.completed()).isTrue();
        assertThat(out.fullText()).isEqualTo("hello");
    }
}
