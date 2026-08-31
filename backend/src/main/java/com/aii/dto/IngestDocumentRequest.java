package com.aii.dto;

import com.aii.domain.KnowledgeDocument;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IngestDocumentRequest(
        @NotBlank String title,
        @NotNull KnowledgeDocument.DocumentType type,
        String serviceName,
        String environment,
        String sourceUrl,
        @NotBlank String content
) {}
