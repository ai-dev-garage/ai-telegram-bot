package com.ai.dev.garage.bot.adapter.in.web;

import com.ai.dev.garage.bot.adapter.in.web.dto.JobDetailView;
import com.ai.dev.garage.bot.adapter.in.web.dto.JobLogLineView;
import com.ai.dev.garage.bot.application.port.in.JobActions;
import com.ai.dev.garage.bot.application.port.in.JobViewQueries;
import com.ai.dev.garage.bot.application.port.out.PlanSessionStore;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobEvent;
import com.ai.dev.garage.bot.domain.JobLog;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.ai.dev.garage.bot.domain.PlanQuestion;
import com.ai.dev.garage.bot.domain.PlanSession;
import com.ai.dev.garage.bot.domain.TaskType;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/ui/jobs")
@RequiredArgsConstructor
public class JobViewController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int LOG_TAIL_SIZE = 200;
    private static final int HTMX_STOP_POLLING = 286;

    private final JobViewQueries jobViewQueries;
    private final JobActions jobActions;
    private final ViewDtoMapper mapper;
    private final PlanSessionStore planSessionStore;

    @GetMapping
    public String list(
        @RequestParam(required = false) JobStatus status,
        @RequestParam(defaultValue = "createdAt") String sort,
        @RequestParam(defaultValue = "desc") String dir,
        @RequestHeader(value = "HX-Request", required = false) String hxRequest,
        Model model) {

        List<Job> jobs = jobViewQueries.listJobs(status, DEFAULT_LIMIT, sort, dir);

        model.addAttribute("jobs", mapper.toSummaryList(jobs));
        model.addAttribute("currentStatus", status);
        model.addAttribute("currentSort", sort);
        model.addAttribute("currentDir", dir);
        model.addAttribute("statuses", JobStatus.values());

        return isHtmx(hxRequest) ? "fragments/job-table :: table" : "jobs/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, Model model) {
        Job job = jobViewQueries.getJob(id);
        List<JobEvent> events = jobViewQueries.getEvents(id);
        List<JobLog> logs = jobViewQueries.getLogTail(id, LOG_TAIL_SIZE);

        var view = job.getTaskType() == TaskType.PLAN_TASK
            ? buildPlanDetailView(job, events, logs)
            : mapper.toDetailView(job, events, logs);
        model.addAttribute("job", view);
        model.addAttribute("statusView", mapper.toStatusView(job));
        return "jobs/detail";
    }

    private JobDetailView buildPlanDetailView(
        Job job, List<JobEvent> events, List<JobLog> logs) {
        PlanSession session = planSessionStore.findByJobId(job.getId()).orElse(null);
        List<PlanQuestion> questions = session != null
            ? planSessionStore.findAllQuestionsBySession(session.getId())
            : List.of();
        return mapper.toDetailView(job, events, logs, session, questions);
    }

    @GetMapping("/{id}/status")
    public String status(@PathVariable long id, Model model, HttpServletResponse response) {
        Job job = jobViewQueries.getJob(id);
        model.addAttribute("statusView", mapper.toStatusView(job));
        if (mapper.isTerminal(job)) {
            response.setStatus(HTMX_STOP_POLLING);
        }
        return "fragments/job-status-panel :: panel";
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable long id, Model model) {
        Job job = jobActions.cancel(id);
        model.addAttribute("statusView", mapper.toStatusView(job));
        return "fragments/job-status-panel :: panel";
    }

    @PostMapping("/{id}/retry")
    public String retry(@PathVariable long id, Model model) {
        Job job = jobActions.retry(id);
        model.addAttribute("statusView", mapper.toStatusView(job));
        return "fragments/job-status-panel :: panel";
    }

    @GetMapping("/{id}/logs")
    public String logsAfter(
        @PathVariable long id,
        @RequestParam int after,
        Model model,
        HttpServletResponse response) {

        List<JobLog> logs = jobViewQueries.getLogsAfter(id, after);
        List<JobLogLineView> lines = mapper.toLogLines(logs);
        int lastSeq = lines.isEmpty() ? after : lines.getLast().seq();

        model.addAttribute("lines", lines);
        model.addAttribute("lastLogSeq", lastSeq);
        response.setHeader("HX-Trigger", "{\"logWatermark\":" + lastSeq + "}");

        return "fragments/log-viewer :: lines";
    }

    @GetMapping("/{id}/events")
    public String events(@PathVariable long id, Model model) {
        List<JobEvent> events = jobViewQueries.getEvents(id);
        model.addAttribute("events", mapper.toEventViews(events));
        return "fragments/event-timeline :: events";
    }

    private static boolean isHtmx(String headerValue) {
        return Objects.equals(headerValue, "true");
    }
}
