package com.example.demo.service;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.math.RoundingMode;

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

    @Autowired
    private PayrollCalculationService payrollCalculationService;

    @Autowired
    private SchemaMaintenanceService schemaMaintenanceService;

    // Create or Update
    public Employee saveEmployee(Employee employee) {
        schemaMaintenanceService.ensureEmployeeSchema();
        employee.setSalary(normalizeSalary(employee.getSalary()));
        if (employee.getAdditionalWorkingDays() != null) {
            for (com.example.demo.MODELS.EmployeeAdditionalWorkingDay day : employee.getAdditionalWorkingDays()) {
                day.setEmployee(employee);
            }
        }
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

    private Double normalizeSalary(Double salary) {
        if (salary == null) {
            return null;
        }
        return BigDecimal.valueOf(salary)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    // Get by ID
    public Optional<Employee> getEmployeeById(Long id) {
        schemaMaintenanceService.ensureEmployeeSchema();
        return employeeRepository.findById(id);
    }

    public Optional<Employee> getEmployeeByIdAndClientId(Long id, Long clientId) {
        schemaMaintenanceService.ensureEmployeeSchema();
        return employeeRepository.findByIdAndClientId(id, clientId);
    }

    // Get by Username
    public Optional<Employee> getEmployeeByUsername(String username) {
        schemaMaintenanceService.ensureEmployeeSchema();
        return employeeRepository.findByUsername(username);
    }

    // Get all employees
    public List<Employee> getAllEmployees() {
        schemaMaintenanceService.ensureEmployeeSchema();
        return employeeRepository.findAll();
    }

    public List<Employee> getEmployeesByClientId(Long clientId) {
        schemaMaintenanceService.ensureEmployeeSchema();
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
        PayrollCalculationService.PayrollResult result =
                payrollCalculationService.calculateMonthlyPayroll(employee.getId(), month, year);

        int totalPaidLeaveCount = result.paidLeaveDays()
                + result.paidCasualDays()
                + result.holidayDaysFull()
                + result.holidayDaysHalf();

        int totalWorkingDays = result.scheduledDays();
        double netSalary = result.netSalary();

        EmployeeNetPayment payment = new EmployeeNetPayment();
        payment.setEmployee(employee);
        payment.setBranch(employee.getBranch());
        payment.setSalary(employee.getSalary());
        payment.setWeekOff(employee.getWeekOff());
        payment.setPaidLeaveDayCount(result.paidLeaveDays());
        payment.setCasualLeaveDayCount(result.paidCasualDays());
        payment.setHolidayCount(result.holidayDaysFull() + result.holidayDaysHalf());
        payment.setPaidLeaveType(paidLeaveType);
        payment.setTotalPaidLeaveCount(totalPaidLeaveCount);
        payment.setTotalWorkingDays(totalWorkingDays);
        payment.setPresentDays(result.workedDays());
        payment.setNetSalary(netSalary);
        payment.setMonth(month);
        payment.setYear(year);

        return payment;
    }
}
