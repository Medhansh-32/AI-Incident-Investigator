package com.aii.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_events")
@Getter
@Setter
@NoArgsConstructor
public class IncidentEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType type;

    @Column(columnDefinition = "TEXT")
    private String payload; // JSON string - kept simple for the scaffold

    private String source;

    @Column(nullable = false)
    private Instant occurredAt = Instant.now();

    public enum EventType {
        DEPLOYMENT, METRIC_SPIKE, LOG_ERROR, STATE_CHANGE, NOTE
    }
}
