package com.aii.mcp.service;

import com.aii.mcp.entity.ServerLogConfig;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

@Service
public class ServerLogSshService {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int CHANNEL_TIMEOUT_MS = 20_000;
    private static final int MAX_OUTPUT_CHARS = 100_000;
    private static final long POLL_INTERVAL_MS = 15L;

    // --- Command allowlist -------------------------------------------------
    // Only these binaries may appear anywhere in a command (including inside a
    // pipeline). If it's not read-only diagnostics, it doesn't belong here.
    // NOTE: this deliberately does NOT include awk, sed, xargs, python,
    // perl, bash, sh, curl, wget, nc, scp, or any other binary that can write
    // files, make network calls, or execute arbitrary code. Adding a new
    // binary to this set is a security decision, not a convenience one.
    private static final Set<String> ALLOWED_BINARIES = Set.of(
            "tail", "head", "cat", "grep", "egrep", "fgrep", "zgrep", "zcat", "gunzip",
            "wc", "sort", "uniq", "echo", "uptime", "free", "df", "pgrep", "journalctl",
            "ls", "stat", "fuser", "date", "find"
    );

    // Fixed-descriptor redirects that don't write to the filesystem, so they're
    // safe even though they contain '>'. Everything else with '>' or '<' is rejected.
    private static final Pattern[] SAFE_REDIRECTS = {
            Pattern.compile("2>&1"),
            Pattern.compile("1>&2"),
            Pattern.compile(">\\s*/dev/null"),
            Pattern.compile("2>\\s*/dev/null")
    };

    private final EncryptionService encryptionService;

    // One live SSH session per service, reused across calls.
    private final Map<String, Session> sessionCache = new ConcurrentHashMap<>();
    // Prevents two threads from opening duplicate sessions for the same service at once.
    private final Map<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();

    public ServerLogSshService(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    public Map<String, Object> tailLog(ServerLogConfig config, int lines) {
        String command = "tail -n " + lines + " -- " + shellQuote(config.getLogFilePath());
        return run(config, command);
    }

    public Map<String, Object> runCommand(ServerLogConfig config, String command) {
        guardAgainstDestructiveCommands(command);
        return run(config, command);
    }

    private Map<String, Object> run(ServerLogConfig config, String command) {
        try {
            return execute(config, command, /* retryOnFailure= */ true);
        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("service", config.getServiceName());
            error.put("error", e.getMessage());
            return error;
        }
    }

    private Map<String, Object> execute(ServerLogConfig config, String command, boolean retryOnFailure) throws Exception {
        Session session = getOrCreateSession(config);
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);

            ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
            ByteArrayOutputStream stdErr = new ByteArrayOutputStream();
            channel.setOutputStream(stdOut);
            channel.setErrStream(stdErr);

            channel.connect(CHANNEL_TIMEOUT_MS);

            long deadline = System.currentTimeMillis() + CHANNEL_TIMEOUT_MS;
            while (!channel.isClosed()) {
                if (System.currentTimeMillis() > deadline) {
                    throw new RuntimeException("Timed out waiting for remote command to finish");
                }
                Thread.sleep(POLL_INTERVAL_MS);
            }

            String out = stdOut.toString(StandardCharsets.UTF_8);
            if (out.length() > MAX_OUTPUT_CHARS) {
                out = "...[truncated]...\n" + out.substring(out.length() - MAX_OUTPUT_CHARS);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("service", config.getServiceName());
            result.put("host", config.getHost());
            result.put("command", command);
            result.put("exitStatus", channel.getExitStatus());
            result.put("stdout", out);
            result.put("stderr", stdErr.toString(StandardCharsets.UTF_8));
            return result;

        } catch (Exception e) {
            // Session may have died (host reboot, idle timeout, network blip).
            // Evict it and retry exactly once with a fresh connection.
            if (retryOnFailure && isLikelyDeadConnection(e)) {
                evictSession(config);
                return execute(config, command, false);
            }
            throw e;
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
        }
    }

