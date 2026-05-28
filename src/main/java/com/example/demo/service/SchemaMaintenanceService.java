package com.example.demo.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SchemaMaintenanceService {
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, Boolean> ensuredSchemas = new ConcurrentHashMap<>();

    public SchemaMaintenanceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void ensureEmployeeSchema() {
        String dbName = resolveDatabaseName();
        if (dbName == null) {
            return;
        }
        if (Boolean.TRUE.equals(ensuredSchemas.get(dbName))) {
            return;
        }

        try {
            ensureEmployeeTableColumns();
            ensureLeavePolicyTable();
            ensureAdditionalWorkingDaysTable();
            ensureEmployeeSalaryDetailsTableColumns();
            ensureAttendanceRecordColumns();
            ensureOvertimeRequestTable();
            ensuredSchemas.put(dbName, Boolean.TRUE);
        } catch (RuntimeException ex) {
            ensuredSchemas.remove(dbName);
            throw ex;
        }
    }

    private String resolveDatabaseName() {
        try {
            return jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private void ensureEmployeeTableColumns() {
        ensureColumn("employee", "shift_start", "VARCHAR(16) NULL");
        ensureColumn("employee", "shift_end", "VARCHAR(16) NULL");
        ensureColumn("employee", "shift_start_time", "VARCHAR(16) NULL");
        ensureColumn("employee", "shift_end_time", "VARCHAR(16) NULL");
        ensureColumn("employee", "leave_policy_type", "VARCHAR(32) NULL");
        ensureColumn("employee", "casual_leave_balance", "INT DEFAULT 0");
        ensureColumn("employee", "permission_allowance_per_month", "INT NULL");
        ensureColumn("employee", "permission_hours_allowed", "DOUBLE NULL");
        ensureColumn("employee", "additional_working_days", "TEXT NULL");
    }

    private void ensureLeavePolicyTable() {
        if (tableExists("leave_policy")) {
            ensureColumn("leave_policy", "employee_id", "BIGINT NULL");
            ensureColumn("leave_policy", "casual_leave_allowed", "INT NULL");
            ensureColumn("leave_policy", "sick_leave_allowed", "INT NULL");
            ensureColumn("leave_policy", "earned_leave_allowed", "INT NULL");
            return;
        }
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS leave_policy (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    employee_id BIGINT NULL,
                    casual_leave_allowed INT NULL,
                    sick_leave_allowed INT NULL,
                    earned_leave_allowed INT NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_leave_policy_employee (employee_id),
                    CONSTRAINT fk_leave_policy_employee
                        FOREIGN KEY (employee_id) REFERENCES employee(id)
                        ON DELETE CASCADE
                )
                """);
    }

    private void ensureAdditionalWorkingDaysTable() {
        if (!tableExists("employee_additional_working_day")) {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS employee_additional_working_day (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    employee_id BIGINT NOT NULL,
                    day_type VARCHAR(32) NOT NULL,
                    time_in VARCHAR(16) NULL,
                    time_out VARCHAR(16) NULL,
                    PRIMARY KEY (id),
                    KEY idx_employee_additional_day_employee (employee_id),
                    CONSTRAINT fk_employee_additional_day_employee
                        FOREIGN KEY (employee_id) REFERENCES employee(id)
                        ON DELETE CASCADE
                )
                """);
        }
    }

    private void ensureEmployeeSalaryDetailsTableColumns() {
        if (!tableExists("employee_salary_details")) {
            return;
        }
        ensureColumn("employee_salary_details", "pf_amount", "DOUBLE DEFAULT 0");
        ensureColumn("employee_salary_details", "pf_percentage", "DOUBLE DEFAULT 0");
        ensureColumn("employee_salary_details", "additional_allowances_total", "DOUBLE DEFAULT 0");
        ensureColumn("employee_salary_details", "additional_allowances_json", "TEXT NULL");
    }

    private void ensureAttendanceRecordColumns() {
        if (!tableExists("attendance_record")) {
            return;
        }
        ensureColumn("attendance_record", "overtime_approved", "BIT DEFAULT 0");
        ensureColumn("attendance_record", "overtime_requested", "BIT DEFAULT 0");
        ensureColumn("attendance_record", "worked_hours", "DOUBLE NULL");
        ensureColumn("attendance_record", "overtime", "DOUBLE NULL");
        ensureColumn("attendance_record", "permission_used", "DOUBLE NULL");
        ensureColumn("attendance_record", "shift_id", "VARCHAR(64) NULL");
    }

    private void ensureOvertimeRequestTable() {
        if (tableExists("overtime_request")) {
            ensureColumn("overtime_request", "employee_id", "BIGINT NULL");
            ensureColumn("overtime_request", "date", "VARCHAR(16) NULL");
            ensureColumn("overtime_request", "overtime_hours", "DOUBLE NOT NULL DEFAULT 0");
            ensureColumn("overtime_request", "status", "VARCHAR(16) NOT NULL DEFAULT 'PENDING'");
            ensureColumn("overtime_request", "created_at", "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP");
            ensureColumn("overtime_request", "updated_at", "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP");
            return;
        }
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS overtime_request (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    employee_id BIGINT NOT NULL,
                    date VARCHAR(16) NOT NULL,
                    overtime_hours DOUBLE NOT NULL DEFAULT 0,
                    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    KEY idx_overtime_request_employee (employee_id),
                    KEY idx_overtime_request_date (date),
                    CONSTRAINT fk_overtime_request_employee
                        FOREIGN KEY (employee_id) REFERENCES employee(id)
                        ON DELETE CASCADE
                )
                """);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                tableName);
        return count != null && count > 0;
    }

    private void ensureColumn(String table, String column, String definition) {
        if (columnExists(table, column)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                table,
                column);
        return count != null && count > 0;
    }
}
