package com.ai.dev.garage.bot.adapter.out.cursor;

import com.ai.dev.garage.bot.adapter.out.cursor.support.CliStreamParseState;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Parses NDJSON (stream-json) output from Cursor Agent CLI and Claude Code {@code -p} mode.
 * <p>
 * Supported shapes:
 * <ul>
 *   <li>Cursor-style: {@code type} {@code assistant} with {@code message.content[]} text blocks;
 *     {@code type} {@code result} with optional {@code subtype}, {@code session_id}, {@code duration_ms}.</li>
 *   <li>Claude Code with {@code --include-partial-messages}: {@code type} {@code stream_event} and
 *     {@code content_block_delta} / {@code text_delta} per
 *     <a href="https://platform.claude.com/docs/en/agent-sdk/streaming-output">Agent SDK streaming</a>
 *     (fixture-tested; CLI version may vary).</li>
 * </ul>
 *
 * <p>PMD {@code GodClass}: single class holds the NDJSON dispatch table; splitting would scatter the format contract.
 */
@SuppressWarnings("PMD.GodClass")
@Slf4j
@Component
@RequiredArgsConstructor
public class CliStreamParser {

    private final ObjectMapper objectMapper;

    public CliStreamResult parse(InputStream inputStream) {
        var state = new CliStreamParseState();

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while (line != null) {
                if (line.isBlank()) {
                    line = reader.readLine();
                    continue;
                }
                try {
                    JsonNode event = objectMapper.readTree(line);
                    captureSessionIdFromEvent(event, state);
                    String type = textOrNull(event, "type");
                    dispatchByType(type, event, state);
                } catch (Exception e) {
                    state.appendUnparseableLine(line);
                    log.debug("Skipping unparseable stream-json line: {}", line, e);
                }
                line = reader.readLine();
            }
        } catch (IOException e) {
            log.error("Error reading CLI stream: {}", e.getMessage(), e);
        }

        flushStreamDeltaBuffer(state.getStreamDeltaBuffer(), state.getAssistantMessages());

        return new CliStreamResult(
            state.getSessionId(),
            state.getAssistantMessages(),
            state.getFullText().toString(),
            state.isResultLineSeen(),
            state.isSuccess(),
            state.getDurationMs(),
            state.getUnparseablePreview());
    }

    private static void captureSessionIdFromEvent(JsonNode event, CliStreamParseState state) {
        if (state.getSessionId() == null && event.has("session_id")) {
            state.setSessionId(event.get("session_id").asText());
        }
    }

    private static void dispatchByType(String type, JsonNode event, CliStreamParseState state) {
        if (Objects.equals(type, "stream_event")) {
            applyStreamEvent(event, state);
        } else if (Objects.equals(type, "assistant")) {
            applyAssistant(event, state);
        } else if (Objects.equals(type, "result")) {
            applyResult(event, state);
        }
    }

    private static void applyStreamEvent(JsonNode event, CliStreamParseState state) {
        String delta = extractStreamEventTextDelta(event);
        if (delta != null && !delta.isEmpty()) {
            state.getStreamDeltaBuffer().append(delta);
            state.getFullText().append(delta);
        }
    }

    private static void applyAssistant(JsonNode event, CliStreamParseState state) {
        flushStreamDeltaBuffer(state.getStreamDeltaBuffer(), state.getAssistantMessages());
        String text = extractAssistantText(event);
        if (text != null && !text.isBlank()) {
            state.getAssistantMessages().add(text);
            state.getFullText().append(text);
        }
    }

    private static void applyResult(JsonNode event, CliStreamParseState state) {
        flushStreamDeltaBuffer(state.getStreamDeltaBuffer(), state.getAssistantMessages());
        state.setResultLineSeen(true);
        state.setSuccess(!Objects.equals(textOrNull(event, "subtype"), "error"));
        if (event.has("duration_ms")) {
            state.setDurationMs(event.get("duration_ms").asLong());
        }
        if (state.getSessionId() == null && event.has("session_id")) {
            state.setSessionId(event.get("session_id").asText());
        }
    }

    private static void flushStreamDeltaBuffer(StringBuilder streamDeltaBuffer, List<String> assistantMessages) {
        if (streamDeltaBuffer.length() > 0) {
            assistantMessages.add(streamDeltaBuffer.toString());
            streamDeltaBuffer.setLength(0);
        }
    }

    /**
     * Claude Code stream-json partial lines: {@code stream_event} wrapping API-style events.
     */
    private static String extractStreamEventTextDelta(JsonNode root) {
        JsonNode inner = root.get("event");
        if (inner == null || inner.isNull()) {
            return null;
        }
        String fromNested = textDeltaFromNestedDelta(inner);
        if (fromNested != null) {
            return fromNested;
        }
        return textDeltaFromContentBlockDelta(inner);
    }

    private static String textDeltaFromNestedDelta(JsonNode inner) {
        JsonNode delta = inner.get("delta");
        if (delta == null || delta.isNull()) {
            return null;
        }
        if (Objects.equals(textOrNull(delta, "type"), "text_delta") && delta.has("text")) {
            return delta.get("text").asText();
        }
        return null;
    }

    private static String textDeltaFromContentBlockDelta(JsonNode inner) {
        if (!Objects.equals(textOrNull(inner, "type"), "content_block_delta")) {
            return null;
        }
        JsonNode d = inner.get("delta");
        if (d == null || d.isNull() || !Objects.equals(textOrNull(d, "type"), "text_delta") || !d.has("text")) {
            return null;
        }
        return d.get("text").asText();
    }

    private static String extractAssistantText(JsonNode event) {
        JsonNode message = event.get("message");
        if (message == null) {
            return null;
        }
        JsonNode content = message.get("content");
        if (content == null || !content.isArray() || content.isEmpty()) {
            return null;
        }
        var sb = new StringBuilder();
        for (JsonNode item : content) {
            if (Objects.equals(textOrNull(item, "type"), "text") && item.has("text")) {
                sb.append(item.get("text").asText());
            }
        }
        return sb.toString();
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return child != null ? child.asText() : null;
    }

    public record CliStreamResult(
        String sessionId,
        List<String> assistantMessages,
        String fullText,
        boolean resultLineSeen,
        boolean success,
        long durationMs,
        String unparseablePreview
    ) {
    }
}
