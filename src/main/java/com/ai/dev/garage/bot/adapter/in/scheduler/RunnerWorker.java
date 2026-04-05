package com.ai.dev.garage.bot.adapter.in.scheduler;

import com.ai.dev.garage.bot.application.execution.TaskExecutionOrchestrator;
import com.ai.dev.garage.bot.application.execution.model.TaskExecutionResult;
import com.ai.dev.garage.bot.application.port.out.JobLogAppender;
import com.ai.dev.garage.bot.application.service.JobService;
import com.ai.dev.garage.bot.config.RunnerProperties;
import com.ai.dev.garage.bot.domain.Job;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Background job poller. Excluded from the {@code test} profile so {@code @SpringBootTest} runs do not
 * hit the DB on a timer while Testcontainers Postgres is stopping (avoids Hikari / connection refused noise).
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class RunnerWorker {
    private final JobService jobService;
    private final TaskExecutionOrchestrator taskExecutionOrchestrator;
    private final JobLogAppender jobLogAppender;
    private final RunnerProperties runnerProperties;

    @Scheduled(fixedDelayString = "${app.runner.poll-interval-ms:2000}")
    public void pollAndRun() {
        Optional<Job> next = jobService.pollQueuedJob();
        if (next.isEmpty()) {
            return;
        }
        var job = next.get();
        job.setExecutorId(runnerProperties.getExecutorId());
        log.debug("Executing job id={} taskType={}", job.getId(), job.getTaskType());
        TaskExecutionResult result = taskExecutionOrchestrator.execute(job, jobLogAppender);
        if (result.selfManaged()) {
            log.debug("Job id={} is self-managed by executor — skipping state transition", job.getId());
            return;
        }
        if (result.success()) {
            jobService.markCompleted(job, result.summary(), result.exitCode());
            log.debug("Job id={} completed exitCode={}", job.getId(), result.exitCode());
        } else {
            jobService.markFailed(job, result.summary(), result.exitCode(), result.error());
            log.warn("Job id={} failed: {}", job.getId(), result.error());
        }
    }
}
