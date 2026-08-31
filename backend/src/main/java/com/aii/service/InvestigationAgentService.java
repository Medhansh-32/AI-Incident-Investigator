package com.aii.service;

import com.aii.domain.Incident;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

/**
 * Phase 5 starting point - NOT the full investigation state machine from the
 * design doc. This is a single LLM call that:
 *   1. retrieves relevant knowledge via RAG (QuestionAnswerAdvisor + pgvector)
 *   2. is given the MCP tools (GitHub, and later K8s/Monitoring/Jira) to call
 *      if it decides it needs live evidence
 *   3. returns a free-text hypothesis
 *
 * The real Investigation Agent should replace this with the explicit
 * multi-step loop from the design doc (Analyze -> Retrieve -> Generate
 * Hypotheses -> Gather Evidence -> Evaluate -> Recommend), each step
 * producing structured output persisted to Investigation/Hypothesis/Evidence.
 * This class is deliberately simple so you have a working baseline to
 * compare that more sophisticated loop against later (see the Evaluation
 * Framework section of the design doc).
 */
@Service
public class InvestigationAgentService {

    private final ChatClient chatClient;

    public InvestigationAgentService(ChatClient.Builder chatClientBuilder,
                                      VectorStore vectorStore,
                                     @Qualifier("mcpToolCallbacks")  ToolCallbackProvider toolCallbackProvider) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                .defaultToolCallbacks(toolCallbackProvider)
                .build();
    }

    public String investigate(Incident incident) {
        String prompt = """
                You are an SRE incident investigator. An incident has been reported:

                Title: %s
                Description: %s
                Service: %s
                Environment: %s
                Severity: %s

                Use the retrieved knowledge (runbooks, past incidents) as context.
                If you need live evidence (recent commits, a specific commit's diff,
                or a pull request), call the available tools.

                Respond with:
                1. Your top root-cause hypothesis
                2. A confidence estimate (0-100%%)
                3. The specific evidence that supports it - cite which tool
                   call or retrieved document each piece of evidence came from
                4. What you'd still want to check to be more confident
                """.formatted(
                incident.getTitle(),
                incident.getDescription(),
                incident.getService() != null ? incident.getService().getName() : "unknown",
                incident.getEnvironment(),
                incident.getSeverity()
        );

        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }
}
