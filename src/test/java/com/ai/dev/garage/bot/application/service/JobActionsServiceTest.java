package com.ai.dev.garage.bot.application.service;

import com.ai.dev.garage.bot.application.port.out.JobStore;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.ai.dev.garage.bot.domain.Requester;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.persistence.EntityNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobActionsServiceTest {

    @Mock
    private JobStore jobStore;

    @InjectMocks
    private JobActionsService jobActionsService;

    @Test
    void shouldThrowWhenCancelCalledForMissingJob() {
        when(jobStore.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobActionsService.cancel(99L))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("job not found");

        verify(jobStore).findById(99L);
    }

    @ParameterizedTest
    @EnumSource(names = {"SUCCESS", "FAILED", "CANCELLED"})
    void shouldThrowWhenCancelCalledForTerminalJob(JobStatus terminal) {
        var job = Job.builder()
            .id(1L)
            .requester(Requester.builder().telegramUserId(1L).telegramChatId(1L).build())
            .status(terminal)
            .intent("x")
            .build();
        when(jobStore.findById(1L)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobActionsService.cancel(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("job already terminal");
    }

    @Test
    void shouldSetCancelledWhenCancelCalledForActiveJob() {
        var job = Job.builder()
            .id(3L)
            .requester(Requester.builder().telegramUserId(1L).telegramChatId(1L).build())
            .status(JobStatus.QUEUED)
            .intent("x")
            .build();
        when(jobStore.findById(3L)).thenReturn(Optional.of(job));
        when(jobStore.save(job)).thenReturn(job);

        var saved = jobActionsService.cancel(3L);

        assertThat(saved.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(saved.getFinishedAt()).isNotNull();
        verify(jobStore).findById(3L);
        verify(jobStore).save(job);
    }

    @Test
    void shouldThrowWhenRetryCalledForMissingJob() {
        when(jobStore.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobActionsService.retry(7L))
            .isInstanceOf(EntityNotFoundException.class);

        verify(jobStore).findById(7L);
    }

    @Test
    void shouldThrowWhenRetryCalledForNonFailedJob() {
        var job = Job.builder()
            .id(2L)
            .requester(Requester.builder().telegramUserId(1L).telegramChatId(1L).build())
            .status(JobStatus.SUCCESS)
            .intent("x")
            .build();
        when(jobStore.findById(2L)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobActionsService.retry(2L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("can only retry failed jobs");
    }

    @Test
    void shouldRequeueWhenRetryCalledForFailedJob() {
        var job = Job.builder()
            .id(5L)
            .requester(Requester.builder().telegramUserId(1L).telegramChatId(1L).build())
            .status(JobStatus.FAILED)
            .intent("x")
            .attempt(2)
            .lastError("boom")
            .resultJson("{ \"err\": true }")
            .build();
        when(jobStore.findById(5L)).thenReturn(Optional.of(job));
        when(jobStore.save(job)).thenReturn(job);

        var saved = jobActionsService.retry(5L);

        assertThat(saved.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(saved.getFinishedAt()).isNull();
        assertThat(saved.getLastError()).isNull();
        assertThat(saved.getResultJson()).isNull();
        assertThat(saved.getAttempt()).isEqualTo(3);
        verify(jobStore).save(job);
    }
}
