package com.qc.enterprise.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DataAggregatorService {

    /**
     * Takes raw, messy data from multiple databases and formats it for the frontend UI.
     */
    public Map<String, List<Map<String, Object>>> cleanAndAggregate(Map<String, List<Map<String, Object>>> rawData) {
        Map<String, List<Map<String, Object>>> cleanFederatedData = new HashMap<>();

        for (Map.Entry<String, List<Map<String, Object>>> entry : rawData.entrySet()) {
            String databaseKey = entry.getKey(); // e.g., "POSTGRES_Result_0"
            List<Map<String, Object>> rawRows = entry.getValue();
            List<Map<String, Object>> cleanRows = new ArrayList<>();

            for (Map<String, Object> rawRow : rawRows) {
                // Create a mutable copy of the row so we don't alter the original database references
                Map<String, Object> cleanRow = new HashMap<>(rawRow);

                // 🧹 RULE 1: Strip PostgreSQL Vectors
                if (databaseKey.startsWith("POSTGRES")) {
                    // Remove the massive 3072-dimension array to save bandwidth
                    cleanRow.remove("embedding");
                }

                // 🧹 RULE 2: Flatten MongoDB IDs
                if (databaseKey.startsWith("MONGO")) {
                    // Mongo returns complex ObjectIds (e.g., {"$oid": "..."}). Flatten it to a simple string.
                    if (cleanRow.containsKey("_id")) {
                        cleanRow.put("id", cleanRow.get("_id").toString());
                        cleanRow.remove("_id");
                    }
                }

                cleanRows.add(cleanRow);
            }
            cleanFederatedData.put(databaseKey, cleanRows);
        }

        return cleanFederatedData;
    }
}