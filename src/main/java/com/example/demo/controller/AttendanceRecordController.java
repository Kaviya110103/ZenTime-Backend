package com.example.demo.controller;

import com.example.demo.MODELS.AttendanceRecord;
import com.example.demo.MODELS.Employee;
import com.example.demo.repo.AttendanceRecordRepository;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.service.AttendanceMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
// import java.time.Month;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/attendance")
@CrossOrigin(origins = "*")
public class AttendanceRecordController {

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private AttendanceMetricsService attendanceMetricsService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private com.example.demo.service.SchemaMaintenanceService schemaMaintenanceService;

    @GetMapping
    public List<AttendanceRecord> getAllAttendanceRecords() {
        schemaMaintenanceService.ensureEmployeeSchema();
        return attendanceRecordRepository.findAll();
    }

    @GetMapping("/filter")
    public Map<String, Object> filterAttendanceRecords(
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate endDate,
            @RequestParam(required = false) Integer month) {
        schemaMaintenanceService.ensureEmployeeSchema();
        
        List<AttendanceRecord> filteredRecords = attendanceRecordRepository.findAll(); // Basic implementation
        
        // Simple filtering logic (replace with proper repository queries)
        if (employeeId != null && !employeeId.isBlank()) {
            Optional<Employee> employee = resolveEmployeeByRef(employeeId);
            if (employee.isPresent()) {
            final Long employeeIdFilter = employee.get().getId();
            filteredRecords = filteredRecords.stream()
                .filter(record -> record.getEmployee() != null && record.getEmployee().getId().equals(employeeIdFilter))
                .toList();
            } else {
                filteredRecords = List.of();
            }
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

        attendanceMetricsService.synchronizeMissedMinutes(filteredRecords);
        
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

    private Optional<Employee> resolveEmployeeByRef(String employeeRef) {
        if (employeeRef == null || employeeRef.isBlank()) {
            return Optional.empty();
        }

        String normalized = employeeRef.trim();

        try {
            return employeeRepository.findById(Long.parseLong(normalized));
        } catch (NumberFormatException ignored) {
            // Continue with employee-code lookup.
        }

        Optional<Employee> byCode = employeeRepository.findByEmployeeCode(normalized.toUpperCase());
        if (byCode.isPresent()) {
            return byCode;
        }

        Matcher matcher = Pattern.compile("(?i)(?:^|\\.)EMP(\\d+)$").matcher(normalized);
        if (matcher.find()) {
            try {
                return employeeRepository.findById(Long.parseLong(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // Keep empty below.
            }
        }

        return Optional.empty();
    }
}
