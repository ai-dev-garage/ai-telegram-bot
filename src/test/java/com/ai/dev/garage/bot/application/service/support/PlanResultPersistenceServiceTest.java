package com.ai.dev.garage.bot.application.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.dev.garage.bot.application.port.out.JobStore;
import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.application.port.out.PlanSessionResult;
import com.ai.dev.garage.bot.application.port.out.PlanSessionResult.ParsedMessage;
import com.ai.dev.garage.bot.application.port.out.PlanSessionResult.ParsedQuestion;
import com.ai.dev.garage.bot.application.port.out.PlanSessionStore;
import com.ai.dev.garage.bot.application.service.PlanSessionService.PlanCompletionListener;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.ai.dev.garage.bot.domain.PlanSession;
import com.ai.dev.garage.bot.domain.PlanState;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlanResultPersistenceServiceTest {

    @Mock private PlanSessionStore planSessionStore;
    @Mock private JobStore jobStore;
    @Mock private JsonCodec jsonCodec;

    private PlanResultPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new PlanResultPersistenceService(planSessionStore, jobStore, jsonCodec);
    }

    @Test
    void shouldPersistQuestionsAndNotifyWhenCliReturnsQuestions() {
        PlanSession session = PlanSession.builder()
            .id(5L)
            .jobId(99L)
            .state(PlanState.PLANNING)
            .round(1)
            .build();
        Job job = Job.builder().id(99L).status(JobStatus.RUNNING).build();
        PlanCompletionListener listener = org.mockito.Mockito.mock(PlanCompletionListener.class);

        when(planSessionStore.findByJobId(99L)).thenReturn(Optional.of(session));
        when(jsonCodec.toJson(any())).thenReturn("[]");
        when(jobStore.findById(99L)).thenReturn(Optional.of(job));

        ParsedQuestion pq = new ParsedQuestion("What?", List.of("A", "B"));
        PlanSessionResult result = new PlanSessionResult(
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
        verify(listener, never()).onPlanReady(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void shouldSetPlanReadyAndNotifyWhenNoQuestions() {
        PlanSession session = PlanSession.builder()
            .id(5L)
            .jobId(99L)
            .state(PlanState.PLANNING)
            .round(1)
            .build();
        Job job = Job.builder().id(99L).status(JobStatus.RUNNING).build();
        PlanCompletionListener listener = org.mockito.Mockito.mock(PlanCompletionListener.class);

        when(planSessionStore.findByJobId(99L)).thenReturn(Optional.of(session));
        when(jobStore.findById(99L)).thenReturn(Optional.of(job));

        PlanSessionResult result = new PlanSessionResult(
            null,
            List.of(new ParsedMessage("done", List.of())),
            true,
            "Final plan body");

        service.persistCliResult(99L, result, listener);

        assertThat(session.getPlanText()).isEqualTo("Final plan body");
        assertThat(session.getState()).isEqualTo(PlanState.PLAN_READY);
        verify(planSessionStore, never()).saveQuestion(any());
        verify(listener).onPlanReady(99L);
        verify(listener, never()).onQuestionsReady(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void shouldNotFailWhenListenerIsNull() {
        PlanSession session = PlanSession.builder()
            .id(5L)
            .jobId(99L)
            .state(PlanState.PLANNING)
            .round(1)
            .build();
        when(planSessionStore.findByJobId(99L)).thenReturn(Optional.of(session));

        PlanSessionResult result = new PlanSessionResult(null, List.of(), true, "x");
        service.persistCliResult(99L, result, null);

        assertThat(session.getState()).isEqualTo(PlanState.PLAN_READY);
    }
}
