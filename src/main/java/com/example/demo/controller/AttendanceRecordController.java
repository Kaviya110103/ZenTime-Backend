package com.example.demo.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.MODELS.AttendanceRecord;
import com.example.demo.repo.AttendanceRecordRepository;

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
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate endDate,
            @RequestParam(required = false) Integer month) {
        
        List<AttendanceRecord> filteredRecords = attendanceRecordRepository.findAll();
        
        // Filter by employee ID
        if (employeeId != null && !employeeId.isEmpty() && !employeeId.equals("All")) {
            try {
                Long empId = Long.parseLong(employeeId);
                filteredRecords = filteredRecords.stream()
                    .filter(r -> r.getEmployee().getId().equals(empId))
                    .toList();
            } catch (NumberFormatException e) {
                // Invalid employee ID format, skip filter
            }
        }
        
        // Filter by branch
        if (branch != null && !branch.isEmpty() && !branch.equals("All Branches")) {
            filteredRecords = filteredRecords.stream()
                .filter(r -> r.getEmployee().getBranch().equals(branch))
                .toList();
        }
        
        // Filter by attendance status - FIXED: Handle "All" status
        if (status != null && !status.isEmpty() && !status.equalsIgnoreCase("All")) {
            filteredRecords = filteredRecords.stream()
                .filter(r -> r.getAttendanceStatus() != null && 
                        r.getAttendanceStatus().equalsIgnoreCase(status))
                .toList();
        }
        
        if (month != null) {
            filteredRecords = filteredRecords.stream()
                .filter(r -> {
                    LocalDateTime timeIn = r.getTimeIn();
                    return timeIn != null && timeIn.getMonthValue() == month;
                })
                .toList();
        }
        
        if (startDate != null && endDate != null) {
            filteredRecords = filteredRecords.stream()
                .filter(r -> {
                    LocalDateTime timeIn = r.getTimeIn();
                    return timeIn != null && 
                           !timeIn.toLocalDate().isBefore(startDate) && 
                           !timeIn.toLocalDate().isAfter(endDate);
                })
                .toList();
        }
        
        // Calculate summary counts
        long presentCount = filteredRecords.stream()
            .filter(r -> r.getAttendanceStatus() != null && 
                    r.getAttendanceStatus().equalsIgnoreCase("Present"))
            .count();
        
        long absentCount = filteredRecords.stream()
            .filter(r -> r.getAttendanceStatus() != null && 
                    r.getAttendanceStatus().equalsIgnoreCase("Absent"))
            .count();
        
        // Return both records and summary
        Map<String, Object> response = new HashMap<>();
        response.put("records", filteredRecords);
        response.put("totalRecords", filteredRecords.size());
        response.put("presentCount", presentCount);
        response.put("absentCount", absentCount);
        response.put("status", status != null ? status : "All");
        
        return response;
    }
}
