package com.ai.dev.garage.bot.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.dev.garage.bot.adapter.in.rest.dto.CreateJobRequest;
import com.ai.dev.garage.bot.adapter.in.rest.dto.JobResponse;
import com.ai.dev.garage.bot.application.port.in.JobLogQueries;
import com.ai.dev.garage.bot.application.port.in.JobManagement;
import com.ai.dev.garage.bot.domain.ApprovalState;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.ai.dev.garage.bot.domain.Requester;
import com.ai.dev.garage.bot.domain.RiskLevel;
import com.ai.dev.garage.bot.domain.TaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = JobController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
@ActiveProfiles("test")
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JobManagement jobManagement;

    @MockBean
    private JobLogQueries jobLogQueries;

    @MockBean
    private JobResponseMapper jobResponseMapper;

    @Test
    void shouldReturnOkWhenListJobs() throws Exception {
        Job job = Job.builder()
            .id(1L)
            .intent("x")
            .taskType(TaskType.SHELL_COMMAND)
            .riskLevel(RiskLevel.LOW)
            .approvalState(ApprovalState.APPROVED)
            .status(JobStatus.QUEUED)
            .build();
        when(jobManagement.listJobs(10)).thenReturn(List.of(job));
        when(jobResponseMapper.toResponse(job)).thenReturn(
            JobResponse.builder().jobId("1").intent("x").status("queued").build()
        );

        mockMvc.perform(get("/jobs").param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jobs").isArray());
    }

    @Test
    void shouldReturn404WhenGetJobNotFound() throws Exception {
        when(jobManagement.getJob("99")).thenThrow(new EntityNotFoundException("job not found"));

        mockMvc.perform(get("/jobs/99"))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn400WhenCreateJobInvalidBody() throws Exception {
        mockMvc.perform(post("/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnCreatedWhenCreateJobValid() throws Exception {
        CreateJobRequest req = CreateJobRequest.builder()
            .intent("git status")
            .requester(CreateJobRequest.Requester.builder()
                .telegramUserId(1L)
                .telegramChatId(2L)
                .build())
            .build();

        Job created = Job.builder()
            .id(5L)
            .intent("git status")
            .taskType(TaskType.SHELL_COMMAND)
            .riskLevel(RiskLevel.LOW)
            .approvalState(ApprovalState.APPROVED)
            .status(JobStatus.QUEUED)
            .build();
        when(jobManagement.createJob(eq("git status"), any(Requester.class), isNull())).thenReturn(created);
        when(jobResponseMapper.toResponse(created)).thenReturn(
            JobResponse.builder().jobId("5").intent("git status").status("queued").build()
        );

        mockMvc.perform(post("/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.jobId").value("5"));
    }

    @Test
    void shouldReturnJoinedLinesWhenGetLogs() throws Exception {
        when(jobLogQueries.getTail(eq(1L), eq(100))).thenReturn(List.of("line1", "line2"));

        mockMvc.perform(get("/jobs/1/logs").param("tail", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.logs").value("line1\nline2"));
    }
}
