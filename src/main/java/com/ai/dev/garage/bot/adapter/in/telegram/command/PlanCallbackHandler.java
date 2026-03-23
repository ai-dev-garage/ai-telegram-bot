package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.telegram.InlineKeyboardBuilder;
import com.ai.dev.garage.bot.adapter.in.telegram.PlanConversationState;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.adapter.in.telegram.command.model.PlanCallbackContext;
import com.ai.dev.garage.bot.adapter.in.telegram.command.support.PlanCallbackKeyboards;
import com.ai.dev.garage.bot.application.port.in.PlanManagement;
import com.ai.dev.garage.bot.application.port.out.JobStore;
import com.ai.dev.garage.bot.application.port.out.PlanSessionStore;
import com.ai.dev.garage.bot.application.support.ContentLengthLimits;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.ai.dev.garage.bot.domain.PlanQuestion;
import com.ai.dev.garage.bot.domain.PlanSession;
import com.ai.dev.garage.bot.domain.PlanState;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles Telegram callbacks for plan mode interactions (user taps / answers).
 * Async completion notifications are sent by {@link com.ai.dev.garage.bot.adapter.in.telegram.command.support.PlanCompletionTelegramNotifier}.
 *
 * <p>PMD {@code CouplingBetweenObjects}: intentional facade; many port/domain types are required in one place.
 */
