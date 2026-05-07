package com.example.demo.controller;

import com.example.demo.MODELS.AttendanceRecord;
import com.example.demo.MODELS.AttendanceRecordDTO;
import com.example.demo.MODELS.DateUtil;
import com.example.demo.MODELS.Employee;
import com.example.demo.MODELS.LeavePermission;
import com.example.demo.MODELS.Location;
import com.example.demo.MODELS.OvertimeRequest;
import com.example.demo.MODELS.OvertimeRequestStatus;
import com.example.demo.repo.AttendanceRecordRepository;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.repo.LeavePermissionRepository;
import com.example.demo.repo.LocationRepository;
import com.example.demo.repo.OvertimeRequestRepository;
import com.example.demo.service.AttendanceMetricsService;
import com.example.demo.service.AttendanceSchedulerService;
import com.example.demo.service.AttendanceService;
import com.example.demo.service.EmployeeService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/attendance")
@CrossOrigin(origins = "*")
public class AttendanceController {

    @Autowired
private EmployeeRepository employeeRepository;
@Autowired
private EmployeeService employeeService;

@Autowired
private LeavePermissionRepository leavePermissionRepository;

@Autowired
private AttendanceMetricsService attendanceMetricsService;
@Autowired
private AttendanceSchedulerService attendanceSchedulerService;
@Autowired
private LocationRepository locationRepository;

