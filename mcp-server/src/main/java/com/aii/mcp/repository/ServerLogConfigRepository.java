package com.aii.mcp.repository;

import com.aii.mcp.entity.ServerLogConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServerLogConfigRepository extends JpaRepository<ServerLogConfig, Long> {
    Optional<ServerLogConfig> findByServiceName(String serviceName);
}