package com.qc.enterprise.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class VectorMathService {

    private static final Logger log = LoggerFactory.getLogger(VectorMathService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EmbeddingModel embeddingModel;

    /**
     * Finds plain-text schemas, calculates their math coordinates via Gemini,
     * and saves them securely in pgvector.
     */
    public void generateMissingEmbeddings() {
        // 1. Find rows where we haven't calculated the math yet
        String findSql = "SELECT id, schema_text FROM schema_context WHERE embedding IS NULL";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(findSql);

        log.info("Found {} schemas that need vector embeddings.", rows.size());

        for (Map<String, Object> row : rows) {
            Integer id = (Integer) row.get("id");
            String text = (String) row.get("schema_text");

            // 2. Call Google Gemini to turn the text into Math (Vector Array)
            float[] vectorMath = embeddingModel.embed(text);

            // 3. Save the vector back into Postgres. We convert the array to a String
            // so Postgres can cast it to a ::vector type.
            String updateSql = "UPDATE schema_context SET embedding = ?::vector WHERE id = ?";
            jdbcTemplate.update(updateSql, Arrays.toString(vectorMath), id);

            log.info("Successfully generated embedding for schema ID: {}", id);
        }
    }

    /**
     * Performs the RAG Vector Search.
     * Converts the user's question into a vector and finds the closest matching schemas
     * that the user's role is allowed to see.
     */
    public String findRelevantSchemas(String question, String userRole) {
        log.info("Embedding user question for vector search...");

        // 1. Convert the English question into a mathematical vector using Gemini
        // (Using float[] to perfectly match your existing codebase)
        float[] questionVector = embeddingModel.embed(question);

        // 2. The Vector Search SQL
        // - `LIKE ?` ensures the user has permission to see the table.
        // - `<=> ?::vector` calculates the Cosine Distance.
        // - `LIMIT 5` ensures we only send the 5 most relevant tables to the AI.
        String sql = """
            SELECT source_database, target_name, schema_text 
            FROM schema_context 
            WHERE allowed_roles LIKE ? 
            ORDER BY embedding <=> ?::vector 
            LIMIT 5
            """;

        List<Map<String, Object>> relevantRows = jdbcTemplate.queryForList(
                sql,
                "%" + userRole + "%",
                Arrays.toString(questionVector)
        );

        if (relevantRows.isEmpty()) {
            log.warn("No relevant schemas found for role: {}", userRole);
            return "No relevant schemas found for this user role.";
        }

        // 3. Stitch the winning schemas together into a clean text block for the AI Prompt
        StringBuilder contextBuilder = new StringBuilder();
        for (Map<String, Object> row : relevantRows) {
            contextBuilder.append("DATABASE_TYPE: ").append(row.get("source_database")).append("\n")
                    .append("TARGET_NAME: ").append(row.get("target_name")).append("\n")
                    .append("SCHEMA: ").append(row.get("schema_text")).append("\n\n");
        }

        return contextBuilder.toString();
    }
}