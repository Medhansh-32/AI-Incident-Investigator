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

    @Column(name = "ssh_key_path")
    private String sshKeyPath;

    @Column(name = "ssh_key_passphrase")
    private String sshKeyPassphrase;

    @Column(name = "log_file_path", nullable = false)
    private String logFilePath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // getters/setters omitted for brevity
}