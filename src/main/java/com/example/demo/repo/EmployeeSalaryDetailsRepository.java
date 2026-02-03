package com.example.demo.repo;

import com.example.demo.MODELS.EmployeeSalaryDetails;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeSalaryDetailsRepository extends JpaRepository<EmployeeSalaryDetails, Long> {
Optional<EmployeeSalaryDetails> findByEmployeeId(Long employeeId);

}