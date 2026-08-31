package com.aii.repository;

import com.aii.domain.Incident;
import com.aii.domain.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    List<Incident> findByStatus(IncidentStatus status);
    List<Incident> findByServiceId(UUID serviceId);
}