   @Autowired
    private AttendanceService attendanceService;



    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired
    private com.example.demo.service.SchemaMaintenanceService schemaMaintenanceService;
    @Autowired
    private OvertimeRequestRepository overtimeRequestRepository;
        private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER_HH_MM = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter TIME_FORMATTER_HH_MM_SS = DateTimeFormatter.ofPattern("H:mm:ss");
    private static final LocalTime DEFAULT_SHIFT_END = LocalTime.of(19, 0);
@PutMapping("/start-day")
public ResponseEntity<?> startDay(@RequestParam Long employeeId,
                                  @RequestParam String location,
                                  @RequestParam(value = "clientId", required = false) Long clientId,
                                  @RequestParam(value = "latitude", required = false) Double latitude,
                                  @RequestParam(value = "longitude", required = false) Double longitude) {
    String todayDate = LocalDate.now().format(dateFormatter);
    String yesterdayDate = LocalDate.now().minusDays(1).format(dateFormatter);

    // 1. Check if employee exists
    Optional<Employee> employeeOptional = employeeRepository.findById(employeeId);
    if (employeeOptional.isEmpty()) {
        return ResponseEntity.badRequest().body("Employee not found.");
    }
    Employee employee = employeeOptional.get();

    if (latitude != null && longitude != null) {
        Long effectiveClientId = clientId != null ? clientId : employee.getClientId();
        if (!isInsideAnyBranchLocation(latitude, longitude, effectiveClientId)) {
            Map<String, Object> inactive = new HashMap<>();
            inactive.put("status", "Inactive");
            inactive.put("message", "Outside assigned branch location. Submit location request.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(inactive);
        }
    }

    // 2. Check yesterday's attendance for missing time-out
    List<AttendanceRecord> yesterdayRecords = attendanceRecordRepository.findByEmployeeIdAndDate(employeeId, yesterdayDate);
    if (!yesterdayRecords.isEmpty()) {
        AttendanceRecord yesterdayRecord = yesterdayRecords.get(0);
        if (yesterdayRecord.getAttendanceStatus().equalsIgnoreCase("Present") &&
            yesterdayRecord.getTimeOut() == null) {
            // If TimoutReason is not set, block and return the record ID
            if (yesterdayRecord.getTimoutReason() == null || yesterdayRecord.getTimoutReason().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("error", "You didn’t mark time-out yesterday. Please submit a timeout reason.");
                response.put("missedTimeoutRecordId", yesterdayRecord.getId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
        }
    }

    // 3. Check if attendance already marked today
    List<AttendanceRecord> todayRecords = attendanceRecordRepository.findByEmployeeIdAndDate(employeeId, todayDate);
    if (!todayRecords.isEmpty()) {
        String status = todayRecords.get(0).getAttendanceStatus();
        return ResponseEntity.badRequest()
                .body("Attendance already marked as '" + status + "' for today.");
    }

    // 4. If all checks passed, create attendance record
    AttendanceRecord record = new AttendanceRecord();
    record.setEmployee(employee);
    record.setAttendanceStatus("Present");
    record.setDate(todayDate);
    record.setLocation(location); // Store location

    attendanceRecordRepository.save(record);

    return ResponseEntity.ok("Day started. Please mark time-in.");
}

private boolean isInsideAnyBranchLocation(double latitude, double longitude, Long clientId) {
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
    
@PostMapping("/submit-timeout-reason")
public ResponseEntity<?> submitTimeoutReason(@RequestParam Long recordId, @RequestParam String reason) {
    schemaMaintenanceService.ensureEmployeeSchema();
    Optional<AttendanceRecord> optionalRecord = attendanceRecordRepository.findById(recordId);
    if (optionalRecord.isPresent()) {
        AttendanceRecord record = optionalRecord.get();
        record.setTimoutReason(reason);
        attendanceRecordRepository.save(record);
        return ResponseEntity.ok("Timeout reason submitted successfully.");
    } else {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Attendance record not found.");
    }
}
@GetMapping("/missed-timeout")
public List<Map<String, Object>> getMissedTimeoutEmployees(
        @RequestParam(value = "clientId", required = false) Long clientId) {
    List<AttendanceRecord> records = attendanceRecordRepository.findByAttendanceStatusAndTimeOutIsNull("Present");
    List<Map<String, Object>> result = new ArrayList<>();
    for (AttendanceRecord record : records) {
        if (clientId != null && (record.getEmployee() == null || !clientId.equals(record.getEmployee().getClientId()))) {
            continue;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("attendanceId", record.getId());
        map.put("firstName", record.getEmployee().getFirstName());
        map.put("mobile", record.getEmployee().getMobile());
        map.put("branch", record.getEmployee().getBranch());
        map.put("position", record.getEmployee().getPosition());
        map.put("date", record.getDate());
        map.put("timeoutReason", record.getTimoutReason()); // Add this line
        result.add(map);
    }
    return result;
}

@GetMapping("/completed-missed-timeout")
public List<Map<String, Object>> getCompletedMissedTimeoutEmployees(
        @RequestParam(value = "clientId", required = false) Long clientId) {
    List<AttendanceRecord> records = attendanceRecordRepository.findByAttendanceStatus("Present");
    List<Map<String, Object>> result = new ArrayList<>();

    for (AttendanceRecord record : records) {
        if (record == null || record.getEmployee() == null) {
            continue;
        }
        if (clientId != null && !clientId.equals(record.getEmployee().getClientId())) {
            continue;
        }
        if (record.getTimeOut() == null) {
            continue;
        }

        String timeoutReason = record.getTimoutReason();
        if (timeoutReason == null || timeoutReason.trim().isEmpty()) {
            continue;
        }

        Map<String, Object> map = new HashMap<>();
        map.put("attendanceId", record.getId());
        map.put("firstName", record.getEmployee().getFirstName());
        map.put("mobile", record.getEmployee().getMobile());
        map.put("branch", record.getEmployee().getBranch());
        map.put("position", record.getEmployee().getPosition());
        map.put("date", record.getDate());
        map.put("timeoutReason", timeoutReason);
        map.put("timeOut", record.getTimeOut());
        map.put("status", "COMPLETED");
        result.add(map);
    }

    return result;
}

@DeleteMapping("/completed-missed-timeout/{attendanceId}")
public ResponseEntity<?> deleteCompletedMissedTimeoutRecord(
        @PathVariable Long attendanceId,
        @RequestParam(value = "clientId", required = false) Long clientId) {
    Optional<AttendanceRecord> optional = attendanceRecordRepository.findById(attendanceId);
    if (optional.isEmpty()) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Attendance record not found.");
    }

    AttendanceRecord record = optional.get();
    Employee employee = record.getEmployee();
    if (employee == null) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid attendance record.");
    }
    if (clientId != null && !clientId.equals(employee.getClientId())) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied for this record.");
    }
    if (record.getTimeOut() == null) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("Only completed clockout requests can be deleted.");
    }

    String timeoutReason = record.getTimoutReason();
    if (timeoutReason == null || timeoutReason.trim().isEmpty()) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("Only completed missed-time requests can be deleted.");
    }

    attendanceRecordRepository.delete(record);
    return ResponseEntity.ok("Completed clockout request deleted successfully.");
}

@DeleteMapping("/completed-missed-timeout")
public ResponseEntity<?> deleteAllCompletedMissedTimeoutRecords(
        @RequestParam(value = "clientId", required = false) Long clientId) {
    List<AttendanceRecord> records = attendanceRecordRepository.findByAttendanceStatus("Present");
    List<AttendanceRecord> toDelete = new ArrayList<>();

    for (AttendanceRecord record : records) {
        if (record == null || record.getEmployee() == null) {
            continue;
        }
        if (clientId != null && !clientId.equals(record.getEmployee().getClientId())) {
            continue;
        }
        if (record.getTimeOut() == null) {
            continue;
        }
        String timeoutReason = record.getTimoutReason();
        if (timeoutReason == null || timeoutReason.trim().isEmpty()) {
            continue;
        }
        toDelete.add(record);
    }

    if (toDelete.isEmpty()) {
        return ResponseEntity.ok(Map.of("deletedCount", 0, "message", "No completed clockout requests found."));
    }

    attendanceRecordRepository.deleteAll(toDelete);
    return ResponseEntity.ok(Map.of("deletedCount", toDelete.size(), "message", "Completed clockout requests deleted."));
}
@PutMapping("/complete-missed-timeout")
public ResponseEntity<?> completeMissedTimeout(
        @RequestParam Long attendanceId,
        @RequestParam String timeOut,
        @RequestParam(required = false) Boolean overtimeApproved // Format: "yyyy-MM-dd'T'HH:mm:ss"
) {
    schemaMaintenanceService.ensureEmployeeSchema();
    Optional<AttendanceRecord> optional = attendanceRecordRepository.findById(attendanceId);
    if (optional.isEmpty()) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Attendance record not found.");
    }
    AttendanceRecord record = optional.get();
    record.setDayStatus("Completed");
    record.setTimeOut(LocalDateTime.parse(timeOut));
    if (overtimeApproved != null) {
        record.setOvertimeApproved(overtimeApproved);
    }
    // Automatically calculate and update missed times
    employeeService.updateMissedTimes(record);
    attendanceRecordRepository.save(record);
    return ResponseEntity.ok("Timeout, missed times, and day status updated.");
}
   @CrossOrigin(origins = "*")
