package com.aii.mcp.tools;

import com.aii.mcp.entity.ServerLogConfig;
import com.aii.mcp.service.ServerLogConfigService;
import com.aii.mcp.service.ServerLogSshService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP tools for pulling recent logs off a service's server over SSH.
 * Connection details (host, port, username, ssh key, log file path) are
 * looked up from the server_log_configs table by service name, so the
 * model only ever needs to know the service name — never the credentials.
 */
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

    @PostConstruct
    public void init() {
        System.out.println(">>> ServerLogsTools CREATED");
    }
}