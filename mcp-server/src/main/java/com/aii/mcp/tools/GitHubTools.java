package com.aii.mcp.tools;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;


@Component
public class GitHubTools {

    private final RestClient restClient;

    public GitHubTools(RestClient githubRestClient) {
        this.restClient = githubRestClient;
    }

    @Tool(description = "Get the most recent commits on a GitHub repository's default branch. "
            + "Use this to check what changed recently around the time an incident started.")
    public List<Map<String, Object>> getRecentCommits(
            @ToolParam(description = "Repository owner, e.g. 'my-org'") String owner,
            @ToolParam(description = "Repository name, e.g. 'payment-service'") String repo,
            @ToolParam(description = "Max number of commits to return, default 10") Integer limit) {

        int perPage = (limit == null || limit <= 0) ? 10 : limit;

        List<Map<String, Object>> commits = restClient.get()
                .uri("/repos/{owner}/{repo}/commits?per_page={perPage}", owner, repo, perPage)
                .retrieve()
                .body(List.class);

        return commits;
    }

    @Tool(description = "Get the file-level diff for a specific commit SHA. "
            + "Use this after finding a suspicious commit via getRecentCommits, to see exactly what changed.")
    public Map<String, Object> getCommitDiff(
            @ToolParam(description = "Repository owner") String owner,
            @ToolParam(description = "Repository name") String repo,
            @ToolParam(description = "The commit SHA to inspect") String sha) {

        return restClient.get()
                .uri("/repos/{owner}/{repo}/commits/{sha}", owner, repo, sha)
                .retrieve()
                .body(Map.class);
    }

    @Tool(description = "Look up a specific pull request by number, including its title, "
            + "description, merge status, and merged_at timestamp. Use this to correlate a "
            + "deployment with the PR that caused it.")
    public Map<String, Object> getPullRequest(
            @ToolParam(description = "Repository owner") String owner,
            @ToolParam(description = "Repository name") String repo,
            @ToolParam(description = "Pull request number") Integer number) {

        return restClient.get()
                .uri("/repos/{owner}/{repo}/pulls/{number}", owner, repo, number)
                .retrieve()
                .body(Map.class);
    }

    @PostConstruct
    public void init() {
        System.out.println(">>> GitHubTools CREATED");
    }
}
