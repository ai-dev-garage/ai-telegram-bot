package com.ai.dev.garage.bot.application.service;

import com.ai.dev.garage.bot.adapter.out.filesystem.PlanFileExporter;
import com.ai.dev.garage.bot.application.port.out.JobStore;
import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.application.port.out.PlanCliRuntime;
import com.ai.dev.garage.bot.application.port.out.PlanSessionStore;
import com.ai.dev.garage.bot.application.service.support.PlanResultPersistenceService;
import com.ai.dev.garage.bot.application.support.AllowedPathValidator;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.ai.dev.garage.bot.domain.PlanQuestion;
import com.ai.dev.garage.bot.domain.PlanSession;
import com.ai.dev.garage.bot.domain.PlanState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanSessionServiceTest {

    @Mock
    private JobStore jobStore;
    @Mock
    private PlanSessionStore planSessionStore;
    @Mock
    private PlanCliRuntime planCliRuntime;
    @Mock
    private JsonCodec jsonCodec;
    @Mock
    private AllowedPathValidator allowedPathValidator;
    @Mock
    private TodoCompletionHook todoCompletionHook;
    @Mock
    private PlanFileExporter planFileExporter;
    @Mock
    private PlanResultPersistenceService planResultPersistenceService;

    private PlanSessionService service;

    @BeforeEach
    void setUp() {
        service = new PlanSessionService(
            jobStore, planSessionStore, planCliRuntime, jsonCodec,
            allowedPathValidator, todoCompletionHook, planFileExporter, planResultPersistenceService);
    }

    private static PlanSession readySession(long id, long jobId) {
        return PlanSession.builder()
            .id(id)
            .jobId(jobId)
            .state(PlanState.PLAN_READY)
            .planText("The final plan text.")
            .round(2)
            .build();
    }

    private static Job runningJob(long id, String intent) {
        return Job.builder()
            .id(id)
            .intent(intent)
            .status(JobStatus.RUNNING)
            .build();
    }

    @Nested
    class PausePlan {

        @Test
        void shouldTransitionToPausedWhenPlanReady() {
            var session = readySession(10L, 42L);
            var job = runningJob(42L, "Build auth");

            when(planSessionStore.findByJobId(42L)).thenReturn(Optional.of(session));
            when(jobStore.findById(42L)).thenReturn(Optional.of(job));
            when(planSessionStore.findAllQuestionsBySession(10L)).thenReturn(List.of());
            when(jsonCodec.toJson(anyMap())).thenReturn("{}");
            when(jobStore.save(job)).thenReturn(job);

            var result = service.pausePlan(42L);

            assertThat(session.getState()).isEqualTo(PlanState.PAUSED);
            assertThat(result.getStatus()).isEqualTo(JobStatus.PAUSED);
            verify(planSessionStore).save(session);
            verify(planFileExporter).exportPlan(eq(job), eq(session), eq(List.of()));
        }

        @Test
        void shouldRejectPauseWhenNotPlanReady() {
            var session = PlanSession.builder()
                .id(10L).jobId(42L).state(PlanState.AWAITING_INPUT).build();
            when(planSessionStore.findByJobId(42L)).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> service.pausePlan(42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not ready to pause");
        }
    }

    @Nested
    class ApprovePlan {

        @Test
        void shouldExportPlanFileOnApprove() {
            var session = readySession(10L, 42L);
            var job = runningJob(42L, "Build auth");
            var questions = List.<PlanQuestion>of();

            when(planSessionStore.findByJobId(42L)).thenReturn(Optional.of(session));
            when(jobStore.findById(42L)).thenReturn(Optional.of(job));
            when(planSessionStore.findAllQuestionsBySession(10L)).thenReturn(questions);
            when(planFileExporter.exportPlan(job, session, questions))
                .thenReturn("/home/user/.ai-dev-garage/plans/plan_build-auth_0000002a.plan.md");
            when(jsonCodec.toJson(anyMap())).thenReturn("{}");
            when(jobStore.save(job)).thenReturn(job);

            var result = service.approvePlan(42L);

            assertThat(session.getState()).isEqualTo(PlanState.APPROVED);
            assertThat(result.getStatus()).isEqualTo(JobStatus.SUCCESS);
            verify(planFileExporter).exportPlan(eq(job), eq(session), eq(questions));
            verify(todoCompletionHook).onJobCompleted(42L);
        }

        @Test
        void shouldAllowApproveFromPausedState() {
            var session = PlanSession.builder()
                .id(10L).jobId(42L).state(PlanState.PAUSED).planText("Plan.").round(1).build();
            var job = runningJob(42L, "Build auth");

            when(planSessionStore.findByJobId(42L)).thenReturn(Optional.of(session));
            when(jobStore.findById(42L)).thenReturn(Optional.of(job));
            when(planSessionStore.findAllQuestionsBySession(10L)).thenReturn(List.of());
            when(jsonCodec.toJson(anyMap())).thenReturn("{}");
            when(jobStore.save(job)).thenReturn(job);

            service.approvePlan(42L);

            assertThat(session.getState()).isEqualTo(PlanState.APPROVED);
        }
    }

    @Nested
    class ListActivePlans {

        @Test
        void shouldIncludePausedInActiveStates() {
            when(planSessionStore.findByStateIn(
                List.of(PlanState.PLANNING, PlanState.AWAITING_INPUT, PlanState.PLAN_READY, PlanState.PAUSED)))
                .thenReturn(List.of());

            List<PlanSession> result = service.listActivePlans();

            assertThat(result).isEmpty();
            verify(planSessionStore).findByStateIn(
                List.of(PlanState.PLANNING, PlanState.AWAITING_INPUT, PlanState.PLAN_READY, PlanState.PAUSED));
        }
    }
}
