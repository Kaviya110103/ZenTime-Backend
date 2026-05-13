package com.example.demo.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.math.BigDecimal;
import java.math.RoundingMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.MODELS.AttendanceRecord;
import com.example.demo.MODELS.Employee;
import com.example.demo.MODELS.EmployeeNetPayment;
import com.example.demo.repo.AttendanceRecordRepository;
import com.example.demo.repo.EmployeeAdditionalWorkingDayRepository;
import com.example.demo.repo.EmployeeNetPaymentRepository;
import com.example.demo.repo.EmployeePushTokenRepository;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.repo.EmployeeSalaryDetailsRepository;
import com.example.demo.repo.LeavePermissionRepository;
import com.example.demo.repo.LocationRequestRepository;
import com.example.demo.repo.OvertimeRequestRepository;

@Service
public class EmployeeService {
    private static final Logger logger = LoggerFactory.getLogger(EmployeeService.class);
    private static final List<String> EMPLOYEE_CHILD_TABLE_PRIORITY = List.of(
            "leave_permission",
            "location_requests",
            "overtime_request",
            "employee_push_tokens",
            "employee_net_payment",
            "employee_additional_working_day",
            "attendance_record"
    );

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

    @Autowired
    private LeavePermissionRepository leavePermissionRepository;

    @Autowired
    private EmployeeAdditionalWorkingDayRepository employeeAdditionalWorkingDayRepository;

    @Autowired
    private OvertimeRequestRepository overtimeRequestRepository;

    @Autowired
    private LocationRequestRepository locationRequestRepository;

    @Autowired
    private EmployeePushTokenRepository employeePushTokenRepository;

    @Autowired
    private EmployeeNetPaymentRepository employeeNetPaymentRepository;

    @Autowired
    private EmployeeSalaryDetailsRepository employeeSalaryDetailsRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    @Transactional
    public void deleteEmployeeById(Long id) {
        schemaMaintenanceService.ensureEmployeeSchema();

        // Remove children that can reference attendance records for this employee.
        safeDeleteByAttendanceJoinIfPresent("location_requests", "attendance_record_id", id);
        safeDeleteByAttendanceJoinIfPresent("leave_permission", "attendance_record_id", id);

        // Remove direct employee-linked rows.
        safeDeleteByEmployeeIdIfPresent("location_requests", "employee_id", id);
        safeDeleteByEmployeeIdIfPresent("leave_permission", "employee_id", id);
        safeDeleteByEmployeeIdIfPresent("overtime_request", "employee_id", id);
        safeDeleteByEmployeeIdIfPresent("employee_push_tokens", "employee_id", id);
        safeDeleteByEmployeeIdIfPresent("employee_net_payment", "employee_id", id);
        safeDeleteByEmployeeIdIfPresent("employee_additional_working_day", "employee_id", id);
        safeDeleteByEmployeeIdIfPresent("attendance_record", "employee_id", id);

        // Compatibility cleanup for legacy schemas that store employeeId without FK.
        safeDeleteByEmployeeIdIfPresent("employee_salary_details", "employee_id", id);

        int deleted = jdbcTemplate.update(
                "DELETE FROM " + quoteIdentifier("employee") + " WHERE " + quoteIdentifier("id") + " = ?",
                id);
        if (deleted == 0) {
            throw new EmptyResultDataAccessException("Employee not found with id " + id, 1);
        }
    }

    private void safeDeleteByEmployeeIdIfPresent(String tableName, String employeeIdColumn, Long employeeId) {
        String sql = "DELETE FROM " + quoteIdentifier(tableName)
                + " WHERE " + quoteIdentifier(employeeIdColumn) + " = ?";
        try {
            jdbcTemplate.update(sql, employeeId);
        } catch (BadSqlGrammarException ex) {
            logger.debug("Skipping cleanup for missing/incompatible table {} while deleting employeeId={}",
                    tableName, employeeId);
        } catch (DataAccessException ex) {
            logger.warn("Failed cleanup in table {} for employeeId={}", tableName, employeeId, ex);
            throw ex;
        }
    }

    private void safeDeleteByAttendanceJoinIfPresent(String childTable, String attendanceFkColumn, Long employeeId) {
        String sql = "DELETE FROM " + quoteIdentifier(childTable)
                + " WHERE " + quoteIdentifier(attendanceFkColumn) + " IN ("
                + "SELECT " + quoteIdentifier("id")
                + " FROM " + quoteIdentifier("attendance_record")
                + " WHERE " + quoteIdentifier("employee_id") + " = ?)";
        try {
            jdbcTemplate.update(sql, employeeId);
        } catch (BadSqlGrammarException ex) {
            logger.debug("Skipping attendance-linked cleanup for missing/incompatible table {} while deleting employeeId={}",
                    childTable, employeeId);
        } catch (DataAccessException ex) {
            logger.warn("Failed attendance-linked cleanup in table {} for employeeId={}", childTable, employeeId, ex);
            throw ex;
        }
    }

