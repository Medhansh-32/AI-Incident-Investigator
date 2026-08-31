package com.aii.controller;

import com.aii.domain.ServiceEntity;
import com.aii.repository.ServiceRepository;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceRepository serviceRepository;

    public ServiceController(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    public record CreateServiceRequest(@NotBlank String name, @NotBlank String environment,
                                        String ownerTeam, String repoUrl) {}

    @PostMapping
    public ResponseEntity<ServiceEntity> create(@RequestBody CreateServiceRequest request) {
        ServiceEntity entity = new ServiceEntity();
        entity.setName(request.name());
        entity.setEnvironment(request.environment());
        entity.setOwnerTeam(request.ownerTeam());
        entity.setRepoUrl(request.repoUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceRepository.save(entity));
    }

    @GetMapping
    public List<ServiceEntity> findAll() {
        return serviceRepository.findAll();
    }
}
