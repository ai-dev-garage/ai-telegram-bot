package com.ai.dev.garage.bot.adapter.in.filewatcher;

import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.application.service.JobService;
import com.ai.dev.garage.bot.config.RunnerProperties;
import com.ai.dev.garage.bot.domain.Job;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityNotFoundException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.file.FileReadingMessageSource;
import org.springframework.integration.file.filters.SimplePatternFileListFilter;
import org.springframework.messaging.MessageChannel;

@Configuration
@RequiredArgsConstructor
public class AgentResultFileWatcher {
    private static final Logger agentLog = LoggerFactory.getLogger("com.ai.dev.garage.bot.agent");
    private static final Logger log = LoggerFactory.getLogger(AgentResultFileWatcher.class);

    private final RunnerProperties runnerProperties;
    private final JsonCodec jsonCodec;
    private final JobService jobService;
    private final ObjectProvider<TelegramBotClient> telegramBotClientProvider;

    @PostConstruct
    void ensureDirectories() {
        if (runnerProperties.getAgentTasksDir() == null) {
            log.warn("agent-tasks-dir is not configured; agent result file watcher will not create directories");
            return;
        }
        try {
            Files.createDirectories(resultsDir());
            Files.createDirectories(processedDir());
        } catch (IOException e) {
            agentLog.error("Failed to create agent result directories: {}", e.getMessage(), e);
        }
    }

    @Bean
    public MessageChannel agentResultChannel() {
        return new DirectChannel();
    }

    @Bean
    public IntegrationFlow agentResultFlow() {
        FileReadingMessageSource source = new FileReadingMessageSource();
        source.setDirectory(resultsDir().toFile());
        source.setFilter(new SimplePatternFileListFilter("result-*.json"));
        source.setAutoCreateDirectory(true);
        source.setUseWatchService(true);
        source.setWatchEvents(
                FileReadingMessageSource.WatchEventType.CREATE,
                FileReadingMessageSource.WatchEventType.MODIFY);

        return IntegrationFlow.from(source, e -> e.poller(Pollers.fixedDelay(2000)))
                .channel("agentResultChannel")
                .get();
    }

    @ServiceActivator(inputChannel = "agentResultChannel")
    public void processResultFile(File resultFile) {
        log.debug("Processing agent result file: {}", resultFile.getName());
        try {
            String content = Files.readString(resultFile.toPath());
            Map<String, Object> result = jsonCodec.fromJson(content);

            String jobIdStr = String.valueOf(result.get("job_id"));
            boolean success = Boolean.TRUE.equals(result.get("success"));
            String summary = String.valueOf(result.getOrDefault("summary", ""));
            String error = String.valueOf(result.getOrDefault("error", ""));

            Job job = jobService.resolveJob(jobIdStr);

            if (success) {
                jobService.markCompleted(job, summary, 0);
                log.info("Agent task completed for job {}: {}", jobIdStr, summary);
            } else {
                jobService.markFailed(job, summary, -1, error);
                agentLog.warn("Agent task failed for job {}: {}", jobIdStr, error);
            }

            notifyRequester(job, success, summary, error);
            moveToProcessed(resultFile);

        } catch (EntityNotFoundException e) {
            agentLog.warn("Orphan agent result file {} (job not found), moving to processed: {}",
                    resultFile.getName(), e.getMessage());
            moveToProcessed(resultFile);
        } catch (Exception e) {
            agentLog.error("Failed to process agent result file {}: {}", resultFile.getName(), e.getMessage(), e);
        }
    }

    private void notifyRequester(Job job, boolean success, String summary, String error) {
        TelegramBotClient telegramBotClient = telegramBotClientProvider.getIfAvailable();
        if (telegramBotClient == null || job.getRequester() == null
                || job.getRequester().getTelegramChatId() == null) {
            return;
        }
        try {
            String emoji = success ? "✅" : "❌";
            String detail = success ? summary : error;
            String message = String.format("%s Job #%d %s\n%s",
                    emoji, job.getId(), success ? "completed" : "failed", detail);
            telegramBotClient.sendPlain(job.getRequester().getTelegramChatId(), message);
        } catch (Exception e) {
            log.warn("Failed to send Telegram notification for job {}: {}", job.getId(), e.getMessage());
        }
    }

    private void moveToProcessed(File resultFile) {
        try {
            Path target = processedDir().resolve(resultFile.getName());
            Files.move(resultFile.toPath(), target);
            log.debug("Moved processed result file to: {}", target);
        } catch (IOException e) {
            log.warn("Failed to move result file {}: {}", resultFile.getName(), e.getMessage());
        }
    }

    private Path resultsDir() {
        return Path.of(runnerProperties.getAgentTasksDir(), "results");
    }

    private Path processedDir() {
        return Path.of(runnerProperties.getAgentTasksDir(), "processed");
    }
}
