package com.example.demo.repo;

import com.example.demo.MODELS.LeavePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LeavePolicyRepository extends JpaRepository<LeavePolicy, Long> {
    Optional<LeavePolicy> findByEmployee_Id(Long employeeId);
    void deleteByEmployee_Id(Long employeeId);
}