@SuppressWarnings("PMD.CouplingBetweenObjects")
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PlanCallbackHandler {

    public static final String CALLBACK_PREFIX = "plan:";

    private static final Pattern CALLBACK_COLON_SPLIT = Pattern.compile(":");

    private static final int ANSWER_CALLBACK_MIN_PARTS = 6;
    private static final int FREETEXT_CALLBACK_MIN_PARTS = 5;
    private static final int CB_IDX_SESSION = 2;
    private static final int CB_IDX_ROUND = 3;
    private static final int CB_IDX_SEQ = 4;
    private static final int CB_IDX_OPTION = 5;

    private final PlanManagement planManagement;
    private final PlanSessionStore planSessionStore;
    private final TelegramBotClient telegramBotClient;
    private final PlanConversationState conversationState;
    private final JobStore jobStore;
    private final ObjectMapper objectMapper;

    private final Map<String, Consumer<PlanCallbackContext>> planCallbacksByPrefix = new LinkedHashMap<>();
    private List<Map.Entry<String, Consumer<PlanCallbackContext>>> planCallbacksOrdered = List.of();

    @PostConstruct
    void registerPlanCallbacksOrdered() {
        registerPlanCallbacks();
        planCallbacksOrdered = planCallbacksByPrefix.entrySet().stream()
            .sorted(Comparator.<Map.Entry<String, Consumer<PlanCallbackContext>>>comparingInt(e -> e.getKey().length())
                .reversed())
            .toList();
    }

    private void registerPlanCallbacks() {
        planCallbacksByPrefix.put("plan:answer:", c -> handleAnswerCallback(c.chatId(), c.userId(), c.data()));
        planCallbacksByPrefix.put("plan:freetext:", c -> handleFreeTextButton(c.chatId(), c.userId(), c.data()));
        planCallbacksByPrefix.put("plan:questions:", c -> handleQuestionsEntry(c.chatId(), c.userId(), c.data()));
        planCallbacksByPrefix.put("plan:view:", c -> handleView(c.chatId(), c.data()));
        planCallbacksByPrefix.put("plan:qa:", c -> handleQaHistory(c.chatId(), c.data()));
        planCallbacksByPrefix.put("plan:continue:", c -> handleContinue(c.chatId(), c.data()));
        planCallbacksByPrefix.put("plan:approve:", c -> handleApprove(c.chatId(), c.data()));
        planCallbacksByPrefix.put("plan:pause:", c -> handlePause(c.chatId(), c.data()));
        planCallbacksByPrefix.put("plan:unpause:", c -> handleUnpause(c.chatId(), c.data()));
        planCallbacksByPrefix.put("plan:reject:", c -> handleReject(c.chatId(), c.data()));
        planCallbacksByPrefix.put("plan:adjust:", c -> handleAdjust(c.chatId(), c.data()));
    }

    /**
     * Handle a callback_data string that starts with "plan:".
     */
    public void handleCallback(String callbackQueryId, Long chatId, Long userId, String data) {
        telegramBotClient.answerCallbackQuery(callbackQueryId, null);

        if (Objects.equals(data, "plan:list")) {
            handleListShortcut(chatId);
            return;
        }

        var ctx = new PlanCallbackContext(chatId, userId, data);
        for (Map.Entry<String, Consumer<PlanCallbackContext>> e : planCallbacksOrdered) {
            if (data.startsWith(e.getKey())) {
                e.getValue().accept(ctx);
                return;
            }
        }
    }

    /**
     * Handle a free-text answer from a user (non-command message while expecting answer).
     */
    public void handleFreeTextAnswer(Long chatId, Long userId, String text) {
        Optional<PlanConversationState.PendingAnswer> pending = conversationState.consumePending(chatId, userId);
        if (pending.isEmpty()) {
            return;
        }

        var pa = pending.get();
        try {
            planManagement.recordAnswer(pa.sessionId(), pa.round(), pa.seq(), text);
            telegramBotClient.sendPlain(chatId, "Answer recorded.");
            presentNextQuestionOrDone(chatId, userId, pa.sessionId());
        } catch (Exception e) {
            telegramBotClient.sendPlain(chatId, "Error recording answer: " + e.getMessage());
        }
    }

    // --- Internal ---

    private void handleAnswerCallback(Long chatId, Long userId, String data) {
        String[] parts = CALLBACK_COLON_SPLIT.split(data, -1);
        if (parts.length < ANSWER_CALLBACK_MIN_PARTS) {
            return;
        }
        try {
            long sessionId = Long.parseLong(parts[CB_IDX_SESSION]);
            int round = Integer.parseInt(parts[CB_IDX_ROUND]);
            int seq = Integer.parseInt(parts[CB_IDX_SEQ]);
            int optionIdx = Integer.parseInt(parts[CB_IDX_OPTION]);

            PlanQuestion question = planSessionStore.findQuestion(sessionId, round, seq).orElse(null);
            if (question == null) {
                telegramBotClient.sendPlain(chatId, "Question not found.");
                return;
            }

            String answer = resolveOption(question, optionIdx);
            planManagement.recordAnswer(sessionId, round, seq, answer);
            telegramBotClient.sendPlain(chatId, "Answer: " + answer);
            presentNextQuestionOrDone(chatId, userId, sessionId);
        } catch (Exception e) {
            telegramBotClient.sendPlain(chatId, "Error: " + e.getMessage());
        }
    }

    private void handleContinue(Long chatId, String data) {
        long jobId = parseIdAfterPrefix(data, "plan:continue:");
        try {
            telegramBotClient.sendPlain(chatId, "Plan #" + jobId + " — working on your plan...");
            planManagement.resumePlan(jobId);
        } catch (Exception e) {
            telegramBotClient.sendPlain(chatId, "Error: " + e.getMessage());
        }
    }

    private void handleApprove(Long chatId, String data) {
        long jobId = parseIdAfterPrefix(data, "plan:approve:");
        try {
            var job = planManagement.approvePlan(jobId);
            String exportedTo = extractExportedPath(job);
            var msg = new StringBuilder("Plan #" + jobId + " — submitted for build.");
            if (exportedTo != null) {
                msg.append("\nSaved to: ").append(exportedTo);
            }
            telegramBotClient.sendPlain(chatId, msg.toString());
        } catch (Exception e) {
            telegramBotClient.sendPlain(chatId, "Error: " + e.getMessage());
        }
    }

    private void handlePause(Long chatId, String data) {
        long jobId = parseIdAfterPrefix(data, "plan:pause:");
        try {
            var job = planManagement.pausePlan(jobId);
            String exportedTo = extractExportedPath(job);
            var msg = new StringBuilder("Plan #" + jobId + " — paused.");
            if (exportedTo != null) {
                msg.append("\nSaved to: ").append(exportedTo);
            }
            telegramBotClient.sendPlain(chatId, msg.toString());
        } catch (Exception e) {
            telegramBotClient.sendPlain(chatId, "Error: " + e.getMessage());
        }
    }

    private void handleUnpause(Long chatId, String data) {
        long jobId = parseIdAfterPrefix(data, "plan:unpause:");
        PlanSession session = planSessionStore.findByJobId(jobId).orElse(null);
        if (session == null) {
            telegramBotClient.sendPlain(chatId, "Plan session not found.");
            return;
        }
        if (session.getState() != PlanState.PAUSED) {
            telegramBotClient.sendPlain(chatId, "Plan is not paused (current: " + session.getState() + ").");
            return;
        }
        session.setState(PlanState.PLAN_READY);
        planSessionStore.save(session);
        jobStore.findById(jobId).ifPresent(job -> {
            job.setStatus(JobStatus.AWAITING_INPUT);
            jobStore.save(job);
        });
        telegramBotClient.sendPlain(chatId, "Plan #" + jobId + " — resumed.");
        handleView(chatId, "plan:view:" + jobId);
    }

    private void handleReject(Long chatId, String data) {
        long jobId = parseIdAfterPrefix(data, "plan:reject:");
        try {
            planManagement.rejectPlan(jobId);
            telegramBotClient.sendPlain(chatId, "Plan #" + jobId + " cancelled.");
        } catch (Exception e) {
            telegramBotClient.sendPlain(chatId, "Error: " + e.getMessage());
        }
    }

    private void handleAdjust(Long chatId, String data) {
        long jobId = parseIdAfterPrefix(data, "plan:adjust:");
        PlanSession session = planSessionStore.findByJobId(jobId).orElse(null);
        if (session == null) {
            telegramBotClient.sendPlain(chatId, "Plan session not found.");
            return;
        }
        int nextRound = session.getRound() + 1;

        var adjustQ = PlanQuestion.builder()
            .planSession(session)
            .round(nextRound)
            .seq(1)
            .questionText("What adjustments would you like?")
            .build();
        planSessionStore.saveQuestion(adjustQ);

        session.setRound(nextRound);
        session.setState(PlanState.AWAITING_INPUT);
        planSessionStore.save(session);

        String cbData = CALLBACK_PREFIX + "freetext:" + session.getId() + ":" + nextRound + ":1";
        var kb = InlineKeyboardBuilder.create()
            .row("Type Adjustment", cbData);
        telegramBotClient.sendWithInlineKeyboard(chatId,
            "What adjustments would you like?", kb.build());
    }

    private void handleView(Long chatId, String data) {
        long jobId = parseIdAfterPrefix(data, "plan:view:");
        PlanSession session = planSessionStore.findByJobId(jobId).orElse(null);
        if (session == null) {
            telegramBotClient.sendPlain(chatId, "Plan session not found.");
            return;
        }

        switch (session.getState()) {
            case AWAITING_INPUT -> sendAwaitingInputView(chatId, jobId, session);
            case PLAN_READY -> sendPlanReadyOrPausedView(chatId, jobId, session, false);
            case PAUSED -> sendPlanReadyOrPausedView(chatId, jobId, session, true);
            default -> telegramBotClient.sendPlain(chatId,
                "Plan #" + jobId + " — " + session.getState().name().toLowerCase(Locale.ROOT));
        }
    }

    private void sendAwaitingInputView(Long chatId, long jobId, PlanSession session) {
        List<PlanQuestion> questions = planSessionStore
            .findQuestionsBySessionAndRound(session.getId(), session.getRound());
        long answered = questions.stream().filter(q -> q.getAnswer() != null).count();
        String text = "Plan #" + jobId + " — awaiting input\n"
            + "Round " + session.getRound() + " — "
            + answered + "/" + questions.size() + " questions answered";
        InlineKeyboardBuilder kb = InlineKeyboardBuilder.create()
            .row("Answer Questions", CALLBACK_PREFIX + "questions:" + session.getId())
            .row("Q&A History", CALLBACK_PREFIX + "qa:" + session.getId())
            .row(List.of(
                new InlineKeyboardBuilder.Button("Cancel", CALLBACK_PREFIX + "reject:" + jobId),
                new InlineKeyboardBuilder.Button("All Plans", "plan:list")
            ));
        telegramBotClient.sendWithInlineKeyboard(chatId, text, kb.build());
    }

    private void sendPlanReadyOrPausedView(Long chatId, long jobId, PlanSession session, boolean paused) {
        String planText = session.getPlanText();
        String display = PlanCallbackKeyboards.truncateForTelegram(planText, ContentLengthLimits.TELEGRAM_PLAIN_MESSAGE_SAFE);
        String header = paused ? "paused" : "plan ready";
        InlineKeyboardBuilder kb = PlanCallbackKeyboards.planDetailKeyboard(jobId, session.getId(), paused, CALLBACK_PREFIX);
        telegramBotClient.sendWithInlineKeyboard(chatId,
            "Plan #" + jobId + " — " + header + ":\n\n" + display, kb.build());
    }

    private void handleQuestionsEntry(Long chatId, Long userId, String data) {
        long sessionId = parseIdAfterPrefix(data, "plan:questions:");
        presentNextQuestionOrDone(chatId, userId, sessionId);
    }

    private void handleFreeTextButton(Long chatId, Long userId, String data) {
        String[] parts = CALLBACK_COLON_SPLIT.split(data, -1);
        if (parts.length < FREETEXT_CALLBACK_MIN_PARTS) {
            return;
        }
        try {
            long sessionId = Long.parseLong(parts[CB_IDX_SESSION]);
            int round = Integer.parseInt(parts[CB_IDX_ROUND]);
            int seq = Integer.parseInt(parts[CB_IDX_SEQ]);
            conversationState.expectAnswer(chatId, userId, sessionId, round, seq);
            telegramBotClient.sendPlain(chatId, "Type your answer:");
        } catch (NumberFormatException e) {
            telegramBotClient.sendPlain(chatId, "Invalid question reference.");
        }
    }

    private void handleQaHistory(Long chatId, String data) {
        long sessionId = parseIdAfterPrefix(data, "plan:qa:");
        PlanSession session = planSessionStore.findById(sessionId).orElse(null);
        if (session == null) {
            telegramBotClient.sendPlain(chatId, "Plan session not found.");
            return;
        }

        List<PlanQuestion> allQuestions = planSessionStore.findAllQuestionsBySession(sessionId);
        if (allQuestions.isEmpty()) {
            InlineKeyboardBuilder kb = InlineKeyboardBuilder.create()
                .row("Back to Plan", CALLBACK_PREFIX + "view:" + session.getJobId());
            telegramBotClient.sendWithInlineKeyboard(chatId, "No Q&A history yet.", kb.build());
            return;
        }

        Map<Integer, List<PlanQuestion>> byRound = new TreeMap<>();
        for (PlanQuestion q : allQuestions) {
            byRound.computeIfAbsent(q.getRound(), k -> new ArrayList<>()).add(q);
        }

        var sb = new StringBuilder("Q&A History — Plan #" + session.getJobId() + "\n\n");
        for (var entry : byRound.entrySet()) {
            sb.append("Round ").append(entry.getKey()).append(":\n");
            for (PlanQuestion q : entry.getValue()) {
                sb.append("Q").append(q.getSeq()).append(": ").append(q.getQuestionText()).append("\n");
                String answer = q.getAnswer() != null ? q.getAnswer() : "(unanswered)";
                sb.append("A: ").append(answer).append("\n\n");
            }
        }

        String text = PlanCallbackKeyboards.truncateForTelegram(sb.toString().trim(),
            ContentLengthLimits.TELEGRAM_PLAIN_MESSAGE_SAFE);
        InlineKeyboardBuilder kb = InlineKeyboardBuilder.create()
            .row("Back to Plan", CALLBACK_PREFIX + "view:" + session.getJobId());
        telegramBotClient.sendWithInlineKeyboard(chatId, text, kb.build());
    }

    private void handleListShortcut(Long chatId) {
        List<PlanSession> active = planManagement.listActivePlans();
        if (active.isEmpty()) {
            telegramBotClient.sendPlain(chatId, "No active plans.");
            return;
        }
        var kb = InlineKeyboardBuilder.create();
        List<String> infoLines = new ArrayList<>();
        for (PlanSession s : active) {
            String label = "#" + s.getJobId() + " — " + PlanCallbackKeyboards.formatState(s) + " (round " + s.getRound() + ")";
            if (s.getState() == PlanState.PLANNING) {
                infoLines.add(label);
            } else {
                kb.row(label, CALLBACK_PREFIX + "view:" + s.getJobId());
            }
        }
        var msg = new StringBuilder("Active plans:");
        if (!infoLines.isEmpty()) {
            msg.append("\n\n");
            for (String line : infoLines) {
                msg.append(line).append("\n");
            }
        }
        telegramBotClient.sendWithInlineKeyboard(chatId, msg.toString().trim(), kb.build());
    }

    void presentNextQuestionOrDone(Long chatId, Long userId, long sessionId) {
        Optional<PlanQuestion> next = planManagement.nextUnansweredQuestion(sessionId);
        if (next.isEmpty()) {
            PlanSession session = planSessionStore.findById(sessionId).orElse(null);
            if (session == null) {
                return;
            }

            long jobId = session.getJobId();
            try {
                telegramBotClient.sendPlain(chatId,
                    "Plan #" + jobId + " — working on your plan with provided answers...");
                planManagement.resumePlan(jobId);
            } catch (Exception e) {
                log.warn("Auto-resume failed for job {}, showing manual button: {}", jobId, e.getMessage());
                InlineKeyboardBuilder kb = InlineKeyboardBuilder.create()
                    .row(List.of(
                        new InlineKeyboardBuilder.Button("Continue Plan", CALLBACK_PREFIX + "continue:" + jobId),
                        new InlineKeyboardBuilder.Button("All Plans", "plan:list")
                    ));
                telegramBotClient.sendWithInlineKeyboard(chatId,
                    "All questions answered.", kb.build());
            }
            return;
        }

        PlanQuestion q = next.get();
        List<String> options = parseOptions(q.getOptions());

        if (options.isEmpty()) {
            String cbData = CALLBACK_PREFIX + "freetext:" + sessionId + ":" + q.getRound() + ":" + q.getSeq();
            InlineKeyboardBuilder kb = InlineKeyboardBuilder.create()
                .row("Type Answer", cbData);
            telegramBotClient.sendWithInlineKeyboard(chatId,
                "Q" + q.getSeq() + ": " + q.getQuestionText(),
                kb.build());
        } else {
            var kb = InlineKeyboardBuilder.create();
            for (int i = 0; i < options.size(); i++) {
                String cbData = CALLBACK_PREFIX + "answer:" + sessionId + ":" + q.getRound() + ":" + q.getSeq() + ":" + i;
                kb.row(options.get(i), cbData);
            }
            String freetextCb = CALLBACK_PREFIX + "freetext:" + sessionId + ":" + q.getRound() + ":" + q.getSeq();
            kb.row("Type your own answer", freetextCb);
            telegramBotClient.sendWithInlineKeyboard(chatId,
                "Q" + q.getSeq() + ": " + q.getQuestionText(),
                kb.build());
        }
    }

    private List<String> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(optionsJson, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.debug("Failed to parse options JSON: {}", optionsJson);
        }
        return List.of();
    }

    private String resolveOption(PlanQuestion question, int optionIdx) {
        List<String> options = parseOptions(question.getOptions());
        if (optionIdx >= 0 && optionIdx < options.size()) {
            return options.get(optionIdx);
        }
        return "Option " + (optionIdx + 1);
    }

    @SuppressWarnings("unchecked") // readValue(Map.class): job result JSON is an untyped map
    private String extractExportedPath(Job job) {
        if (job.getResultJson() == null) {
            return null;
        }
        try {
            Map<String, Object> result = objectMapper.readValue(job.getResultJson(), Map.class);
            Object path = result.get("exportedTo");
            return path instanceof String s ? s : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static long parseIdAfterPrefix(String data, String prefix) {
        return Long.parseLong(data.substring(prefix.length()).trim());
    }
}
