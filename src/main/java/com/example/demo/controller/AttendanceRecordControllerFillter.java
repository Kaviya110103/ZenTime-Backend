package com.example.demo.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.MODELS.AttendanceRecord;
import com.example.demo.MODELS.Employee;
import com.example.demo.repo.AttendanceRecordRepository;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.service.AttendanceMetricsService;
import com.example.demo.service.EmployeeService;
import com.example.demo.service.AttendanceSchedulerService;

import java.util.HashMap;
import java.util.Map;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Comparator;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/attendance-records")
@CrossOrigin(origins = "*")

public class AttendanceRecordControllerFillter {

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private AttendanceMetricsService attendanceMetricsService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceSchedulerService attendanceSchedulerService;

    private void safeSyncRecords(List<AttendanceRecord> records) {
        try {
            attendanceMetricsService.synchronizeMissedMinutes(records);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void safeSyncRecord(AttendanceRecord record) {
        try {
            attendanceMetricsService.synchronizeMissedMinutes(record);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // 1. Get all attendance details
    @GetMapping("/all")
    public List<AttendanceRecord> getAllAttendanceRecords() {
        List<AttendanceRecord> records = attendanceRecordRepository.findAll();
        safeSyncRecords(records);
        return records;
    }

    // 2. Get attendance details by employee ID
    @GetMapping("/by-employee")
    public List<AttendanceRecord> getAttendanceByEmployeeId(@RequestParam String employeeId) {
        Optional<Employee> employee = resolveEmployeeByRef(employeeId);
        if (employee.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now();
        LocalDate from = today.withDayOfMonth(1);
        attendanceSchedulerService.ensureAbsentForEmployeeDateRange(employee.get(), from, today);

        List<AttendanceRecord> records = attendanceRecordRepository.findByEmployeeId(employee.get().getId());
        safeSyncRecords(records);
        return records;
    }

    // 3. Get attendance details by month and employee ID
    @GetMapping("/by-employee-month")
    public List<AttendanceRecord> getAttendanceByEmployeeIdAndMonth(
            @RequestParam String employeeId,
            @RequestParam int month,
            @RequestParam int year) {
        Optional<Employee> employee = resolveEmployeeByRef(employeeId);
        if (employee.isEmpty()) {
            return List.of();
        }
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate from = yearMonth.atDay(1);
        LocalDate to = yearMonth.atEndOfMonth();
        attendanceSchedulerService.ensureAbsentForEmployeeDateRange(employee.get(), from, to);

        List<AttendanceRecord> records = attendanceRecordRepository.findByEmployeeId(employee.get().getId())
                .stream()
                .filter(record -> isRecordInMonth(record, month, year))
                .toList();
        safeSyncRecords(records);
        return records;
    }

    @GetMapping("/by-employee-month-metrics")
    public ResponseEntity<?> getAttendanceMetricsByEmployeeIdAndMonth(
            @RequestParam String employeeId,
            @RequestParam int month,
            @RequestParam int year) {
        Optional<Employee> employee = resolveEmployeeByRef(employeeId);
        if (employee.isEmpty()) {
            Map<String, Object> emptyResponse = new HashMap<>();
            emptyResponse.put("employeeId", employeeId);
            emptyResponse.put("month", month);
            emptyResponse.put("year", year);
            emptyResponse.put("absentCount", 0);
            emptyResponse.put("presentCount", 0);
            emptyResponse.put("workingDays", 0);
            emptyResponse.put("totalLateMinutes", 0);
            emptyResponse.put("totalEarlyOutMinutes", 0);
            emptyResponse.put("totalMissedTimes", 0);
            emptyResponse.put("approvedPermissionCount", 0);
            emptyResponse.put("approvedPermissionMinutes", 0);
            emptyResponse.put("maxPermissionsPerMonth", AttendanceMetricsService.MAX_APPROVED_PERMISSIONS_PER_MONTH);
            emptyResponse.put("maxPermissionMinutesPerMonth", AttendanceMetricsService.MAX_APPROVED_PERMISSION_MINUTES_PER_MONTH);
            return ResponseEntity.ok(emptyResponse);
        }

        Long resolvedEmployeeId = employee.get().getId();
        AttendanceMetricsService.MonthlyMetrics metrics =
                attendanceMetricsService.calculateMonthlyMetrics(resolvedEmployeeId, month, year);

        Map<String, Object> response = new HashMap<>();
        response.put("employeeId", resolvedEmployeeId);
        response.put("month", month);
        response.put("year", year);
        response.put("absentCount", metrics.absentCount());
        response.put("presentCount", metrics.presentCount());
        response.put("workingDays", metrics.presentCount());
        response.put("totalLateMinutes", metrics.monthlyLateMinutes());
        response.put("totalEarlyOutMinutes", metrics.monthlyEarlyOutMinutes());
        response.put("totalMissedTimes", metrics.totalMissedMinutes());
        response.put("approvedPermissionCount", metrics.approvedPermissionCount());
        response.put("approvedPermissionMinutes", metrics.approvedPermissionMinutes());
        response.put("maxPermissionsPerMonth", AttendanceMetricsService.MAX_APPROVED_PERMISSIONS_PER_MONTH);
        response.put("maxPermissionMinutesPerMonth", AttendanceMetricsService.MAX_APPROVED_PERMISSION_MINUTES_PER_MONTH);
        return ResponseEntity.ok(response);
    }

    // 4. Get attendance details by date and employee ID
    @GetMapping("/by-employee-date")
    public ResponseEntity<?> getAttendanceByEmployeeIdAndDate(
            @RequestParam String employeeId,
            @RequestParam String date) {
        Optional<Employee> employee = resolveEmployeeByRef(employeeId);
        if (employee.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        LocalDate targetDate = parseFlexibleDate(date);
        if (targetDate == null) {
            return ResponseEntity.ok(List.of());
        }

        List<AttendanceRecord> records = attendanceRecordRepository.findByEmployeeId(employee.get().getId())
                .stream()
                .filter(record -> {
                    LocalDate recordDate = parseFlexibleDate(record.getDate());
                    return recordDate != null && recordDate.equals(targetDate);
                })
                .toList();

        if (records.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        if (records.size() == 1) {
            AttendanceRecord record = records.get(0);
            safeSyncRecord(record);
            return ResponseEntity.ok(record);
        }

        safeSyncRecords(records);
        return ResponseEntity.ok(records);
    }

    // 5. Get attendance details by employee branch
    @GetMapping("/by-branch")
    public List<AttendanceRecord> getAttendanceByBranch(@RequestParam String branch) {
        List<AttendanceRecord> records = attendanceRecordRepository.findByEmployeeBranch(branch);
        safeSyncRecords(records);
        return records;
    }

    // 6. Get all details by particular month and particular date for all employees
    @GetMapping("/by-month-date")
    public List<AttendanceRecord> getAttendanceByMonthAndDate(
            @RequestParam int month,
            @RequestParam int year,
            @RequestParam String date) {
        List<AttendanceRecord> records = attendanceRecordRepository.findAll()
                .stream()
                .filter(record -> isRecordInMonth(record, month, year))
                .filter(record -> {
                    LocalDate recordDate = parseFlexibleDate(record.getDate());
                    LocalDate targetDate = parseFlexibleDate(date);
                    if (recordDate == null || targetDate == null) {
                        return false;
                    }
                    return recordDate.equals(targetDate);
                })
                .toList();
        safeSyncRecords(records);
        return records;
    }

    // 7. Get all attendance details by date and attendance status (Present/Absent)
    @GetMapping("/by-date-status")
    public List<AttendanceRecord> getAttendanceByDateAndStatus(
            @RequestParam String date,
            @RequestParam String attendanceStatus) {
        List<AttendanceRecord> records = attendanceRecordRepository.findByDateAndAttendanceStatus(date, attendanceStatus);
        safeSyncRecords(records);
        return records;
    }
    @GetMapping("/branches")
public List<String> getAllBranches() {
    return attendanceRecordRepository.findAllDistinctBranches();
}

@PutMapping("/calculate-missed-times")
public ResponseEntity<?> calculateAndUpdateMissedTimes(@RequestParam Long attendanceId) {
    Optional<AttendanceRecord> optional = attendanceRecordRepository.findById(attendanceId);
    if (optional.isEmpty()) {
        return ResponseEntity.status(404).body("Attendance record not found.");
    }
    AttendanceRecord record = optional.get();

    if (record.getTimeIn() != null && record.getTimeOut() != null) {
        employeeService.updateMissedTimes(record);
        return ResponseEntity.ok("Missed times calculated and updated: " + record.getMissedTimes() + " minutes.");
    } else {
        return ResponseEntity.badRequest().body("Both timeIn and timeOut must be set to calculate missed times.");
    }
}
@PutMapping("/calculate-missed-times-all")
public ResponseEntity<?> calculateMissedTimesForAll() {
    List<AttendanceRecord> records = attendanceRecordRepository.findAll();
    int updatedCount = 0;

    for (AttendanceRecord record : records) {
        if (record.getTimeIn() != null && record.getTimeOut() != null) {
            employeeService.updateMissedTimes(record);
            updatedCount++;
        }
    }
    return ResponseEntity.ok("Missed times calculated and updated for " + updatedCount + " records.");
}

@PutMapping("/upload-both-images-all")
public ResponseEntity<?> uploadBothImagesForAll(@RequestParam("file") MultipartFile file) {
    try {
        byte[] imageBytes = file.getBytes();
        List<AttendanceRecord> records = attendanceRecordRepository.findAll();
        for (AttendanceRecord record : records) {
            record.setImageIn(imageBytes);
            record.setImageOut(imageBytes);
        }
        attendanceRecordRepository.saveAll(records);
        return ResponseEntity.ok("Both images uploaded for all attendance records.");
    } catch (Exception e) {
        return ResponseEntity.status(500).body("Failed to upload images for all records.");
    }
}

@GetMapping("/by-status-month")
    public List<AttendanceRecord> getAttendanceByStatusAndMonth(
            @RequestParam String attendanceStatus,
            @RequestParam int month,
            @RequestParam int year) {
        List<AttendanceRecord> records = attendanceRecordRepository.findByAttendanceStatus(attendanceStatus)
                .stream()
                .filter(record -> isRecordInMonth(record, month, year))
                .toList();
        safeSyncRecords(records);
        return records;
    }

@GetMapping("/by-month-status-branch")
    public List<AttendanceRecord> getAttendanceByMonthStatusBranch(
            @RequestParam int month,
            @RequestParam int year,
            @RequestParam String attendanceStatus,
            @RequestParam String branch) {
        List<AttendanceRecord> records = attendanceRecordRepository.findByAttendanceStatus(attendanceStatus)
                .stream()
                .filter(record -> isRecordInMonth(record, month, year))
                .filter(record -> record.getEmployee() != null
                        && record.getEmployee().getBranch() != null
                        && record.getEmployee().getBranch().equalsIgnoreCase(branch))
                .toList();
        safeSyncRecords(records);
        return records;
    }



@GetMapping("/check-today-attendance")
    public ResponseEntity<?> checkTodayAttendance(@RequestParam String employeeId) {
    Optional<Employee> employee = resolveEmployeeByRef(employeeId);
    if (employee.isEmpty()) {
        return ResponseEntity.ok("Employee not found.");
    }

    LocalDate today = java.time.LocalDate.now();
    List<String> candidates = List.of(
            today.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            today.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")),
            today.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
    );
    List<AttendanceRecord> records = new ArrayList<>();
    for (String candidate : candidates) {
        records = attendanceRecordRepository.findByEmployeeIdAndDate(employee.get().getId(), candidate);
        if (!records.isEmpty()) {
            break;
        }
    }

    if (records.isEmpty()) {
        return ResponseEntity.ok("No attendance for today!");
    }

    AttendanceRecord record = records.get(0); // Assuming one record per day per employee

    if ("Absent".equalsIgnoreCase(record.getAttendanceStatus())) {
        return ResponseEntity.ok("Absent today!");
    }
    if (!"Present".equalsIgnoreCase(record.getAttendanceStatus())) {
        return ResponseEntity.ok("Not marked as present today!");
    }
    if (record.getTimeIn() == null) {
        return ResponseEntity.ok("Time-in not marked!");
    }
    if (record.getTimeOut() == null) {
        return ResponseEntity.ok("Time-out not marked!");
    }
    if (record.getDayStatus() == null || !"Completed".equalsIgnoreCase(record.getDayStatus())) {
        return ResponseEntity.ok("Day not closed!");
    }
    return ResponseEntity.ok("Attendance complete for today.");
}
// Get attendance details of all employees for a particular date
@GetMapping("/by-date")
public List<AttendanceRecord> getAttendanceByDate(
        @RequestParam String date) {
    List<AttendanceRecord> records = attendanceRecordRepository.findByDate(date);
    safeSyncRecords(records);
    return records;
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

private boolean isRecordInMonth(AttendanceRecord record, int month, int year) {
    if (record == null) {
        return false;
    }
    LocalDate date = parseFlexibleDate(record.getDate());
    if (date == null) {
        return false;
    }
    return date.getMonthValue() == month && date.getYear() == year;
}

private LocalDate parseFlexibleDate(String raw) {
    if (raw == null || raw.isBlank()) {
        return null;
    }
    String value = raw.trim();
    DateTimeFormatter[] formatters = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    };
    for (DateTimeFormatter formatter : formatters) {
        try {
            return LocalDate.parse(value, formatter);
        } catch (DateTimeParseException ignored) {
            // try next format
        }
    }
    return null;
}
@GetMapping("/by-date-status-all")
public List<AttendanceRecord> getAttendanceByDateAndStatusAll(
        @RequestParam String date,
        @RequestParam String attendanceStatus) {
    List<AttendanceRecord> records = attendanceRecordRepository.findByDateAndAttendanceStatus(date, attendanceStatus);
    attendanceMetricsService.synchronizeMissedMinutes(records);
    return records;
}
// Get all ABSENT employees for a particular date
@GetMapping("/absent-by-date")
public List<AttendanceRecord> getAbsentByDate(
        @RequestParam String date) {
    List<AttendanceRecord> records = attendanceRecordRepository
            .findByDateAndAttendanceStatus(date, "Absent");
    attendanceMetricsService.synchronizeMissedMinutes(records);
    return records;
}

@GetMapping("/auto-absent/run-today")
public ResponseEntity<?> runAutoAbsentToday() {
    int updated = attendanceSchedulerService.runAutoAbsentForToday();
    return ResponseEntity.ok(Map.of("updatedAbsentCount", updated));
}

@GetMapping("/debug-dates")
public ResponseEntity<?> debugAttendanceDates(@RequestParam String employeeId) {
    Optional<Employee> employee = resolveEmployeeByRef(employeeId);
    if (employee.isEmpty()) {
        return ResponseEntity.ok(List.of());
    }
    List<Map<String, Object>> rows = attendanceRecordRepository.findByEmployeeId(employee.get().getId())
            .stream()
            .map(record -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", record.getId());
                row.put("dateRaw", record.getDate());
                LocalDate parsed = parseFlexibleDate(record.getDate());
                row.put("dateParsed", parsed != null ? parsed.toString() : null);
                row.put("status", record.getAttendanceStatus());
                return row;
            })
            .sorted(Comparator.comparing(r -> String.valueOf(r.get("dateParsed")), Comparator.nullsLast(String::compareTo)))
            .collect(Collectors.toList());
    return ResponseEntity.ok(rows);
}























































}
