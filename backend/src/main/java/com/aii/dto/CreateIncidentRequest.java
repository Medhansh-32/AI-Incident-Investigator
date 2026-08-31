package com.aii.dto;

import com.aii.domain.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateIncidentRequest(
        @NotBlank String title,
        String description,
        String serviceName,
        @NotBlank String environment,
        @NotNull Severity severity,
        String detectedBy
) {}
