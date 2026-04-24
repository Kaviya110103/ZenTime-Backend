package com.example.demo.repo;

import com.example.demo.MODELS.OvertimeRequest;
import com.example.demo.MODELS.OvertimeRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OvertimeRequestRepository extends JpaRepository<OvertimeRequest, Long> {
    Optional<OvertimeRequest> findFirstByEmployeeIdAndDateOrderByIdDesc(Long employeeId, String date);

    List<OvertimeRequest> findByEmployeeIdAndStatus(Long employeeId, OvertimeRequestStatus status);

    List<OvertimeRequest> findByEmployeeClientIdOrderByIdDesc(Long clientId);

    List<OvertimeRequest> findByEmployeeClientIdAndStatusOrderByIdDesc(Long clientId, OvertimeRequestStatus status);
}
