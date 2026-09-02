package com.aii.mcp.service;

import com.aii.mcp.entity.ServerLogConfig;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ServerLogSshService {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int CHANNEL_TIMEOUT_MS = 20_000;
    private static final int MAX_OUTPUT_CHARS = 100_000;
    private static final long POLL_INTERVAL_MS = 15L;

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

    private static void guardAgainstDestructiveCommands(String command) {
        String lower = command.toLowerCase();
        String[] blocked = {"rm ", "rm -", ">", ">>", "mkfs", "dd ", "shutdown", "reboot", "kill ", "chmod ", "chown "};
        for (String token : blocked) {
            if (lower.contains(token)) {
                throw new IllegalArgumentException("Command rejected: contains disallowed token '" + token.trim() + "'");
            }
        }
    }
}