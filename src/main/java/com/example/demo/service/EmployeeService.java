package com.example.demo.service;

import java.time.Duration;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
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
        if (employee.getId() == null) {
            String companyCode = normalizeCompanyCode(employee.getCompanyCode());
            employee.setCompanyCode(companyCode);
            employee.setEmployeeCode(normalizeOptionalEmployeeCode(employee.getEmployeeCode()));

            Employee saved = employeeRepository.save(employee);
            if (saved.getEmployeeCode() == null || saved.getEmployeeCode().isBlank()) {
                saved.setEmployeeCode(buildDefaultEmployeeCode(saved.getCompanyCode(), saved.getId()));
                return employeeRepository.save(saved);
            }
            return saved;
        }

        employee.setEmployeeCode(normalizeOptionalEmployeeCode(employee.getEmployeeCode()));
        return employeeRepository.save(employee);
    }

    private String normalizeCompanyCode(String companyCode) {
        if (companyCode == null || companyCode.isBlank()) {
            throw new IllegalArgumentException("companyCode is required to create employee code");
        }
        return companyCode.trim().toUpperCase();
    }

    private String normalizeOptionalEmployeeCode(String employeeCode) {
        if (employeeCode == null) {
            return null;
        }
        String trimmed = employeeCode.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toUpperCase();
    }

    private String buildDefaultEmployeeCode(String companyCode, Long id) {
        return companyCode + ".EMP" + id;
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
        LocalTime allowedStart = resolveShiftStart(record);
        LocalTime allowedEnd = resolveShiftEnd(record);

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

    private LocalTime resolveShiftStart(AttendanceRecord record) {
        if (record != null && record.getEmployee() != null) {
            String shiftStart = record.getEmployee().getShiftStartTime();
            if (shiftStart != null && !shiftStart.isBlank()) {
                try {
                    return LocalTime.parse(shiftStart.trim());
                } catch (DateTimeParseException ignored) {
                    // Fallback to current default if bad data exists.
                }
            }
        }
        return LocalTime.of(10, 0);
    }

    private LocalTime resolveShiftEnd(AttendanceRecord record) {
        if (record != null && record.getEmployee() != null) {
            String shiftEnd = record.getEmployee().getShiftEndTime();
            if (shiftEnd != null && !shiftEnd.isBlank()) {
                try {
                    return LocalTime.parse(shiftEnd.trim());
                } catch (DateTimeParseException ignored) {
                    // Fallback to current default if bad data exists.
                }
            }
        }
        return LocalTime.of(19, 0);
    }

    public EmployeeNetPayment calculateNetPayment(Employee employee, int month, int year, int paidLeaveDayCount,
            int casualLeaveDayCount, int holidayCount, String paidLeaveType, int presentDays) {
        int totalPaidLeaveCount = paidLeaveDayCount + casualLeaveDayCount + holidayCount;

        YearMonth yearMonth = YearMonth.of(year, month);
        int totalDaysInMonth = yearMonth.lengthOfMonth();

        int totalWorkingDays = totalDaysInMonth - totalPaidLeaveCount;

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
