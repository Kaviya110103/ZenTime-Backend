package com.example.demo.controller;




import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.demo.MODELS.AttendanceRecord;
import com.example.demo.MODELS.Employee;
import com.example.demo.MODELS.LeavePermission;
import com.example.demo.MODELS.PublicHoliday;
import com.example.demo.repo.AttendanceRecordRepository;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.repo.LeavePermissionRepository;
import com.example.demo.service.AttendanceMetricsService;
import com.example.demo.service.PublicHolidayService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
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
    private PublicHolidayService publicHolidayService;


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

    if ("approved".equals(newStatus) && "Permission".equalsIgnoreCase(leave.getLeaveType())) {
        int permissionMinutes = attendanceMetricsService.calculatePermissionDurationMinutes(leave);
        if (permissionMinutes <= 0) {
            return ResponseEntity.badRequest()
                    .body("Invalid permission time range. Please provide valid startTime and endTime.");
        }

        LocalDate permissionDate = resolvePermissionMonthDate(leave);
        if (permissionDate == null) {
            return ResponseEntity.badRequest().body("Invalid permission date.");
        }

        AttendanceMetricsService.PermissionUsage usage =
                attendanceMetricsService.getApprovedPermissionUsage(
                        leave.getEmployee().getId(),
                        permissionDate.getMonthValue(),
                        permissionDate.getYear(),
                        leave.getId());

        int finalCount = usage.approvedPermissionCount() + 1;
        int finalMinutes = usage.approvedPermissionMinutes() + permissionMinutes;
        if (finalCount > AttendanceMetricsService.MAX_APPROVED_PERMISSIONS_PER_MONTH) {
            return ResponseEntity.badRequest().body(
                    "Approval limit exceeded: maximum "
                            + AttendanceMetricsService.MAX_APPROVED_PERMISSIONS_PER_MONTH
                            + " approved permissions per month.");
        }
        if (finalMinutes > AttendanceMetricsService.MAX_APPROVED_PERMISSION_MINUTES_PER_MONTH) {
            return ResponseEntity.badRequest().body(
                    "Approval limit exceeded: maximum "
                            + AttendanceMetricsService.MAX_APPROVED_PERMISSION_MINUTES_PER_MONTH
                            + " approved permission minutes per month.");
        }
    }

    leave.setStatus(newStatus);
    leavePermissionRepository.save(leave);

    Long employeeId = leave.getEmployee().getId();

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    LocalDate start = LocalDate.parse(leave.getStartDate(), formatter);
    LocalDate end = LocalDate.parse(leave.getEndDate(), formatter);

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            String formattedDate = date.format(formatter);
            List<AttendanceRecord> existingRecords = attendanceRecordRepository.findByEmployeeIdAndDate(employeeId, formattedDate);

            if ("approved".equals(newStatus)) {
                PublicHoliday holiday = publicHolidayService
                        .getHoliday(leave.getEmployee().getClientId(), date)
                        .orElse(null);
                if (holiday != null) {
                    continue;
                }
                if (existingRecords.isEmpty()) {
                    AttendanceRecord attendance = new AttendanceRecord();
                    attendance.setEmployee(leave.getEmployee());
                attendance.setDate(formattedDate);
                attendance.setAttendanceStatus("Absent");
                attendance.setDayStatus("Leave Approved - Absent");
                attendanceRecordRepository.save(attendance);
            }
        } else {
            // If status changed from approved to rejected or other, delete "Leave Approved - Absent" records
            for (AttendanceRecord record : existingRecords) {
                if ("Leave Approved - Absent".equals(record.getDayStatus())) {
                    attendanceRecordRepository.delete(record);
                }
            }
        }
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
}


