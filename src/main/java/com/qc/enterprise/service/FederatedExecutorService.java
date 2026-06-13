package com.qc.enterprise.service;

import com.qc.enterprise.dto.ExecutionPlan;
import com.qc.enterprise.service.runners.DatabaseRunners;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier; // 👈 Add this
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor; // 👈 Add this

@Service
public class FederatedExecutorService {

    @Autowired
    private DatabaseRunners runners;

    // 1. Inject the Magic Executor
    @Autowired
    @Qualifier("securityContextExecutor")
    private Executor securityExecutor;

    // Notice we removed the userRole string here too!
    public Map<String, List<Map<String, Object>>> executeFederatedPlans(List<ExecutionPlan> plans) {
        Map<String, CompletableFuture<List<Map<String, Object>>>> futureTasks = new HashMap<>();

        for (int i = 0; i < plans.size(); i++) {
            ExecutionPlan plan = plans.get(i);
            String taskKey = plan.databaseType() + "_Result_" + i;

            // 2. Pass the executor as the second argument to supplyAsync!
            if (plan.databaseType().equals("POSTGRES")) {
                futureTasks.put(taskKey, CompletableFuture.supplyAsync(() -> runners.runPostgres(plan.query()), securityExecutor));
            }
            else if (plan.databaseType().equals("MONGO")) {
                // 👇 Pass the targetName to Mongo
                futureTasks.put(taskKey, CompletableFuture.supplyAsync(() -> runners.runMongo(plan.query(), plan.targetName()), securityExecutor));
            }
            // 👇 Update "enterprise-data" to your ACTUAL bucket name from MinIO Console
            else if (plan.databaseType().equals("MINIO_CSV")) {
                futureTasks.put(taskKey, CompletableFuture.supplyAsync(() ->
                                runners.runMinio(plan.query(), "querycraft-files", plan.targetName()),
                        securityExecutor)
                );
            }
        }

        // Wait for all databases to finish...
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futureTasks.values().toArray(new CompletableFuture[0])
        );
        allFutures.join();

        // Collect results...
        Map<String, List<Map<String, Object>>> finalFederatedResult = new HashMap<>();
        for (Map.Entry<String, CompletableFuture<List<Map<String, Object>>>> entry : futureTasks.entrySet()) {
            try {
                finalFederatedResult.put(entry.getKey(), entry.getValue().get());
            } catch (Exception e) {
                finalFederatedResult.put(entry.getKey(), List.of(Map.of("ERROR", e.getMessage())));
            }
        }

        return finalFederatedResult;
    }
}