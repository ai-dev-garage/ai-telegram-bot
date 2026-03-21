package com.ai.dev.garage.bot.adapter.out.persistence;

import com.ai.dev.garage.bot.domain.JobLog;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobLogJpaRepository extends JpaRepository<JobLog, Long> {

    @Query("select coalesce(max(l.seq), 0) from JobLog l where l.jobId = :jobId")
    int maxSeq(@Param("jobId") Long jobId);

    List<JobLog> findByJobIdOrderBySeqDesc(Long jobId, Pageable pageable);

    List<JobLog> findByJobIdAndSeqGreaterThanOrderBySeqAsc(Long jobId, int afterSeq);
}
