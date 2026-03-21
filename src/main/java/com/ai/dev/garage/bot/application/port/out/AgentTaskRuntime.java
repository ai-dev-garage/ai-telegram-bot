package com.ai.dev.garage.bot.application.port.out;

import com.ai.dev.garage.bot.domain.Job;

/** SPI for handing off {@link com.ai.dev.garage.bot.domain.TaskType#AGENT_TASK} jobs to an external CLI (Cursor, Claude, etc.). */
public interface AgentTaskRuntime {

    boolean startForJob(Job job);
}