    private void purgeDirectEmployeeReferences(Long employeeId) {
        List<FkReference> references = findFkReferencesTo("employee", "id");
        if (references.isEmpty()) {
            return;
        }

        references.sort(Comparator
                .comparingInt((FkReference ref) -> tablePriority(ref.tableName))
                .thenComparing(ref -> ref.tableName)
                .thenComparing(ref -> ref.columnName));

        Map<String, Set<String>> tableColumns = new LinkedHashMap<>();
        for (FkReference reference : references) {
            tableColumns.computeIfAbsent(reference.tableName, key -> new LinkedHashSet<>())
                    .add(reference.columnName);
        }

        Set<String> pendingTables = new LinkedHashSet<>(tableColumns.keySet());
        DataIntegrityViolationException lastFailure = null;
        int maxPasses = pendingTables.size() + 3;

        for (int pass = 0; pass < maxPasses && !pendingTables.isEmpty(); pass++) {
            boolean progressed = false;
            for (String tableName : new ArrayList<>(pendingTables)) {
                try {
                    deleteByEmployeeColumns(tableName, tableColumns.get(tableName), employeeId);
                    pendingTables.remove(tableName);
                    progressed = true;
                } catch (DataIntegrityViolationException ex) {
                    lastFailure = ex;
                    logger.debug(
                            "Deferring cleanup for table {} while deleting employeeId={} due to nested FK dependency",
                            tableName, employeeId);
                }
            }
            if (!progressed) {
                break;
            }
        }

        if (!pendingTables.isEmpty()) {
            throw new DataIntegrityViolationException(
                    "Unable to delete dependent rows for employeeId=" + employeeId
                            + ". Pending FK tables: " + pendingTables,
                    lastFailure);
        }
    }

    private void deleteByEmployeeColumns(String tableName, Set<String> employeeFkColumns, Long employeeId) {
        if (employeeFkColumns == null || employeeFkColumns.isEmpty()) {
            return;
        }
        String quotedTable = quoteIdentifier(tableName);
        for (String columnName : employeeFkColumns) {
            String sql = "DELETE FROM " + quotedTable + " WHERE " + quoteIdentifier(columnName) + " = ?";
            jdbcTemplate.update(sql, employeeId);
        }
    }

    private void purgeSecondLevelReferencesByEmployee(
            String parentTable,
            String parentPrimaryKeyColumn,
            String parentEmployeeColumn,
            Long employeeId) {

        List<FkReference> childReferences = findFkReferencesTo(parentTable, parentPrimaryKeyColumn);
        if (childReferences.isEmpty()) {
            return;
        }

        String quotedParent = quoteIdentifier(parentTable);
        String quotedParentPk = quoteIdentifier(parentPrimaryKeyColumn);
        String quotedParentEmployee = quoteIdentifier(parentEmployeeColumn);

        for (FkReference childRef : childReferences) {
            String quotedChild = quoteIdentifier(childRef.tableName);
            String quotedChildFk = quoteIdentifier(childRef.columnName);
            String sql = "DELETE child FROM " + quotedChild + " child "
                    + "JOIN " + quotedParent + " parent ON child." + quotedChildFk + " = parent." + quotedParentPk + " "
                    + "WHERE parent." + quotedParentEmployee + " = ?";
            jdbcTemplate.update(sql, employeeId);
        }
    }

    private List<FkReference> findFkReferencesTo(String referencedTable, String referencedColumn) {
        String sql = """
                SELECT kcu.TABLE_NAME, kcu.COLUMN_NAME
                FROM information_schema.KEY_COLUMN_USAGE kcu
                WHERE kcu.CONSTRAINT_SCHEMA = DATABASE()
                  AND LOWER(kcu.REFERENCED_TABLE_NAME) = LOWER(?)
                  AND LOWER(kcu.REFERENCED_COLUMN_NAME) = LOWER(?)
                  AND LOWER(kcu.TABLE_NAME) <> LOWER(?)
                ORDER BY kcu.TABLE_NAME, kcu.ORDINAL_POSITION
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new FkReference(rs.getString("TABLE_NAME"), rs.getString("COLUMN_NAME")),
                referencedTable,
                referencedColumn,
                referencedTable);
    }

    private int tablePriority(String tableName) {
        int idx = EMPLOYEE_CHILD_TABLE_PRIORITY.indexOf(tableName == null ? "" : tableName.toLowerCase());
        return idx >= 0 ? idx : Integer.MAX_VALUE;
    }

    private String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Unsafe SQL identifier: " + identifier);
        }
        return "`" + identifier.replace("`", "``") + "`";
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                tableName);
        return count != null && count > 0;
    }

    private static final class FkReference {
        private final String tableName;
        private final String columnName;

        private FkReference(String tableName, String columnName) {
            this.tableName = tableName;
            this.columnName = columnName;
        }
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