    private Session getOrCreateSession(ServerLogConfig config) throws Exception {
        String key = sessionKey(config);
        Session existing = sessionCache.get(key);
        if (existing != null && existing.isConnected()) {
            return existing;
        }

        ReentrantLock lock = sessionLocks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            // Re-check after acquiring the lock — another thread may have just created it.
            existing = sessionCache.get(key);
            if (existing != null && existing.isConnected()) {
                return existing;
            }

            if (config.getEncryptedPrivateKey() == null || config.getEncryptedPrivateKey().isBlank()) {
                throw new IllegalStateException("No SSH key configured for service '" + config.getServiceName() + "'");
            }

            String privateKeyPem = encryptionService.decrypt(config.getEncryptedPrivateKey());
            byte[] privateKeyBytes = privateKeyPem.getBytes(StandardCharsets.UTF_8);

            JSch jsch = new JSch();
            jsch.addIdentity(config.getServiceName(), privateKeyBytes, null, null);

            Session session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
            session.setConfig("StrictHostKeyChecking", "no"); // use a real known_hosts file in production
            session.connect(CONNECT_TIMEOUT_MS);

            sessionCache.put(key, session);
            return session;
        } finally {
            lock.unlock();
        }
    }

    private void evictSession(ServerLogConfig config) {
        String key = sessionKey(config);
        Session dead = sessionCache.remove(key);
        if (dead != null && dead.isConnected()) {
            dead.disconnect();
        }
    }

    private static boolean isLikelyDeadConnection(Exception e) {
        // JSch throws generic JSchException/IOException for both auth issues and dropped
        // connections, so this is a heuristic rather than a precise type check.
        String msg = e.getMessage();
        if (msg == null) return true;
        String lower = msg.toLowerCase();
        return lower.contains("session is down")
                || lower.contains("channel is not opened")
                || lower.contains("broken pipe")
                || lower.contains("connection refused")
                || lower.contains("connection reset")
                || lower.contains("end of ist")
                || lower.contains("timeout");
    }

    private static String sessionKey(ServerLogConfig config) {
        return config.getServiceName() + "@" + config.getHost() + ":" + config.getPort();
    }

    @PreDestroy
    public void shutdown() {
        sessionCache.values().forEach(session -> {
            if (session.isConnected()) session.disconnect();
        });
        sessionCache.clear();
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /**
     * Allowlist-based validation. A command is accepted only if:
     *   1. It contains no chaining or substitution operators (; && || ` $( newline).
     *   2. It contains no file-writing redirection ('>' / '<'), except the fixed,
     *      non-writing forms in SAFE_REDIRECTS (e.g. 2>&1, 2>/dev/null).
     *   3. It doesn't end with a background operator ('&').
     *   4. Every pipeline segment's leading binary is in ALLOWED_BINARIES, where
     *      pipeline segments are split on '|' that appears OUTSIDE single/double
     *      quotes — a quoted grep/egrep pattern is allowed to contain a literal
     *      '|' (e.g. regex alternation like 'foo|bar') without being mistaken
     *      for a real shell pipe boundary.
     *
     * This replaces the previous denylist, which only rejected a handful of known-bad
     * tokens and could be bypassed by anything not on that specific list (curl, pkill,
     * tee, mv, cp, cat of secret files, etc). An allowlist fails closed instead of open:
     * anything not explicitly recognized as safe is rejected by default.
     *
     * This is still string-based validation, not a real shell parser, so treat it as a
     * strong guardrail rather than a formal guarantee — the more robust long-term fix is
     * to stop accepting free-form shell strings entirely and expose only small,
     * parameterized tool methods (as most of ServerLogsTools already does).
     */
    private static void guardAgainstDestructiveCommands(String command) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("Command rejected: empty command");
        }

        if (command.contains(";") || command.contains("&&") || command.contains("||")
                || command.contains("`") || command.contains("$(") || command.contains("\n")
                || command.contains("\r")) {
            throw new IllegalArgumentException(
                    "Command rejected: chaining or command substitution is not allowed");
        }

        if (command.trim().endsWith("&")) {
            throw new IllegalArgumentException("Command rejected: background execution is not allowed");
        }

        String scrubbed = command;
        for (Pattern safe : SAFE_REDIRECTS) {
            scrubbed = safe.matcher(scrubbed).replaceAll("");
        }
        if (scrubbed.contains(">") || scrubbed.contains("<")) {
            throw new IllegalArgumentException("Command rejected: file redirection is not allowed");
        }

        List<String> segments = splitPipelineRespectingQuotes(command);
        for (String segment : segments) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("Command rejected: empty pipeline segment");
            }
            String firstToken = trimmed.split("\\s+", 2)[0];
            String binary = firstToken.contains("/")
                    ? firstToken.substring(firstToken.lastIndexOf('/') + 1)
                    : firstToken;
            if (!ALLOWED_BINARIES.contains(binary)) {
                throw new IllegalArgumentException(
                        "Command rejected: '" + binary + "' is not on the read-only command allowlist. "
                                + "Allowed: " + String.join(", ", ALLOWED_BINARIES));
            }
        }
    }

    /**
     * Splits a command into pipeline segments on '|', but ignores any '|' that
     * appears inside a single- or double-quoted region. This is a simple
     * character-scanning tokenizer (not a full shell parser) — it's enough to
     * stop a literal pipe inside a quoted grep/egrep pattern (e.g. 'foo|bar')
     * from being misread as a pipeline boundary and rejected as an "unknown
     * binary" segment.
     */
    private static List<String> splitPipelineRespectingQuotes(String command) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;

        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                current.append(c);
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                current.append(c);
            } else if (c == '|' && !inSingle && !inDouble) {
                segments.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        segments.add(current.toString());
        return segments;
    }
}
