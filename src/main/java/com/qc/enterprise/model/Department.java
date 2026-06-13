package com.qc.enterprise.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal; // 1. Add this import!

@Data
@Entity
@Table(name = "departments")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "department_name", nullable = false)
    private String departmentName;

    // 2. Change Double to BigDecimal
    @Column(nullable = false)
    private BigDecimal budget;
}