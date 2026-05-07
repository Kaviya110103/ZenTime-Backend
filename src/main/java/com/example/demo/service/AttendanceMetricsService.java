package com.example.demo.service;

import com.example.demo.MODELS.AttendanceRecord;
import com.example.demo.MODELS.Employee;
import com.example.demo.MODELS.LeavePermission;
import com.example.demo.repo.AttendanceRecordRepository;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.repo.LeavePermissionRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Service
public class AttendanceMetricsService {
    public static final int MAX_APPROVED_PERMISSIONS_PER_MONTH = 0;
    public static final int MAX_APPROVED_PERMISSION_MINUTES_PER_MONTH = 0;
    private static final int DEFAULT_MINUTES_PER_PERMISSION = 60;

    private static final DateTimeFormatter DB_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER_HH_MM = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter TIME_FORMATTER_HH_MM_SS = DateTimeFormatter.ofPattern("H:mm:ss");
    private static final LocalTime DEFAULT_SHIFT_START = LocalTime.of(10, 0);
    private static final LocalTime DEFAULT_SHIFT_END = LocalTime.of(19, 0);

    private final EmployeeRepository employeeRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final LeavePermissionRepository leavePermissionRepository;

    public AttendanceMetricsService(
            EmployeeRepository employeeRepository,
            AttendanceRecordRepository attendanceRecordRepository,
            LeavePermissionRepository leavePermissionRepository) {
        this.employeeRepository = employeeRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.leavePermissionRepository = leavePermissionRepository;
    }

    public int calculateDailyLateMinutes(AttendanceRecord record) {
        if (record == null || record.getTimeIn() == null) {
            return 0;
        }
        LocalTime shiftStart = resolveShiftStart(record.getEmployee());
        LocalTime timeIn = record.getTimeIn().toLocalTime();
        if (!timeIn.isAfter(shiftStart)) {
            return 0;
        }
        return (int) Duration.between(shiftStart, timeIn).toMinutes();
    }

    public int calculateDailyEarlyOutMinutes(AttendanceRecord record) {
        if (record == null || record.getTimeOut() == null) {
            return 0;
        }
        LocalTime shiftEnd = resolveShiftEnd(record.getEmployee());
        LocalTime timeOut = record.getTimeOut().toLocalTime();
        if (!timeOut.isBefore(shiftEnd)) {
            return 0;
        }
        return (int) Duration.between(timeOut, shiftEnd).toMinutes();
    }

    public int calculateDailyMissedMinutes(AttendanceRecord record) {
        return calculateDailyLateMinutes(record) + calculateDailyEarlyOutMinutes(record);
    }

