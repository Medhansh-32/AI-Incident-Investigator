package com.aii.service;

import com.aii.domain.KnowledgeDocument;
import com.aii.dto.IngestDocumentRequest;
import com.aii.repository.KnowledgeDocumentRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Phase 2 (RAG) skeleton.
 *
 * Flow:
 *   1. ingest(): store document metadata (Postgres, JPA) + chunk the text +
 *      embed each chunk + store vectors (pgvector, via Spring AI's VectorStore)
 *   2. search(): embed the query, run similarity search, optionally filter
 *      by metadata (service/environment), return ranked chunks with citations
 *
 * NOTE: this requires a working embedding model configured in application.yml
 * (Ollama's nomic-embed-text by default). Incident CRUD works without it;
 * this service will throw if no embedding model is reachable.
 */
@Service
public class KnowledgeService {

    private final KnowledgeDocumentRepository documentRepository;
    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter = new TokenTextSplitter();

    public KnowledgeService(KnowledgeDocumentRepository documentRepository, VectorStore vectorStore) {
        this.documentRepository = documentRepository;
        this.vectorStore = vectorStore;
    }

    public KnowledgeDocument ingest(IngestDocumentRequest request) {
        // 1. Persist document metadata as the system of record
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setTitle(request.title());
        doc.setType(request.type());
        doc.setServiceName(request.serviceName());
        doc.setEnvironment(request.environment());
        doc.setSourceUrl(request.sourceUrl());
        KnowledgeDocument saved = documentRepository.save(doc);

        // 2. Wrap the raw text as a Spring AI Document with metadata for
        //    later hybrid filtering (service/environment/type/document_id)
        Document sourceDocument = new Document(request.content(), Map.of(
                "document_id", saved.getId().toString(),
                "document_type", saved.getType().name(),
                "service", saved.getServiceName() == null ? "" : saved.getServiceName(),
                "environment", saved.getEnvironment() == null ? "" : saved.getEnvironment(),
                "title", saved.getTitle()
        ));

        // 3. Chunk it
        List<Document> chunks = splitter.apply(List.of(sourceDocument));

        // 4. Embed + store each chunk in pgvector (handled by Spring AI's VectorStore)
        vectorStore.add(chunks);

        return saved;
    }

    /**
     * Hybrid-ish search: semantic similarity, optionally narrowed by service name.
     * For true metadata filtering, build a Filter.Expression via
     * SearchRequest.builder().filterExpression(...) - simplified here for the scaffold.
     */
    public List<Document> search(String query, String serviceName, int topK) {
        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .query(query)
                .topK(topK);

        if (serviceName != null && !serviceName.isBlank()) {
            requestBuilder.filterExpression("service == '" + serviceName + "'");
        }

        return vectorStore.similaritySearch(requestBuilder.build());
    }
}
