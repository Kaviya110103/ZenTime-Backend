package com.example.demo.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.MODELS.AttendanceRecord;
import com.example.demo.MODELS.Employee;
import com.example.demo.MODELS.Location;
import com.example.demo.MODELS.LocationRequest;
import com.example.demo.repo.AttendanceRecordRepository;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.repo.LocationRepository;
import com.example.demo.repo.LocationRequestRepository;
import com.example.demo.service.TenantDatabaseProvisioningService;

@RestController
@RequestMapping("/api/location-requests")
@CrossOrigin(origins = "*")
public class LocationRequestController {
    private static final DateTimeFormatter DB_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LocationRequestRepository locationRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final LocationRepository locationRepository;
    private final TenantDatabaseProvisioningService tenantDatabaseProvisioningService;

    public LocationRequestController(LocationRequestRepository locationRequestRepository,
                                     EmployeeRepository employeeRepository,
                                     AttendanceRecordRepository attendanceRecordRepository,
                                     LocationRepository locationRepository,
                                     TenantDatabaseProvisioningService tenantDatabaseProvisioningService) {
        this.locationRequestRepository = locationRequestRepository;
        this.employeeRepository = employeeRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.locationRepository = locationRepository;
        this.tenantDatabaseProvisioningService = tenantDatabaseProvisioningService;
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submit(@RequestBody SubmitLocationRequest body,
                                    @RequestParam(value = "clientId", required = false) Long clientId) {
        if (body == null || body.employeeId == null || body.latitude == null || body.longitude == null
                || body.reason == null || body.reason.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "employeeId, latitude, longitude and reason are required"));
        }

        Optional<Employee> employeeOpt = clientId == null
                ? employeeRepository.findById(body.employeeId)
                : employeeRepository.findByIdAndClientId(body.employeeId, clientId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Employee not found"));
        }
        Employee employee = employeeOpt.get();

        Long effectiveClientId = clientId != null ? clientId : employee.getClientId();
        ensureLocationRequestTableExistsForEmployee(employee);
        boolean insideAssignedLocation = isInsideAssignedLocation(body.latitude, body.longitude, effectiveClientId);
        if (insideAssignedLocation) {
            return ResponseEntity.badRequest().body(Map.of("message", "Employee is inside assigned location. Request is not required."));
        }

        String today = LocalDate.now().format(DB_DATE_FORMATTER);
        AttendanceRecord attendanceRecord = attendanceRecordRepository
                .findByDateAndEmployeeId(today, employee.getId())
                .orElseGet(() -> {
                    AttendanceRecord created = new AttendanceRecord();
                    created.setEmployee(employee);
                    created.setDate(today);
                    created.setAttendanceStatus("Present");
                    return created;
                });

        if (attendanceRecord.getTimeIn() == null) {
            attendanceRecord.setTimeIn(LocalDateTime.now());
        }
        if (attendanceRecord.getAttendanceStatus() == null || attendanceRecord.getAttendanceStatus().isBlank()) {
            attendanceRecord.setAttendanceStatus("Present");
        }
        if (body.currentAddress != null && !body.currentAddress.isBlank()) {
            attendanceRecord.setLocation(body.currentAddress.trim());
        }
        attendanceRecordRepository.save(attendanceRecord);

        LocationRequest request = new LocationRequest();
        request.setEmployee(employee);
        request.setAttendanceRecord(attendanceRecord);
        request.setCurrentLatitude(body.latitude);
        request.setCurrentLongitude(body.longitude);
        request.setCurrentAddress(body.currentAddress);
        request.setReason(body.reason.trim());
        request.setStatus("pending");
        request.setRequestedAt(LocalDateTime.now());
        locationRequestRepository.save(request);

