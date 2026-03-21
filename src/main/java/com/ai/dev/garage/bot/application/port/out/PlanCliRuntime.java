package com.ai.dev.garage.bot.application.port.out;

import com.ai.dev.garage.bot.domain.Job;

/**
 * SPI for running CLI agents in plan mode (conversational, structured output).
 * Separate from {@link AgentTaskRuntime} which is fire-and-forget.
 */
public interface PlanCliRuntime {

    /**
     * Start a new plan session. Blocks until the CLI process completes.
     *
     * @return parsed result including session ID, assistant messages, and extracted questions
     */
    PlanSessionResult startPlan(Job job, String prompt);

    /**
     * Resume an existing plan session with the user's compiled answers.
     * Blocks until the CLI process completes.
     */
    PlanSessionResult resumePlan(Job job, String cliSessionId, String userMessage);
}
