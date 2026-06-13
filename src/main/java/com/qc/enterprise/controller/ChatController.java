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
@RequestMapping("/api")
public class ChatController {

    @Autowired
    private AiQueryService aiQueryService;

    @Autowired
    private FederatedExecutorService federatedExecutorService;

    @Autowired
    private VectorMathService vectorMathService;

    // 👇 1. Inject your new Day 7 Services
    @Autowired
    private DataAggregatorService dataAggregatorService;

    @Autowired
    private AuditLogService auditLogService;

    public record ChatRequest(String question) {}

    @PostMapping("/chat")
    public ResponseEntity<?> chatWithData(@RequestBody ChatRequest request) {
        // ⏱️ Start the Audit Stopwatch
        long startTime = System.currentTimeMillis();

        // Declare these outside the try block so the catch blocks can log them if something breaks!
        String userRole = "UNKNOWN";
        List<ExecutionPlan> plans = null;

        try {
            String question = request.question();

            // 1. Identity Verification
            userRole = SecurityContextHolder.getContext()
                    .getAuthentication().getAuthorities().iterator().next().getAuthority();
            System.out.println("User asked: " + question + " | Role: " + userRole);

            // 2. The RAG Pipeline: Find ONLY the schemas this user is allowed to see!
            String dynamicContext = vectorMathService.findRelevantSchemas(question, userRole);
            System.out.println("DEBUG - Retrieved Context:\n" + dynamicContext);

            // 3. AI Generation
            plans = aiQueryService.generateExecutionPlans(question, dynamicContext);
            System.out.println("AI Generated Plans: " + plans);

            // 4. Parallel Execution (Raw Data)
            Map<String, List<Map<String, Object>>> rawData = federatedExecutorService.executeFederatedPlans(plans);

            // 👇 5. Data Transformation: Clean up vectors and flatten Mongo IDs
            Map<String, List<Map<String, Object>>> cleanData = dataAggregatorService.cleanAndAggregate(rawData);

            // 👇 6. Enterprise Audit Logging: Save successful transaction async
            long executionTimeMs = System.currentTimeMillis() - startTime;
            auditLogService.logTransactionAsync(userRole, question, plans, executionTimeMs, "SUCCESS");

            // Return the beautiful, clean JSON!
            return ResponseEntity.ok(cleanData);

        } catch (SecurityException se) {
            // 👇 Log security breaches to the Black Box!
            long executionTimeMs = System.currentTimeMillis() - startTime;
            auditLogService.logTransactionAsync(userRole, request.question(), plans, executionTimeMs, "BLOCKED_BY_SECURITY");
            return ResponseEntity.status(403).body(Map.of("Security Alert", se.getMessage()));

        } catch (Exception e) {
            // 👇 Log total system failures to the Black Box!
            long executionTimeMs = System.currentTimeMillis() - startTime;
            auditLogService.logTransactionAsync(userRole, request.question(), plans, executionTimeMs, "FAILED");
            return ResponseEntity.badRequest().body(Map.of("Error", e.getMessage()));
        }
    }
}