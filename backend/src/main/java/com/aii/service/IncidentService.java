package com.aii.service;

import com.aii.domain.*;
import com.aii.dto.CreateIncidentRequest;
import com.aii.repository.IncidentEventRepository;
import com.aii.repository.IncidentRepository;
import com.aii.repository.ServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final ServiceRepository serviceRepository;
    private final IncidentEventRepository incidentEventRepository;

    public IncidentService(IncidentRepository incidentRepository,
                            ServiceRepository serviceRepository,
                            IncidentEventRepository incidentEventRepository) {
        this.incidentRepository = incidentRepository;
        this.serviceRepository = serviceRepository;
        this.incidentEventRepository = incidentEventRepository;
    }

    @Transactional
    public Incident create(CreateIncidentRequest request) {
        Incident incident = new Incident();
        incident.setTitle(request.title());
        incident.setDescription(request.description());
        incident.setEnvironment(request.environment());
        incident.setSeverity(request.severity());
        incident.setDetectedBy(request.detectedBy());
        incident.setStartedAt(Instant.now());
        incident.setStatus(IncidentStatus.DETECTED);

        if (request.serviceName() != null && !request.serviceName().isBlank()) {
            serviceRepository.findByName(request.serviceName())
                    .ifPresent(incident::setService);
        }

        Incident saved = incidentRepository.save(incident);

        recordEvent(saved, IncidentEvent.EventType.STATE_CHANGE,
                "Incident created with status DETECTED", "system");

        return saved;
    }

    public Incident getOrThrow(UUID id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Incident not found: " + id));
    }

    public List<Incident> findAll() {
        return incidentRepository.findAll();
    }

    @Transactional
    public Incident transitionStatus(UUID incidentId, IncidentStatus newStatus) {
        Incident incident = getOrThrow(incidentId);

        if (!incident.getStatus().canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    "Cannot transition from " + incident.getStatus() + " to " + newStatus
                            + " - lifecycle only moves forward one step at a time");
        }

        incident.setStatus(newStatus);
        if (newStatus == IncidentStatus.RESOLVED) {
            incident.setResolvedAt(Instant.now());
        }

        recordEvent(incident, IncidentEvent.EventType.STATE_CHANGE,
                "Status changed to " + newStatus, "user");

        return incidentRepository.save(incident);
    }

    @Transactional
    public void recordEvent(Incident incident, IncidentEvent.EventType type, String payload, String source) {
        IncidentEvent event = new IncidentEvent();
        event.setType(type);
        event.setPayload(payload);
        event.setSource(source);
        incident.addEvent(event);
        incidentEventRepository.save(event);
    }

    public List<IncidentEvent> getTimeline(UUID incidentId) {
        return incidentEventRepository.findByIncidentIdOrderByOccurredAtAsc(incidentId);
    }
}
