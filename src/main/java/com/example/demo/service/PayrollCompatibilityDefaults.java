package com.example.demo.service;

import com.example.demo.MODELS.Employee;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

public final class PayrollCompatibilityDefaults {
    public static final LocalTime DEFAULT_SHIFT_START = LocalTime.of(9, 0);
    public static final LocalTime DEFAULT_SHIFT_END = LocalTime.of(18, 0);
    public static final double DEFAULT_PERMISSION_HOURS_ALLOWED = 2.0;
    public static final int DEFAULT_CASUAL_LEAVE_ALLOWED = 0;
    public static final int DEFAULT_SICK_LEAVE_ALLOWED = 0;
    public static final int DEFAULT_EARNED_LEAVE_ALLOWED = 0;

    private static final DateTimeFormatter TIME_FORMATTER_HH_MM = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter TIME_FORMATTER_HH_MM_SS = DateTimeFormatter.ofPattern("H:mm:ss");

    private PayrollCompatibilityDefaults() {
    }

    public static LocalTime resolveShiftStart(Employee employee) {
        if (employee != null) {
            Optional<LocalTime> newColumnValue = parseFlexibleTime(employee.getShiftStart());
            if (newColumnValue.isPresent()) {
                return newColumnValue.get();
            }

            Optional<LocalTime> legacyColumnValue = parseFlexibleTime(employee.getShiftStartTime());
            if (legacyColumnValue.isPresent()) {
                return legacyColumnValue.get();
            }
        }
        return DEFAULT_SHIFT_START;
    }

    public static LocalTime resolveShiftEnd(Employee employee) {
        if (employee != null) {
            Optional<LocalTime> newColumnValue = parseFlexibleTime(employee.getShiftEnd());
            if (newColumnValue.isPresent()) {
                return newColumnValue.get();
            }

            Optional<LocalTime> legacyColumnValue = parseFlexibleTime(employee.getShiftEndTime());
            if (legacyColumnValue.isPresent()) {
                return legacyColumnValue.get();
            }
        }
        return DEFAULT_SHIFT_END;
    }

    public static double resolvePermissionHoursAllowed(Employee employee) {
        if (employee == null) {
            return DEFAULT_PERMISSION_HOURS_ALLOWED;
        }
        if (employee.getPermissionHoursAllowed() != null) {
            return Math.max(0.0, employee.getPermissionHoursAllowed());
        }
        if (employee.getPermissionAllowancePerMonth() != null && employee.getPermissionAllowancePerMonth() > 0) {
            return employee.getPermissionAllowancePerMonth();
        }
        return DEFAULT_PERMISSION_HOURS_ALLOWED;
    }

    public static Optional<LocalTime> parseFlexibleTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim();
        try {
            return Optional.of(LocalTime.parse(value, TIME_FORMATTER_HH_MM_SS));
        } catch (DateTimeParseException ignored) {
            // Backward compatibility: old and mobile clients often send H:mm.
        }
        try {
            return Optional.of(LocalTime.parse(value, TIME_FORMATTER_HH_MM));
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }
}
