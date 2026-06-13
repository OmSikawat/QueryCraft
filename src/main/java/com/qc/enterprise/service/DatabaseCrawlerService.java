package com.qc.enterprise.service;

import io.minio.MinioClient;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import io.minio.ListObjectsArgs;
import io.minio.Result;
import io.minio.messages.Item;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DatabaseCrawlerService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseCrawlerService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private VectorMathService vectorMathService;

    @Autowired
    private MinioClient minioClient;

    // ⏱️ Runs automatically every 5 minutes (300,000 ms)
//    @Scheduled(fixedRate = 300000)
    public void crawlDatabases() {
        log.info("🕵️‍♂️ Automation Engine: Crawling databases for new schemas...");
        boolean foundNewData = false;

        try {
            foundNewData |= crawlPostgres();
            foundNewData |= crawlMongo();
            foundNewData |= crawlMinio();

            if (foundNewData) {
                log.info("🧠 New schemas detected! Triggering Vector Math Engine...");
                vectorMathService.generateMissingEmbeddings();
            } else {
                log.info("💤 No new schemas found. Vector database is up to date.");
            }
        } catch (Exception e) {
            log.error("❌ Crawler failed: {}", e.getMessage());
        }
    }

    private boolean crawlPostgres() {
        boolean updated = false;

        // Extract all tables and columns from Postgres
        String extractSql = """
            SELECT table_name, string_agg(column_name || ' (' || data_type || ')', ', ') as columns
            FROM information_schema.columns
            WHERE table_schema = 'public' 
              AND table_name != 'schema_context' -- Ignore the AI's own brain
            GROUP BY table_name;
            """;

        List<Map<String, Object>> liveTables = jdbcTemplate.queryForList(extractSql);

        for (Map<String, Object> table : liveTables) {
            String tableName = (String) table.get("table_name");
            String columns = (String) table.get("columns");
            String schemaText = "Table '" + tableName + "' has columns: " + columns;

            // Check if it already exists in the AI's memory
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM schema_context WHERE target_name = ? AND source_database = 'POSTGRES'",
                    Integer.class, tableName
            );

            if (exists != null && exists == 0) {
                log.info("🚨 New Postgres Table found: {}", tableName);

                // 👇 STEP 2: Calculate the roles dynamically
                String assignedRoles = determineRoles(tableName);

                jdbcTemplate.update(
                        "INSERT INTO schema_context (source_database, target_name, schema_text, allowed_roles) VALUES (?, ?, ?, ?)",
                        "POSTGRES", tableName, schemaText, assignedRoles // 👈 Replaced the hardcoded string
                );
                updated = true;
            }
        }
        return updated;
    }

    private boolean crawlMongo() {
        boolean updated = false;
        Set<String> collections = mongoTemplate.getCollectionNames();

        for (String collectionName : collections) {
            // Ignore MongoDB system collections and our audit logs
            if (collectionName.startsWith("system.") || collectionName.equals("audit_logs")) continue;

            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM schema_context WHERE target_name = ? AND source_database = 'MONGO'",
                    Integer.class, collectionName
            );

            if (exists != null && exists == 0) {
                log.info("🚨 New Mongo Collection found: {}", collectionName);

                // Grab the newest document to figure out what keys/fields it uses
                Document sampleDoc = mongoTemplate.getCollection(collectionName).find().first();
                String keys = sampleDoc != null ? String.join(", ", sampleDoc.keySet()) : "unknown_fields";
                String schemaText = "MongoDB Collection '" + collectionName + "' has fields: " + keys;

                jdbcTemplate.update(
                        "INSERT INTO schema_context (source_database, target_name, schema_text, allowed_roles) VALUES (?, ?, ?, ?)",
                        "MONGO", collectionName, schemaText, "ROLE_ADMIN,ROLE_USER"
                );
                updated = true;
            }
        }
        return updated;
    }

    // ... (end of crawlMongo method) ...

    // 👇 STEP 1: Add this new helper method at the bottom of the class
    private String determineRoles(String targetName) {
        String roles = "ROLE_ADMIN"; // Admin always gets access

        String lowerName = targetName.toLowerCase();

        if (lowerName.contains("salary") || lowerName.contains("employee") || lowerName.contains("payroll")) {
            roles += ",ROLE_HR";
        }
        else if (lowerName.contains("public") || lowerName.contains("inventory")) {
            roles += ",ROLE_USER,ROLE_HR,ROLE_SALES";
        }

        return roles;
    }
    private boolean crawlMinio() {
        boolean updated = false;
        String bucketName = "querycraft-files"; // 👈 Using your actual bucket name!

        try {
            // Get a list of all files in the bucket
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucketName).build()
            );

            for (Result<Item> result : results) {
                Item item = result.get();
                String fileName = item.objectName();

                // Only process CSV files
                if (fileName.endsWith(".csv")) {

                    // Check if AI already knows about this file
                    Integer exists = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM schema_context WHERE target_name = ? AND source_database = 'MINIO_CSV'",
                            Integer.class, fileName
                    );

                    if (exists != null && exists == 0) {
                        log.info("🚨 New MinIO CSV File found: {}", fileName);

                        String schemaText = "STORAGE_TYPE: MINIO_CSV | FILE: " + fileName;
                        String assignedRoles = determineRoles(fileName);

                        jdbcTemplate.update(
                                "INSERT INTO schema_context (source_database, target_name, schema_text, allowed_roles) VALUES (?, ?, ?, ?)",
                                "MINIO_CSV", fileName, schemaText, assignedRoles
                        );
                        updated = true;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to crawl MinIO: {}", e.getMessage());
        }
        return updated;
    }
} // <-- This is the final closing bracket of the DatabaseCrawlerService class