@PostMapping("/mark-time-in")
public ResponseEntity<String> markTimeIn(
        @RequestParam Long recordId,
        @RequestParam MultipartFile imageIn) {
    Optional<AttendanceRecord> optionalRecord = attendanceRecordRepository.findById(recordId);
    if (optionalRecord.isPresent()) {
        try {
            AttendanceRecord record = optionalRecord.get();
            record.setTimeIn(LocalDateTime.now());
            record.setImageIn(imageIn.getBytes());
            attendanceRecordRepository.save(record);
            return ResponseEntity.ok("Time-in recorded successfully.");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Image processing failed");
        }
    } else {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Attendance record not found");
    }
}

    @GetMapping("/all")
    public List<AttendanceRecord> getAllAttendanceRecords() {
        return attendanceRecordRepository.findAll();
    }


 @GetMapping("/monthly/{employeeId}")
    public List<AttendanceRecord> getMonthlyAttendance(@PathVariable Long employeeId) {
        return attendanceRecordRepository.findByEmployeeId(employeeId);
    }

    // Get one record by date
   @GetMapping("/{employeeId}/{isoDate}")
    public ResponseEntity<?> getByDate(
            @PathVariable Long employeeId,
            @PathVariable String isoDate) {

        String dbDate = DateUtil.isoToDb(isoDate);          // 14/07/2025
        return attendanceRecordRepository.findByEmployee_IdAndDate(employeeId, dbDate)
                .map(AttendanceRecordDTO::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /* ---------- 2. Monthly list endpoint --------------------------------- */
    // GET /api/attendance/monthly/9/2025/07
    @GetMapping("/monthly/{employeeId}/{year}/{month}")
    public List<AttendanceRecordDTO> getMonth(
            @PathVariable Long employeeId,
            @PathVariable int year,
            @PathVariable int month) {

        String start = DateUtil.dbStartOfMonth(year, month); // 01/07/2025
        String end   = DateUtil.dbEndOfMonth(year, month);   // 31/07/2025

        return attendanceRecordRepository.findMonthlySlice(employeeId, start, end)
                   .stream()
                   .map(AttendanceRecordDTO::fromEntity)
                   .toList();
    }
    // Endpoint to mark time-out with image
    @PostMapping("/mark-time-out")  // worked
    public ResponseEntity<String> markTimeOut(
            @RequestParam Long recordId,
            @RequestParam MultipartFile imageOut,
            @RequestParam(required = false) Boolean overtimeApproved,
            @RequestParam(required = false) Boolean overtimeRequested) {
        schemaMaintenanceService.ensureEmployeeSchema();
        Optional<AttendanceRecord> optionalRecord = attendanceRecordRepository.findById(recordId);
        if (optionalRecord.isPresent()) {
            AttendanceRecord record = optionalRecord.get();
            try {
                LocalDateTime now = LocalDateTime.now();
                record.setTimeOut(now);
                if (overtimeApproved != null) {
                    record.setOvertimeApproved(overtimeApproved);
                }
                if (overtimeRequested != null) {
                    record.setOvertimeRequested(overtimeRequested);
                }
                if (imageOut != null && !imageOut.isEmpty()) {
                    record.setImageOut(imageOut.getBytes());
                }
                attendanceRecordRepository.save(record);

                if (Boolean.TRUE.equals(overtimeRequested)) {
                    Employee employee = record.getEmployee();
                    if (employee != null) {
                        LocalTime shiftEnd = parseShiftEnd(employee.getShiftEndTime());
                        LocalTime logoutTime = now.toLocalTime();
                        long overtimeMinutes = 0;
                        if (logoutTime.isAfter(shiftEnd)) {
                            overtimeMinutes = Duration.between(shiftEnd, logoutTime).toMinutes();
                        }
                        double overtimeHours = Math.round((Math.max(overtimeMinutes, 0) / 60.0) * 100.0) / 100.0;
                        if (overtimeHours > 0) {
                            OvertimeRequest request = overtimeRequestRepository
                                    .findFirstByEmployeeIdAndDateOrderByIdDesc(employee.getId(), record.getDate())
                                    .orElseGet(OvertimeRequest::new);
                            request.setEmployee(employee);
                            request.setDate(record.getDate());
                            request.setOvertimeHours(overtimeHours);
                            request.setStatus(OvertimeRequestStatus.PENDING);
                            overtimeRequestRepository.save(request);
                            record.setOvertimeApproved(false);
                            attendanceRecordRepository.save(record);
                        }
                    }
                }

                return ResponseEntity.ok("Time-out recorded successfully.");
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to process image.");
            }
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Attendance record not found.");
        }
    }

    // Endpoint to update day status
    @PostMapping("/update-day-status")
    public ResponseEntity<String> updateDayStatus(
            @RequestParam Long recordId,
            @RequestParam String dayStatus) {
    
        Optional<AttendanceRecord> optionalRecord = attendanceRecordRepository.findById(recordId);
    
        if (optionalRecord.isPresent()) {
            AttendanceRecord record = optionalRecord.get();
    
            // 1. Set day status
            record.setDayStatus(dayStatus);
    
            // 2. Set timeOut to current time
            record.setTimeOut(LocalDateTime.now());
    
            // 3. Save the updated record
            attendanceRecordRepository.save(record);
    
            // 4. Update missed time after saving timeOut
            employeeService.updateMissedTimes(record);
    
            return ResponseEntity.ok("Day status, timeOut, and missed time updated successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Attendance record not found.");
        }
    }
     // ✅ 1. Get attendance between startDate and endDate
     @GetMapping("/by-date-range")
     public ResponseEntity<List<AttendanceRecord>> getAttendanceByDateRange(
             @RequestParam Long employeeId,
             @RequestParam String startDate,
             @RequestParam String endDate) {
 
         List<AttendanceRecord> records = attendanceRecordRepository
                 .findByEmployeeIdAndDateBetween(employeeId, startDate, endDate);
 
         return ResponseEntity.ok(records);
     }
 
     // ✅ 2. Get attendance for a specific month (format: yyyy-MM)
     @GetMapping("/by-month")
     public ResponseEntity<List<AttendanceRecord>> getAttendanceByMonth(
             @RequestParam Long employeeId,
             @RequestParam String month) {
         // Extract start and end dates from the month
         LocalDate start = LocalDate.parse(month + "-01");
         LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
 
         List<AttendanceRecord> records = attendanceRecordRepository
                 .findByEmployeeIdAndDateBetween(
                         employeeId,
                         start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                         end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                 );
 
         return ResponseEntity.ok(records);
     }

    // Endpoint to check if attendance record exists by employeeId and recordId
    @GetMapping("/check-record")
    public ResponseEntity<Object> checkRecord(
            @RequestParam Long employeeId,
            @RequestParam Long recordId) {
    
        Optional<AttendanceRecord> optionalRecord = attendanceRecordRepository.findById(recordId);
    
        if (optionalRecord.isPresent()) {
            AttendanceRecord record = optionalRecord.get();
    
            if (record.getEmployee() != null && record.getEmployee().getId().equals(employeeId)) {
                return ResponseEntity.ok(record); // Return the full record details
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Employee ID does not match the record.");
            }
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Attendance record not found.");
        }
    }
    

    @GetMapping({"/{recordId}/time-in", "/api/attendance/{recordId}/time-in"})
    public ResponseEntity<byte[]> getTimeInImage(@PathVariable Long recordId) {
        Optional<AttendanceRecord> record = attendanceRecordRepository.findById(recordId);
        if (record.isPresent() && record.get().getImageIn() != null) {
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG) // Adjust based on your image format
                    .body(record.get().getImageIn());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Image not found.".getBytes());
        }
    }
@GetMapping("/incomplete/yesterday/count")
public ResponseEntity<Long> countIncompleteDayStatusYesterday() {
    LocalDate yesterday = LocalDate.now().minusDays(1);
    String formattedDate = yesterday.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

    Long count = attendanceRecordRepository.countByDateAndDayStatusNot(formattedDate, "Complete");
    return ResponseEntity.ok(count);
}

    // Endpoint to fetch timeIn, imageIn, id, and employeeId by recordId
    @GetMapping("/TimeInDetails/{id}")
    public ResponseEntity<AttendanceRecord> getAttendanceById(@PathVariable Long id) {
        AttendanceRecord record = attendanceRecordRepository.findById(id).orElse(null);
    
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
    
        // Create a new AttendanceRecord to filter fields
        AttendanceRecord filteredRecord = new AttendanceRecord();
        filteredRecord.setId(record.getId());
        filteredRecord.setTimeIn(record.getTimeIn());
        filteredRecord.setImageIn(record.getImageIn());
    
        // Set only employee ID via a minimal Employee object
        if (record.getEmployee() != null) {
            Employee minimalEmployee = new Employee();
            minimalEmployee.setId(record.getEmployee().getId());
            filteredRecord.setEmployee(minimalEmployee);
        }
    
        return ResponseEntity.ok(filteredRecord);
    }
    

    @GetMapping("/TimeOutDetails/{id}")
    public ResponseEntity<AttendanceRecord> getAttendanceTimeOutById(@PathVariable Long id) {
        AttendanceRecord record = attendanceRecordRepository.findById(id).orElse(null);
    
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
    
        // Create a new AttendanceRecord to return only necessary fields
        AttendanceRecord filteredRecord = new AttendanceRecord();
        filteredRecord.setId(record.getId());
        filteredRecord.setTimeOut(record.getTimeOut());
        filteredRecord.setImageOut(record.getImageOut());
    
        if (record.getEmployee() != null) {
            Employee minimalEmployee = new Employee();
            minimalEmployee.setId(record.getEmployee().getId());
            filteredRecord.setEmployee(minimalEmployee);
        }
    
        return ResponseEntity.ok(filteredRecord);
    }
    
 // Endpoint to get attendance details by employeeId and today's date
 @GetMapping("/get-today-attendance")
 public ResponseEntity<Object> getTodayAttendance(@RequestParam Long employeeId) {
     // Get today's date in dd/MM/yyyy format
     String todayDate = LocalDate.now().format(dateFormatter);

     // Fetch records for the given employeeId and today's date
     List<AttendanceRecord> records = attendanceRecordRepository.findByEmployeeIdAndDate(employeeId, todayDate);

     if (records.isEmpty()) {
         return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No attendance records found for today.");
     }

     return ResponseEntity.ok(records);
 }
 @GetMapping("/get-yesterday-attendance")
 public ResponseEntity<Object> getYesterdayAttendance(@RequestParam Long employeeId) {
     // Get yesterday's date in dd/MM/yyyy format
     String yesterdayDate = LocalDate.now().minusDays(1).format(dateFormatter);

     // Fetch records for the given employeeId and yesterday's date
     List<AttendanceRecord> records = attendanceRecordRepository.findByEmployeeIdAndDate(employeeId, yesterdayDate);

     if (records.isEmpty()) {
         return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No attendance records found for yesterday.");
     }

     return ResponseEntity.ok(records);
 }
 @GetMapping("/check-attendance-present-status")
 public ResponseEntity<String> checkAttendanceStatus(@RequestParam Long employeeId) {
     // Get today's date in dd/MM/yyyy format
     String todayDate = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
     
     // Fetch the attendance record for today
     List<AttendanceRecord> records = attendanceRecordRepository.findByEmployeeIdAndDate(employeeId, todayDate);
     
     if (!records.isEmpty()) { // Check if the list is not empty
         AttendanceRecord record = records.get(0); // Get the first record
         
         // Check if attendanceStatus is "Present"
         if ("Present".equals(record.getAttendanceStatus())) {
             // Check for missing details step by step
             if (record.getTimeIn() == null || record.getImageIn() == null) {
                 return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body("Time-in and image-in are missing. Please enter them.");
             } else if (record.getTimeOut() == null || record.getImageOut() == null) {
                 return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body("Time-out and image-out are missing. Please enter them.");
             } else if (record.getDayStatus() == null) {
                 return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body("Day status is missing. Please enter it.");
             } else {
                 return ResponseEntity.ok("All details are present.");
             }
         } else {
             return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Attendance status is not 'Present'.");
         }
     } else {
         return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No attendance record found for today.");
     }
 }
@PostMapping("/auto-mark-absent")
public ResponseEntity<?> autoMarkAbsentForMissedAttendance() {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    String todayDate = LocalDate.now().format(formatter);

    List<Employee> allEmployees = employeeRepository.findAll();
    int absentCount = 0;

    for (Employee employee : allEmployees) {
        List<AttendanceRecord> attendanceRecords =
            attendanceRecordRepository.findByEmployeeIdAndDate(employee.getId(), todayDate);

        boolean isAlreadyMarkedToday = !attendanceRecords.isEmpty() && (
            "Present".equalsIgnoreCase(attendanceRecords.get(0).getAttendanceStatus()) ||
            "Absent".equalsIgnoreCase(attendanceRecords.get(0).getAttendanceStatus())
        );

        if (isAlreadyMarkedToday) continue; // Skip if already Present or Absent

        List<LeavePermission> leaves = leavePermissionRepository.findByEmployeeId(employee.getId());
        boolean hasLeaveToday = leaves.stream().anyMatch(leave -> {
            try {
                LocalDate start = LocalDate.parse(leave.getStartDate(), formatter);
                LocalDate end = LocalDate.parse(leave.getEndDate(), formatter);
                LocalDate today = LocalDate.now();
                return !today.isBefore(start) && !today.isAfter(end);
            } catch (Exception e) {
                return false;
            }
        });

        if (!hasLeaveToday) {
            AttendanceRecord absentRecord = new AttendanceRecord();
            absentRecord.setEmployee(employee);
            absentRecord.setAttendanceStatus("Absent");
            absentRecord.setDate(todayDate);
            attendanceRecordRepository.save(absentRecord);
            absentCount++;
        }
    }

    return ResponseEntity.ok(absentCount + " employees marked as absent for today.");
}




 @PostMapping("/mark-absent")
 public ResponseEntity<String> markAbsent(@RequestParam Long employeeId) {
     String todayDate = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")); // or match `dateFormatter`
 
     List<AttendanceRecord> existingRecords = attendanceRecordRepository.findByEmployeeIdAndDate(employeeId, todayDate);
 
     if (!existingRecords.isEmpty()) {
         String status = existingRecords.get(0).getAttendanceStatus();
         return ResponseEntity.badRequest()
                 .body("Attendance already marked as '" + status + "' for today.");
     }
 
     Optional<Employee> employeeOptional = employeeRepository.findById(employeeId);
     if (employeeOptional.isEmpty()) {
         return ResponseEntity.badRequest().body("Employee not found.");
     }
 
     AttendanceRecord attendanceRecord = new AttendanceRecord();
     attendanceRecord.setEmployee(employeeOptional.get());
     attendanceRecord.setAttendanceStatus("Absent");
     attendanceRecord.setDate(todayDate);
 
     attendanceRecordRepository.save(attendanceRecord);
 
     return ResponseEntity.ok("Attendance marked as absent for employee ID: " + employeeId);
 }






//admin

@GetMapping("/attendance-status-today")
public ResponseEntity<Map<String, Object>> getAttendanceStatusForToday() {
    // Get today's date in the desired format (same format used for attendance records)
    String todayDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));  // Update this format based on your DB date format

    // Fetch all employees
    List<Employee> allEmployees = employeeRepository.findAll();

    // Prepare the categories
    List<Employee> presentEmployees = new ArrayList<>();
    List<Employee> absentEmployees = new ArrayList<>();
    List<Employee> notPostedEmployees = new ArrayList<>();

    // Iterate through all employees and check attendance records for today
    for (Employee employee : allEmployees) {
        List<AttendanceRecord> existingRecords = attendanceRecordRepository.findByEmployeeIdAndDate(employee.getId(), todayDate);

        if (existingRecords.isEmpty()) {
            // If no record found for the employee on today's date, mark as "Not Posted"
            notPostedEmployees.add(employee);
        } else {
            // If attendance record exists, check the status
            String status = existingRecords.get(0).getAttendanceStatus();
            if ("Present".equalsIgnoreCase(status)) {
                presentEmployees.add(employee);
            } else if ("Absent".equalsIgnoreCase(status)) {
                absentEmployees.add(employee);
            }
        }
    }

    // Prepare the response object
    Map<String, Object> response = new HashMap<>();
    response.put("presentCount", presentEmployees.size());
    response.put("absentCount", absentEmployees.size());
    response.put("notPostedCount", notPostedEmployees.size());
    
    response.put("presentEmployees", presentEmployees);
    response.put("absentEmployees", absentEmployees);
    response.put("notPostedEmployees", notPostedEmployees);

    return ResponseEntity.ok(response);
}










