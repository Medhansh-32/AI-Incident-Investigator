package com.aii.mcp;

import com.aii.mcp.tools.GitHubTools;
import com.aii.mcp.tools.ServerLogsTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }

    @Bean
    public RestClient githubRestClient(
            @org.springframework.beans.factory.annotation.Value("${github.token:}") String token) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github+json");
        if (token != null && !token.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + token);
        }
        return builder.build();
    }

    @Bean("applicationToolProvider")
    public ToolCallbackProvider applicationToolProvider(
            GitHubTools githubTools,
            ServerLogsTools serverLogsTools) {

        return MethodToolCallbackProvider.builder()
                .toolObjects(githubTools, serverLogsTools)
                .build();
    }
}
