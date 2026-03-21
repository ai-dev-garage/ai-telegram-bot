package com.ai.dev.garage.bot.adapter.out.persistence;

import com.ai.dev.garage.bot.application.port.in.DashboardQueries;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DashboardQueriesJpaAdapter implements DashboardQueries {

    private final JobJpaRepository jobJpaRepository;

    @Override
    public Map<JobStatus, Long> countsByStatus() {
        EnumMap<JobStatus, Long> map = new EnumMap<>(JobStatus.class);
        for (JobStatus s : JobStatus.values()) {
            map.put(s, 0L);
        }
        for (Object[] row : jobJpaRepository.countGroupedByStatus()) {
            map.merge((JobStatus) row[0], (Long) row[1], Long::sum);
        }
        return map;
    }

    @Override
    public List<Job> recentFailures(int limit) {
        int n = Math.max(1, limit);
        return jobJpaRepository.findAllByStatusOrderByIdDesc(JobStatus.FAILED, PageRequest.of(0, n));
    }
}
