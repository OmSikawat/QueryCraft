package com.qc.enterprise.controller;

import com.qc.enterprise.service.VectorMathService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/webhook")
public class MinioWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MinioWebhookController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VectorMathService vectorMathService;

    @PostMapping("/minio")
    public ResponseEntity<String> handleMinioUpload(@RequestBody Map<String, Object> payload) {
        try {
            // MinIO S3 Event Notifications are deeply nested JSON. We carefully extract the filename.
            List<Map<String, Object>> records = (List<Map<String, Object>>) payload.get("Records");
            if (records == null || records.isEmpty()) {
                return ResponseEntity.ok("Ignored: No records found.");
            }

            Map<String, Object> s3Data = (Map<String, Object>) records.get(0).get("s3");
            Map<String, Object> objectData = (Map<String, Object>) s3Data.get("object");
            String fileName = (String) objectData.get("key");

            log.info("📥 Webhook Triggered! New file uploaded to MinIO: {}", fileName);

            // Only process CSV files
            if (fileName != null && fileName.endsWith(".csv")) {
                Integer exists = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM schema_context WHERE target_name = ? AND source_database = 'MINIO_CSV'",
                        Integer.class, fileName
                );

                if (exists != null && exists == 0) {
                    String schemaText = "STORAGE_TYPE: MINIO_CSV | FILE: " + fileName;

                    jdbcTemplate.update(
                            "INSERT INTO schema_context (source_database, target_name, schema_text, allowed_roles) VALUES (?, ?, ?, ?)",
                            "MINIO_CSV", fileName, schemaText, "ROLE_ADMIN,ROLE_USER"
                    );

                    log.info("🧠 Auto-Embedding new file into Vector Database...");
                    vectorMathService.generateMissingEmbeddings();
                }
            }
            return ResponseEntity.ok("Webhook processed successfully.");

        } catch (Exception e) {
            log.error("❌ Failed to process MinIO webhook: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Webhook processing failed.");
        }
    }
}