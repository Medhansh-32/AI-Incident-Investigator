package com.aii.mcp.service;

import com.aii.mcp.entity.ServerLogConfig;
import com.aii.mcp.repository.ServerLogConfigRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ServerLogConfigService {

    private final ServerLogConfigRepository repository;
    private final EncryptionService encryptionService;

    public ServerLogConfigService(ServerLogConfigRepository repository,
                                  EncryptionService encryptionService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
    }

    public ServerLogConfig getByServiceName(String serviceName) {
        return repository.findByServiceName(serviceName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No log config found for service '" + serviceName + "'. "
                                + "Check the service name or register it first."));
    }

    public List<String> listServiceNames() {
        return repository.findAll().stream().map(ServerLogConfig::getServiceName).toList();
    }

    /**
     * Encrypts the given plaintext private key and stores a new config.
     * The plaintext never touches the DB.
     */
    public ServerLogConfig register(String serviceName, String host, int port,
                                    String username, String plaintextPrivateKey,
                                    String logFilePath) {

        if (repository.findByServiceName(serviceName).isPresent()) {
            throw new IllegalArgumentException(
                    "Service '" + serviceName + "' is already registered.");
        }

        ServerLogConfig config = new ServerLogConfig();
        config.setServiceName(serviceName);
        config.setHost(host);
        config.setPort(port);
        config.setUsername(username);
        config.setEncryptedPrivateKey(encryptionService.encrypt(plaintextPrivateKey));
        config.setLogFilePath(logFilePath);
        config.setCreatedAt(Instant.now());
        config.setUpdatedAt(Instant.now());

        return repository.save(config);
    }

    /**
     * Returns the decrypted private key bytes, ready for JSch.addIdentity().
     * Never log or return this value to a client.
     */
    public byte[] getDecryptedPrivateKey(String serviceName) {
        ServerLogConfig config = getByServiceName(serviceName);
        String plaintext = encryptionService.decrypt(config.getEncryptedPrivateKey());
        return plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}