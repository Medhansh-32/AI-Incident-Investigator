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

    @Tool(description = "Run a read-only diagnostic command against a registered service's server, from a fixed "
            + "allowlist of binaries (tail, head, cat, grep/egrep/fgrep/zgrep, zcat, gunzip, wc, sort, uniq, echo, "
            + "uptime, free, df, pgrep, journalctl, ls, stat, fuser, date). No chaining ('; && ||'), no command "
            + "substitution, no file redirection, no backgrounding. Use this when a plain file tail isn't enough.")
    public Map<String, Object> runLogCommand(
            @ToolParam(description = "Registered service name, e.g. 'payment-service'") String serviceName,
            @ToolParam(description = "The exact read-only shell command to run, using only allowlisted binaries") String command) {

        ServerLogConfig config = configService.getByServiceName(serviceName);
        return sshService.runCommand(config, command);
    }

    @Tool(description = "List all services currently registered for log access, so the model knows "
            + "what it can query without guessing service names.")
    public List<String> listRegisteredServices() {
        return configService.listServiceNames();
    }

    @Tool(description = "Search a service's log file for a pattern (case-insensitive) within an optional "
            + "date prefix (e.g. '2026-09-01'). Returns matching lines, most recent first, capped at maxResults. "
            + "Only searches the live log file — use searchArchivedLogs to include rotated/older logs.")
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

    @Tool(description = "List all log files available for a registered service, "
            + "including the live log, rotated logs, and logs stored in archive directories. "
            + "Use this before searching historical logs when the archive layout is unknown.")
    public Map<String, Object> listLogArchives(
            @ToolParam(description = "Registered service name") String serviceName) {

        ServerLogConfig config = configService.getByServiceName(serviceName);

        String logPath = config.getLogFilePath();

        if (logPath == null || logPath.isBlank()) {
            throw new IllegalArgumentException(
                    "No log file path configured for service: " + serviceName);
        }

        String logDir = getLogDirectory(logPath);

        String cmd =
                "find " + shellQuote(logDir) +
                        " -maxdepth 2 -type f " +
                        "\\( -name '*.log' -o -name '*.log.*' -o -name '*.gz' \\) " +
                        "-printf '%T@ %p\\n' " +
                        "2>/dev/null | sort -rn";

        return sshService.runCommand(config, cmd);
    }

    @Tool(description = "Search a service's live, rotated, and archived log files for a text or regex pattern. "
            + "Automatically discovers log files up to 2 directory levels below the configured log directory, "
            + "including .gz files. Returns up to maxResults matching lines per file, newest files first.")
    public Map<String, Object> searchArchivedLogs(
            @ToolParam(description = "Registered service name") String serviceName,
            @ToolParam(description = "Text or regex pattern to search for") String pattern,
            @ToolParam(description = "Max matching lines per file, default 200") Integer maxResults) {

        ServerLogConfig config = configService.getByServiceName(serviceName);

        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("Search pattern cannot be empty");
        }

        int cap = (maxResults == null || maxResults <= 0)
                ? 200
                : Math.min(maxResults, 1000);

        String logPath = config.getLogFilePath();

        if (logPath == null || logPath.isBlank()) {
            throw new IllegalArgumentException(
                    "No log file path configured for service: " + serviceName);
        }

        String logDir = getLogDirectory(logPath);

        String safePattern = shellQuote(pattern);

        /*
         * Discover files newest first.
         *
         * Example:
         *
         * /var/log/mobycy/zypp-erp/zypp-erp.log
         * /var/log/mobycy/zypp-erp/archived/zypp-erp-2026-09-01.0.log.gz
         * /var/log/mobycy/zypp-erp/archived/zypp-erp-2026-08-31.0.log.gz
         */
        String cmd =
                "find " + shellQuote(logDir) +
                        " -maxdepth 2 -type f " +
                        "\\( -name '*.log' -o -name '*.log.*' -o -name '*.gz' \\) " +
                        "-printf '%T@ %p\\n' " +
                        "2>/dev/null | sort -rn | " +
                        "while read -r entry; do " +
                        "file=\"${entry#* }\"; " +
                        "echo \"===== $file =====\"; " +
                        "zcat -f " + "\"$file\"" +
                        " 2>/dev/null | grep -i -E -- " + safePattern +
                        " | head -n " + cap + "; " +
                        "done";

        return sshService.runCommand(config, cmd);
    }

    @Tool(description = "Get the first and last log timestamp plus total line count for a service's live log file — "
            + "useful to know what time range is actually covered before running other queries. Pair with "
            + "listLogArchives to see how much history exists beyond this file.")
    public Map<String, Object> getLogFileRange(
            @ToolParam(description = "Registered service name") String serviceName) {

        ServerLogConfig config = configService.getByServiceName(serviceName);
        String cmd = "echo '--- first ---'; head -1 " + config.getLogFilePath()
                + "; echo '--- last ---'; tail -1 " + config.getLogFilePath()
                + "; echo '--- lines ---'; wc -l " + config.getLogFilePath();
        return sshService.runCommand(config, cmd);
    }

    @Tool(description = "Get a breakdown of HTTP response codes logged by a service. "
            + "Optionally restrict results to a date prefix such as '2026-09-01'. "
            + "Searches the live log, rotated logs, and archived logs including .gz files. "
            + "The date filter can match either the log filename or the log contents.")
    public Map<String, Object> getResponseCodeStats(
            @ToolParam(description = "Registered service name") String serviceName,
            @ToolParam(description = "Optional date prefix, e.g. '2026-09-01'") String datePrefix) {

        ServerLogConfig config = configService.getByServiceName(serviceName);

        String logPath = config.getLogFilePath();

        if (logPath == null || logPath.isBlank()) {
            throw new IllegalArgumentException(
                    "No log file path configured for service: " + serviceName);
        }

        String logDir = getLogDirectory(logPath);

        String cmd;

        if (datePrefix == null || datePrefix.isBlank()) {

            cmd =
                    "find " + shellQuote(logDir) +
                            " -maxdepth 2 -type f " +
                            "\\( -name '*.log' -o -name '*.log.*' -o -name '*.gz' \\) " +
                            "-print 2>/dev/null | " +
                            "while read -r file; do " +
                            "zcat -f \"$file\" 2>/dev/null; " +
                            "done | " +
                            "grep -oP 'Response:\\d+' | " +
                            "sort | uniq -c | sort -rn";

        } else {

            String safeDate = shellQuote(datePrefix);

            /*
             * Search both:
             *
             * 1. filename
             * 2. log contents
             *
             * This handles archives such as:
             *
             * zypp-erp-2026-09-01.0.log.gz
             *
             * even if the log lines themselves don't contain the date.
             */
            cmd =
                    "find " + shellQuote(logDir) +
                            " -maxdepth 2 -type f " +
                            "\\( -name '*.log' -o -name '*.log.*' -o -name '*.gz' \\) " +
                            "-print 2>/dev/null | " +
                            "while read -r file; do " +
                            "if echo \"$file\" | grep -F -- " + safeDate + " >/dev/null; then " +
                            "zcat -f \"$file\" 2>/dev/null; " +
                            "else " +
                            "zcat -f \"$file\" 2>/dev/null | grep -F -- " + safeDate + "; " +
                            "fi; " +
                            "done | " +
                            "grep -oP 'Response:\\d+' | " +
                            "sort | uniq -c | sort -rn";
        }

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

    @Tool(description = "Check basic OS-level health of a registered service's HOST (shared across services on "
            + "the same box): CPU/memory load and disk usage. The trailing process check is a coarse, name-based "
            + "match (pgrep) and is NOT reliable for per-service liveness on a shared multi-service host — many "
            + "services run under generic /app/app.jar-style launch commands with no service name in the process "
            + "list, which produces false 'not running' flags. Use checkServiceLiveness for an accurate per-service "
            + "liveness check instead. Read-only.")
    public Map<String, Object> getHostHealth(
            @ToolParam(description = "Registered service name") String serviceName,
            @ToolParam(description = "Process name to check is running, e.g. 'java' (coarse signal only)") String processName) {

        ServerLogConfig config = configService.getByServiceName(serviceName);
        String proc = (processName == null || processName.isBlank()) ? "java" : sanitize(processName);
        String cmd = "echo '--- uptime/load ---'; uptime; "
                + "echo '--- memory ---'; free -m; "
                + "echo '--- disk ---'; df -h; "
                + "echo '--- process (coarse name match, may miss generic launches) ---'; "
                + "pgrep -fa " + proc + " || echo 'not running'";
        return sshService.runCommand(config, cmd);
    }

    @Tool(description = "Accurately check whether a registered service is actually alive, without relying on "
            + "process-name matching. Reports which process (if any) currently holds the service's log file open "
            + "(the process actually writing it, found via 'fuser') plus the log file's last-modified time and "
            + "the server's current time, so recency can be judged directly. This fixes the false 'not running' "
            + "flags that name-based pgrep checks produce for services launched as generic /app/app.jar processes "
            + "(their process name never matches the service name). Read-only. Prefer this over getHostHealth's "
            + "process check when you need a real per-service up/down answer.")
    public Map<String, Object> checkServiceLiveness(
            @ToolParam(description = "Registered service name") String serviceName) {

        ServerLogConfig config = configService.getByServiceName(serviceName);
        String logPath = config.getLogFilePath();
        String cmd = "echo '--- process holding log file open (fuser) ---'; "
                + "fuser -v " + logPath + " 2>&1; "
                + "echo '--- log file last write time ---'; "
                + "stat -c 'last write: %y' " + logPath + " 2>/dev/null; "
                + "echo '--- current server time ---'; "
                + "date";
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

    @Tool(description = "List all log files discovered for a registered service, "
            + "including the live log, rotated logs, and archived logs. "
            + "Files are returned from newest to oldest based on modification time.")
    public Map<String, Object> listServiceLogs(
            @ToolParam(description = "Registered service name") String serviceName) {

        ServerLogConfig config = configService.getByServiceName(serviceName);

        String logPath = config.getLogFilePath();

        if (logPath == null || logPath.isBlank()) {
            throw new IllegalArgumentException("No log file path configured for service: " + serviceName);
        }

        int lastSlash = logPath.lastIndexOf('/');

        String logDir = lastSlash > 0
                ? logPath.substring(0, lastSlash)
                : ".";

        String cmd =
                "find " + shellQuote(logDir) +
                        " -maxdepth 2 -type f " +
                        "\\( -name '*.log' -o -name '*.log.*' -o -name '*.gz' \\) " +
                        "-printf '%TY-%Tm-%Td %TH:%TM:%TS %p\\n' 2>/dev/null " +
                        "| sort -r";

        return sshService.runCommand(config, cmd);
    }


    private String getLogDirectory(String logPath) {
        int lastSlash = logPath.lastIndexOf('/');

        return lastSlash > 0
                ? logPath.substring(0, lastSlash)
                : ".";
    }

    private String shellQuote(String value) {
        if (value == null) {
            return "''";
        }

        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

}

