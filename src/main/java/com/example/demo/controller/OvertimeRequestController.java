package com.example.demo.controller;

import com.example.demo.MODELS.AttendanceRecord;
import com.example.demo.MODELS.Employee;
import com.example.demo.MODELS.OvertimeRequest;
import com.example.demo.MODELS.OvertimeRequestStatus;
import com.example.demo.repo.AttendanceRecordRepository;
import com.example.demo.repo.OvertimeRequestRepository;
import com.example.demo.service.SchemaMaintenanceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/overtime-requests")
@CrossOrigin(origins = "*")
public class OvertimeRequestController {
    private final OvertimeRequestRepository overtimeRequestRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final SchemaMaintenanceService schemaMaintenanceService;

    public OvertimeRequestController(
            OvertimeRequestRepository overtimeRequestRepository,
            AttendanceRecordRepository attendanceRecordRepository,
            SchemaMaintenanceService schemaMaintenanceService) {
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.schemaMaintenanceService = schemaMaintenanceService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getOvertimeRequests(
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) String status) {
        schemaMaintenanceService.ensureEmployeeSchema();
        List<OvertimeRequest> requests;
        OvertimeRequestStatus parsedStatus = parseStatus(status);
        if (clientId != null && parsedStatus != null) {
            requests = overtimeRequestRepository.findByEmployeeClientIdAndStatusOrderByIdDesc(clientId, parsedStatus);
        } else if (clientId != null) {
            requests = overtimeRequestRepository.findByEmployeeClientIdOrderByIdDesc(clientId);
        } else if (parsedStatus != null) {
            requests = overtimeRequestRepository.findAll()
                    .stream()
                    .filter(req -> req != null && parsedStatus.equals(req.getStatus()))
                    .sorted(Comparator.comparing(OvertimeRequest::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        } else {
            requests = overtimeRequestRepository.findAll();
            requests.sort(Comparator.comparing(OvertimeRequest::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        }

        List<Map<String, Object>> response = new ArrayList<>();
        for (OvertimeRequest request : requests) {
            Employee employee = request.getEmployee();
            if (employee == null) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("id", request.getId());
            row.put("employeeId", employee.getId());
            row.put("employeeName", String.format("%s %s",
                    safe(employee.getFirstName()),
                    safe(employee.getLastName())).trim());
            row.put("branch", employee.getBranch());
            row.put("date", request.getDate());
            row.put("overtimeHours", request.getOvertimeHours() == null ? 0.0 : request.getOvertimeHours());
            row.put("reason", request.getReason());
            row.put("status", request.getStatus() == null ? OvertimeRequestStatus.PENDING.name() : request.getStatus().name());
            row.put("createdAt", request.getCreatedAt());
            row.put("updatedAt", request.getUpdatedAt());
            response.add(row);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<?> approve(@PathVariable Long requestId) {
        return updateStatus(requestId, OvertimeRequestStatus.APPROVED);
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<?> reject(@PathVariable Long requestId) {
        return updateStatus(requestId, OvertimeRequestStatus.REJECTED);
    }

    private ResponseEntity<?> updateStatus(Long requestId, OvertimeRequestStatus status) {
        schemaMaintenanceService.ensureEmployeeSchema();
        Optional<OvertimeRequest> optional = overtimeRequestRepository.findById(requestId);
        if (optional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Overtime request not found.");
        }

        OvertimeRequest request = optional.get();
        request.setStatus(status);
        overtimeRequestRepository.save(request);

        Employee employee = request.getEmployee();
        if (employee != null && request.getDate() != null) {
            List<AttendanceRecord> records =
                    attendanceRecordRepository.findByEmployeeIdAndDate(employee.getId(), request.getDate());
            for (AttendanceRecord record : records) {
                record.setOvertimeRequested(Boolean.TRUE);
                record.setOvertimeApproved(status == OvertimeRequestStatus.APPROVED);
                attendanceRecordRepository.save(record);
            }
        }

        return ResponseEntity.ok(Map.of(
                "id", request.getId(),
                "status", request.getStatus().name()
        ));
    }

    private OvertimeRequestStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OvertimeRequestStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
