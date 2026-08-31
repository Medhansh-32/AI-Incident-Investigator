package com.aii.controller;

import com.aii.domain.Incident;
import com.aii.domain.IncidentEvent;
import com.aii.dto.CreateIncidentRequest;
import com.aii.dto.IncidentResponse;
import com.aii.dto.UpdateStatusRequest;
import com.aii.service.IncidentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping
    public ResponseEntity<IncidentResponse> create(@Valid @RequestBody CreateIncidentRequest request) {
        Incident created = incidentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(IncidentResponse.from(created));
    }

    @GetMapping
    public List<IncidentResponse> findAll() {
        return incidentService.findAll().stream()
                .map(IncidentResponse::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public IncidentResponse getOne(@PathVariable UUID id) {
        return IncidentResponse.from(incidentService.getOrThrow(id));
    }

    @PatchMapping("/{id}/status")
    public IncidentResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
        Incident updated = incidentService.transitionStatus(id, request.status());
        return IncidentResponse.from(updated);
    }

    @GetMapping("/{id}/timeline")
    public List<IncidentEvent> getTimeline(@PathVariable UUID id) {
        return incidentService.getTimeline(id);
    }

    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(java.util.NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleBadTransition(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }
}
