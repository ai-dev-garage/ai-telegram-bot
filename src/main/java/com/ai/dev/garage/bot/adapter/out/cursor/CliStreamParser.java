package com.ai.dev.garage.bot.adapter.out.cursor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CliStreamParser {

    private final ObjectMapper objectMapper;

    public CliStreamResult parse(InputStream inputStream) {
        String sessionId = null;
        List<String> assistantMessages = new ArrayList<>();
        StringBuilder fullText = new StringBuilder();
        StringBuilder streamDeltaBuffer = new StringBuilder();
        boolean success = false;
        long durationMs = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    JsonNode event = objectMapper.readTree(line);
                    String type = textOrNull(event, "type");

                    if (sessionId == null && event.has("session_id")) {
                        sessionId = event.get("session_id").asText();
                    }

                    if ("stream_event".equals(type)) {
                        String delta = extractStreamEventTextDelta(event);
                        if (delta != null && !delta.isEmpty()) {
                            streamDeltaBuffer.append(delta);
                            fullText.append(delta);
                        }
                    } else if ("assistant".equals(type)) {
                        flushStreamDeltaBuffer(streamDeltaBuffer, assistantMessages);
                        String text = extractAssistantText(event);
                        if (text != null && !text.isBlank()) {
                            assistantMessages.add(text);
                            fullText.append(text);
                        }
                    } else if ("result".equals(type)) {
                        flushStreamDeltaBuffer(streamDeltaBuffer, assistantMessages);
                        success = !"error".equals(textOrNull(event, "subtype"));
                        if (event.has("duration_ms")) {
                            durationMs = event.get("duration_ms").asLong();
                        }
                        if (sessionId == null && event.has("session_id")) {
                            sessionId = event.get("session_id").asText();
                        }
                    }
                } catch (Exception e) {
                    log.debug("Skipping unparseable stream-json line: {}", line, e);
                }
            }
        } catch (IOException e) {
            log.error("Error reading CLI stream: {}", e.getMessage(), e);
        }

        flushStreamDeltaBuffer(streamDeltaBuffer, assistantMessages);

        return new CliStreamResult(sessionId, assistantMessages, fullText.toString(), success, durationMs);
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
        // Headless doc jq example: .event.delta.type == "text_delta"
        JsonNode delta = inner.get("delta");
        if (delta != null && !delta.isNull() && "text_delta".equals(textOrNull(delta, "type")) && delta.has("text")) {
            return delta.get("text").asText();
        }
        // Agent SDK: event.type == content_block_delta, delta nested
        if ("content_block_delta".equals(textOrNull(inner, "type"))) {
            JsonNode d = inner.get("delta");
            if (d != null && !d.isNull() && "text_delta".equals(textOrNull(d, "type")) && d.has("text")) {
                return d.get("text").asText();
            }
        }
        return null;
    }

    private String extractAssistantText(JsonNode event) {
        JsonNode message = event.get("message");
        if (message == null) {
            return null;
        }
        JsonNode content = message.get("content");
        if (content == null || !content.isArray() || content.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : content) {
            if ("text".equals(textOrNull(item, "type")) && item.has("text")) {
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
        boolean success,
        long durationMs
    ) {
    }
}
