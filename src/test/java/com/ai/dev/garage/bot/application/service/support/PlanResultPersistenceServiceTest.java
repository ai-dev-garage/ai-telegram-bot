package com.ai.dev.garage.bot.application.service.support;

import com.ai.dev.garage.bot.application.port.out.JobStore;
import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.application.port.out.PlanSessionResult;
import com.ai.dev.garage.bot.application.port.out.PlanSessionResult.ParsedMessage;
import com.ai.dev.garage.bot.application.port.out.PlanSessionResult.ParsedQuestion;
import com.ai.dev.garage.bot.application.port.out.PlanSessionStore;
import com.ai.dev.garage.bot.application.service.PlanSessionService.PlanCompletionListener;
import com.ai.dev.garage.bot.application.service.TodoCompletionHook;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.ai.dev.garage.bot.domain.PlanSession;
import com.ai.dev.garage.bot.domain.PlanState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanResultPersistenceServiceTest {

    @Mock
    private PlanSessionStore planSessionStore;
    @Mock
    private JobStore jobStore;
    @Mock
    private JsonCodec jsonCodec;
    @Mock
    private TodoCompletionHook todoCompletionHook;

    private PlanResultPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new PlanResultPersistenceService(planSessionStore, jobStore, jsonCodec, todoCompletionHook);
    }

    @Test
    void shouldPersistQuestionsAndNotifyWhenCliReturnsQuestions() {
        var session = PlanSession.builder()
            .id(5L)
            .jobId(99L)
            .state(PlanState.PLANNING)
            .round(1)
            .build();
        var job = Job.builder().id(99L).status(JobStatus.RUNNING).build();
        var listener = mock(PlanCompletionListener.class);

        when(planSessionStore.findByJobId(99L)).thenReturn(Optional.of(session));
        when(jsonCodec.toJson(any())).thenReturn("[]");
        when(jobStore.findById(99L)).thenReturn(Optional.of(job));

        var pq = new ParsedQuestion("What?", List.of("A", "B"));
        var result = new PlanSessionResult(
            "cli-sid-1",
            List.of(new ParsedMessage("msg", List.of(pq))),
            false,
            "");

        service.persistCliResult(99L, result, listener);

        assertThat(session.getState()).isEqualTo(PlanState.AWAITING_INPUT);
        assertThat(session.getCliSessionId()).isEqualTo("cli-sid-1");
        verify(planSessionStore).saveQuestion(any());
        verify(planSessionStore).save(session);
        assertThat(job.getStatus()).isEqualTo(JobStatus.AWAITING_INPUT);
        verify(jobStore).save(job);
        verify(listener).onQuestionsReady(99L, 5L);
        verify(listener, never()).onPlanReady(anyLong());
        verifyNoInteractions(todoCompletionHook);
    }

    @Test
    void shouldSetPlanReadyAndNotifyWhenNoQuestions() {
        var session = PlanSession.builder()
            .id(5L)
            .jobId(99L)
            .state(PlanState.PLANNING)
            .round(1)
            .build();
        var job = Job.builder().id(99L).status(JobStatus.RUNNING).build();
        var listener = mock(PlanCompletionListener.class);

        when(planSessionStore.findByJobId(99L)).thenReturn(Optional.of(session));
        when(jobStore.findById(99L)).thenReturn(Optional.of(job));

        var result = new PlanSessionResult(
            null,
            List.of(new ParsedMessage("done", List.of())),
            true,
            "Final plan body");

        service.persistCliResult(99L, result, listener);

        assertThat(session.getPlanText()).isEqualTo("Final plan body");
        assertThat(session.getState()).isEqualTo(PlanState.PLAN_READY);
        verify(planSessionStore, never()).saveQuestion(any());
        verify(listener).onPlanReady(99L);
        verify(listener, never()).onQuestionsReady(anyLong(), anyLong());
        verifyNoInteractions(todoCompletionHook);
    }

    @Test
    void shouldMarkJobAndSessionFailedWhenCliUnhealthy() {
        var session = PlanSession.builder()
            .id(5L)
            .jobId(99L)
            .state(PlanState.PLANNING)
            .round(1)
            .build();
        var job = Job.builder().id(99L).status(JobStatus.RUNNING).build();
        var listener = mock(PlanCompletionListener.class);

        when(planSessionStore.findByJobId(99L)).thenReturn(Optional.of(session));
        when(jobStore.findById(99L)).thenReturn(Optional.of(job));

        var result = new PlanSessionResult(
            null,
            List.of(),
            false,
            "",
            false,
            "process exited with code 1");

        service.persistCliResult(99L, result, listener);

        assertThat(session.getState()).isEqualTo(PlanState.FAILED);
        assertThat(session.getPlanText()).isEqualTo("process exited with code 1");
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getLastError()).isEqualTo("process exited with code 1");
        assertThat(job.getFinishedAt()).isNotNull();
        verify(todoCompletionHook).onJobFailed(99L);
        verify(listener).onPlanError(99L, "process exited with code 1");
        verify(listener, never()).onPlanReady(anyLong());
    }

    @Test
    void shouldNotFailWhenListenerIsNull() {
        var session = PlanSession.builder()
            .id(5L)
            .jobId(99L)
            .state(PlanState.PLANNING)
            .round(1)
            .build();
        when(planSessionStore.findByJobId(99L)).thenReturn(Optional.of(session));

        var result = new PlanSessionResult(null, List.of(), true, "x");
        service.persistCliResult(99L, result, null);

        assertThat(session.getState()).isEqualTo(PlanState.PLAN_READY);
        verifyNoInteractions(todoCompletionHook);
    }
}
