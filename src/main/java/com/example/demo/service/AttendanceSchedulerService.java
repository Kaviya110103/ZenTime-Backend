package com.example.demo.service;


import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.demo.MODELS.AttendanceRecord;
import com.example.demo.MODELS.Employee;
import com.example.demo.MODELS.LeavePermission;
import com.example.demo.repo.AttendanceRecordRepository;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.repo.LeavePermissionRepository;

@Service
public class AttendanceSchedulerService {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private LeavePermissionRepository leavePermissionRepository;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

   // 🕐 Scheduled to run every day at 3:00 PM
@Scheduled(cron = "0 0 15 * * ?")
public void autoMarkAbsentAt3PM() {
    String todayDate = LocalDate.now().format(formatter);

    List<Employee> allEmployees = employeeRepository.findAll();
    int absentCount = 0;

    for (Employee employee : allEmployees) {
        List<AttendanceRecord> attendanceRecords =
                attendanceRecordRepository.findByEmployeeIdAndDate(employee.getId(), todayDate);

        boolean isPresent = !attendanceRecords.isEmpty() &&
                "Present".equalsIgnoreCase(attendanceRecords.get(0).getAttendanceStatus());

        if (isPresent) continue;

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

    System.out.println(absentCount + " employees auto-marked as Absent at 3:00 PM.");
}

}
