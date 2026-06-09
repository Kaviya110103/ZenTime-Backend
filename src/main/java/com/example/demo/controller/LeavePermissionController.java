package com.example.demo.controller;




import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.demo.MODELS.AttendanceRecord;
import com.example.demo.MODELS.Employee;
import com.example.demo.MODELS.LeavePermission;
import com.example.demo.repo.AttendanceRecordRepository;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.repo.LeavePermissionRepository;
import com.example.demo.service.AttendanceMetricsService;
import com.example.demo.service.AttendanceSchedulerService;
import com.example.demo.service.PushNotificationService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;


@RestController
@RequestMapping("/api/leaves")
@CrossOrigin(origins = "*") // Allow frontend to access

public class LeavePermissionController {


    @Autowired
    private LeavePermissionRepository leavePermissionRepository;
 
    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceMetricsService attendanceMetricsService;

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private AttendanceSchedulerService attendanceSchedulerService;

    // ✅ 1. POST Leave Permission
@PostMapping("/create")
public ResponseEntity<String> createLeave(@RequestParam Long employeeId,
                                          @RequestBody LeavePermission leavePermission) {
    Optional<Employee> employeeOpt = employeeRepository.findById(employeeId);
    if (employeeOpt.isEmpty()) {
        return ResponseEntity.badRequest().body("Employee not found.");
    }

    // Set employee and default status
    leavePermission.setEmployee(employeeOpt.get());
    leavePermission.setStatus("pending");

    // Set the current date in dd/MM/yyyy format
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    String today = LocalDate.now().format(formatter);
    leavePermission.setDate(today);

    leavePermissionRepository.save(leavePermission);
    return ResponseEntity.ok("Leave permission submitted successfully.");
}


    // ✅ 2. GET all leave permissions
    @GetMapping("/all")
    public List<LeavePermission> getAllLeaves(
            @RequestParam(value = "clientId", required = false) Long clientId) {
        if (clientId != null) {
            return leavePermissionRepository.findByEmployee_ClientId(clientId);
        }
        return leavePermissionRepository.findAll();
    }


    // ✅ 3. GET leave permissions by Employee ID
    @GetMapping("/employee/{employeeId}")
    public List<LeavePermission> getLeavesByEmployee(@PathVariable Long employeeId) {
        return leavePermissionRepository.findByEmployeeId(employeeId);
    }


    // ✅ 4. PUT update leave status by Leave ID
@PutMapping("/status/{leaveId}")
public ResponseEntity<String> updateLeaveStatus(@PathVariable Long leaveId,
                                                @RequestParam String status) {
    Optional<LeavePermission> leaveOpt = leavePermissionRepository.findById(leaveId);
    if (leaveOpt.isEmpty()) {
        return ResponseEntity.status(404).body("Leave ID not found.");
    }

    LeavePermission leave = leaveOpt.get();
    String newStatus = status.toLowerCase();
    if (!"approved".equals(newStatus) && !"rejected".equals(newStatus) && !"pending".equals(newStatus)) {
        return ResponseEntity.badRequest().body("Invalid status.");
    }

    String approvalWarning = null;
    if ("approved".equals(newStatus) && "Permission".equalsIgnoreCase(leave.getLeaveType())) {
        int permissionMinutes = attendanceMetricsService.calculatePermissionDurationMinutes(leave);
        LocalDate permissionDate = resolvePermissionMonthDate(leave);

        if (permissionMinutes > 0 && permissionDate != null) {
            AttendanceMetricsService.PermissionUsage usage =
                    attendanceMetricsService.getApprovedPermissionUsage(
                            leave.getEmployee().getId(),
                            permissionDate.getMonthValue(),
                            permissionDate.getYear(),
                            leave.getId());

            int finalCount = usage.approvedPermissionCount() + 1;
            int finalMinutes = usage.approvedPermissionMinutes() + permissionMinutes;
            int maxPermissionsPerMonth =
                    attendanceMetricsService.resolveMaxApprovedPermissionsPerMonth(leave.getEmployee());
            int maxPermissionMinutesPerMonth =
                    attendanceMetricsService.resolveMaxApprovedPermissionMinutesPerMonth(leave.getEmployee());
            if (finalCount > maxPermissionsPerMonth) {
                approvalWarning =
                        "Approval limit exceeded: maximum "
                                + maxPermissionsPerMonth
                                + " approved permissions per month.";
            }
            if (approvalWarning == null
                    && finalMinutes > maxPermissionMinutesPerMonth) {
                approvalWarning =
                        "Approval limit exceeded: maximum "
                                + maxPermissionMinutesPerMonth
                                + " approved permission minutes per month.";
            }
        }
    }

    boolean isSwapWeekoff = isSwapWeekoffType(leave.getLeaveType());
    leave.setStatus(newStatus);
    leavePermissionRepository.save(leave);
    pushNotificationService.notifyLeaveStatus(leave);

    Long employeeId = leave.getEmployee().getId();

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    Optional<LocalDate> startOpt = parseLeaveDate(leave.getStartDate(), formatter);
    Optional<LocalDate> endOpt = parseLeaveDate(leave.getEndDate(), formatter);
    if (startOpt.isEmpty() || endOpt.isEmpty()) {
        if (approvalWarning != null) {
            return ResponseEntity.ok("Leave status updated to " + newStatus + ". Note: " + approvalWarning);
        }
        return ResponseEntity.ok("Leave status updated to " + newStatus);
    }
    LocalDate start = startOpt.get();
    LocalDate end = endOpt.get();

    for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
        String formattedDate = date.format(formatter);
        List<AttendanceRecord> existingRecords = attendanceRecordRepository.findByEmployeeIdAndDate(employeeId, formattedDate);

        if ("approved".equals(newStatus)) {
            if (isSwapWeekoff) {
                markSwapWeekoffAttendance(leave, formattedDate, existingRecords);
            } else if (existingRecords.isEmpty()) {
                AttendanceRecord attendance = new AttendanceRecord();
                attendance.setEmployee(leave.getEmployee());
                attendance.setDate(formattedDate);
                attendance.setAttendanceStatus("Absent");
                attendance.setDayStatus("Leave Approved - Absent");
                attendanceRecordRepository.save(attendance);
            }
        } else {
            // If status changed from approved to rejected or other, delete records created only by this approval.
            for (AttendanceRecord record : existingRecords) {
                if ("Leave Approved - Absent".equals(record.getDayStatus())
                        || isSwapWeekoffAttendanceRecord(record)) {
                    attendanceRecordRepository.delete(record);
                }
            }
        }
    }

