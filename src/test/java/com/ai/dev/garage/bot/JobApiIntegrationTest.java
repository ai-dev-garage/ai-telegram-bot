package com.ai.dev.garage.bot;

import com.ai.dev.garage.bot.adapter.out.persistence.JobJpaRepository;
import com.ai.dev.garage.bot.adapter.out.persistence.JobLogJpaRepository;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobLog;
import com.ai.dev.garage.bot.domain.JobLogSource;
import com.ai.dev.garage.bot.domain.Requester;
import com.ai.dev.garage.bot.domain.RiskLevel;
import com.ai.dev.garage.bot.domain.TaskType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class JobApiIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JobJpaRepository jobJpaRepository;

    @Autowired
    private JobLogJpaRepository jobLogJpaRepository;

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void shouldPersistJobLogRoundTripWhenNativePostgresEnum() {
        Job job = Job.builder()
            .requester(Requester.builder().telegramUserId(9L).telegramChatId(99L).build())
            .intent("enum regression test")
            .taskType(TaskType.SHELL_COMMAND)
            .riskLevel(RiskLevel.LOW)
            .build();
        job = jobJpaRepository.save(job);

        JobLog backendLine = JobLog.builder()
            .jobId(job.getId())
            .seq(1)
            .level("INFO")
            .source(JobLogSource.BACKEND)
            .line("backend log")
            .build();
        JobLog agentLine = JobLog.builder()
            .jobId(job.getId())
            .seq(2)
            .level("INFO")
            .source(JobLogSource.AGENT)
            .line("agent log")
            .build();
        backendLine = jobLogJpaRepository.saveAndFlush(backendLine);
        agentLine = jobLogJpaRepository.saveAndFlush(agentLine);

        assertThat(jobLogJpaRepository.findById(backendLine.getId()).orElseThrow().getSource())
            .isEqualTo(JobLogSource.BACKEND);
        assertThat(jobLogJpaRepository.findById(agentLine.getId()).orElseThrow().getSource())
            .isEqualTo(JobLogSource.AGENT);
    }

    @Test
    void shouldReturnCreatedWhenPostJobs() {
        String url = "http://localhost:" + port + "/jobs";
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String json = """
            {"intent":"git status","requester":{"telegramUserId":1,"telegramChatId":2}}
            """;
        ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(json, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("jobId");
    }

    @Test
    void shouldReturnOkWhenGetJobs() {
        String url = "http://localhost:" + port + "/jobs?limit=5";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("jobs");
    }
}
