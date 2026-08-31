package com.aii.repository;

import com.aii.domain.Investigation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InvestigationRepository extends JpaRepository<Investigation, UUID> {
    Optional<Investigation> findByIncidentId(UUID incidentId);
}
