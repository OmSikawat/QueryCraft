package com.qc.enterprise.controller;

import com.qc.enterprise.dto.ExecutionPlan;
import com.qc.enterprise.service.AiQueryService;
import com.qc.enterprise.service.AuditLogService;
import com.qc.enterprise.service.DataAggregatorService;
import com.qc.enterprise.service.FederatedExecutorService;
import com.qc.enterprise.service.VectorMathService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai") // 👈 Kept your original base URL!
public class AgentController {

    @Autowired
    private VectorMathService vectorMathService;

    @Autowired
    private AiQueryService aiQueryService;

    @Autowired
    private FederatedExecutorService federatedExecutorService;

    // 👇 Injected Day 7 Services
    @Autowired
    private DataAggregatorService dataAggregatorService;

    @Autowired
    private AuditLogService auditLogService;

    // ---------------------------------------------------------
    // 🛠️ ADMIN UTILITY: Generate vectors for new schemas
    // Endpoint: POST http://localhost:8080/api/ai/embed
    // ---------------------------------------------------------
    @PostMapping("/embed")
    public ResponseEntity<String> generateEmbeddings() {
        vectorMathService.generateMissingEmbeddings();
        return ResponseEntity.ok("Embeddings generated successfully!");
    }

    // ---------------------------------------------------------
    // 🚀 MAIN CHAT PIPELINE: Day 7 Enterprise RAG Engine
    // Endpoint: POST http://localhost:8080/api/ai/query
    // ---------------------------------------------------------
    @PostMapping("/query")
    public ResponseEntity<?> askQuestion(@RequestBody Map<String, String> request) {
        long startTime = System.currentTimeMillis();
        String userRole = "UNKNOWN";
        List<ExecutionPlan> plans = null;

        try {
            String question = request.get("question");

            // 1. Identity Verification
            userRole = SecurityContextHolder.getContext()
                    .getAuthentication().getAuthorities().iterator().next().getAuthority();
            System.out.println("User asked: " + question + " | Role: " + userRole);

            // 2. The RAG Pipeline
            String dynamicContext = vectorMathService.findRelevantSchemas(question, userRole);

            // 3. AI Generation
            plans = aiQueryService.generateExecutionPlans(question, dynamicContext);
            System.out.println("AI Generated Plans: " + plans);

            // 4. Parallel Execution (Raw Data)
            Map<String, List<Map<String, Object>>> rawData = federatedExecutorService.executeFederatedPlans(plans);

            // 5. Data Transformation (Clean vectors, flatten IDs)
            Map<String, List<Map<String, Object>>> cleanData = dataAggregatorService.cleanAndAggregate(rawData);

            // 6. Enterprise Audit Logging
            long executionTimeMs = System.currentTimeMillis() - startTime;
            auditLogService.logTransactionAsync(userRole, question, plans, executionTimeMs, "SUCCESS");

            return ResponseEntity.ok(cleanData);

        } catch (SecurityException se) {
            long executionTimeMs = System.currentTimeMillis() - startTime;
            // The AI might not have generated plans if security blocked it early, so we pass 'plans' safely
            auditLogService.logTransactionAsync(userRole, request.get("question"), plans, executionTimeMs, "BLOCKED_BY_SECURITY");
            return ResponseEntity.status(403).body(Map.of("Security Alert", se.getMessage()));

        } catch (Exception e) {
            long executionTimeMs = System.currentTimeMillis() - startTime;
            auditLogService.logTransactionAsync(userRole, request.get("question"), plans, executionTimeMs, "FAILED");
            return ResponseEntity.badRequest().body(Map.of("Error", e.getMessage()));
        }
    }
}