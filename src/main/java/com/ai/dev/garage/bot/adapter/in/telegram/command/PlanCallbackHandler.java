package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.telegram.InlineKeyboardBuilder;
import com.ai.dev.garage.bot.adapter.in.telegram.PlanConversationState;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.application.port.in.PlanManagement;
import com.ai.dev.garage.bot.application.port.out.PlanSessionStore;
import com.ai.dev.garage.bot.application.service.PlanSessionService;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.PlanQuestion;
import com.ai.dev.garage.bot.domain.PlanSession;
import com.ai.dev.garage.bot.domain.PlanState;
import com.ai.dev.garage.bot.application.port.out.JobStore;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Handles Telegram callbacks for plan mode interactions and sends
 * async notifications when the CLI finishes a round.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PlanCallbackHandler implements PlanSessionService.PlanCompletionListener {

    public static final String CALLBACK_PREFIX = "plan:";

    private final PlanManagement planManagement;
    private final PlanSessionStore planSessionStore;
    private final PlanSessionService planSessionService;
    private final TelegramBotClient telegramBotClient;
    private final PlanConversationState conversationState;
    private final JobStore jobStore;
    private final ObjectMapper objectMapper;

    @PostConstruct
    void registerListener() {
        planSessionService.setCompletionListener(this);
    }

    /**
     * Handle a callback_data string that starts with "plan:".
     */
    public void handleCallback(String callbackQueryId, Long chatId, Long userId, String data) {
        telegramBotClient.answerCallbackQuery(callbackQueryId, null);

        if (data.startsWith("plan:answer:")) {
            handleAnswerCallback(chatId, userId, data);
        } else if (data.startsWith("plan:freetext:")) {
            handleFreeTextButton(chatId, userId, data);
        } else if (data.startsWith("plan:questions:")) {
            handleQuestionsEntry(chatId, userId, data);
        } else if (data.startsWith("plan:view:")) {
            handleView(chatId, userId, data);
        } else if (data.startsWith("plan:qa:")) {
            handleQaHistory(chatId, data);
        } else if (data.startsWith("plan:continue:")) {
            handleContinue(chatId, data);
        } else if (data.startsWith("plan:approve:")) {
            handleApprove(chatId, data);
        } else if (data.startsWith("plan:pause:")) {
            handlePause(chatId, data);
        } else if (data.startsWith("plan:unpause:")) {
            handleUnpause(chatId, data);
        } else if (data.startsWith("plan:reject:")) {
            handleReject(chatId, data);
        } else if (data.startsWith("plan:adjust:")) {
            handleAdjust(chatId, userId, data);
        } else if ("plan:list".equals(data)) {
            handleListShortcut(chatId);
        }
    }

    /**
     * Handle a free-text answer from a user (non-command message while expecting answer).
     */
    public void handleFreeTextAnswer(Long chatId, Long userId, String text) {
        Optional<PlanConversationState.PendingAnswer> pending = conversationState.consumePending(chatId, userId);
        if (pending.isEmpty()) return;

        var pa = pending.get();
        try {
            planManagement.recordAnswer(pa.sessionId(), pa.round(), pa.seq(), text);
            telegramBotClient.sendPlain(chatId, "Answer recorded.");
            presentNextQuestionOrDone(chatId, userId, pa.sessionId());
        } catch (Exception e) {
            telegramBotClient.sendPlain(chatId, "Error recording answer: " + e.getMessage());
        }
    }

    // --- PlanCompletionListener callbacks (called from async plan executor) ---

    @Override
    public void onQuestionsReady(long jobId, long sessionId) {
        Job job = jobStore.findById(jobId).orElse(null);
        if (job == null || job.getRequester() == null) return;
        Long chatId = job.getRequester().getTelegramChatId();

        PlanSession session = planSessionStore.findById(sessionId).orElse(null);
        int total = 0;
        if (session != null) {
            total = planSessionStore.findQuestionsBySessionAndRound(sessionId, session.getRound()).size();
        }

        String text = "Plan #" + jobId + " — agent has " + total + " question" + (total != 1 ? "s" : "") + ":";
        InlineKeyboardBuilder kb = InlineKeyboardBuilder.create()
            .row("Answer Questions", CALLBACK_PREFIX + "questions:" + sessionId)
            .row("Cancel Plan", CALLBACK_PREFIX + "reject:" + jobId);
        telegramBotClient.sendWithInlineKeyboard(chatId, text, kb.build());
    }

    @Override
    public void onPlanReady(long jobId) {
        Job job = jobStore.findById(jobId).orElse(null);
        if (job == null || job.getRequester() == null) return;
        Long chatId = job.getRequester().getTelegramChatId();

        PlanSession session = planSessionStore.findByJobId(jobId).orElse(null);
        if (session == null) return;

        String planText = session.getPlanText();
        String display = truncateForTelegram(planText, 3800);

        InlineKeyboardBuilder kb = InlineKeyboardBuilder.create()
            .row(List.of(
                new InlineKeyboardBuilder.Button("Build", CALLBACK_PREFIX + "approve:" + jobId),
                new InlineKeyboardBuilder.Button("Adjust", CALLBACK_PREFIX + "adjust:" + jobId)
            ))
            .row(List.of(
                new InlineKeyboardBuilder.Button("Pause", CALLBACK_PREFIX + "pause:" + jobId),
                new InlineKeyboardBuilder.Button("Cancel", CALLBACK_PREFIX + "reject:" + jobId)
            ))
            .row("Q&A History", CALLBACK_PREFIX + "qa:" + session.getId());

        telegramBotClient.sendWithInlineKeyboard(chatId,
            "Plan #" + jobId + " — plan ready:\n\n" + display,
            kb.build());
    }

    @Override
    public void onPlanError(long jobId, String error) {
        Job job = jobStore.findById(jobId).orElse(null);
        if (job == null || job.getRequester() == null) return;
        Long chatId = job.getRequester().getTelegramChatId();
        telegramBotClient.sendPlain(chatId,
            "Plan #" + jobId + " failed: " + error);
    }

    // --- Internal ---

    private void handleAnswerCallback(Long chatId, Long userId, String data) {
        String[] parts = data.split(":");
        if (parts.length < 6) return;
        try {
            long sessionId = Long.parseLong(parts[2]);
            int round = Integer.parseInt(parts[3]);
            int seq = Integer.parseInt(parts[4]);
            int optionIdx = Integer.parseInt(parts[5]);

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
            Job job = planManagement.approvePlan(jobId);
            String exportedTo = extractExportedPath(job);
            StringBuilder msg = new StringBuilder("Plan #" + jobId + " — submitted for build.");
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
            Job job = planManagement.pausePlan(jobId);
            String exportedTo = extractExportedPath(job);
            StringBuilder msg = new StringBuilder("Plan #" + jobId + " — paused.");
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
        handleView(chatId, null, "plan:view:" + jobId);
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

    private void handleAdjust(Long chatId, Long userId, String data) {
        long jobId = parseIdAfterPrefix(data, "plan:adjust:");
        PlanSession session = planSessionStore.findByJobId(jobId).orElse(null);
        if (session == null) {
            telegramBotClient.sendPlain(chatId, "Plan session not found.");
            return;
        }
        int nextRound = session.getRound() + 1;

        PlanQuestion adjustQ = PlanQuestion.builder()
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
        InlineKeyboardBuilder kb = InlineKeyboardBuilder.create()
            .row("Type Adjustment", cbData);
        telegramBotClient.sendWithInlineKeyboard(chatId,
            "What adjustments would you like?", kb.build());
    }

    private void handleView(Long chatId, Long userId, String data) {
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
                "Plan #" + jobId + " — " + session.getState().name().toLowerCase());
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
        String display = truncateForTelegram(planText, 3800);
        String header = paused ? "paused" : "plan ready";
        InlineKeyboardBuilder kb = planDetailKeyboard(jobId, session.getId(), paused);
        telegramBotClient.sendWithInlineKeyboard(chatId,
            "Plan #" + jobId + " — " + header + ":\n\n" + display, kb.build());
    }

    private InlineKeyboardBuilder planDetailKeyboard(long jobId, long sessionId, boolean paused) {
        InlineKeyboardBuilder kb = InlineKeyboardBuilder.create();
        if (paused) {
            kb.row(List.of(
                new InlineKeyboardBuilder.Button("Build", CALLBACK_PREFIX + "approve:" + jobId),
                new InlineKeyboardBuilder.Button("Resume", CALLBACK_PREFIX + "unpause:" + jobId)
            ))
                .row(List.of(
                    new InlineKeyboardBuilder.Button("Adjust", CALLBACK_PREFIX + "adjust:" + jobId),
                    new InlineKeyboardBuilder.Button("Cancel", CALLBACK_PREFIX + "reject:" + jobId)
                ));
        } else {
            kb.row(List.of(
                new InlineKeyboardBuilder.Button("Build", CALLBACK_PREFIX + "approve:" + jobId),
                new InlineKeyboardBuilder.Button("Adjust", CALLBACK_PREFIX + "adjust:" + jobId)
            ))
                .row(List.of(
                    new InlineKeyboardBuilder.Button("Pause", CALLBACK_PREFIX + "pause:" + jobId),
                    new InlineKeyboardBuilder.Button("Cancel", CALLBACK_PREFIX + "reject:" + jobId)
                ));
        }
        return kb.row(List.of(
            new InlineKeyboardBuilder.Button("Q&A History", CALLBACK_PREFIX + "qa:" + sessionId),
            new InlineKeyboardBuilder.Button("All Plans", "plan:list")
        ));
    }

    private void handleQuestionsEntry(Long chatId, Long userId, String data) {
        long sessionId = parseIdAfterPrefix(data, "plan:questions:");
        presentNextQuestionOrDone(chatId, userId, sessionId);
    }

    private void handleFreeTextButton(Long chatId, Long userId, String data) {
        String[] parts = data.split(":");
        if (parts.length < 5) return;
        try {
            long sessionId = Long.parseLong(parts[2]);
            int round = Integer.parseInt(parts[3]);
            int seq = Integer.parseInt(parts[4]);
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

        StringBuilder sb = new StringBuilder("Q&A History — Plan #" + session.getJobId() + "\n\n");
        for (var entry : byRound.entrySet()) {
            sb.append("Round ").append(entry.getKey()).append(":\n");
            for (PlanQuestion q : entry.getValue()) {
                sb.append("Q").append(q.getSeq()).append(": ").append(q.getQuestionText()).append("\n");
                String answer = q.getAnswer() != null ? q.getAnswer() : "(unanswered)";
                sb.append("A: ").append(answer).append("\n\n");
            }
        }

        String text = truncateForTelegram(sb.toString().trim(), 3800);
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
        InlineKeyboardBuilder kb = InlineKeyboardBuilder.create();
        List<String> infoLines = new ArrayList<>();
        for (PlanSession s : active) {
            String label = "#" + s.getJobId() + " — " + formatState(s) + " (round " + s.getRound() + ")";
            if (s.getState() == PlanState.PLANNING) {
                infoLines.add(label);
            } else {
                kb.row(label, CALLBACK_PREFIX + "view:" + s.getJobId());
            }
        }
        StringBuilder msg = new StringBuilder("Active plans:");
        if (!infoLines.isEmpty()) {
            msg.append("\n\n");
            for (String line : infoLines) {
                msg.append(line).append("\n");
            }
        }
        telegramBotClient.sendWithInlineKeyboard(chatId, msg.toString().trim(), kb.build());
    }

    private static String formatState(PlanSession s) {
        return switch (s.getState()) {
            case PLANNING -> "analyzing...";
            case AWAITING_INPUT -> "awaiting your input";
            case PLAN_READY -> "plan ready";
            case PAUSED -> "paused";
            default -> s.getState().name().toLowerCase();
        };
    }

    void presentNextQuestionOrDone(Long chatId, Long userId, long sessionId) {
        Optional<PlanQuestion> next = planManagement.nextUnansweredQuestion(sessionId);
        if (next.isEmpty()) {
            PlanSession session = planSessionStore.findById(sessionId).orElse(null);
            if (session == null) return;

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
            InlineKeyboardBuilder kb = InlineKeyboardBuilder.create();
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
        if (optionsJson == null || optionsJson.isBlank()) return List.of();
        try {
            return objectMapper.readValue(optionsJson, new TypeReference<>() {});
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

    @SuppressWarnings("unchecked")
    private String extractExportedPath(Job job) {
        if (job.getResultJson() == null) return null;
        try {
            Map<String, Object> result = objectMapper.readValue(job.getResultJson(), Map.class);
            Object path = result.get("exportedTo");
            return path instanceof String s ? s : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static long parseIdAfterPrefix(String data, String prefix) {
        return Long.parseLong(data.substring(prefix.length()).trim());
    }

    private static String truncateForTelegram(String s, int max) {
        if (s == null) return "(empty plan)";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

}
