package com.aii.dto;

import com.aii.domain.Incident;
import com.aii.domain.IncidentStatus;
import com.aii.domain.Severity;

import java.time.Instant;
import java.util.UUID;

public record IncidentResponse(
        UUID id,
        String title,
        String description,
        String serviceName,
        String environment,
        Severity severity,
        IncidentStatus status,
        String detectedBy,
        Instant startedAt,
        Instant createdAt,
        Instant resolvedAt
) {
    public static IncidentResponse from(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getTitle(),
                incident.getDescription(),
                incident.getService() != null ? incident.getService().getName() : null,
                incident.getEnvironment(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getDetectedBy(),
                incident.getStartedAt(),
                incident.getCreatedAt(),
                incident.getResolvedAt()
        );
    }
}
