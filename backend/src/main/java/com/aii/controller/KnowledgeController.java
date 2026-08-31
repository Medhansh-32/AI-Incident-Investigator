package com.aii.controller;

import com.aii.domain.KnowledgeDocument;
import com.aii.dto.IngestDocumentRequest;
import com.aii.dto.SearchRequest;
import com.aii.service.KnowledgeService;
import jakarta.validation.Valid;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/documents")
    public ResponseEntity<KnowledgeDocument> ingest(@Valid @RequestBody IngestDocumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(knowledgeService.ingest(request));
    }

    @PostMapping("/search")
    public List<Document> search(@RequestBody SearchRequest request) {
        int topK = request.topK() != null ? request.topK() : 5;
        return knowledgeService.search(request.query(), request.serviceName(), topK);
    }
}