    public int synchronizeMissedMinutes(AttendanceRecord record) {
        if (record == null) {
            return 0;
        }
        int recalculated = calculateDailyMissedMinutes(record);
        Integer current = record.getMissedTimes();
        if (current == null || current != recalculated) {
            record.setMissedtimes(recalculated);
            try {
                attendanceRecordRepository.save(record);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return recalculated;
    }

    public void synchronizeMissedMinutes(List<AttendanceRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (AttendanceRecord record : records) {
            if (record == null) {
                continue;
            }
            int recalculated = calculateDailyMissedMinutes(record);
            Integer current = record.getMissedTimes();
            if (current == null || current != recalculated) {
                record.setMissedtimes(recalculated);
                changed = true;
            }
        }
        if (changed) {
            try {
                attendanceRecordRepository.saveAll(records);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public MonthlyMetrics calculateMonthlyMetrics(Long employeeId, int month, int year) {
        Optional<Employee> employeeOpt = employeeRepository.findById(employeeId);
        if (employeeOpt.isEmpty()) {
            return MonthlyMetrics.empty();
        }

        List<AttendanceRecord> records = attendanceRecordRepository.findByEmployeeId(employeeId);
        int absentCount = 0;
        int presentCount = 0;
        int monthlyLateDays = 0;
        int monthlyLateMinutes = 0;
        int monthlyEarlyOutMinutes = 0;
        int totalMissedMinutes = 0;

        for (AttendanceRecord record : records) {
            if (!isRecordInMonth(record.getDate(), month, year)) {
                continue;
            }

            if (record.getAttendanceStatus() != null) {
                if ("Absent".equalsIgnoreCase(record.getAttendanceStatus())) {
                    absentCount++;
                } else if ("Present".equalsIgnoreCase(record.getAttendanceStatus())) {
                    presentCount++;
                }
            }

            int lateMinutes = calculateDailyLateMinutes(record);
            int earlyOutMinutes = calculateDailyEarlyOutMinutes(record);
            int missedMinutes = lateMinutes + earlyOutMinutes;
            if (lateMinutes > 0) {
                monthlyLateDays++;
            }

            monthlyLateMinutes += lateMinutes;
            monthlyEarlyOutMinutes += earlyOutMinutes;
            totalMissedMinutes += missedMinutes;
        }

        PermissionUsage permissionUsage = getApprovedPermissionUsage(employeeId, month, year, null);

        return new MonthlyMetrics(
                absentCount,
                presentCount,
                monthlyLateDays,
                monthlyLateMinutes,
                monthlyEarlyOutMinutes,
                totalMissedMinutes,
                permissionUsage.approvedPermissionCount(),
                permissionUsage.approvedPermissionMinutes());
    }

    public int resolveMaxApprovedPermissionsPerMonth(Employee employee) {
        if (employee == null || employee.getPermissionAllowancePerMonth() == null) {
            return MAX_APPROVED_PERMISSIONS_PER_MONTH;
        }
        return Math.max(0, employee.getPermissionAllowancePerMonth());
    }

    public int resolveMaxApprovedPermissionMinutesPerMonth(Employee employee) {
        int permissionCount = resolveMaxApprovedPermissionsPerMonth(employee);
        if (permissionCount <= 0) {
            return 0;
        }
        return permissionCount * DEFAULT_MINUTES_PER_PERMISSION;
    }

    public PermissionUsage getApprovedPermissionUsage(Long employeeId, int month, int year, Long excludeLeaveId) {
        List<LeavePermission> permissions = leavePermissionRepository.findByEmployeeIdAndStatus(employeeId, "approved");
        int approvedPermissionCount = 0;
        int approvedPermissionMinutes = 0;

        for (LeavePermission permission : permissions) {
            if (excludeLeaveId != null && excludeLeaveId.equals(permission.getId())) {
                continue;
            }
            if (!"Permission".equalsIgnoreCase(permission.getLeaveType())) {
                continue;
            }
            if (!isPermissionInMonth(permission, month, year)) {
                continue;
            }

            approvedPermissionCount++;
            approvedPermissionMinutes += calculatePermissionDurationMinutes(permission);
        }

        return new PermissionUsage(approvedPermissionCount, approvedPermissionMinutes);
    }

    public int calculatePermissionDurationMinutes(LeavePermission permission) {
        if (permission == null) {
            return 0;
        }
        return calculatePermissionDurationMinutes(permission.getStartTime(), permission.getEndTime());
    }

    public int calculatePermissionDurationMinutes(String startTimeRaw, String endTimeRaw) {
        Optional<LocalTime> startOpt = parseFlexibleTime(startTimeRaw);
        Optional<LocalTime> endOpt = parseFlexibleTime(endTimeRaw);
        if (startOpt.isEmpty() || endOpt.isEmpty()) {
            return 0;
        }

        LocalTime start = startOpt.get();
        LocalTime end = endOpt.get();
        if (!end.isAfter(start)) {
            return 0;
        }
        return (int) Duration.between(start, end).toMinutes();
    }

    public int resolveShiftDurationMinutes(Employee employee) {
        LocalTime shiftStart = resolveShiftStart(employee);
        LocalTime shiftEnd = resolveShiftEnd(employee);
        int shiftMinutes = (int) Duration.between(shiftStart, shiftEnd).toMinutes();
        return Math.max(shiftMinutes, 1);
    }

    private boolean isRecordInMonth(String dbDate, int month, int year) {
        if (dbDate == null || dbDate.isBlank()) {
            return false;
        }
        try {
            LocalDate date = LocalDate.parse(dbDate.trim(), DB_DATE_FORMATTER);
            return date.getMonthValue() == month && date.getYear() == year;
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    private boolean isPermissionInMonth(LeavePermission permission, int month, int year) {
        if (permission == null) {
            return false;
        }
        String dateSource = permission.getDate();
        if (dateSource == null || dateSource.isBlank()) {
            dateSource = permission.getStartDate();
        }
        return isRecordInMonth(dateSource, month, year);
    }

    private LocalTime resolveShiftStart(Employee employee) {
        if (employee != null) {
            Optional<LocalTime> parsed = parseFlexibleTime(employee.getShiftStartTime());
            if (parsed.isPresent()) {
                return parsed.get();
            }
        }
        return DEFAULT_SHIFT_START;
    }

    private LocalTime resolveShiftEnd(Employee employee) {
        if (employee != null) {
            Optional<LocalTime> parsed = parseFlexibleTime(employee.getShiftEndTime());
            if (parsed.isPresent()) {
                return parsed.get();
            }
        }
        return DEFAULT_SHIFT_END;
    }

    private Optional<LocalTime> parseFlexibleTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim();
        try {
            return Optional.of(LocalTime.parse(value, TIME_FORMATTER_HH_MM_SS));
        } catch (DateTimeParseException ignored) {
            // Fallback to HH:mm.
        }
        try {
            return Optional.of(LocalTime.parse(value, TIME_FORMATTER_HH_MM));
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    public record PermissionUsage(int approvedPermissionCount, int approvedPermissionMinutes) {
    }

    public record MonthlyMetrics(
            int absentCount,
            int presentCount,
            int monthlyLateDays,
            int monthlyLateMinutes,
            int monthlyEarlyOutMinutes,
            int totalMissedMinutes,
            int approvedPermissionCount,
            int approvedPermissionMinutes) {
        public static MonthlyMetrics empty() {
            return new MonthlyMetrics(0, 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
