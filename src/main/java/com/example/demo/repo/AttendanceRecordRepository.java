package com.example.demo.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.MODELS.AttendanceRecord;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    Optional<AttendanceRecord> findById(Long id);

    static Optional<AttendanceRecord> findTopByEmployeeIdOrderByIdDesc(Long employeeId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'findTopByEmployeeIdOrderByIdDesc'");
    }
    List<AttendanceRecord> findByEmployeeIdAndDate(Long employeeId, String date);
    List<AttendanceRecord> findByDate(String date);
    List<AttendanceRecord> findByEmployeeIdAndDateBetween(Long employeeId, String startDate, String endDate);
    List<AttendanceRecord> findByEmployeeId(Long employeeId);
        Optional<AttendanceRecord> findByDateAndEmployeeId(String date, Long employeeId);


    
    
    // You can add custom query methods here if needed, e.g.,
    // findByEmployeeIdAndDate(Long employeeId, LocalDate date);
}