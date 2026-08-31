package com.aii.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mcp")
public class McpToolController {

    private final ToolCallbackProvider toolCallbackProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpToolController(@Qualifier("mcpToolCallbacks") ToolCallbackProvider toolCallbackProvider) {
        this.toolCallbackProvider = toolCallbackProvider;
    }

    public record CallToolRequest(String toolName, String argumentsJson) {}

    @GetMapping("/tools")
    public List<Map<String, String>> listTools() {
        ToolCallback[] callbacks = toolCallbackProvider.getToolCallbacks();
        return Arrays.stream(callbacks)
                .map(tc -> Map.of(
                        "name", tc.getToolDefinition().name(),
                        "description", tc.getToolDefinition().description()
                ))
                .toList();
    }

    @PostMapping("/tools/call")
    public ResponseEntity<Object> callTool(@RequestBody CallToolRequest request) {
        ToolCallback callback = Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .filter(tc -> tc.getToolDefinition().name().equals(request.toolName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such tool: " + request.toolName()));

        String rawResult = callback.call(request.argumentsJson());

        return ResponseEntity.ok(unwrap(rawResult));
    }

    /**
     * The MCP client wraps tool output as a JSON array of content blocks,
     * e.g. [{"type":"text","text":"{...actual json...}"}]. This pulls out
     * the first block's "text" field and parses it as real JSON if possible,
     * so callers get a clean object instead of nested, escaped strings.
     */
    private Object unwrap(String rawResult) {
        try {
            JsonNode root = objectMapper.readTree(rawResult);

            // Case 1: MCP content array [{ "type": "text", "text": "..." }]
            if (root.isArray() && root.size() > 0 && root.get(0).has("text")) {
                String innerText = root.get(0).get("text").asText();
                return parseIfJson(innerText);
            }

            // Case 2: already a plain JSON object/array — return as-is
            return objectMapper.treeToValue(root, Object.class);

        } catch (Exception e) {
            // Not valid JSON at all — just return the raw string
            return rawResult;
        }
    }

    private Object parseIfJson(String text) {
        try {
            return objectMapper.readValue(text, Object.class);
        } catch (Exception e) {
            return text; // inner text wasn't JSON either, return as plain string
        }
    }
}