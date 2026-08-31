package com.aii.mcp.service;


import com.aii.mcp.entity.ServerLogConfig;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ServerLogSshService {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int CHANNEL_TIMEOUT_MS = 20_000;
    private static final int MAX_OUTPUT_CHARS = 100_000;

    public Map<String, Object> tailLog(ServerLogConfig config, int lines) {
        String command = "tail -n " + lines + " -- " + shellQuote(config.getLogFilePath());
        return run(config, command);
    }

    public Map<String, Object> runCommand(ServerLogConfig config, String command) {
        guardAgainstDestructiveCommands(command);
        return run(config, command);
    }

    private Map<String, Object> run(ServerLogConfig config, String command) {
        Session session = null;
        ChannelExec channel = null;
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());

            if (config.getSshKeyPath() != null && !config.getSshKeyPath().isBlank()) {
                if (config.getSshKeyPassphrase() != null && !config.getSshKeyPassphrase().isBlank()) {
                    jsch.addIdentity(config.getSshKeyPath(), config.getSshKeyPassphrase());
                } else {
                    jsch.addIdentity(config.getSshKeyPath());
                }
            } else {
                throw new IllegalStateException("No SSH key configured for service '" + config.getServiceName() + "'");
            }

            session.setConfig("StrictHostKeyChecking", "no"); // use a real known_hosts file in production
            session.connect(CONNECT_TIMEOUT_MS);

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
                Thread.sleep(100);
            }

            String out = stdOut.toString();
            if (out.length() > MAX_OUTPUT_CHARS) {
                out = "...[truncated]...\n" + out.substring(out.length() - MAX_OUTPUT_CHARS);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("service", config.getServiceName());
            result.put("host", config.getHost());
            result.put("command", command);
            result.put("exitStatus", channel.getExitStatus());
            result.put("stdout", out);
            result.put("stderr", stdErr.toString());
            return result;

        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("service", config.getServiceName());
            error.put("error", e.getMessage());
            return error;
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
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