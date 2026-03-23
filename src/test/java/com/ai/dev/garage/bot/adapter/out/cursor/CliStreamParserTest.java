package com.ai.dev.garage.bot.adapter.out.cursor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CliStreamParserTest {

    private CliStreamParser parser;

    @BeforeEach
    void setUp() {
        parser = new CliStreamParser(new ObjectMapper());
    }

    @Test
    void parsesCursorStyleAssistantAndResult() {
        String ndjson = """
            {"type":"assistant","session_id":"sess-cursor","message":{"content":[{"type":"text","text":"What stack?"}]}}
            {"type":"result","subtype":"success","session_id":"sess-cursor","duration_ms":42}
            """;
        var result = parser.parse(stream(ndjson));
        assertThat(result.sessionId()).isEqualTo("sess-cursor");
        assertThat(result.assistantMessages()).containsExactly("What stack?");
        assertThat(result.fullText()).isEqualTo("What stack?");
        assertThat(result.success()).isTrue();
        assertThat(result.durationMs()).isEqualTo(42);
    }

    @Test
    void parsesClaudeStreamEventContentBlockDeltaAndResult() {
        // Shape from Agent SDK streaming docs + Claude Code headless stream-json.
        String ndjson = """
            {"type":"stream_event","session_id":"sess-claude","event":{"type":"content_block_delta","delta":{"type":"text_delta","text":"Which "}}}
            {"type":"stream_event","session_id":"sess-claude","event":{"type":"content_block_delta","delta":{"type":"text_delta","text":"region?"}}}
            {"type":"result","subtype":"success","session_id":"sess-claude","duration_ms":99}
            """;
        var result = parser.parse(stream(ndjson));
        assertThat(result.sessionId()).isEqualTo("sess-claude");
        assertThat(result.assistantMessages()).containsExactly("Which region?");
        assertThat(result.fullText()).isEqualTo("Which region?");
        assertThat(result.success()).isTrue();
    }

    @Test
    void parsesHeadlessDocStyleStreamEventWithNestedDelta() {
        // https://code.claude.com/docs/en/headless — jq selects .event.delta.text
        String ndjson = """
            {"type":"stream_event","session_id":"s2","event":{"delta":{"type":"text_delta","text":"Hello"}}}
            {"type":"result","subtype":"success","session_id":"s2"}
            """;
        var result = parser.parse(stream(ndjson));
        assertThat(result.assistantMessages()).containsExactly("Hello");
        assertThat(result.fullText()).isEqualTo("Hello");
        assertThat(result.success()).isTrue();
    }

    @Test
    void flushesStreamBufferBeforeAssistantMessage() {
        String ndjson = """
            {"type":"stream_event","session_id":"x","event":{"delta":{"type":"text_delta","text":"Part1"}}}
            {"type":"assistant","session_id":"x","message":{"content":[{"type":"text","text":"Part2"}]}}
            {"type":"result","subtype":"success","session_id":"x"}
            """;
        var result = parser.parse(stream(ndjson));
        assertThat(result.assistantMessages()).containsExactly("Part1", "Part2");
        assertThat(result.fullText()).isEqualTo("Part1Part2");
    }

    private static ByteArrayInputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
}
