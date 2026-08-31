package com.aii.dto;

public record SearchRequest(String query, String serviceName, Integer topK) {}
