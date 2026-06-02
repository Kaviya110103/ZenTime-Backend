package com.example.demo.repo;

import com.example.demo.MODELS.AttendanceSupportRequest;
import com.example.demo.MODELS.AttendanceSupportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AttendanceSupportRequestRepository extends JpaRepository<AttendanceSupportRequest, Long> {
    List<AttendanceSupportRequest> findByEmployeeIdAndStatus(
            Long employeeId,
            AttendanceSupportStatus status);

    List<AttendanceSupportRequest> findByEmployeeClientIdOrderByIdDesc(Long clientId);

    List<AttendanceSupportRequest> findByEmployeeClientIdAndStatusOrderByIdDesc(
            Long clientId,
            AttendanceSupportStatus status);

    boolean existsByEmployeeIdAndAttendanceDateAndStatusAndIdNot(
            Long employeeId,
            LocalDate attendanceDate,
            AttendanceSupportStatus status,
            Long id);
}
