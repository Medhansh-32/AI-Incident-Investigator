package com.aii.mcp.tools;

import com.aii.mcp.entity.ServerLogConfig;
import com.aii.mcp.service.ServerLogConfigService;
import com.aii.mcp.service.ServerLogSshService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ServerLogsTools {

    private final ServerLogConfigService configService;
    private final ServerLogSshService sshService;

    @Tool(description = "Fetch the last N lines of a registered service's log file. "
            + "Use this to check what a service was logging around the time an incident started.")
    public Map<String, Object> getRecentLogs(
            @ToolParam(description = "Registered service name, e.g. 'payment-service'") String serviceName,
            @ToolParam(description = "Number of trailing lines to fetch, default 200") Integer lines) {

        ServerLogConfig config = configService.getByServiceName(serviceName);
        int tailLines = (lines == null || lines <= 0) ? 200 : lines;
        return sshService.tailLog(config, tailLines);
    }

    @Tool(description = "Run a read-only diagnostic command (e.g. 'journalctl -u myapp -n 200 --no-pager') "
            + "against a registered service's server. Use this when a plain file tail isn't enough. "
            + "Destructive commands (writes, deletes, restarts, permission changes) are rejected.")
    public Map<String, Object> runLogCommand(
            @ToolParam(description = "Registered service name, e.g. 'payment-service'") String serviceName,
            @ToolParam(description = "The exact read-only shell command to run") String command) {

        ServerLogConfig config = configService.getByServiceName(serviceName);
        return sshService.runCommand(config, command);
    }

    @Tool(description = "List all services currently registered for log access, so the model knows "
            + "what it can query without guessing service names.")
    public List<String> listRegisteredServices() {
        return configService.listServiceNames();
    }

    @Tool(description = "Search a service's log file for a pattern (case-insensitive) within an optional "
            + "date prefix (e.g. '2026-09-01'). Returns matching lines, most recent first, capped at maxResults.")
    public Map<String, Object> searchLogs(
            @ToolParam(description = "Registered service name") String serviceName,
            @ToolParam(description = "Text or regex pattern to search for") String pattern,
            @ToolParam(description = "Optional date prefix to restrict the search, e.g. '2026-09-01'") String datePrefix,
            @ToolParam(description = "Max matching lines to return, default 200") Integer maxResults) {

        ServerLogConfig config = configService.getByServiceName(serviceName);
        int cap = (maxResults == null || maxResults <= 0) ? 200 : maxResults;
        String cmd = buildSearchCommand(config, pattern, datePrefix, cap);
        return sshService.runCommand(config, cmd);
    }

    @Tool(description = "Get a breakdown of HTTP response codes logged by a service, optionally restricted "
            + "to a date prefix (e.g. '2026-09-01'). Useful for spotting elevated error rates per endpoint.")
    public Map<String, Object> getResponseCodeStats(
            @ToolParam(description = "Registered service name") String serviceName,
            @ToolParam(description = "Optional date prefix, e.g. '2026-09-01'") String datePrefix) {

        ServerLogConfig config = configService.getByServiceName(serviceName);
        String grepScope = (datePrefix == null || datePrefix.isBlank())
                ? "cat " + config.getLogFilePath()
                : "grep \"" + sanitize(datePrefix) + "\" " + config.getLogFilePath();
        String cmd = grepScope + " | grep -oP 'Response:\\d+' | sort | uniq -c | sort -rn";
        return sshService.runCommand(config, cmd);
    }

    @Tool(description = "Get a count of distinct ERROR-level messages logged by a service, most frequent first. "
            + "Helps spot repeating failures (e.g. the same bad input hit on a schedule).")
    public Map<String, Object> getErrorSummary(
            @ToolParam(description = "Registered service name") String serviceName,
            @ToolParam(description = "Max distinct error types to return, default 20") Integer maxResults) {

        ServerLogConfig config = configService.getByServiceName(serviceName);
        int cap = (maxResults == null || maxResults <= 0) ? 20 : maxResults;
        String cmd = "grep -oP '(?<=ERROR ).*' " + config.getLogFilePath()
                + " | sort | uniq -c | sort -rn | head -" + cap;
        return sshService.runCommand(config, cmd);
    }

    @Tool(description = "Check basic OS-level health of a registered service's host: CPU/memory load, "
            + "disk usage, and whether the named process is running. Read-only.")
    public Map<String, Object> getHostHealth(
            @ToolParam(description = "Registered service name") String serviceName,
            @ToolParam(description = "Process name to check is running, e.g. 'java'") String processName) {

        ServerLogConfig config = configService.getByServiceName(serviceName);
        String proc = (processName == null || processName.isBlank()) ? "java" : sanitize(processName);
        String cmd = "echo '--- uptime/load ---'; uptime; "
                + "echo '--- memory ---'; free -m; "
                + "echo '--- disk ---'; df -h; "
                + "echo '--- process ---'; pgrep -fa " + proc + " || echo 'not running'";
        return sshService.runCommand(config, cmd);
    }

    @Tool(description = "Get the first and last log timestamp plus total line count for a service's log file — "
            + "useful to know what time range is actually covered before running other queries.")
    public Map<String, Object> getLogFileRange(
            @ToolParam(description = "Registered service name") String serviceName) {

        ServerLogConfig config = configService.getByServiceName(serviceName);
        String cmd = "echo '--- first ---'; head -1 " + config.getLogFilePath()
                + "; echo '--- last ---'; tail -1 " + config.getLogFilePath()
                + "; echo '--- lines ---'; wc -l " + config.getLogFilePath();
        return sshService.runCommand(config, cmd);
    }

    private String buildSearchCommand(ServerLogConfig config, String pattern, String datePrefix, int cap) {
        String safePattern = sanitize(pattern);
        String base = "grep -i \"" + safePattern + "\" " + config.getLogFilePath();
        if (datePrefix != null && !datePrefix.isBlank()) {
            base = "grep \"" + sanitize(datePrefix) + "\" " + config.getLogFilePath()
                    + " | grep -i \"" + safePattern + "\"";
        }
        return base + " | tail -" + cap;
    }

    // Strip characters that could break out of the quoted grep argument or chain commands.
    private String sanitize(String input) {
        return input.replaceAll("[\"'`;&|$><]", "");
    }

    @PostConstruct
    public void init() {
        System.out.println(">>> ServerLogsTools CREATED");
    }
}