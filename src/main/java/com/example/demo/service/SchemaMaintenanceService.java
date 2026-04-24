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
        if (ensuredSchemas.putIfAbsent(dbName, Boolean.TRUE) != null) {
            return;
        }

        ensureEmployeeTableColumns();
        ensureAdditionalWorkingDaysTable();
        ensureEmployeeSalaryDetailsTableColumns();
        ensureAttendanceRecordColumns();
        ensureOvertimeRequestTable();
    }

    private String resolveDatabaseName() {
        try {
            return jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private void ensureEmployeeTableColumns() {
        ensureColumn("employee", "leave_policy_type", "VARCHAR(32) NULL");
        ensureColumn("employee", "casual_leave_balance", "INT DEFAULT 0");
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
    }

    private void ensureOvertimeRequestTable() {
        if (tableExists("overtime_request")) {
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
