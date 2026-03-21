package com.ai.dev.garage.bot.adapter.out.persistence;

import com.ai.dev.garage.bot.domain.ApprovalState;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.ai.dev.garage.bot.domain.TaskType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JobJpaRepository extends JpaRepository<Job, Long> {
    List<Job> findAllByOrderByIdDesc(Pageable pageable);

    Page<Job> findByStatus(JobStatus status, Pageable pageable);

    List<Job> findAllByStatusOrderByIdDesc(JobStatus status, Pageable pageable);

    @Query("SELECT j.status, COUNT(j) FROM Job j GROUP BY j.status")
    List<Object[]> countGroupedByStatus();

    @Query("SELECT j FROM Job j WHERE j.status = :status AND j.approvalState = :approvalState "
         + "AND j.taskType NOT IN :excludedTypes ORDER BY j.id ASC LIMIT 1")
    Optional<Job> findNextRunnable(JobStatus status, ApprovalState approvalState, List<TaskType> excludedTypes);
}
