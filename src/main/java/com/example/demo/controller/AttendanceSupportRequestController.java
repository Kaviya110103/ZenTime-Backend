package com.example.demo.controller;

import com.example.demo.MODELS.AttendanceSupportRequest;
import com.example.demo.MODELS.AttendanceRecord;
import com.example.demo.MODELS.AttendanceSupportStatus;
import com.example.demo.MODELS.Employee;
import com.example.demo.repo.AttendanceRecordRepository;
import com.example.demo.repo.AttendanceSupportRequestRepository;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.service.SchemaMaintenanceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/attendance-support")
@CrossOrigin(origins = "*")
public class AttendanceSupportRequestController {
    private final AttendanceSupportRequestRepository supportRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final SchemaMaintenanceService schemaMaintenanceService;
    private static final String TYPE_ATTENDANCE = "Attendance Support";
    private static final String TYPE_GENERAL = "General Support";
    private static final DateTimeFormatter ATTENDANCE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public AttendanceSupportRequestController(
            AttendanceSupportRequestRepository supportRepository,
            AttendanceRecordRepository attendanceRecordRepository,
            EmployeeRepository employeeRepository,
            SchemaMaintenanceService schemaMaintenanceService) {
        this.supportRepository = supportRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.employeeRepository = employeeRepository;
        this.schemaMaintenanceService = schemaMaintenanceService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) String requestType,
            @RequestParam(required = false) String status) {
        schemaMaintenanceService.ensureEmployeeSchema();
        AttendanceSupportStatus parsedStatus = parseStatus(status);
        List<AttendanceSupportRequest> requests;
        if (clientId != null && parsedStatus != null) {
            requests = supportRepository.findByEmployeeClientIdAndStatusOrderByIdDesc(clientId, parsedStatus);
        } else if (clientId != null) {
            requests = supportRepository.findByEmployeeClientIdOrderByIdDesc(clientId);
        } else {
            requests = supportRepository.findAll();
            if (parsedStatus != null) {
                requests = requests.stream()
                        .filter(request -> parsedStatus.equals(request.getStatus()))
                        .toList();
            }
            requests = requests.stream()
                    .sorted(Comparator.comparing(
                            AttendanceSupportRequest::getId,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        }
        String normalizedType = normalizeRequestType(requestType);
        if (normalizedType != null) {
            requests = requests.stream()
                    .filter(request -> normalizedType.equals(normalizeRequestType(request.getRequestType())))
                    .toList();
        }
        return ResponseEntity.ok(requests.stream().map(this::toResponse).toList());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateRequest body) {
        schemaMaintenanceService.ensureEmployeeSchema();
        if (body == null || body.employeeId == null) {
            return ResponseEntity.badRequest().body("employeeId is required.");
        }
        Optional<Employee> employeeOpt = employeeRepository.findById(body.employeeId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Employee not found.");
        }

        String requestType = normalizeRequestType(body.requestType);
        if (requestType == null) {
            requestType = body.message == null || body.message.isBlank() ? TYPE_ATTENDANCE : TYPE_GENERAL;
        }

        AttendanceSupportRequest request = new AttendanceSupportRequest();
        request.setEmployee(employeeOpt.get());
        request.setRequestType(requestType);
        if (TYPE_GENERAL.equals(requestType)) {
            if (body.message == null || body.message.isBlank()) {
                return ResponseEntity.badRequest().body("message is required for general support.");
            }
            request.setMessage(body.message.trim());
            request.setStatus(AttendanceSupportStatus.OPEN);
        } else {
            if (body.attendanceDate == null || body.reason == null || body.reason.isBlank()
                    || body.timeIn == null || body.timeIn.isBlank() || body.timeOut == null || body.timeOut.isBlank()) {
                return ResponseEntity.badRequest().body("attendanceDate, timeIn, timeOut and reason are required for attendance support.");
            }
            LocalTime timeIn = parseTime(body.timeIn);
            LocalTime timeOut = parseTime(body.timeOut);
            if (timeIn == null || timeOut == null) {
                return ResponseEntity.badRequest().body("timeIn and timeOut must be valid HH:mm values.");
            }
            request.setAttendanceDate(body.attendanceDate);
            request.setTimeIn(timeIn);
            request.setTimeOut(timeOut);
            request.setReason(body.reason.trim());
            request.setStatus(AttendanceSupportStatus.PENDING);
        }
        if (body.approvedMinutes != null) {
            request.setApprovedMinutes(Math.max(0, body.approvedMinutes));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(supportRepository.save(request)));
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<?> approve(
            @PathVariable Long requestId,
            @RequestBody(required = false) ApprovalRequest body) {
        schemaMaintenanceService.ensureEmployeeSchema();
        Optional<AttendanceSupportRequest> optional = supportRepository.findById(requestId);
        if (optional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Attendance support request not found.");
        }

        AttendanceSupportRequest request = optional.get();
        if (!TYPE_ATTENDANCE.equals(normalizeRequestType(request.getRequestType()))) {
            return ResponseEntity.badRequest().body("Only attendance support requests can be approved.");
        }
        if (request.getAttendanceDate() == null || request.getAttendanceDate().isAfter(LocalDate.now())) {
            return ResponseEntity.badRequest().body("Only current or past attendance dates can be approved.");
        }
        if (request.getTimeIn() == null || request.getTimeOut() == null) {
            return ResponseEntity.badRequest().body("Time in and time out are required before approval.");
        }
        Long employeeId = request.getEmployee() == null ? null : request.getEmployee().getId();
        if (employeeId == null) {
            return ResponseEntity.badRequest().body("Employee not found for support request.");
        }
        if (supportRepository.existsByEmployeeIdAndAttendanceDateAndStatusAndIdNot(
                employeeId,
                request.getAttendanceDate(),
                AttendanceSupportStatus.APPROVED,
                request.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Approved attendance support already exists for this employee and date.");
        }

        request.setStatus(AttendanceSupportStatus.APPROVED);
        request.setApprovedBy(body == null || body.approvedBy == null ? null : body.approvedBy.trim());
        request.setApprovedAt(LocalDateTime.now());
        if (body != null && body.approvedMinutes != null) {
            request.setApprovedMinutes(Math.max(0, body.approvedMinutes));
        }
        upsertAttendanceRecord(request);
        return ResponseEntity.ok(toResponse(supportRepository.save(request)));
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<?> reject(
            @PathVariable Long requestId,
            @RequestBody(required = false) ApprovalRequest body) {
        schemaMaintenanceService.ensureEmployeeSchema();
        Optional<AttendanceSupportRequest> optional = supportRepository.findById(requestId);
        if (optional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Attendance support request not found.");
        }
        AttendanceSupportRequest request = optional.get();
        if (!TYPE_ATTENDANCE.equals(normalizeRequestType(request.getRequestType()))) {
            return ResponseEntity.badRequest().body("Only attendance support requests can be rejected.");
        }
        request.setStatus(AttendanceSupportStatus.REJECTED);
        request.setApprovedBy(body == null || body.approvedBy == null ? null : body.approvedBy.trim());
        request.setApprovedAt(LocalDateTime.now());
        return ResponseEntity.ok(toResponse(supportRepository.save(request)));
    }

    @PostMapping("/{requestId}/resolve")
    public ResponseEntity<?> resolve(@PathVariable Long requestId) {
        return updateGeneralStatus(requestId, AttendanceSupportStatus.RESOLVED);
    }

    @PostMapping("/{requestId}/close")
    public ResponseEntity<?> close(@PathVariable Long requestId) {
        return updateGeneralStatus(requestId, AttendanceSupportStatus.CLOSED);
    }

    private ResponseEntity<?> updateGeneralStatus(Long requestId, AttendanceSupportStatus status) {
        schemaMaintenanceService.ensureEmployeeSchema();
        Optional<AttendanceSupportRequest> optional = supportRepository.findById(requestId);
        if (optional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Support request not found.");
        }
        AttendanceSupportRequest request = optional.get();
        if (!TYPE_GENERAL.equals(normalizeRequestType(request.getRequestType()))) {
            return ResponseEntity.badRequest().body("Only general support requests can be resolved or closed.");
        }
        request.setStatus(status);
        return ResponseEntity.ok(toResponse(supportRepository.save(request)));
    }

    private void upsertAttendanceRecord(AttendanceSupportRequest request) {
        Employee employee = request.getEmployee();
        String date = request.getAttendanceDate().format(ATTENDANCE_DATE_FORMATTER);
        List<AttendanceRecord> existingRecords = attendanceRecordRepository.findByEmployeeIdAndDate(employee.getId(), date);
        AttendanceRecord record = existingRecords.isEmpty() ? new AttendanceRecord() : existingRecords.get(0);
        record.setEmployee(employee);
        record.setDate(date);
        record.setTimeIn(LocalDateTime.of(request.getAttendanceDate(), request.getTimeIn()));
        record.setTimeOut(LocalDateTime.of(request.getAttendanceDate(), request.getTimeOut()));
        record.setAttendanceStatus("Present");
        record.setDayStatus("Completed");
        record.setTimoutReason(null);
        if (record.getLocation() == null || record.getLocation().isBlank()) {
            record.setLocation("Attendance Support");
        }
        updateDerivedAttendanceHours(record);
        attendanceRecordRepository.save(record);
    }

    private void updateDerivedAttendanceHours(AttendanceRecord record) {
        if (record.getTimeIn() == null || record.getTimeOut() == null) {
            record.setWorkedHours(null);
            record.setOvertime(0.0);
            return;
        }
        long minutes = java.time.Duration.between(record.getTimeIn(), record.getTimeOut()).toMinutes();
        if (minutes < 0) {
            minutes += 24 * 60;
        }
        double workedHours = Math.round((minutes / 60.0) * 100.0) / 100.0;
        record.setWorkedHours(workedHours);
        record.setOvertime(Math.max(0.0, workedHours - 8.0));
        record.setMissedTimes(0);
    }

    private Map<String, Object> toResponse(AttendanceSupportRequest request) {
        Employee employee = request.getEmployee();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", request.getId());
        response.put("employeeId", employee == null ? null : employee.getId());
        response.put("employeeName", employee == null
                ? ""
                : String.format("%s %s", safe(employee.getFirstName()), safe(employee.getLastName())).trim());
        response.put("branch", employee == null ? "" : safe(employee.getBranch()));
        response.put("requestType", normalizeRequestType(request.getRequestType()) == null ? TYPE_ATTENDANCE : normalizeRequestType(request.getRequestType()));
        response.put("attendanceDate", request.getAttendanceDate());
        response.put("timeIn", request.getTimeIn());
        response.put("timeOut", request.getTimeOut());
        response.put("reason", request.getReason());
        response.put("message", request.getMessage());
        response.put("status", request.getStatus());
        response.put("approvedBy", request.getApprovedBy());
        response.put("approvedAt", request.getApprovedAt());
        response.put("approvedMinutes", request.getApprovedMinutes());
        response.put("createdAt", request.getCreatedAt());
        response.put("updatedAt", request.getUpdatedAt());
        return response;
    }

    private AttendanceSupportStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AttendanceSupportStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String normalizeRequestType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().replace("_", " ").replace("-", " ").toLowerCase();
        if (value.contains("general")) {
            return TYPE_GENERAL;
        }
        if (value.contains("attendance")) {
            return TYPE_ATTENDANCE;
        }
        return null;
    }

    private LocalTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    public static class CreateRequest {
        public Long employeeId;
        public LocalDate attendanceDate;
        public String timeIn;
        public String timeOut;
        public String reason;
        public String message;
        public String requestType;
        public Integer approvedMinutes;
    }

    public static class ApprovalRequest {
        public String approvedBy;
        public Integer approvedMinutes;
    }
}
