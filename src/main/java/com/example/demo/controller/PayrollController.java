package com.example.demo.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.MODELS.EmailDetails;
import com.example.demo.MODELS.Employee;
import com.example.demo.MODELS.AttendanceRecord;
import com.example.demo.MODELS.LeavePermission;
import com.example.demo.repo.AttendanceRecordRepository;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.repo.LeavePermissionRepository;
import com.example.demo.service.EmailService;

@RestController
@RequestMapping("/api/payroll")
public class PayrollController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private com.example.demo.service.PayrollCalculationService payrollCalculationService;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private LeavePermissionRepository leavePermissionRepository;

    private static final DateTimeFormatter DB_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @GetMapping("/month-payroll")
    public ResponseEntity<?> calculateMonthPayroll(
            @RequestParam Long employeeId,
            @RequestParam int month,
            @RequestParam int year,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false, defaultValue = "false") boolean debug
    ) {
        // 1. Get employee and salary
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null) {
            return ResponseEntity.badRequest().body("Employee not found");
        }
        double salary = employee.getSalary() == null ? 0.0 : employee.getSalary();

        com.example.demo.service.PayrollCalculationService.PayrollResult result =
                payrollCalculationService.calculateMonthlyPayroll(employeeId, month, year, clientId);

        int daysInMonth = result.daysInMonth();
        int holidayTotalDays = result.holidayDaysFull() + result.holidayDaysHalf();
        int scheduledDaysExcludingHolidays = Math.max(0, result.scheduledDays() - holidayTotalDays);
        int weekOffDays = Math.max(0, daysInMonth - result.scheduledDays());
        int approvedLeaveTakenCount = Math.max(
                0,
                result.paidLeaveDays()
                        + result.paidCasualDays()
                        + result.unpaidCasualDays()
                        + result.unpaidLeaveDays());
        int permissionTakenCount = countApprovedPermissionsInMonth(employeeId, month, year);
        int absentDays = Math.max(
                0,
                scheduledDaysExcludingHolidays
                        - result.workedDays()
                        - result.paidLeaveDays()
                        - result.paidCasualDays());

        if (isPresentToday(employeeId, month, year)) {
            absentDays = Math.max(0, absentDays - 1);
        }

        // 5. Prepare response
        Map<String, Object> response = new HashMap<>();
        response.put("employeeId", employeeId);
        response.put("employeeName", employee.getFirstName() + " " + employee.getLastName());
        response.put("firstName", employee.getFirstName());
        response.put("lastName", employee.getLastName());
        response.put("mobile", employee.getMobile());
        response.put("position", employee.getPosition());
        response.put("branch", employee.getBranch());
        response.put("email", employee.getEmail());
        response.put("month", month);
        response.put("year", year);
        response.put("salary", salary);
        response.put("daysInMonth", daysInMonth);
        response.put("scheduledDays", scheduledDaysExcludingHolidays);
        response.put("weekOffDays", weekOffDays);
        response.put("workedDays", result.workedDays());
        response.put("absentDays", absentDays);
        response.put("approvedLeaveTakenCount", approvedLeaveTakenCount);
        response.put("permissionTakenCount", permissionTakenCount);
        response.put("holidayDaysFull", result.holidayDaysFull());
        response.put("holidayDaysHalf", result.holidayDaysHalf());
        response.put("holidayDaysTotal", holidayTotalDays);
        response.put("paidLeaveDays", result.paidLeaveDays());
        response.put("paidCasualDays", result.paidCasualDays());
        response.put("unpaidCasualDays", result.unpaidCasualDays());
        response.put("unpaidLeaveDays", result.unpaidLeaveDays());
        response.put("expectedHours", result.expectedHours());
        response.put("payableHours", result.payableHours());
        response.put("workedHours", result.workedHours());
        response.put("perHourSalary", result.perHourSalary());
        response.put("missingHours", result.missingHours());
        response.put("netSalary", result.netSalary());
        response.put("overtimeHours", result.overtimeHours());
        if (debug) {
            response.put("debugDays",
                    payrollCalculationService.calculateMonthlyPayrollDebug(employeeId, month, year, clientId));
        }

        return ResponseEntity.ok(response);
    }

    private boolean isPresentToday(Long employeeId, int month, int year) {
        LocalDate today = LocalDate.now();
        if (today.getMonthValue() != month || today.getYear() != year) {
            return false;
        }
        String todayKey = today.format(DB_DATE_FORMATTER);
        Optional<AttendanceRecord> recordOpt =
                attendanceRecordRepository.findByEmployee_IdAndDate(employeeId, todayKey);
        if (recordOpt.isEmpty()) {
            return false;
        }
        AttendanceRecord record = recordOpt.get();
        if (record.getTimeIn() != null) {
            return true;
        }
        String status = record.getAttendanceStatus();
        return status != null && status.equalsIgnoreCase("Present");
    }

    private int countApprovedPermissionsInMonth(Long employeeId, int month, int year) {
        List<LeavePermission> approved =
                leavePermissionRepository.findByEmployeeIdAndStatus(employeeId, "approved");
        int count = 0;
        for (LeavePermission leave : approved) {
            if (!isPermissionType(leave.getLeaveType())) {
                continue;
            }
            List<LocalDate> dates = expandLeaveDates(leave, month, year);
            if (!dates.isEmpty()) {
                count += 1;
            }
        }
        return count;
    }

    private boolean isPermissionType(String raw) {
        if (raw == null) {
            return false;
        }
        return raw.trim().toUpperCase().contains("PERMISSION");
    }

    private List<LocalDate> expandLeaveDates(LeavePermission leave, int month, int year) {
        List<LocalDate> dates = new ArrayList<>();

        LocalDate start = parseDbDate(leave.getStartDate());
        LocalDate end = parseDbDate(leave.getEndDate());
        if (start == null || end == null) {
            LocalDate single = parseDbDate(leave.getDate());
            if (single != null) {
                dates.add(single);
            }
        } else {
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                dates.add(date);
            }
        }

        List<LocalDate> filtered = new ArrayList<>();
        for (LocalDate date : dates) {
            if (date.getMonthValue() == month && date.getYear() == year) {
                filtered.add(date);
            }
        }
        return filtered;
    }

    private LocalDate parseDbDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), DB_DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            return null;
        }
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
