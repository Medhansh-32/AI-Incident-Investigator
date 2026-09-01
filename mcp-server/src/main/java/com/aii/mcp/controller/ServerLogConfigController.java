package com.aii.mcp.controller;

import com.aii.mcp.entity.ServerLogConfig;
import com.aii.mcp.service.ServerLogConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@RestController
@RequestMapping("/admin/log-configs")
public class ServerLogConfigController {

    private final ServerLogConfigService service;

    public ServerLogConfigController(ServerLogConfigService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        ServerLogConfig config = service.register(
                request.serviceName(),
                request.host(),
                request.port(),
                request.username(),
                request.privateKey(),
                request.logFilePath()
        );

        // Never echo the key back, even encrypted.
        return ResponseEntity.ok(Map.of(
                "message", "Log config registered",
                "serviceName", config.getServiceName(),
                "id", config.getId()
        ));
    }

    @GetMapping
    public ResponseEntity<?> checkMCPServerHealth() {
        return ResponseEntity.ok(Map.of("message", "healthy"));
    }

    public record RegisterRequest(
            String serviceName,
            String host,
            int port,
            String username,
            String privateKey,
            String logFilePath
    ) {}
}
