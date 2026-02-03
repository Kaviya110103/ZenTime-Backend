package com.example.demo.service;


import java.time.Duration;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.MODELS.AttendanceRecord;
import com.example.demo.MODELS.Employee;
import com.example.demo.MODELS.EmployeeNetPayment;
import com.example.demo.repo.AttendanceRecordRepository;
import com.example.demo.repo.EmployeeRepository;


@Service
public class EmployeeService {


    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;


    // Create or Update
    public Employee saveEmployee(Employee employee) {
        return employeeRepository.save(employee);
    }


    // Get by ID
    public Optional<Employee> getEmployeeById(Long id) {
        return employeeRepository.findById(id);
    }


    // Get by Username
    public Optional<Employee> getEmployeeByUsername(String username) {
        return employeeRepository.findByUsername(username);
    }


    // Get all employees
    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }


    // Delete by ID
    public void deleteEmployeeById(Long id) {
        employeeRepository.deleteById(id);
    }
    public void updateMissedTimes(AttendanceRecord record) {
        LocalTime allowedStart = LocalTime.of(10, 0); // 10:00 AM
        LocalTime allowedEnd = LocalTime.of(19, 0);   // 7:00 PM
    
        int missedMinutes = 0;
    
        if (record.getTimeIn() != null) {
            LocalTime timeIn = record.getTimeIn().toLocalTime();
            if (timeIn.isAfter(allowedStart)) {
                missedMinutes += Duration.between(allowedStart, timeIn).toMinutes();
            }
        }
    
        if (record.getTimeOut() != null) {
            LocalTime timeOut = record.getTimeOut().toLocalTime();
            if (timeOut.isBefore(allowedEnd)) {
                missedMinutes += Duration.between(timeOut, allowedEnd).toMinutes();
            }
        }
    
        record.setMissedtimes(missedMinutes);
        attendanceRecordRepository.save(record);
    }
    // Example service method (not a full implementation)
public EmployeeNetPayment calculateNetPayment(Employee employee, int month, int year, int paidLeaveDayCount, int casualLeaveDayCount, int holidayCount, String paidLeaveType, int presentDays) {
    int totalPaidLeaveCount = paidLeaveDayCount + casualLeaveDayCount + holidayCount;

    // Get total days in month
    YearMonth yearMonth = YearMonth.of(year, month);
    int totalDaysInMonth = yearMonth.lengthOfMonth();

    // Calculate total working days (excluding paid leaves)
    int totalWorkingDays = totalDaysInMonth - totalPaidLeaveCount;

    // Calculate net salary
    double netSalary = ((double) presentDays / totalWorkingDays) * employee.getSalary();

    EmployeeNetPayment payment = new EmployeeNetPayment();
    payment.setEmployee(employee);
    payment.setBranch(employee.getBranch());
    payment.setSalary(employee.getSalary());
    payment.setWeekOff(employee.getWeekOff());
    payment.setPaidLeaveDayCount(paidLeaveDayCount);
    payment.setCasualLeaveDayCount(casualLeaveDayCount);
    payment.setHolidayCount(holidayCount);
    payment.setPaidLeaveType(paidLeaveType);
    payment.setTotalPaidLeaveCount(totalPaidLeaveCount);
    payment.setTotalWorkingDays(totalWorkingDays);
    payment.setPresentDays(presentDays);
    payment.setNetSalary(netSalary);
    payment.setMonth(month);
    payment.setYear(year);

    return payment;
}

}
