package com.example.demo.service;

import com.example.demo.MODELS.AttendanceRecord;
import com.example.demo.MODELS.AttendanceRecordDTO;
import com.example.demo.MODELS.Employee;
import com.example.demo.repo.AttendanceRecordRepository;
import com.example.demo.repo.EmployeeRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class AttendanceService {

    @Autowired
    private AttendanceRecordRepository attendanceRepo;

    @Autowired
    private EmployeeRepository employeeRepo;

    public Optional<AttendanceRecord> getLatestAttendanceRecord(Long employeeId) {
        Optional<Employee> emp = employeeRepo.findById(employeeId);
        if (emp.isEmpty()) return Optional.empty();

        return attendanceRepo.findLatestByEmployee(emp.get());
    }

    
    
public List<AttendanceRecord> getTodayAbsentRecords() {
    String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    return attendanceRepo.findTodayAbsent(today);
}



public List<AttendanceRecord> getMonthlyAttendanceForEmployee(Long employeeId) {
    throw new UnsupportedOperationException("Unimplemented method 'getMonthlyAttendanceForEmployee'");
}

}
