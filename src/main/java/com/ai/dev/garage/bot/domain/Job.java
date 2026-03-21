package com.ai.dev.garage.bot.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private Requester requester;

    @Column(nullable = false, length = 4000)
    private String intent;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false)
    private TaskType taskType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "risk_level", nullable = false)
    private RiskLevel riskLevel;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "approval_state", nullable = false)
    private ApprovalState approvalState = ApprovalState.PENDING;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private JobStatus status = JobStatus.QUEUED;

    @Builder.Default
    @Column(name = "target_executor", nullable = false)
    private String targetExecutor = "mac_mini";

    @Column(name = "executor_id", length = 128)
    private String executorId;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "task_payload_json", columnDefinition = "jsonb", nullable = false)
    private String taskPayloadJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb")
    private String resultJson;

    @Builder.Default
    @Column(nullable = false)
    private Integer attempt = 1;

    @Builder.Default
    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts = 3;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Set on create response only; not persisted. */
    @Transient
    private Boolean agentCliInvoked;

    @PrePersist
    public void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
