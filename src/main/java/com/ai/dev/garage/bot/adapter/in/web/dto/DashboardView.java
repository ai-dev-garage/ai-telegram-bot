package com.ai.dev.garage.bot.adapter.in.web.dto;

import java.util.List;

public record DashboardView(
    List<StatusCountView> counts,
    List<JobSummaryView> recentFailures
) {
}
