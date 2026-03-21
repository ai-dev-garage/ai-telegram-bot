package com.ai.dev.garage.bot.adapter.in.web;

import com.ai.dev.garage.bot.adapter.in.web.dto.DashboardView;
import com.ai.dev.garage.bot.adapter.in.web.dto.JobDetailView;
import com.ai.dev.garage.bot.adapter.in.web.dto.JobEventView;
import com.ai.dev.garage.bot.adapter.in.web.dto.JobLogLineView;
import com.ai.dev.garage.bot.adapter.in.web.dto.JobStatusView;
import com.ai.dev.garage.bot.adapter.in.web.dto.JobSummaryView;
import com.ai.dev.garage.bot.adapter.in.web.dto.StatusCountView;
import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobEvent;
import com.ai.dev.garage.bot.domain.JobLog;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.ai.dev.garage.bot.domain.PlanQuestion;
import com.ai.dev.garage.bot.domain.PlanSession;
import com.ai.dev.garage.bot.domain.PlanState;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ViewDtoMapper {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter TIME_ONLY = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_SEC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int INTENT_MAX_LENGTH = 80;

    private static final Map<JobStatus, String> STATUS_BADGE = Map.of(
        JobStatus.SUCCESS,        "bg-green-500 text-white",
        JobStatus.FAILED,         "bg-red-500 text-white",
        JobStatus.RUNNING,        "bg-blue-500 text-white",
        JobStatus.QUEUED,         "bg-gray-400 text-white",
        JobStatus.CANCELLED,      "bg-gray-600 text-white",
        JobStatus.AWAITING_INPUT, "bg-yellow-500 text-white",
        JobStatus.PAUSED,         "bg-orange-500 text-white"
    );

    private static final Map<String, String> LEVEL_CLASS = Map.of(
        "ERROR", "text-red-600",
        "WARN",  "text-yellow-600",
        "INFO",  "text-gray-700",
        "DEBUG", "text-gray-400"
    );

    private static final Set<JobStatus> TERMINAL = Set.of(JobStatus.SUCCESS, JobStatus.FAILED, JobStatus.CANCELLED);
    private static final Set<JobStatus> CANCELLABLE = Set.of(JobStatus.QUEUED, JobStatus.RUNNING, JobStatus.AWAITING_INPUT, JobStatus.PAUSED);

    private static final Map<PlanState, String> PLAN_STATE_BADGE = Map.of(
        PlanState.PLANNING,        "bg-blue-500 text-white",
        PlanState.AWAITING_INPUT,  "bg-yellow-500 text-white",
        PlanState.PLAN_READY,      "bg-green-500 text-white",
        PlanState.PAUSED,          "bg-orange-500 text-white",
        PlanState.APPROVED,        "bg-emerald-600 text-white",
        PlanState.REJECTED,        "bg-red-500 text-white",
        PlanState.CANCELLED,       "bg-gray-600 text-white"
    );

    private final JsonCodec jsonCodec;

    public JobStatusView toStatusView(Job job) {
        String error = orDash(job.getLastError());
        return new JobStatusView(
            job.getId(),
            job.getStatus().name(),
            badgeClass(job.getStatus()),
            error,
            !"—".equals(error),
            job.getStatus() == JobStatus.FAILED,
            CANCELLABLE.contains(job.getStatus()));
    }

    public boolean isTerminal(Job job) {
        return TERMINAL.contains(job.getStatus());
    }

    public List<StatusCountView> toStatusCounts(Map<JobStatus, Long> counts) {
        return counts.entrySet().stream()
            .map(e -> new StatusCountView(
                e.getKey().name(),
                e.getValue(),
                badgeClass(e.getKey())))
            .toList();
    }

    public DashboardView toDashboardView(Map<JobStatus, Long> counts, List<Job> failures) {
        return new DashboardView(toStatusCounts(counts), toSummaryList(failures));
    }

    public List<JobSummaryView> toSummaryList(List<Job> jobs) {
        return jobs.stream().map(this::toSummary).toList();
    }

    public JobSummaryView toSummary(Job job) {
        return new JobSummaryView(
            job.getId(),
            truncate(job.getIntent()),
            job.getStatus().name(),
            badgeClass(job.getStatus()),
            job.getTaskType().name(),
            job.getRiskLevel().name(),
            job.getTargetExecutor(),
            job.getAttempt(),
            formatDateTime(job.getCreatedAt()),
            formatDateTime(job.getUpdatedAt()));
    }

    public JobDetailView toDetailView(Job job, List<JobEvent> events, List<JobLog> logs) {
        return toDetailView(job, events, logs, null, List.of());
    }

    public JobDetailView toDetailView(Job job, List<JobEvent> events, List<JobLog> logs,
                                      PlanSession planSession, List<PlanQuestion> planQuestions) {
        List<JobEventView> eventViews = events.stream().map(this::toEventView).toList();
        List<JobLog> safeLogs = logs.stream().filter(Objects::nonNull).toList();
        List<JobLogLineView> logViews = safeLogs.stream().map(this::toLogLine).toList();
        int lastSeq = safeLogs.isEmpty() ? 0 : safeLogs.getLast().getSeq();

        boolean isPlanTask = planSession != null;
        String planText = isPlanTask ? planSession.getPlanText() : null;
        String planState = isPlanTask ? planSession.getState().name() : null;
        String planBadge = isPlanTask
            ? PLAN_STATE_BADGE.getOrDefault(planSession.getState(), "bg-gray-400 text-white")
            : null;
        List<JobDetailView.PlanQuestionView> qViews = planQuestions.stream()
            .map(q -> new JobDetailView.PlanQuestionView(
                q.getRound(), q.getSeq(), q.getQuestionText(),
                q.getAnswer() != null ? q.getAnswer() : "(unanswered)",
                q.getAnswer() != null))
            .toList();

        return new JobDetailView(
            job.getId(),
            job.getIntent(),
            job.getStatus().name(),
            badgeClass(job.getStatus()),
            job.getTaskType().name(),
            job.getRiskLevel().name(),
            job.getApprovalState().name(),
            job.getTargetExecutor(),
            orDash(job.getExecutorId()),
            orDash(job.getApprovedBy()),
            orDash(job.getRequester() != null ? job.getRequester().getTelegramUsername() : null),
            job.getAttempt(),
            job.getMaxAttempts(),
            orDash(job.getLastError()),
            formatDateTime(job.getStartedAt()),
            formatDateTime(job.getFinishedAt()),
            formatDateTime(job.getCreatedAt()),
            formatDateTime(job.getUpdatedAt()),
            prettyJson(job.getTaskPayloadJson()),
            job.getResultJson() != null ? prettyJson(job.getResultJson()) : "—",
            eventViews,
            logViews,
            lastSeq,
            isPlanTask,
            planText,
            planState,
            planBadge,
            qViews);
    }

    public List<JobLogLineView> toLogLines(List<JobLog> logs) {
        return logs.stream().filter(Objects::nonNull).map(this::toLogLine).toList();
    }

    public List<JobEventView> toEventViews(List<JobEvent> events) {
        return events.stream().map(this::toEventView).toList();
    }

    private JobEventView toEventView(JobEvent event) {
        boolean hasData = event.getDataJson() != null && !event.getDataJson().isBlank();
        return new JobEventView(
            event.getEventType(),
            hasData ? prettyJson(event.getDataJson()) : "—",
            formatDateTimeSec(event.getCreatedAt()),
            hasData);
    }

    private JobLogLineView toLogLine(JobLog log) {
        return new JobLogLineView(
            log.getSeq(),
            log.getLevel(),
            log.getLine(),
            formatTime(log.getCreatedAt()),
            LEVEL_CLASS.getOrDefault(log.getLevel(), "text-gray-700"));
    }

    private String prettyJson(String json) {
        try {
            Map<String, Object> parsed = jsonCodec.fromJson(json);
            return jsonCodec.toJson(parsed);
        } catch (Exception e) {
            return json;
        }
    }

    private static String badgeClass(JobStatus status) {
        return STATUS_BADGE.getOrDefault(status, "bg-gray-400 text-white");
    }

    private static String truncate(String text) {
        if (text == null) return "";
        return text.length() <= INTENT_MAX_LENGTH ? text : text.substring(0, INTENT_MAX_LENGTH) + "…";
    }

    private static String orDash(String value) {
        return value != null && !value.isBlank() ? value : "—";
    }

    private static String formatDateTime(OffsetDateTime dt) {
        return dt != null ? dt.format(DATE_TIME) : "—";
    }

    private static String formatDateTimeSec(OffsetDateTime dt) {
        return dt != null ? dt.format(DATE_TIME_SEC) : "—";
    }

    private static String formatTime(OffsetDateTime dt) {
        return dt != null ? dt.format(TIME_ONLY) : "—";
    }
}
