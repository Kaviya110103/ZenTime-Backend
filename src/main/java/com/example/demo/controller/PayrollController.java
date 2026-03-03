package com.example.demo.controller;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.MODELS.AttendanceRecord;
import com.example.demo.MODELS.EmailDetails;
import com.example.demo.MODELS.Employee;
import com.example.demo.repo.AttendanceRecordRepository;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.service.AttendanceMetricsService;
import com.example.demo.service.EmailService;

@RestController
@RequestMapping("/api/payroll")
@CrossOrigin(origins = "http://localhost:3000")

public class PayrollController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AttendanceMetricsService attendanceMetricsService;

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

        AttendanceMetricsService.MonthlyMetrics metrics =
                attendanceMetricsService.calculateMonthlyMetrics(employeeId, month, year);

        // 4. Calculate salary deductions consistently using monthly metrics.
        double perDaySalary = salary / daysInMonth;
        double absentDeduction = metrics.absentCount() * perDaySalary;
        int shiftMinutesPerDay = attendanceMetricsService.resolveShiftDurationMinutes(employee);
        double perMinuteSalary = perDaySalary / shiftMinutesPerDay;
        double missedTimeDeduction = metrics.totalMissedMinutes() * perMinuteSalary;
        double netSalary = salary - absentDeduction - missedTimeDeduction;
        if (netSalary < 0) {
            netSalary = 0;
        }

        // 5. Prepare response
        Map<String, Object> response = new HashMap<>();
        response.put("employeeId", employeeId);
        response.put("employeeName", employee.getFirstName() + " " + employee.getLastName());
        response.put("month", month);
        response.put("year", year);
        response.put("salary", salary);
        response.put("daysInMonth", daysInMonth);
        response.put("absentDays", metrics.absentCount());
        response.put("totalLateMinutes", metrics.monthlyLateMinutes());
        response.put("totalEarlyOutMinutes", metrics.monthlyEarlyOutMinutes());
        response.put("totalMissedMinutes", metrics.totalMissedMinutes());
        response.put("permissionCount", metrics.approvedPermissionCount());
        response.put("totalApprovedPermissionsTaken", metrics.approvedPermissionCount());
        response.put("approvedPermissionCount", metrics.approvedPermissionCount());
        response.put("approvedPermissionMinutes", metrics.approvedPermissionMinutes());
        response.put("maxPermissionsPerMonth", AttendanceMetricsService.MAX_APPROVED_PERMISSIONS_PER_MONTH);
        response.put("maxPermissionMinutesPerMonth", AttendanceMetricsService.MAX_APPROVED_PERMISSION_MINUTES_PER_MONTH);
        response.put("perDaySalary", perDaySalary);
        response.put("perMinuteSalary", perMinuteSalary);
        response.put("absentDeduction", absentDeduction);
        response.put("missedTimeDeduction", missedTimeDeduction);
        response.put("netSalary", netSalary);

        return ResponseEntity.ok(response);
    }

    // API to send email with payroll details
    @PostMapping("/send-email")
    public ResponseEntity<?> sendPayrollEmail(@RequestBody EmailDetails emailDetails) {
        if (emailDetails.getReceiver() == null || emailDetails.getReceiver().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Receiver email is required"));
        }

        // Set default sender if not provided
        if (emailDetails.getSender() == null || emailDetails.getSender().isEmpty()) {
            emailDetails.setSender("b.inba.ips444@gmail.com");
        }

        String emailResponse = emailService.sendEmail(emailDetails);
        return ResponseEntity.ok(Map.of("message", emailResponse));
    }

    // Alias endpoint for frontend compatibility
    @PostMapping("/send-payroll-email")
    public ResponseEntity<?> sendPayrollEmailAlias(@RequestBody EmailDetails emailDetails) {
        return sendPayrollEmail(emailDetails);
    }

    



}
