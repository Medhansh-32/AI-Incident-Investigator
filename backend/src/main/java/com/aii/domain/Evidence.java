package com.aii.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "evidence")
@Getter
@Setter
@NoArgsConstructor
public class Evidence {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "hypothesis_id", nullable = false)
    private Hypothesis hypothesis;

    @Enumerated(EnumType.STRING)
    private Type type;

    private String source; // e.g. "monitoring-mcp", "github-mcp", "rag"

    @Column(columnDefinition = "TEXT")
    private String content;

    private boolean supports; // true = supports the hypothesis, false = contradicts

    private double weight;

    public enum Type {
        METRIC, LOG, COMMIT, DEPLOYMENT, DOCUMENT, PRIOR_INCIDENT
    }
}
