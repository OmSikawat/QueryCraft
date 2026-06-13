package com.qc.enterprise.dto;

// 1. Added targetName so the AI can specify the Mongo Collection or MinIO File
public record ExecutionPlan(String databaseType, String targetName, String query) {}