        return ResponseEntity.ok(Map.of(
                "message", "Location request submitted. Time-in recorded successfully.",
                "requestId", request.getId(),
                "attendanceId", attendanceRecord.getId(),
                "timeIn", attendanceRecord.getTimeIn()
        ));
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(value = "clientId", required = false) Long clientId,
                                  @RequestParam(value = "status", required = false) String status) {
        List<LocationRequest> rows;
        if (clientId == null) {
            rows = status == null || status.isBlank()
                    ? locationRequestRepository.findAll()
                    : locationRequestRepository.findAll().stream()
                        .filter(r -> status.equalsIgnoreCase(r.getStatus()))
                        .toList();
        } else {
            rows = status == null || status.isBlank()
                    ? locationRequestRepository.findByEmployee_ClientIdOrderByRequestedAtDesc(clientId)
                    : locationRequestRepository.findByEmployee_ClientIdAndStatusIgnoreCaseOrderByRequestedAtDesc(clientId, status);
        }

        List<Map<String, Object>> payload = rows.stream().map(this::toResponse).toList();
        return ResponseEntity.ok(payload);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id,
                                          @RequestParam String status,
                                          @RequestParam(value = "reviewedBy", required = false) String reviewedBy,
                                          @RequestParam(value = "remark", required = false) String remark,
                                          @RequestParam(value = "clientId", required = false) Long clientId) {
        String normalized = status == null ? "" : status.trim().toLowerCase();
        if (!"approved".equals(normalized) && !"rejected".equals(normalized)) {
            return ResponseEntity.badRequest().body(Map.of("message", "status must be approved or rejected"));
        }

        Optional<LocationRequest> requestOpt = locationRequestRepository.findById(id);
        if (requestOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Request not found"));
        }
        LocationRequest request = requestOpt.get();

        if (clientId != null) {
            Long requestClientId = request.getEmployee() != null ? request.getEmployee().getClientId() : null;
            if (requestClientId == null || !clientId.equals(requestClientId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Request does not belong to this client"));
            }
        }

        request.setStatus(normalized);
        request.setDecisionAt(LocalDateTime.now());
        request.setReviewedBy(reviewedBy);
        request.setReviewRemark(remark);
        locationRequestRepository.save(request);

        return ResponseEntity.ok(toResponse(request));
    }

    private Map<String, Object> toResponse(LocationRequest request) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", request.getId());
        row.put("status", request.getStatus());
        row.put("reason", request.getReason());
        row.put("currentLatitude", request.getCurrentLatitude());
        row.put("currentLongitude", request.getCurrentLongitude());
        row.put("currentAddress", request.getCurrentAddress());
        row.put("requestedAt", request.getRequestedAt());
        row.put("decisionAt", request.getDecisionAt());
        row.put("reviewedBy", request.getReviewedBy());
        row.put("reviewRemark", request.getReviewRemark());
        row.put("attendanceId", request.getAttendanceRecord() != null ? request.getAttendanceRecord().getId() : null);
        if (request.getEmployee() != null) {
            Map<String, Object> employeeMap = new HashMap<>();
            employeeMap.put("id", request.getEmployee().getId());
            employeeMap.put("firstName", request.getEmployee().getFirstName());
            employeeMap.put("lastName", request.getEmployee().getLastName());
            employeeMap.put("branch", request.getEmployee().getBranch());
            employeeMap.put("companyCode", request.getEmployee().getCompanyCode());
            employeeMap.put("clientId", request.getEmployee().getClientId());
            row.put("employee", employeeMap);
        }
        return row;
    }

    private boolean isInsideAssignedLocation(double latitude, double longitude, Long clientId) {
        List<Location> locations = clientId == null ? locationRepository.findAll() : locationRepository.findByClientId(clientId);
        for (Location location : locations) {
            if (location.getLatitude() == null || location.getLongitude() == null || location.getRadius() == null) {
                continue;
            }
            double distance = distanceMeters(latitude, longitude, location.getLatitude(), location.getLongitude());
            if (distance <= location.getRadius()) {
                return true;
            }
        }
        return false;
    }

    private double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double rad = Math.PI / 180.0;
        double dLat = (lat2 - lat1) * rad;
        double dLon = (lon2 - lon1) * rad;
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371000.0 * c;
    }

    private void ensureLocationRequestTableExistsForEmployee(Employee employee) {
        if (employee == null || employee.getCompanyCode() == null || employee.getCompanyCode().isBlank()) {
            return;
        }

        String companyCode = employee.getCompanyCode().trim().toLowerCase();
        String tenantDbName = tenantDatabaseProvisioningService.buildTenantDatabaseName(companyCode);

        boolean hasTable = tenantDatabaseProvisioningService.hasRequiredTables(
                tenantDbName,
                java.util.Set.of("location_requests"));
        if (!hasTable) {
            tenantDatabaseProvisioningService.provisionTenantDatabase(companyCode);
        }
    }

    public static class SubmitLocationRequest {
        public Long employeeId;
        public Double latitude;
        public Double longitude;
        public String currentAddress;
        public String reason;
    }
}
