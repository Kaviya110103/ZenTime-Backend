package com.example.demo.controller;

import com.example.demo.MODELS.AttendanceRecord;
import com.example.demo.repo.AttendanceRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
// import java.time.Month;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attendance")
@CrossOrigin(origins = "*")
public class AttendanceRecordController {

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @GetMapping
    public List<AttendanceRecord> getAllAttendanceRecords() {
        return attendanceRecordRepository.findAll();
    }

    @GetMapping("/filter")
    public Map<String, Object> filterAttendanceRecords(
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate endDate,
            @RequestParam(required = false) Integer month) {
        
        List<AttendanceRecord> filteredRecords = attendanceRecordRepository.findAll(); // Basic implementation
        
        // Simple filtering logic (replace with proper repository queries)
        if (employeeId != null) {
            filteredRecords = filteredRecords.stream()
                .filter(record -> record.getEmployee().getId().equals(employeeId))
                .toList();
        }
        
        if (branch != null && !branch.equals("All Branches")) {
            filteredRecords = filteredRecords.stream()
                .filter(record -> record.getEmployee().getBranch().equals(branch))
                .toList();
        }
        
        if (month != null) {
            filteredRecords = filteredRecords.stream()
                .filter(record -> {
                    LocalDateTime timeIn = record.getTimeIn();
                    return timeIn != null && timeIn.getMonthValue() == month;
                })
                .toList();
        }
        
        if (startDate != null && endDate != null) {
            filteredRecords = filteredRecords.stream()
                .filter(record -> {
                    LocalDateTime timeIn = record.getTimeIn();
                    return timeIn != null && 
                           !timeIn.toLocalDate().isBefore(startDate) && 
                           !timeIn.toLocalDate().isAfter(endDate);
                })
                .toList();
        }
        
        // Calculate summary counts
        long presentCount = filteredRecords.stream()
            .filter(r -> "Present".equals(r.getAttendanceStatus()))
            .count();
        
        long absentCount = filteredRecords.stream()
            .filter(r -> "Absent".equals(r.getAttendanceStatus()))
            .count();
        
        // Return both records and summary
        Map<String, Object> response = new HashMap<>();
        response.put("records", filteredRecords);
        response.put("totalRecords", filteredRecords.size());
        response.put("presentCount", presentCount);
        response.put("absentCount", absentCount);
        
        return response;
    }
}
