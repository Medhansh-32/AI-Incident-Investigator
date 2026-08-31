package com.aii.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
public class KnowledgeDocument {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType type;

    private String serviceName;   // denormalized for simple metadata filtering
    private String environment;
    private String sourceUrl;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public enum DocumentType {
        RUNBOOK, POSTMORTEM, ARCHITECTURE, ADR, INCIDENT_SUMMARY
    }
}
