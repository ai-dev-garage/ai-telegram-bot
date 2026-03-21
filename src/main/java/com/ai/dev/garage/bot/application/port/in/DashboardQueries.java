package com.ai.dev.garage.bot.application.port.in;

import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;
import java.util.List;
import java.util.Map;

public interface DashboardQueries {

    Map<JobStatus, Long> countsByStatus();

    List<Job> recentFailures(int limit);
}
