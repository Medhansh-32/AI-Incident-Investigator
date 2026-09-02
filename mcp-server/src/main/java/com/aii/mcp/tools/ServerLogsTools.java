package com.aii.mcp.tools;

import com.aii.mcp.entity.ServerLogConfig;
import com.aii.mcp.service.ServerLogConfigService;
import com.aii.mcp.service.ServerLogSshService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ServerLogsTools {

    private final ServerLogConfigService configService;
    private final ServerLogSshService sshService;

    // ------------------------------------------------------------------
    // Basic tools (unchanged — already flat, single commands)
    // ------------------------------------------------------------------

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
            + "uptime, free, df, pgrep, journalctl, ls, stat, fuser, date, find). No chaining ('; && ||'), no command "
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
        String logPath = requireLogPath(config, serviceName);
        String logDir = getLogDirectory(logPath);

        String cmd =
                "find " + shellQuote(logDir) +
                        " -maxdepth 2 -type f " +
                        "\\( -name '*.log' -o -name '*.log.*' -o -name '*.gz' \\) " +
                        "-printf '%T@ %p\\n' " +
                        "2>/dev/null | sort -rn";

        return sshService.runCommand(config, cmd);
    }

    @Tool(description = "List all log files discovered for a registered service, "
            + "including the live log, rotated logs, and archived logs. "
            + "Files are returned from newest to oldest based on modification time.")
    public Map<String, Object> listServiceLogs(
            @ToolParam(description = "Registered service name") String serviceName) {

        ServerLogConfig config = configService.getByServiceName(serviceName);
        String logPath = requireLogPath(config, serviceName);
        String logDir = getLogDirectory(logPath);

        String cmd =
                "find " + shellQuote(logDir) +
                        " -maxdepth 2 -type f " +
                        "\\( -name '*.log' -o -name '*.log.*' -o -name '*.gz' \\) " +
                        "-printf '%TY-%Tm-%Td %TH:%TM:%TS %p\\n' 2>/dev/null " +
                        "| sort -r";

        return sshService.runCommand(config, cmd);
    }

    @Tool(description = "Get the first and last log timestamp plus total line count for a service's live log file — "
            + "useful to know what time range is actually covered before running other queries. Pair with "
            + "listLogArchives to see how much history exists beyond this file.")
    public Map<String, Object> getLogFileRange(
            @ToolParam(description = "Registered service name") String serviceName) {

        ServerLogConfig config = configService.getByServiceName(serviceName);
        String logPath = requireLogPath(config, serviceName);
        String q = shellQuote(logPath);
        String cmd = "echo '--- first ---'; head -1 " + q
                + "; echo '--- last ---'; tail -1 " + q
                + "; echo '--- lines ---'; wc -l " + q;
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
        String logPath = requireLogPath(config, serviceName);
        String q = shellQuote(logPath);
        String cmd = "echo '--- process holding log file open (fuser) ---'; "
                + "fuser -v " + q + " 2>&1; "
                + "echo '--- log file last write time ---'; "
                + "stat -c 'last write: %y' " + q + " 2>/dev/null; "
                + "echo '--- current server time ---'; "
                + "date";
        return sshService.runCommand(config, cmd);
    }

    // ------------------------------------------------------------------
    // Multi-file tools — file discovery/filtering happens in Java, and each
    // SSH call is a single flat pipeline (no while/if/;), so it clears the
    // allowlist guard. This replaces the old shell-loop implementations,
    // which always failed because they required semicolons to work.
    // ------------------------------------------------------------------

    @Tool(description = "Search a service's live, rotated, and archived log files for a text or regex pattern, "
            + "optionally restricted to files matching a date prefix (e.g. '2026-09-01') in the filename.")
    public Map<String, Object> searchArchivedLogs(
            @ToolParam(description = "Registered service name") String serviceName,
            @ToolParam(description = "Text or regex pattern to search for") String pattern,
            @ToolParam(description = "Optional date prefix, e.g. '2026-09-01', to restrict which files are scanned") String datePrefix,
            @ToolParam(description = "Max matching lines per file, default 200") Integer maxResults) {

        ServerLogConfig config = configService.getByServiceName(serviceName);
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("Search pattern cannot be empty");
        }
        int cap = (maxResults == null || maxResults <= 0) ? 200 : Math.min(maxResults, 1000);
        String logPath = requireLogPath(config, serviceName);
        String logDir = getLogDirectory(logPath);

        List<String> files = listCandidateFiles(config, logDir);
        if (datePrefix != null && !datePrefix.isBlank()) {
            files = files.stream().filter(f -> f.contains(datePrefix)).toList();
        }
        if (files.isEmpty()) {
            return emptyResult(serviceName, "no matching files found");
        }

        String fileArgs = joinQuoted(files);
        String cmd = "zcat -f " + fileArgs + " 2>/dev/null | grep -i -E -- " + shellQuote(pattern)
                + " | head -n " + cap;
        return sshService.runCommand(config, cmd);
    }

    @Tool(description = "Get a breakdown of HTTP response codes logged by a service. "
            + "Optionally restrict results to a date prefix such as '2026-09-01'. "
            + "Searches the live log, rotated logs, and archived logs including .gz files. "
            + "The date filter matches either the log filename or the log contents.")
    public Map<String, Object> getResponseCodeStats(
            @ToolParam(description = "Registered service name") String serviceName,
            @ToolParam(description = "Optional date prefix, e.g. '2026-09-01'") String datePrefix) {

        ServerLogConfig config = configService.getByServiceName(serviceName);
        String logPath = requireLogPath(config, serviceName);
        String logDir = getLogDirectory(logPath);
        List<String> files = listCandidateFiles(config, logDir);

        if (files.isEmpty()) {
            return emptyResult(serviceName, "no log files found");
        }

        String combinedOut = collectByDatePrefix(config, files, datePrefix, "grep -oP 'Response:\\\\d+'");
        return countAndSort(serviceName, combinedOut, "responseCode", Integer.MAX_VALUE);
    }

    @Tool(description = "Get a count of distinct ERROR-level messages, optionally restricted to a date prefix, "
            + "across the live log and archived logs.")
    public Map<String, Object> getErrorSummary(
            @ToolParam(description = "Registered service name") String serviceName,
            @ToolParam(description = "Optional date prefix, e.g. '2026-09-01'") String datePrefix,
            @ToolParam(description = "Max distinct error types to return, default 20") Integer maxResults) {

        ServerLogConfig config = configService.getByServiceName(serviceName);
        int cap = (maxResults == null || maxResults <= 0) ? 20 : maxResults;
        String logPath = requireLogPath(config, serviceName);
        String logDir = getLogDirectory(logPath);
        List<String> files = listCandidateFiles(config, logDir);

        if (files.isEmpty()) {
            return emptyResult(serviceName, "no log files found");
        }

        String combinedOut = collectByDatePrefix(config, files, datePrefix, "grep -oP '(?<=ERROR ).*'");
        return countAndSort(serviceName, combinedOut, "errorMessage", cap);
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /**
     * Lists candidate log files (live/rotated/archived) for a directory as a
     * plain Java list, by running ONE flat "find | sort" command. No looping
     * or filtering happens on the remote shell — that all happens here in Java,
     * which is what lets the downstream commands stay single-pipeline and pass
     * the allowlist guard.
     */
    private List<String> listCandidateFiles(ServerLogConfig config, String logDir) {
        String cmd = "find " + shellQuote(logDir) +
                " -maxdepth 2 -type f \\( -name '*.log' -o -name '*.log.*' -o -name '*.gz' \\) " +
                "-printf '%T@ %p\\n' 2>/dev/null | sort -rn";
        Map<String, Object> result = sshService.runCommand(config, cmd);
        String stdout = String.valueOf(result.getOrDefault("stdout", ""));

        List<String> files = new ArrayList<>();
        for (String line : stdout.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            int sp = line.indexOf(' ');
            if (sp < 0) continue;
            String path = line.substring(sp + 1).trim();
            if (!path.isEmpty()) files.add(path);
        }
        return files;
    }

    /**
     * Splits files into "name matches the date prefix" vs "name doesn't", and
     * runs one flat command per group (only non-empty groups), applying the
     * given extraction pipe (e.g. a grep -oP pattern) to each. Results are
     * concatenated in Java. This replaces the old single shell loop with an
     * if/then/else per file, which required semicolons and always failed.
     */
    private String collectByDatePrefix(ServerLogConfig config, List<String> files, String datePrefix, String extractPipe) {
        StringBuilder combined = new StringBuilder();

        if (datePrefix == null || datePrefix.isBlank()) {
            String fileArgs = joinQuoted(files);
            String cmd = "zcat -f " + fileArgs + " 2>/dev/null | " + extractPipe;
            Map<String, Object> r = sshService.runCommand(config, cmd);
            combined.append(String.valueOf(r.getOrDefault("stdout", "")));
            return combined.toString();
        }

        List<String> nameMatches = files.stream().filter(f -> f.contains(datePrefix)).toList();
        List<String> nameNonMatches = files.stream().filter(f -> !f.contains(datePrefix)).toList();

        if (!nameMatches.isEmpty()) {
            String fileArgs = joinQuoted(nameMatches);
            String cmd = "zcat -f " + fileArgs + " 2>/dev/null | " + extractPipe;
            Map<String, Object> r = sshService.runCommand(config, cmd);
            combined.append(String.valueOf(r.getOrDefault("stdout", "")));
        }

        if (!nameNonMatches.isEmpty()) {
            String fileArgs = joinQuoted(nameNonMatches);
            // Filter by content date first, then extract — still one flat pipeline.
            String cmd = "zcat -f " + fileArgs + " 2>/dev/null | grep -F -- " + shellQuote(datePrefix)
                    + " | " + extractPipe;
            Map<String, Object> r = sshService.runCommand(config, cmd);
            combined.append("\n").append(String.valueOf(r.getOrDefault("stdout", "")));
        }

        return combined.toString();
    }

    /** Counts distinct lines (like `sort | uniq -c | sort -rn`), done in Java, and caps the result. */
    private Map<String, Object> countAndSort(String serviceName, String rawOutput, String labelKey, int cap) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String line : rawOutput.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            counts.merge(trimmed, 1L, Long::sum);
        }

        List<Map<String, Object>> sorted = counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(cap)
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put(labelKey, e.getKey());
                    row.put("count", e.getValue());
                    return row;
                })
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", serviceName);
        result.put("counts", sorted);
        return result;
    }

    private Map<String, Object> emptyResult(String serviceName, String note) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", serviceName);
        result.put("stdout", "");
        result.put("note", note);
        return result;
    }

    private String requireLogPath(ServerLogConfig config, String serviceName) {
        String logPath = config.getLogFilePath();
        if (logPath == null || logPath.isBlank()) {
            throw new IllegalArgumentException("No log file path configured for service: " + serviceName);
        }
        return logPath;
    }

    private String joinQuoted(List<String> files) {
        return files.stream().map(this::shellQuote).collect(Collectors.joining(" "));
    }

    private String buildSearchCommand(ServerLogConfig config, String pattern, String datePrefix, int cap) {
        String safePattern = sanitize(pattern);
        String q = shellQuote(config.getLogFilePath());
        String base = "grep -i \"" + safePattern + "\" " + q;
        if (datePrefix != null && !datePrefix.isBlank()) {
            base = "grep \"" + sanitize(datePrefix) + "\" " + q
                    + " | grep -i \"" + safePattern + "\"";
        }
        return base + " | tail -" + cap;
    }

    // Strip characters that could break out of the quoted grep argument or chain commands.
    private String sanitize(String input) {
        if (input == null) return "";
        return input.replaceAll("[\"'`;&|$><]", "");
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

    @PostConstruct
    public void init() {
        System.out.println(">>> ServerLogsTools CREATED");
    }
}
