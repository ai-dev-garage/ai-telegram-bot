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
@Table(
    name = "plan_questions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"plan_session_id", "round", "seq"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_session_id", nullable = false)
    private PlanSession planSession;

    @Column(nullable = false)
    private Integer round;

    @Column(nullable = false)
    private Integer seq;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String options;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(name = "answered_at")
    private OffsetDateTime answeredAt;
}
