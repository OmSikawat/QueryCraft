package com.qc.enterprise.controller;

import com.qc.enterprise.service.SchemaExtractionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schema")
public class SchemaController {

    @Autowired
    private SchemaExtractionService schemaExtractionService;

    @PostMapping("/refresh")
    public Map<String, Object> refreshSchemas() {
        Map<String, Object> response = new HashMap<>();

        try {
            List<String> savedSchemas = schemaExtractionService.refreshAllSchemas();

            response.put("status", "SUCCESS");
            response.put("message", "Schemas and CSV headers successfully extracted to pgvector.");
            response.put("data", savedSchemas);
        } catch (Exception e) {
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }

        return response;
    }
}