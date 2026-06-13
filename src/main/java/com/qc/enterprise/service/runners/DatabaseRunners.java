package com.qc.enterprise.service.runners;

import com.qc.enterprise.service.QueryValidatorService;
import io.minio.*;
import io.minio.messages.InputSerialization;
import io.minio.messages.OutputSerialization;
import io.minio.messages.QuoteFields;
import io.minio.messages.FileHeaderInfo;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DatabaseRunners {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private MinioClient minioClient;
    @Autowired private QueryValidatorService validatorService;

    // ------------------------------------
    // 1. POSTGRES RUNNER (SECURED)
    // ------------------------------------
    public List<Map<String, Object>> runPostgres(String sql) {
        validatorService.validateSafeSelect(sql);
        System.out.println("Executing safe Postgres SQL: " + sql);

        // 1. Run the raw query
        List<Map<String, Object>> rawResults = jdbcTemplate.queryForList(sql);

        // 2. SECURITY BOUNCER: Get the active user's role
        String userRole = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getAuthorities().iterator().next().getAuthority();

        // 3. Post-Query Firewall: Strip out any rows the user isn't allowed to see!
        // (Assuming you are querying the schema_context table which has the allowed_roles column)
        if (!"ROLE_ADMIN".equals(userRole)) {
            rawResults.removeIf(row -> {
                Object allowedRoles = row.get("allowed_roles");
                return allowedRoles != null && !allowedRoles.toString().contains(userRole);
            });
        }

        return rawResults;
    }

    // ------------------------------------
    // 2. MONGO RUNNER (SECURED)
    // ------------------------------------
    public List<Map<String, Object>> runMongo(String jsonQuery, String collectionName) {
        System.out.println("Executing Mongo Document Query: " + jsonQuery);

        // 🛡️ SECURITY BOUNCER: Check who is actually asking for this data!
        String userRole = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getAuthorities().iterator().next().getAuthority();

        // If a Sales user tries to access system logs, kick them out immediately.
        if (!"ROLE_ADMIN".equals(userRole)) {
            System.err.println("SECURITY ALERT: User with role " + userRole + " attempted to access Mongo system logs.");
            throw new SecurityException("Unauthorized: Your role (" + userRole + ") does not have permission to access system logs.");
        }

        // FAILSAFE: If the AI hallucinates a $sort anyway, clean it up to prevent a crash
        if (jsonQuery.contains("$sort") || jsonQuery.trim().startsWith("[")) {
            jsonQuery = "{}";
        }

        BasicQuery query = new BasicQuery(jsonQuery);

        // Add safe enterprise defaults: Always get the newest logs, limit to 10
        org.springframework.data.domain.Sort sort = org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "timestamp"
        );
        query.with(sort);
        query.limit(10);

        List<Document> results = mongoTemplate.find(query, Document.class, collectionName);

        List<Map<String, Object>> formattedResults = new ArrayList<>();
        for (Document doc : results) {
            formattedResults.add(doc);
        }
        return formattedResults;
    }

    // ------------------------------------
    // 3. MINIO CSV RUNNER (SECURED)
    // ------------------------------------
    public List<Map<String, Object>> runMinio(String sql, String bucketName, String fileName) {
        System.out.println("Executing MinIO S3 Select on " + fileName + ": " + sql);

        // 🛡️ SECURITY BOUNCER: Check file-level access
        String userRole = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getAuthorities().iterator().next().getAuthority();

        // Rule: Only Admins can query files with 'salary', 'admin', or 'financial' in the name
        if (!"ROLE_ADMIN".equals(userRole) &&
                (fileName.contains("salary") || fileName.contains("admin") || fileName.contains("financial"))) {
            System.err.println("SECURITY ALERT: User attempted to access protected file: " + fileName);
            throw new SecurityException("Unauthorized: Your role cannot access protected cloud files.");
        }

        try {
            InputSerialization is = new InputSerialization(null, false, null, null, FileHeaderInfo.USE, null, null, null);
            OutputSerialization os = new OutputSerialization(null, null, null, QuoteFields.ASNEEDED, null);

            io.minio.SelectResponseStream stream = minioClient.selectObjectContent(
                    SelectObjectContentArgs.builder()
                            .bucket(bucketName)
                            .object(fileName)
                            .sqlExpression(sql)
                            .inputSerialization(is)
                            .outputSerialization(os)
                            .requestProgress(false) // 👈 THE FIX: Explicitly disable progress tracking
                            .build()
            );

            StringBuilder resultBuilder = new StringBuilder();
            byte[] buf = new byte[1024];
            int len;
            try (stream) {
                while ((len = stream.read(buf)) != -1) {
                    resultBuilder.append(new String(buf, 0, len));
                }
            }
            return List.of(Map.of("minio_raw_data", resultBuilder.toString()));

        } catch (Exception e) {
            throw new RuntimeException("MinIO Fetch Failed: " + e.getMessage());
        }
    }
}