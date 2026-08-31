package com.aii.dto;

import com.aii.domain.IncidentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull IncidentStatus status) {}
