package com.example.demo.controller;

import com.example.demo.MODELS.Employee;
import com.example.demo.MODELS.AttendanceRecord;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.repo.AttendanceRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payroll")
@CrossOrigin(origins = "*") // Allow frontend to access

public class PayrollController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @GetMapping("/month-payroll")
    public ResponseEntity<?> calculateMonthPayroll(
            @RequestParam Long employeeId,
            @RequestParam int month,
            @RequestParam int year
    ) {
        // 1. Get employee and salary
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null) {
            return ResponseEntity.badRequest().body("Employee not found");
        }
        double salary = employee.getSalary();

        // 2. Get total days in month
        YearMonth yearMonth = YearMonth.of(year, month);
        int daysInMonth = yearMonth.lengthOfMonth();

        // 3. Get absent days count for the month  1092252
        String monthStr = String.format("%02d/%02d/%d", 1, month, year); // e.g. 01/06/2025
        String monthPattern = String.format("/%02d/%d", month, year); // e.g. /06/2025

        List<AttendanceRecord> records = attendanceRecordRepository.findByEmployeeId(employeeId);
        long absentDays = records.stream()
            .filter(r -> r.getAttendanceStatus() != null
                && r.getAttendanceStatus().equalsIgnoreCase("Absent")
                && r.getDate() != null
                && r.getDate().endsWith(monthPattern))
            .count();

        // 4. Calculate per day salary and net salary
        double perDaySalary = salary / daysInMonth;
        double netSalary = salary - ((absentDays-1)* perDaySalary);

        // 5. Prepare response
        Map<String, Object> response = new HashMap<>();
        response.put("employeeId", employeeId);
        response.put("employeeName", employee.getFirstName() + " " + employee.getLastName());
        response.put("month", month);
        response.put("year", year);
        response.put("salary", salary);
        response.put("daysInMonth", daysInMonth);
        response.put("absentDays", (absentDays-1));
        response.put("perDaySalary", perDaySalary);
        response.put("netSalary", netSalary);

        return ResponseEntity.ok(response);
    }

    



}