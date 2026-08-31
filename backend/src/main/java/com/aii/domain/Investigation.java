package com.aii.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Placeholder aggregate for Phase 5 (the investigation agent).
 * Not populated by any logic yet in this scaffold - just the shape,
 * so the orchestrator you build later has somewhere to write to.
 */
@Entity
@Table(name = "investigations")
@Getter
@Setter
@NoArgsConstructor
public class Investigation {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Enumerated(EnumType.STRING)
    private Status status = Status.NOT_STARTED;

    private Instant startedAt;
    private Instant completedAt;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @OneToMany(mappedBy = "investigation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Hypothesis> hypotheses = new ArrayList<>();

    public enum Status {
        NOT_STARTED, ANALYZING, RETRIEVING_KNOWLEDGE, GENERATING_HYPOTHESES,
        GATHERING_EVIDENCE, EVALUATING, ROOT_CAUSE_FOUND, RECOMMENDING, DONE
    }
}
