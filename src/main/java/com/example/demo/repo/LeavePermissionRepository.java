package com.example.demo.repo;



import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.MODELS.LeavePermission;

import java.util.List;


public interface LeavePermissionRepository extends JpaRepository<LeavePermission, Long> {
    List<LeavePermission> findByEmployeeId(Long employeeId);
    void deleteByEmployeeId(Long employeeId);
    List<LeavePermission> findByStatusIgnoreCase(String status);
    List<LeavePermission> findByEmployee_ClientId(Long clientId);
    List<LeavePermission> findByStatusIgnoreCaseAndEmployee_ClientId(String status, Long clientId);
    List<LeavePermission> findByEmployeeIdAndStatus(Long employeeId, String status);
    List<LeavePermission> findByEmployeeIdAndDateAndStatus(Long employeeId, String date, String status);
     long countByStatusIgnoreCase(String status);
     long countByStatusIgnoreCaseAndEmployee_ClientId(String status, Long clientId);
}

