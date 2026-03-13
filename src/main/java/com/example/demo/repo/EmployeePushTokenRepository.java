package com.example.demo.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.demo.MODELS.EmployeePushToken;

public interface EmployeePushTokenRepository extends JpaRepository<EmployeePushToken, Long> {
    Optional<EmployeePushToken> findByEmployeeIdAndToken(Long employeeId, String token);

    List<EmployeePushToken> findByEmployeeId(Long employeeId);

    @Query("select t.token from EmployeePushToken t")
    List<String> findAllTokens();
}
