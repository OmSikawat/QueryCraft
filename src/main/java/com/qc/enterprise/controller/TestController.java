package com.qc.enterprise.controller;

import com.qc.enterprise.dto.ExecutionPlan;
import com.qc.enterprise.model.Department;
import com.qc.enterprise.model.SystemLog;
import com.qc.enterprise.repository.DepartmentRepository;
import com.qc.enterprise.repository.SystemLogRepository;
import com.qc.enterprise.service.FederatedExecutorService;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private SystemLogRepository systemLogRepository;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private FederatedExecutorService federatedExecutorService;

    // Add this inside a Controller class
    @PostMapping("/execute")
    public ResponseEntity<?> executeManually(@RequestBody List<ExecutionPlan> plans) {
        // Now this will work flawlessly
        return ResponseEntity.ok(federatedExecutorService.executeFederatedPlans(plans));
    }

    @GetMapping("/postgres")
    public List<Department> testPostgres() {
        return departmentRepository.findAll();
    }

    @GetMapping("/mongo")
    public List<SystemLog> testMongo() {
        return systemLogRepository.findAll();
    }

    @GetMapping("/minio")
    public String testMinio() {
        try {
            // Note: Update "sample.pdf" to whatever file you uploaded to MinIO!
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket("querycraft-files")
                    .object("Acme_Corp_contract.pdf")
                    .build());
            return "SUCCESS: MinIO is connected and your file was found!";
        } catch (Exception e) {
            return "FAILED to connect to MinIO or find file: " + e.getMessage();
        }
    }
}