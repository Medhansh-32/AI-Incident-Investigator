package com.aii.controller;

import com.aii.service.IncidentService;
import com.aii.service.InvestigationAgentService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/incidents")
public class InvestigationController {

    private final IncidentService incidentService;
    private final InvestigationAgentService investigationAgentService;

    public InvestigationController(IncidentService incidentService,
                                    InvestigationAgentService investigationAgentService) {
        this.incidentService = incidentService;
        this.investigationAgentService = investigationAgentService;
    }

    /**
     * Baseline investigation: one LLM call, RAG-augmented, with MCP tools
     * available. Replace this with the real multi-step orchestrator (Phase 5)
     * and keep this as your "naive baseline" for the evaluation framework.
     */
    @PostMapping("/{id}/investigate")
    public Map<String, String> investigate(@PathVariable UUID id) {
        var incident = incidentService.getOrThrow(id);
        String result = investigationAgentService.investigate(incident);
        return Map.of("incidentId", id.toString(), "investigation", result);
    }
}
