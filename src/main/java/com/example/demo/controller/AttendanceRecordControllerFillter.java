package com.example.demo.controller;

import java.time.Duration;
import java.time.LocalTime;
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
import com.example.demo.repo.AttendanceRecordRepository;

@RestController
@RequestMapping("/api/attendance-records")
@CrossOrigin(origins = "*")

public class AttendanceRecordControllerFillter {

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    // 1. Get all attendance details
    @GetMapping("/all")
    public List<AttendanceRecord> getAllAttendanceRecords() {
        return attendanceRecordRepository.findAll();
    }

    // 2. Get attendance details by employee ID
    @GetMapping("/by-employee")
    public List<AttendanceRecord> getAttendanceByEmployeeId(@RequestParam Long employeeId) {
        return attendanceRecordRepository.findByEmployeeId(employeeId);
    }

    // 3. Get attendance details by month and employee ID
    @GetMapping("/by-employee-month")
    public List<AttendanceRecord> getAttendanceByEmployeeIdAndMonth(
            @RequestParam Long employeeId,
            @RequestParam int month,
            @RequestParam int year) {
        String monthPattern = String.format("/%02d/%d", month, year);
        return attendanceRecordRepository.findByEmployeeIdAndMonthPattern(employeeId, monthPattern);
    }

    // 4. Get attendance details by date and employee ID
    @GetMapping("/by-employee-date")
    public ResponseEntity<?> getAttendanceByEmployeeIdAndDate(
            @RequestParam Long employeeId,
            @RequestParam String date) {
        return attendanceRecordRepository.findByDateAndEmployeeId(date, employeeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 5. Get attendance details by employee branch
    @GetMapping("/by-branch")
    public List<AttendanceRecord> getAttendanceByBranch(@RequestParam String branch) {
        return attendanceRecordRepository.findByEmployeeBranch(branch);
    }

    // 6. Get all details by particular month and particular date for all employees
    @GetMapping("/by-month-date")
    public List<AttendanceRecord> getAttendanceByMonthAndDate(
            @RequestParam int month,
            @RequestParam int year,
            @RequestParam String date) {
        String monthPattern = String.format("/%02d/%d", month, year);
        return attendanceRecordRepository.findByMonthPatternAndDate(monthPattern, date);
    }

    // 7. Get all attendance details by date and attendance status (Present/Absent)
    @GetMapping("/by-date-status")
    public List<AttendanceRecord> getAttendanceByDateAndStatus(
            @RequestParam String date,
            @RequestParam String attendanceStatus) {
        return attendanceRecordRepository.findByDateAndAttendanceStatus(date, attendanceStatus);
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
        // Calculate missedTimes in minutes (example: difference between timeIn and timeOut)
        long minutesWorked = java.time.Duration.between(record.getTimeIn(), record.getTimeOut()).toMinutes();
        // Example: expected working minutes per day is 8 hours = 480 minutes
        int expectedMinutes = 480;
        int missedTimes = (int) Math.max(0, expectedMinutes - minutesWorked);
        record.setMissedTimes(missedTimes);
        attendanceRecordRepository.save(record);
        return ResponseEntity.ok("Missed times calculated and updated: " + missedTimes + " minutes.");
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
            // Expected times
            LocalTime expectedIn = LocalTime.of(10, 0);  // 10:00 AM
            LocalTime expectedOut = LocalTime.of(19, 0); // 7:00 PM

            LocalTime actualIn = record.getTimeIn().toLocalTime();
            LocalTime actualOut = record.getTimeOut().toLocalTime();

            // Minutes late after 10:00
            long lateMinutes = 0;
            if (actualIn.isAfter(expectedIn)) {
                lateMinutes = Duration.between(expectedIn, actualIn).toMinutes();
            }

            // Minutes early before 19:00
            long earlyMinutes = 0;
            if (actualOut.isBefore(expectedOut)) {
                earlyMinutes = Duration.between(actualOut, expectedOut).toMinutes();
            }

            int missedTimes = (int) (lateMinutes + earlyMinutes);
            record.setMissedTimes(missedTimes);
            updatedCount++;
        }
    }
    attendanceRecordRepository.saveAll(records);
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
    String monthPattern = String.format("/%02d/%d", month, year);
    return attendanceRecordRepository.findByAttendanceStatusAndMonthPattern(attendanceStatus, monthPattern);
}

@GetMapping("/by-month-status-branch")
public List<AttendanceRecord> getAttendanceByMonthStatusBranch(
        @RequestParam int month,
        @RequestParam int year,
        @RequestParam String attendanceStatus,
        @RequestParam String branch) {
    String monthPattern = String.format("/%02d/%d", month, year);
    return attendanceRecordRepository.findByMonthStatusBranch(monthPattern, attendanceStatus, branch);
}



@GetMapping("/check-today-attendance")
public ResponseEntity<?> checkTodayAttendance(@RequestParam Long employeeId) {
    String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    List<AttendanceRecord> records = attendanceRecordRepository.findByEmployeeIdAndDate(employeeId, today);

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
    return attendanceRecordRepository.findByDate(date);
}
@GetMapping("/by-date-status-all")
public List<AttendanceRecord> getAttendanceByDateAndStatusAll(
        @RequestParam String date,
        @RequestParam String attendanceStatus) {
    return attendanceRecordRepository.findByDateAndAttendanceStatus(date, attendanceStatus);
}
// Get all ABSENT employees for a particular date
@GetMapping("/absent-by-date")
public List<AttendanceRecord> getAbsentByDate(
        @RequestParam String date) {
    return attendanceRecordRepository
            .findByDateAndAttendanceStatus(date, "Absent");
}























































}