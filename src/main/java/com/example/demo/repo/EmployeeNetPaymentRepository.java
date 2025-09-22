package com.example.demo.repo;

import com.example.demo.MODELS.EmployeeNetPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmployeeNetPaymentRepository extends JpaRepository<EmployeeNetPayment, Long> {
    // You can add custom query methods here if needed
}