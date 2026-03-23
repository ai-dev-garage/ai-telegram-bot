package com.ai.dev.garage.bot.adapter.in.telegram;

import com.ai.dev.garage.bot.config.TelegramProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import reactor.core.publisher.Mono;

/**
 * Outbound adapter for Telegram Bot HTTP API (hexagonal “outbound port” implementation).
 */
@Component
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
public class TelegramBotClient {

    private static final Logger LOG = LoggerFactory.getLogger(TelegramBotClient.class);

    private final WebClient webClient;

    /**
     * Explicit ctor: builds {@link WebClient} base URL from the bot token (not a plain dependency list).
     */
    public TelegramBotClient(TelegramProperties telegramProperties) {
        this.webClient = WebClient.builder()
            .baseUrl("https://api.telegram.org/bot" + telegramProperties.getToken())
            .build();
    }

    private static boolean isJsonBooleanTrue(Object value) {
        return value instanceof Boolean b && b;
    }

    @SuppressWarnings("unchecked") // WebClient bodyToMono(Map.class); Telegram JSON is loosely typed maps
    public Map<String, Object> getUpdates(long offset, int timeoutSeconds) {
        Map<String, Object> response = webClient.get()
            .uri(uriBuilder -> uriBuilder.path("/getUpdates")
                .queryParam("timeout", timeoutSeconds)
                .queryParam("offset", offset)
                .build())
            .retrieve()
            .bodyToMono(Map.class)
            .doOnNext(body -> LOG.debug("Telegram getUpdates ok={}", body != null ? body.get("ok") : null))
            .doOnError(ex -> LOG.warn("Telegram getUpdates failed: {}", ex.getMessage()))
            .onErrorResume(ex -> Mono.empty())
            .block();
        return response == null ? Map.of() : response;
    }

    /**
     * Plain text (no parse_mode). Use for any message that includes job text, paths, logs, or model output —
     * Telegram's legacy Markdown rejects many characters and returns HTTP 400.
     */
    @SuppressWarnings("unchecked") // bodyToMono(Map.class) for Telegram JSON response
    public void sendPlain(Long chatId, String text) {
        webClient.post()
            .uri("/sendMessage")
            .bodyValue(Map.of("chat_id", chatId, "text", text))
            .retrieve()
            .bodyToMono(Map.class)
            .doOnNext(body -> {
                Object okField = body != null ? body.get("ok") : null;
                boolean ok = isJsonBooleanTrue(okField);
                if (!ok) {
                    LOG.warn("Telegram sendMessage (plain) chatId={} ok=false body={}", chatId, body);
                } else {
                    LOG.debug("Telegram sendMessage (plain) chatId={} ok=true", chatId);
                }
            })
            .doOnError(ex -> LOG.warn("Telegram sendMessage (plain) failed chatId={}: {}", chatId, ex.getMessage()))
            .onErrorResume(ex -> Mono.empty())
            .block();
    }

    @SuppressWarnings("unchecked") // bodyToMono(Map.class) for Telegram JSON response
    public void sendMarkdown(Long chatId, String text) {
        webClient.post()
            .uri("/sendMessage")
            .bodyValue(Map.of("chat_id", chatId, "text", text, "parse_mode", "Markdown"))
            .retrieve()
            .bodyToMono(Map.class)
            .doOnNext(body -> {
                Object okField = body != null ? body.get("ok") : null;
                boolean ok = isJsonBooleanTrue(okField);
                if (!ok) {
                    LOG.warn("Telegram sendMessage (Markdown) chatId={} ok=false body={}", chatId, body);
                } else {
                    LOG.debug("Telegram sendMessage (Markdown) chatId={} ok=true", chatId);
                }
            })
            .doOnError(ex -> LOG.warn("Telegram sendMessage (Markdown) failed chatId={}: {}", chatId, ex.getMessage()))
            .onErrorResume(ex -> Mono.empty())
            .block();
    }

    /**
     * Plain text + inline keyboard (avoid Markdown clashes with path characters).
     */
    @SuppressWarnings("unchecked") // bodyToMono(Map.class) for Telegram JSON response
    public void sendWithInlineKeyboard(Long chatId, String text, List<List<Map<String, String>>> inlineKeyboard) {
        Map<String, Object> replyMarkup = new LinkedHashMap<>();
        replyMarkup.put("inline_keyboard", inlineKeyboard);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("reply_markup", replyMarkup);
        webClient.post()
            .uri("/sendMessage")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map.class)
            .doOnNext(res -> {
                Object okField = res != null ? res.get("ok") : null;
                boolean ok = isJsonBooleanTrue(okField);
                if (!ok) {
                    LOG.warn("Telegram sendMessage (keyboard) chatId={} ok=false body={}", chatId, res);
                } else {
                    LOG.debug("Telegram sendMessage (keyboard) chatId={} ok=true", chatId);
                }
            })
            .doOnError(ex -> LOG.warn("Telegram sendMessage (keyboard) failed chatId={}: {}", chatId, ex.getMessage()))
            .onErrorResume(ex -> Mono.empty())
            .block();
    }

    @SuppressWarnings("unchecked") // bodyToMono(Map.class) for Telegram JSON response
    public void answerCallbackQuery(String callbackQueryId, String optionalUserVisibleText) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("callback_query_id", callbackQueryId);
        if (optionalUserVisibleText != null && !optionalUserVisibleText.isBlank()) {
            body.put("text", optionalUserVisibleText);
            body.put("show_alert", false);
        }
        webClient.post()
            .uri("/answerCallbackQuery")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map.class)
            .doOnNext(res -> LOG.debug("Telegram answerCallbackQuery ok={}", res != null ? res.get("ok") : null))
            .doOnError(ex -> LOG.warn("Telegram answerCallbackQuery failed: {}", ex.getMessage()))
            .onErrorResume(ex -> Mono.empty())
            .block();
    }

    /**
     * Register bot commands via Telegram setMyCommands API for the command menu.
     */
    @SuppressWarnings("unchecked") // bodyToMono(Map.class) for Telegram JSON response
    public void setMyCommands(List<Map<String, String>> commands) {
        webClient.post()
            .uri("/setMyCommands")
            .bodyValue(Map.of("commands", commands))
            .retrieve()
            .bodyToMono(Map.class)
            .doOnNext(res -> {
                Object okField = res != null ? res.get("ok") : null;
                boolean ok = isJsonBooleanTrue(okField);
                if (!ok) {
                    LOG.warn("Telegram setMyCommands ok=false description={} body={}",
                        res != null ? res.get("description") : null, res);
                } else {
                    String names = commands.stream()
                        .map(c -> c.get("command"))
                        .collect(Collectors.joining(", "));
                    LOG.info("Telegram setMyCommands registered {} commands: {}", commands.size(), names);
                }
            })
            .doOnError(ex -> LOG.warn("Telegram setMyCommands failed: {}", ex.getMessage()))
            .onErrorResume(ex -> Mono.empty())
            .block();
    }

    @SuppressWarnings("unchecked") // Telegram "result" is a JSON array of heterogeneous maps
    public static List<Map<String, Object>> extractUpdates(Map<String, Object> response) {
        if (response == null || !isJsonBooleanTrue(response.get("ok"))) {
            return List.of();
        }
        Object result = response.get("result");
        if (result instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }
}