    if (isSwapWeekoff && !"approved".equals(newStatus)) {
        attendanceSchedulerService.ensureAbsentForEmployeeDateRange(leave.getEmployee(), start, end);
    }

    if (approvalWarning != null) {
        return ResponseEntity.ok("Leave status updated to " + newStatus + ". Note: " + approvalWarning);
    }
    return ResponseEntity.ok("Leave status updated to " + newStatus);
}


    // ✅ 5. GET leave by status (pending/approved/rejected)
    @GetMapping("/status/{status}")
    public List<LeavePermission> getLeavesByStatus(@PathVariable String status) {
        return leavePermissionRepository.findByStatusIgnoreCase(status);
    }

@GetMapping("/status/{status}/count")
public ResponseEntity<Long> countLeavesByStatus(
        @PathVariable String status,
        @RequestParam(value = "clientId", required = false) Long clientId) {
    long cnt = clientId == null
            ? leavePermissionRepository.countByStatusIgnoreCase(status)
            : leavePermissionRepository.countByStatusIgnoreCaseAndEmployee_ClientId(status, clientId);
    return ResponseEntity.ok(cnt);
}

    
    @PostMapping("/create/permission")
    public ResponseEntity<String> createLeavePermission(
            @RequestParam Long employeeId,
            @RequestParam String leaveType,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam String reason,
            @RequestParam String date,
            @RequestParam String startTime,
            @RequestParam String endTime) {

        Optional<Employee> employeeOpt = employeeRepository.findById(employeeId);
        if (!employeeOpt.isPresent()) {
            return ResponseEntity.badRequest().body("Employee not found.");
        }

        Employee employee = employeeOpt.get();

        LeavePermission leave = new LeavePermission();
        leave.setEmployee(employee);
        leave.setDate(date);
        leave.setLeaveType(leaveType);
        leave.setStartDate(startDate);
        leave.setEndDate(endDate);
        leave.setReason(reason);
        leave.setStartTime(startTime);
        leave.setEndTime(endTime);
        leave.setStatus("pending");

        leavePermissionRepository.save(leave);
        return ResponseEntity.ok("Leave request submitted successfully.");
    }

    private LocalDate resolvePermissionMonthDate(LeavePermission leave) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String dateRaw = leave.getDate();
        if (dateRaw == null || dateRaw.isBlank()) {
            dateRaw = leave.getStartDate();
        }
        if (dateRaw == null || dateRaw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateRaw, formatter);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private Optional<LocalDate> parseLeaveDate(String rawDate, DateTimeFormatter formatter) {
        if (rawDate == null || rawDate.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(rawDate.trim(), formatter));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    private boolean isSwapWeekoffType(String leaveTypeRaw) {
        if (leaveTypeRaw == null) {
            return false;
        }
        return leaveTypeRaw.trim().toLowerCase(Locale.ROOT).equals("swap weekoff");
    }

    private void markSwapWeekoffAttendance(
            LeavePermission leave,
            String formattedDate,
            List<AttendanceRecord> existingRecords) {
        AttendanceRecord record = existingRecords.isEmpty()
                ? new AttendanceRecord()
                : existingRecords.get(0);

        record.setEmployee(leave.getEmployee());
        record.setDate(formattedDate);
        record.setAttendanceStatus("Week Off");
        record.setDayStatus("Week Off");
        record.setLocation("Swap Weekoff Approved");
        record.setAttendancelocation("Swap Weekoff Approved");
        record.setMissedTimes(0);
        record.setWorkedHours(0.0);
        record.setOvertime(0.0);
        record.setPermissionUsed(0.0);
        attendanceRecordRepository.save(record);

        for (int i = 1; i < existingRecords.size(); i++) {
            AttendanceRecord duplicate = existingRecords.get(i);
            if (duplicate.getTimeIn() == null && duplicate.getTimeOut() == null) {
                attendanceRecordRepository.delete(duplicate);
            }
        }
    }

    private boolean isSwapWeekoffAttendanceRecord(AttendanceRecord record) {
        return record != null
                && "Swap Weekoff Approved".equalsIgnoreCase(record.getLocation())
                && "Week Off".equalsIgnoreCase(record.getAttendanceStatus())
                && "Week Off".equalsIgnoreCase(record.getDayStatus());
    }
}


