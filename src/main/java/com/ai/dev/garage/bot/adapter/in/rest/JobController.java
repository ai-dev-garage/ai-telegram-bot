package com.ai.dev.garage.bot.adapter.in.rest;

import com.ai.dev.garage.bot.adapter.in.rest.dto.ApproveJobRequest;
import com.ai.dev.garage.bot.adapter.in.rest.dto.CreateJobRequest;
import com.ai.dev.garage.bot.adapter.in.rest.dto.JobResponse;
import com.ai.dev.garage.bot.application.port.in.JobLogQueries;
import com.ai.dev.garage.bot.application.port.in.JobManagement;
import com.ai.dev.garage.bot.domain.Requester;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class JobController {
    private final JobManagement jobManagement;
    private final JobLogQueries jobLogQueries;
    private final JobResponseMapper jobResponseMapper;

    @GetMapping("/jobs")
    public Map<String, Object> listJobs(@RequestParam(defaultValue = "10") int limit) {
        List<JobResponse> jobs = jobManagement.listJobs(limit).stream().map(jobResponseMapper::toResponse).toList();
        return Map.of("jobs", jobs);
    }

    @GetMapping("/jobs/{jobId}")
    public JobResponse getJob(@PathVariable String jobId) {
        return jobResponseMapper.toResponse(jobManagement.getJob(jobId));
    }

    @GetMapping("/jobs/{jobId}/logs")
    public Map<String, String> getLogs(@PathVariable String jobId, @RequestParam(defaultValue = "100") int tail) {
        Long resolvedId = "last".equalsIgnoreCase(jobId)
            ? jobManagement.getJob("last").getId()
            : Long.valueOf(jobId);
        List<String> logs = jobLogQueries.getTail(resolvedId, tail);
        return Map.of("logs", String.join("\n", logs));
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse createJob(@Valid @RequestBody CreateJobRequest request) {
        Requester requester = toDomainRequester(request.getRequester());
        return jobResponseMapper.toResponse(jobManagement.createJob(request.getIntent(), requester, null));
    }

    @PostMapping("/jobs/{jobId}/approve")
    public JobResponse approve(@PathVariable String jobId, @RequestBody(required = false) ApproveJobRequest request) {
        String approvedBy = request == null ? "" : request.getApprovedBy();
        return jobResponseMapper.toResponse(jobManagement.approve(jobId, approvedBy));
    }

    @PostMapping("/jobs/{jobId}/reject")
    public JobResponse reject(@PathVariable String jobId) {
        return jobResponseMapper.toResponse(jobManagement.reject(jobId));
    }

    @PostMapping("/jobs/{jobId}/cancel")
    public JobResponse cancel(@PathVariable String jobId) {
        return jobResponseMapper.toResponse(jobManagement.cancel(jobId));
    }

    private static Requester toDomainRequester(CreateJobRequest.Requester r) {
        return Requester.builder()
            .telegramUserId(r.getTelegramUserId())
            .telegramChatId(r.getTelegramChatId())
            .telegramUsername(r.getTelegramUsername())
            .build();
    }
}
