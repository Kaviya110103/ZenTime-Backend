package com.example.demo.controller;

import com.example.demo.MODELS.AttendanceRecord;
import com.example.demo.MODELS.Employee;
import com.example.demo.MODELS.LeavePermission;
import com.example.demo.repo.AttendanceRecordRepository;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.repo.LeavePermissionRepository;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private AttendanceRecordRepository attendanceRecordRepository;
        private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @CrossOrigin(origins = "http://127.0.0.1:5500")
    @PostMapping("/start-day")
    public ResponseEntity<String> startDay(@RequestParam Long employeeId) {
        // Format today's date as dd/MM/yyyy
        String todayDate = LocalDate.now().format(dateFormatter);
    
        // Check if an attendance record already exists for this employee on today's date
        List<AttendanceRecord> existingRecords = attendanceRecordRepository.findByEmployeeIdAndDate(employeeId, todayDate);
    
        if (!existingRecords.isEmpty()) {
            String status = existingRecords.get(0).getAttendanceStatus();
            return ResponseEntity.badRequest()
                    .body("Attendance already marked as '" + status + "' for today.");
        }
    
        // Fetch the employee from the database
        Optional<Employee> employeeOptional = employeeRepository.findById(employeeId);
        if (employeeOptional.isEmpty()) {
            return ResponseEntity.badRequest().body("Employee not found.");
        }
    
        // Create a new attendance record for present status
        AttendanceRecord record = new AttendanceRecord();
        record.setEmployee(employeeOptional.get());
        record.setAttendanceStatus("Present");
        record.setDate(todayDate);
    
        // Save the attendance record
        attendanceRecordRepository.save(record);
    
        return ResponseEntity.ok("Day started. Please mark time-in.");
    }
    
    // Endpoint to mark time-in with image
    @PostMapping("/mark-time-in")     // worked
    public ResponseEntity<String> markTimeIn(
            @RequestParam Long recordId,
            @RequestParam MultipartFile imageIn) {
        Optional<AttendanceRecord> optionalRecord = attendanceRecordRepository.findById(recordId);
        if (optionalRecord.isPresent()) {
            AttendanceRecord record = optionalRecord.get();
            try {
                record.setTimeIn(LocalDateTime.now());
                if (imageIn != null && !imageIn.isEmpty()) {
                    record.setImageIn(imageIn.getBytes());
                }
                attendanceRecordRepository.save(record);
                return ResponseEntity.ok("Time-in recorded successfully.");
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to process image.");
            }
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Attendance record not found.");
        }
    }

    // Endpoint to mark time-out with image
    @PostMapping("/mark-time-out")  // worked
    public ResponseEntity<String> markTimeOut(
            @RequestParam Long recordId,
            @RequestParam MultipartFile imageOut) {
        Optional<AttendanceRecord> optionalRecord = attendanceRecordRepository.findById(recordId);
        if (optionalRecord.isPresent()) {
            AttendanceRecord record = optionalRecord.get();
            try {
                record.setTimeOut(LocalDateTime.now());
                if (imageOut != null && !imageOut.isEmpty()) {
                    record.setImageOut(imageOut.getBytes());
                }
                attendanceRecordRepository.save(record);
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
    

    @GetMapping("/api/attendance/{recordId}/time-in")
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
                Map<String, Object> error = new HashMap<>();
                error.put("error", "No record found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            });
}






 
}
