package com.qc.enterprise.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * Asynchronously saves a record of the AI transaction to MongoDB.
     */
    public void logTransactionAsync(String userRole, String question, Object aiPlans, long executionTimeMs, String status) {

        // Fire and forget! Run this on a separate background thread.
        CompletableFuture.runAsync(() -> {
            try {
                // 🛡️ THE FIX: Use a classic HashMap. It safely allows nulls
                // and Spring Boot will automatically serialize it to Mongo JSON!
                Map<String, Object> auditDocument = new HashMap<>();
                auditDocument.put("timestamp", Instant.now().toString());
                auditDocument.put("userRole", userRole);
                auditDocument.put("question", question);
                auditDocument.put("generatedPlans", aiPlans != null ? aiPlans : "[]");
                auditDocument.put("executionTimeMs", executionTimeMs);
                auditDocument.put("status", status);

                // Insert into a dedicated 'audit_logs' collection
                mongoTemplate.insert(auditDocument, "audit_logs");
                log.info("✅ Enterprise Audit Log saved successfully.");

            } catch (Exception e) {
                log.error("❌ Failed to save audit log: {}", e.getMessage());
            }
        });
    }
}