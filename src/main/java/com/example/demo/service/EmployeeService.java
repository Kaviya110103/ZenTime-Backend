package com.example.demo.service;

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

    @Autowired
    private AttendanceMetricsService attendanceMetricsService;

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
        return companyCode.trim().toLowerCase();
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

    public Optional<Employee> getEmployeeByIdAndClientId(Long id, Long clientId) {
        return employeeRepository.findByIdAndClientId(id, clientId);
    }

    // Get by Username
    public Optional<Employee> getEmployeeByUsername(String username) {
        return employeeRepository.findByUsername(username);
    }

    // Get all employees
    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }

    public List<Employee> getEmployeesByClientId(Long clientId) {
        return employeeRepository.findByClientId(clientId);
    }

    // Delete by ID
    public void deleteEmployeeById(Long id) {
        employeeRepository.deleteById(id);
    }

    public void updateMissedTimes(AttendanceRecord record) {
        int missedMinutes = attendanceMetricsService.calculateDailyMissedMinutes(record);
        record.setMissedtimes(missedMinutes);
        attendanceRecordRepository.save(record);
    }

    public EmployeeNetPayment calculateNetPayment(Employee employee, int month, int year, int paidLeaveDayCount,
            int casualLeaveDayCount, int holidayCount, String paidLeaveType, int presentDays) {
        int totalPaidLeaveCount = paidLeaveDayCount + casualLeaveDayCount + holidayCount;

        YearMonth yearMonth = YearMonth.of(year, month);
        int totalDaysInMonth = yearMonth.lengthOfMonth();

        int totalWorkingDays = totalDaysInMonth - totalPaidLeaveCount;

        int safeTotalWorkingDays = Math.max(totalWorkingDays, 1);
        double baseNetSalary = ((double) presentDays / safeTotalWorkingDays) * employee.getSalary();
        AttendanceMetricsService.MonthlyMetrics metrics =
                attendanceMetricsService.calculateMonthlyMetrics(employee.getId(), month, year);
        int shiftMinutesPerDay = attendanceMetricsService.resolveShiftDurationMinutes(employee);
        double perDaySalary = employee.getSalary() / totalDaysInMonth;
        double missedTimeDeduction = (perDaySalary / shiftMinutesPerDay) * metrics.totalMissedMinutes();
        double netSalary = baseNetSalary - missedTimeDeduction;
        if (netSalary < 0) {
            netSalary = 0;
        }

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
