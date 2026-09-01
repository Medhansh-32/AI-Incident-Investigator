package com.aii.mcp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "server_log_configs")
@Data
public class ServerLogConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_name", nullable = false, unique = true)
    private String serviceName;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private Integer port = 22;

    @Column(nullable = false)
    private String username;

    // Replaces sshKeyPath. Holds AES-256-GCM ciphertext (Base64), never plaintext.
    @Column(name = "encrypted_private_key", nullable = false, columnDefinition = "TEXT")
    private String encryptedPrivateKey;

    @Column(name = "log_file_path", nullable = false)
    private String logFilePath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // getters/setters via Lombok @Data
}