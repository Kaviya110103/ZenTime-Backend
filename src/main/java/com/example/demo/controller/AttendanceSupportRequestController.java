package com.example.demo.controller;

import com.example.demo.MODELS.AttendanceSupportRequest;
import com.example.demo.MODELS.AttendanceSupportStatus;
import com.example.demo.MODELS.Employee;
import com.example.demo.repo.AttendanceSupportRequestRepository;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.service.SchemaMaintenanceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/attendance-support")
@CrossOrigin(origins = "*")
public class AttendanceSupportRequestController {
    private final AttendanceSupportRequestRepository supportRepository;
    private final EmployeeRepository employeeRepository;
    private final SchemaMaintenanceService schemaMaintenanceService;

    public AttendanceSupportRequestController(
            AttendanceSupportRequestRepository supportRepository,
            EmployeeRepository employeeRepository,
            SchemaMaintenanceService schemaMaintenanceService) {
        this.supportRepository = supportRepository;
        this.employeeRepository = employeeRepository;
        this.schemaMaintenanceService = schemaMaintenanceService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(required = false) Long clientId,
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
        return ResponseEntity.ok(requests.stream().map(this::toResponse).toList());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateRequest body) {
        schemaMaintenanceService.ensureEmployeeSchema();
        if (body == null || body.employeeId == null || body.attendanceDate == null
                || body.reason == null || body.reason.isBlank()) {
            return ResponseEntity.badRequest().body("employeeId, attendanceDate and reason are required.");
        }
        Optional<Employee> employeeOpt = employeeRepository.findById(body.employeeId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Employee not found.");
        }

        AttendanceSupportRequest request = new AttendanceSupportRequest();
        request.setEmployee(employeeOpt.get());
        request.setAttendanceDate(body.attendanceDate);
        request.setReason(body.reason.trim());
        request.setStatus(AttendanceSupportStatus.PENDING);
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
        if (request.getAttendanceDate() == null || request.getAttendanceDate().isAfter(LocalDate.now())) {
            return ResponseEntity.badRequest().body("Only current or past attendance dates can be approved.");
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
        request.setStatus(AttendanceSupportStatus.REJECTED);
        request.setApprovedBy(body == null || body.approvedBy == null ? null : body.approvedBy.trim());
        request.setApprovedAt(LocalDateTime.now());
        return ResponseEntity.ok(toResponse(supportRepository.save(request)));
    }

    private Map<String, Object> toResponse(AttendanceSupportRequest request) {
        Employee employee = request.getEmployee();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", request.getId());
        response.put("employeeId", employee == null ? null : employee.getId());
        response.put("employeeName", employee == null
                ? ""
                : String.format("%s %s", safe(employee.getFirstName()), safe(employee.getLastName())).trim());
        response.put("attendanceDate", request.getAttendanceDate());
        response.put("reason", request.getReason());
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

    public static class CreateRequest {
        public Long employeeId;
        public LocalDate attendanceDate;
        public String reason;
        public Integer approvedMinutes;
    }

    public static class ApprovalRequest {
        public String approvedBy;
        public Integer approvedMinutes;
    }
}