// newwwwwwww


@GetMapping("/getByDateAndEmployee")
public ResponseEntity<Map<String, Object>> getAttendanceByDateAndEmployeeId(
        @RequestParam String date,
        @RequestParam Long employeeId) {

    return attendanceRecordRepository.findByDateAndEmployeeId(date, employeeId)
            .map(attendance -> {
                Map<String, Object> response = new HashMap<>();
                response.put("employeeId", employeeId);
                response.put("date", attendance.getDate());
                response.put("dayStatus", attendance.getDayStatus());
                response.put("attendanceStatus", attendance.getAttendanceStatus());
                response.put("timeIn", attendance.getTimeIn());
                response.put("timeOut", attendance.getTimeOut());
                return ResponseEntity.ok(response);
            })
            .orElseGet(() -> {
                Map<String, Object> response = new HashMap<>();
                response.put("found", false);
                response.put("employeeId", employeeId);
                response.put("date", date);
                response.put("message", "No record found");
                return ResponseEntity.ok(response);
            });
}




    @GetMapping("/latest/{employeeId}")
    public ResponseEntity<?> getLatestAttendance(@PathVariable Long employeeId) {
        Optional<AttendanceRecord> latestRecord = attendanceService.getLatestAttendanceRecord(employeeId);
        if (latestRecord.isPresent()) {
            return ResponseEntity.ok(latestRecord.get());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("found", false);
        response.put("employeeId", employeeId);
        response.put("message", "No attendance record found.");
        return ResponseEntity.ok(response);
    }
@GetMapping("/latest-today-or-yesterday/{employeeId}")
public ResponseEntity<?> getTodayOrYesterdayAttendance(@PathVariable Long employeeId) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    String today = LocalDate.now().format(formatter);
    String yesterday = LocalDate.now().minusDays(1).format(formatter);

    // Try to get today's record
    List<AttendanceRecord> todayRecords = attendanceRecordRepository.findByEmployeeIdAndDate(employeeId, today);
    if (!todayRecords.isEmpty()) {
        return ResponseEntity.ok(todayRecords.get(0));
    }

    // If not found, try to get yesterday's record
    List<AttendanceRecord> yesterdayRecords = attendanceRecordRepository.findByEmployeeIdAndDate(employeeId, yesterday);
    if (!yesterdayRecords.isEmpty()) {
        return ResponseEntity.ok(yesterdayRecords.get(0));
    }

    Map<String, Object> response = new HashMap<>();
    response.put("found", false);
    response.put("employeeId", employeeId);
    response.put("message", "No attendance record found for today or yesterday.");
    return ResponseEntity.ok(response);
}

    @GetMapping("/employee/{employeeId}")
    public List<AttendanceRecord> getAttendanceByMonthAndYear(
            @PathVariable Long employeeId,
            @RequestParam int month,
            @RequestParam int year) {

        List<AttendanceRecord> records = attendanceRecordRepository.findByEmployeeId(employeeId);
        
        // Filter based on month and year from string `date`
        return records.stream()
                .filter(r -> {
                    try {
                        LocalDate recordDate = LocalDate.parse(r.getDate(), DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                        return recordDate.getMonthValue() == month && recordDate.getYear() == year;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }
@GetMapping("/today-timein")
public ResponseEntity<List<Map<String, Object>>> getTodayTimeInDetails(
        @RequestParam(value = "clientId", required = false) Long clientId) {
    String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    
    List<AttendanceRecord> records = attendanceRecordRepository.findAllTimeInByDate(today);

    // Filter only Present records with non-null Time In
    List<Map<String, Object>> result = records.stream()
        .filter(record -> 
            "Present".equalsIgnoreCase(record.getAttendanceStatus()) &&
            record.getTimeIn() != null &&
            (clientId == null || (record.getEmployee() != null && clientId.equals(record.getEmployee().getClientId())))
        )
        .map(record -> {
            Map<String, Object> map = new HashMap<>();
            map.put("employeeId", record.getEmployee().getId());
            map.put("name", record.getEmployee().getFirstName());
            map.put("branch", record.getEmployee().getBranch());
            map.put("profileImage", record.getEmployee().getProfileImage());
            map.put("timeIn", record.getTimeIn());
            map.put("status", record.getAttendanceStatus());
            return map;
        })
        .collect(Collectors.toList());

    return ResponseEntity.ok(result);
}

@GetMapping("/today-absent")
public List<Map<String, Object>> getTodayAbsent(
        @RequestParam(value = "clientId", required = false) Long clientId) {
    try {
        // Keep today's absent list fresh even when scheduler was missed/restarted.
        attendanceSchedulerService.runAutoAbsentForToday();
    } catch (Exception ex) {
        System.out.println("today-absent auto-sync skipped: " + ex.getMessage());
    }

    List<AttendanceRecord> absentRecords = attendanceService.getTodayAbsentRecords();

    return absentRecords.stream()
    .filter(record -> clientId == null || (record.getEmployee() != null && clientId.equals(record.getEmployee().getClientId())))
    .map(record -> {
        Map<String, Object> map = new HashMap<>();
        map.put("id", record.getEmployee().getId());
        map.put("name", record.getEmployee().getFirstName());
        map.put("branch", record.getEmployee().getBranch());
        map.put("mobile", record.getEmployee().getMobile());
        map.put("date", record.getDate());
        return map;
    }).collect(Collectors.toList());
}
@GetMapping("/today-time-in-late")
public ResponseEntity<List<Map<String, Object>>> getTodayTimeInLateDetails(
        @RequestParam(value = "clientId", required = false) Long clientId) {
    String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    List<AttendanceRecord> records = attendanceRecordRepository.findAllTimeInByDate(today);

    List<Map<String, Object>> result = records.stream()
        .filter(record -> {
            if (record.getTimeIn() != null && record.getEmployee() != null) {
                if (clientId != null && !clientId.equals(record.getEmployee().getClientId())) {
                    return false;
                }
                return attendanceMetricsService.calculateDailyLateMinutes(record) > 0;
            }
            return false;
        })
        .map(record -> {
            Map<String, Object> map = new HashMap<>();
            map.put("employeeId", record.getEmployee().getId());
            map.put("name", record.getEmployee().getFirstName());
            map.put("branch", record.getEmployee().getBranch());
            map.put("profileImage", record.getEmployee().getProfileImage());
            map.put("timeIn", record.getTimeIn());
            map.put("status", record.getAttendanceStatus());
            map.put("lateMinutes", attendanceMetricsService.calculateDailyLateMinutes(record));
            return map;
        })
        .collect(Collectors.toList());

    return ResponseEntity.ok(result);
}
@GetMapping("/late-arrivals")
public ResponseEntity<List<Map<String, Object>>> getLateArrivalsByDate(@RequestParam String date) {
    // Example input: "30/05/2025"
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    LocalDate parsedDate;
    try {
        parsedDate = LocalDate.parse(date, formatter);
    } catch (DateTimeParseException e) {
        return ResponseEntity.badRequest().body(Collections.singletonList(Map.of("error", "Invalid date format. Use dd/MM/yyyy")));
    }

    List<AttendanceRecord> records = attendanceRecordRepository.findAllTimeInByDate(date);

    List<Map<String, Object>> result = records.stream()
        .filter(record -> {
            if (record.getTimeIn() != null && record.getEmployee() != null) {
                return attendanceMetricsService.calculateDailyLateMinutes(record) > 0;
            }
            return false;
        })
        .map(record -> {
            Map<String, Object> map = new HashMap<>();
            map.put("employeeId", record.getEmployee().getId());
            map.put("name", record.getEmployee().getFirstName());
            map.put("branch", record.getEmployee().getBranch());
            map.put("mobile", record.getEmployee().getMobile()); // Optional
            map.put("profileImage", record.getEmployee().getProfileImage()); // Optional
            map.put("timeIn", record.getTimeIn().toString());
            map.put("status", record.getAttendanceStatus());
            map.put("lateMinutes", attendanceMetricsService.calculateDailyLateMinutes(record));
            return map;
        })
        .collect(Collectors.toList());

    return ResponseEntity.ok(result);
}


  @GetMapping("/summary")
public Map<String, Object> getDashboardSummary(
        @RequestParam(value = "clientId", required = false) Long clientId) {
    Map<String, Object> summary = new HashMap<>();

    long totalEmployees = clientId == null ? employeeRepository.count() : employeeRepository.countByClientId(clientId);
    summary.put("totalEmployees", totalEmployees);

    LocalDate today = LocalDate.now();
    LocalDate yesterday = today.minusDays(1);

    List<AttendanceRecord> todayRecords = findRecordsByDateFlexible(today);
    List<AttendanceRecord> yesterdayRecords = findRecordsByDateFlexible(yesterday);

    if (clientId != null) {
        todayRecords = todayRecords.stream()
                .filter(a -> a.getEmployee() != null && clientId.equals(a.getEmployee().getClientId()))
                .collect(Collectors.toList());
        yesterdayRecords = yesterdayRecords.stream()
                .filter(a -> a.getEmployee() != null && clientId.equals(a.getEmployee().getClientId()))
                .collect(Collectors.toList());
    }

    // Today stats
    long todayPresent = todayRecords.stream().filter(a -> "Present".equalsIgnoreCase(a.getAttendanceStatus())).count();
    long todayAbsent = todayRecords.stream().filter(a -> "Absent".equalsIgnoreCase(a.getAttendanceStatus())).count();
    long todayLate = todayRecords.stream()
            .filter(a -> attendanceMetricsService.calculateDailyLateMinutes(a) > 0)
            .count();
    long todayOnTime = todayPresent - todayLate;
    int todayLateMinutes = todayRecords.stream()
            .mapToInt(attendanceMetricsService::calculateDailyLateMinutes)
            .sum();

    // Yesterday stats
    long yesterdayPresent = yesterdayRecords.stream().filter(a -> "Present".equalsIgnoreCase(a.getAttendanceStatus())).count();
    long yesterdayAbsent = yesterdayRecords.stream().filter(a -> "Absent".equalsIgnoreCase(a.getAttendanceStatus())).count();
    long yesterdayLate = yesterdayRecords.stream()
            .filter(a -> attendanceMetricsService.calculateDailyLateMinutes(a) > 0)
            .count();
    long yesterdayOnTime = yesterdayPresent - yesterdayLate;
    int yesterdayLateMinutes = yesterdayRecords.stream()
            .mapToInt(attendanceMetricsService::calculateDailyLateMinutes)
            .sum();

    // Percentage comparisons (today - yesterday) / yesterday * 100
    summary.put("presentToday", todayPresent);
    summary.put("absentToday", todayAbsent);
    summary.put("lateArrivalsToday", todayLate);
    summary.put("lateMinutesToday", todayLateMinutes);
    summary.put("onTimeToday", todayOnTime);

    summary.put("presentYesterday", yesterdayPresent);
    summary.put("absentYesterday", yesterdayAbsent);
    summary.put("lateArrivalsYesterday", yesterdayLate);
    summary.put("lateMinutesYesterday", yesterdayLateMinutes);
    summary.put("onTimeYesterday", yesterdayOnTime);

    summary.put("absentChangePercent", calculatePercentageChange(yesterdayAbsent, todayAbsent));
    summary.put("lateChangePercent", calculatePercentageChange(yesterdayLate, todayLate));
    summary.put("onTimeChangePercent", calculatePercentageChange(yesterdayOnTime, todayOnTime));

    return summary;
}

private List<AttendanceRecord> findRecordsByDateFlexible(LocalDate date) {
    if (date == null) {
        return new ArrayList<>();
    }
    List<String> candidates = List.of(
            date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
            date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
    );
    Map<Long, AttendanceRecord> unique = new LinkedHashMap<>();
    for (String dateValue : candidates) {
        List<AttendanceRecord> records = attendanceRecordRepository.findByDate(dateValue);
        for (AttendanceRecord record : records) {
            if (record != null && record.getId() != null) {
                unique.putIfAbsent(record.getId(), record);
            }
        }
    }
    return new ArrayList<>(unique.values());
}

private double calculatePercentageChange(long oldValue, long newValue) {
    if (oldValue == 0 && newValue == 0) return 0.0;
    if (oldValue == 0) return 100.0; // from 0 to something = 100% increase
    return ((double) (newValue - oldValue) / oldValue) * 100;
}

//////////////////////filter
@GetMapping("/monthly-summary")
public ResponseEntity<?> getEmployeeMonthlySummary(
        @RequestParam String employeeId,
        @RequestParam(value = "clientId", required = false) Long clientId,
        @RequestParam int month,
        @RequestParam int year) {

    Optional<Employee> employeeOpt = resolveEmployeeByRef(employeeId, clientId);
    if (employeeOpt.isEmpty()) {
        Map<String, String> error = new HashMap<>();
        error.put("message", "Employee not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    Employee employee = employeeOpt.get();
    Long resolvedEmployeeId = employee.getId();

    AttendanceMetricsService.MonthlyMetrics metrics =
            attendanceMetricsService.calculateMonthlyMetrics(resolvedEmployeeId, month, year);

    // Total days in the month
    int daysInMonth = java.time.YearMonth.of(year, month).lengthOfMonth();

    // Prepare response
    Map<String, Object> response = new HashMap<>();
    response.put("employeeId", resolvedEmployeeId);
    response.put("firstName", employee.getFirstName());
    response.put("lastName", employee.getLastName());
    response.put("branch", employee.getBranch());
    response.put("position", employee.getPosition());
    response.put("mobile", employee.getMobile());
    response.put("dob", employee.getDob());
    response.put("email", employee.getEmail());
    response.put("address", employee.getAddress());
    response.put("salary", employee.getSalary());
    response.put("absentCount", metrics.absentCount());
    response.put("presentCount", metrics.presentCount());
    response.put("workingDays", metrics.presentCount());
    response.put("lateDays", metrics.monthlyLateDays());
    response.put("totalLateMinutes", metrics.monthlyLateMinutes());
    response.put("totalEarlyOutMinutes", metrics.monthlyEarlyOutMinutes());
    response.put("totalMissedTimes", metrics.totalMissedMinutes());
    response.put("permissionCount", metrics.approvedPermissionCount());
    response.put("totalApprovedPermissionsTaken", metrics.approvedPermissionCount());
    response.put("approvedPermissionCount", metrics.approvedPermissionCount());
    response.put("approvedPermissionMinutes", metrics.approvedPermissionMinutes());
    response.put("maxPermissionsPerMonth", attendanceMetricsService.resolveMaxApprovedPermissionsPerMonth(employee));
    response.put("maxPermissionMinutesPerMonth", attendanceMetricsService.resolveMaxApprovedPermissionMinutesPerMonth(employee));
    response.put("daysInMonth", daysInMonth);

    return ResponseEntity.ok(response);
}

private Optional<Employee> resolveEmployeeByRef(String employeeRef, Long clientId) {
    if (employeeRef == null || employeeRef.isBlank()) {
        return Optional.empty();
    }

    String normalized = employeeRef.trim();

    try {
        Long id = Long.parseLong(normalized);
        return clientId == null
                ? employeeRepository.findById(id)
                : employeeRepository.findByIdAndClientId(id, clientId);
    } catch (NumberFormatException ignored) {
        // Continue with employee-code lookup.
    }

    Optional<Employee> byCode = employeeRepository.findByEmployeeCode(normalized.toUpperCase());
    if (byCode.isPresent()
            && (clientId == null || clientId.equals(byCode.get().getClientId()))) {
        return byCode;
    }

    Matcher matcher = Pattern.compile("(?i)(?:^|\\.)EMP(\\d+)$").matcher(normalized);
    if (matcher.find()) {
        try {
            Long id = Long.parseLong(matcher.group(1));
            return clientId == null
                    ? employeeRepository.findById(id)
                    : employeeRepository.findByIdAndClientId(id, clientId);
        } catch (NumberFormatException ignored) {
            // Keep empty below.
        }
    }

    return Optional.empty();
}

private LocalTime parseShiftEnd(String raw) {
    if (raw == null || raw.isBlank()) {
        return DEFAULT_SHIFT_END;
    }
    try {
        return LocalTime.parse(raw.trim(), TIME_FORMATTER_HH_MM_SS);
    } catch (DateTimeParseException ignored) {
        // Fallback below
    }
    try {
        return LocalTime.parse(raw.trim(), TIME_FORMATTER_HH_MM);
    } catch (DateTimeParseException ignored) {
        return DEFAULT_SHIFT_END;
    }
}


}
