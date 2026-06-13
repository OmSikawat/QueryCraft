package com.qc.enterprise.service;

import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SchemaExtractionService {

    private static final Logger log = LoggerFactory.getLogger(SchemaExtractionService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MinioClient minioClient;

    /**
     * MASTER METHOD: Clears old schemas and extracts fresh ones.
     */
    public List<String> refreshAllSchemas() {
        // Clear out old schemas to prevent duplicates
        jdbcTemplate.update("DELETE FROM schema_context");
        log.info("Cleared old schema context.");

        List<String> results = new ArrayList<>();
        results.addAll(extractAndSavePostgresSchemas());
        results.addAll(extractAndSaveMongoSchemas());
        results.addAll(extractAndSaveMinioCsvSchemas());

        return results;
    }

    /**
     * 1. Extracts Relational Schemas from PostgreSQL
     */
    private List<String> extractAndSavePostgresSchemas() {
        List<String> extracted = new ArrayList<>();
        String tableQuery = "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'";
        List<String> tables = jdbcTemplate.queryForList(tableQuery, String.class);

        for (String table : tables) {
            // Do not extract the vector storage table itself
            if (table.equals("schema_context")) continue;

            String columnQuery = "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = ?";
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(columnQuery, table);

            StringBuilder schemaText = new StringBuilder();
            schemaText.append("Table '").append(table).append("' has columns: ");
            for (Map<String, Object> col : columns) {
                schemaText.append(col.get("column_name")).append(" (").append(col.get("data_type")).append("), ");
            }

            String finalDescription = schemaText.toString();
            extracted.add("POSTGRES: " + finalDescription);
            saveToContext("POSTGRES", table, finalDescription);
        }
        return extracted;
    }

    /**
     * 2. Extracts Document Schemas from MongoDB
     */
    private List<String> extractAndSaveMongoSchemas() {
        List<String> extracted = new ArrayList<>();
        Set<String> collections = mongoTemplate.getCollectionNames();

        for (String collectionName : collections) {
            Document sampleDoc = mongoTemplate.getCollection(collectionName).find().first();
            if (sampleDoc != null) {
                StringBuilder schemaText = new StringBuilder();
                schemaText.append("MongoDB Collection '").append(collectionName).append("' has fields: ");
                for (String key : sampleDoc.keySet()) {
                    schemaText.append(key).append(", ");
                }

                String finalDescription = schemaText.toString();
                extracted.add("MONGO: " + finalDescription);
                saveToContext("MONGO", collectionName, finalDescription);
            }
        }
        return extracted;
    }

    /**
     * 3. Extracts CSV Headers from MinIO (ETL Prep)
     */
    private List<String> extractAndSaveMinioCsvSchemas() {
        List<String> extracted = new ArrayList<>();
        String bucketName = "querycraft-files";

        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucketName).build());

            for (Result<Item> result : results) {
                Item item = result.get();
                String fileName = item.objectName();

                if (fileName.toLowerCase().endsWith(".csv")) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                            minioClient.getObject(GetObjectArgs.builder()
                                    .bucket(bucketName)
                                    .object(fileName)
                                    .build())))) {

                        String headerLine = reader.readLine();
                        if (headerLine != null) {
                            String finalDescription = "MinIO CSV File '" + fileName + "' has columns: " + headerLine;
                            extracted.add("MINIO_CSV: " + finalDescription);
                            saveToContext("MINIO_CSV", fileName, finalDescription);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to extract CSV headers from MinIO", e);
        }
        return extracted;
    }

    /**
     * Helper method to insert the plain text into pgvector
     */
    private void saveToContext(String source, String target, String text) {
        // By default, let's say normal tables are accessible by Users and Admins.
        // But system_logs (MongoDB) are strictly for Admins.
        String allowedRoles = "ROLE_USER,ROLE_ADMIN";
        if (source.equals("MONGO")) {
            allowedRoles = "ROLE_ADMIN";
        } else if (source.equals("MINIO_CSV")) {
            allowedRoles = "ROLE_SALES,ROLE_ADMIN";
        }

        String insertSql = "INSERT INTO schema_context (source_database, target_name, schema_text, allowed_roles) VALUES (?, ?, ?, ?)";

        jdbcTemplate.update(insertSql, source, target, text, allowedRoles);
        log.info("Saved schema for {} -> {} with roles {}", source, target, allowedRoles);
    }
}