package com.aii.mcp.service;

import com.aii.mcp.entity.ServerLogConfig;
import com.aii.mcp.repository.ServerLogConfigRepository;
import org.springframework.stereotype.Service;

@Service
public class ServerLogConfigService {

    private final ServerLogConfigRepository repository;

    public ServerLogConfigService(ServerLogConfigRepository repository) {
        this.repository = repository;
    }

    public ServerLogConfig getByServiceName(String serviceName) {
        return repository.findByServiceName(serviceName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No log config found for service '" + serviceName + "'. "
                                + "Check the service name or register it first."));
    }
}