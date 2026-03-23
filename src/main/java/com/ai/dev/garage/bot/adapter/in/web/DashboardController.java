package com.ai.dev.garage.bot.adapter.in.web;

import com.ai.dev.garage.bot.adapter.in.web.dto.DashboardView;
import com.ai.dev.garage.bot.application.port.in.DashboardQueries;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/ui")
@RequiredArgsConstructor
public class DashboardController {

    private static final int RECENT_FAILURES_LIMIT = 10;

    private final DashboardQueries dashboardQueries;
    private final ViewDtoMapper mapper;

    @GetMapping
    public String dashboard(Model model) {
        DashboardView view = mapper.toDashboardView(
            dashboardQueries.countsByStatus(),
            dashboardQueries.recentFailures(RECENT_FAILURES_LIMIT));
        model.addAttribute("dashboard", view);
        model.addAttribute("counts", view.counts());
        model.addAttribute("jobs", view.recentFailures());
        return "dashboard";
    }

    @GetMapping("/dashboard/counts")
    public String counts(Model model) {
        model.addAttribute("counts", mapper.toStatusCounts(dashboardQueries.countsByStatus()));
        return "fragments/status-counts :: counts";
    }

    @GetMapping("/dashboard/failures")
    public String failures(Model model) {
        model.addAttribute("jobs", mapper.toSummaryList(dashboardQueries.recentFailures(RECENT_FAILURES_LIMIT)));
        return "fragments/job-table :: table";
    }
}
