package com.example.demo.service;


import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.demo.MODELS.AdditionalWorkingDayType;
import com.example.demo.MODELS.AttendanceRecord;
import com.example.demo.MODELS.Employee;
import com.example.demo.MODELS.EmployeeAdditionalWorkingDay;
import com.example.demo.MODELS.Holiday;
import com.example.demo.MODELS.LeavePermission;
import com.example.demo.repo.AttendanceRecordRepository;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.repo.HolidayRepository;
import com.example.demo.repo.LeavePermissionRepository;
import com.example.demo.tenant.TenantContext;

@Service
public class AttendanceSchedulerService {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private LeavePermissionRepository leavePermissionRepository;

    @Autowired
    private HolidayRepository holidayRepository;

    @Value("${tenant.routing.enabled:false}")
    private boolean routingEnabled;

private final JdbcTemplate masterJdbcTemplate;
private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
private static final String SWAP_WEEKOFF_TYPE = "swap weekoff";

public AttendanceSchedulerService(@Qualifier("masterDataSource") DataSource masterDataSource) {
    this.masterJdbcTemplate = new JdbcTemplate(masterDataSource);
}

   // 🕐 Scheduled to run every day at 3:00 PM
// Retry after 3 PM so a restart at exactly 3 PM does not skip the day's absences.
@Scheduled(cron = "0 */5 15-23 * * ?", zone = "Asia/Kolkata")
public void autoMarkAbsentAt3PM() {
    int updated = runAutoAbsentForToday();
    System.out.println("Auto absent scheduler completed for " + nowInBusinessZone().toLocalDate() + ". updatedAbsentCount=" + updated);
}

public int runAutoAbsentForToday() {
    LocalDate today = nowInBusinessZone().toLocalDate();
    String existingTenant = TenantContext.getTenantDb();
    boolean hasTenantContext = existingTenant != null && !existingTenant.isBlank();

    if (!routingEnabled || hasTenantContext) {
        int absentCount = runAutoAbsentForDate(today);
        System.out.println(absentCount + " employees auto-marked as Absent for " + today + ".");
        return absentCount;
    }

    List<String> tenantTargets = resolveTenantTargets();
    if (tenantTargets.isEmpty()) {
        int absentCount = runAutoAbsentForDate(today);
        System.out.println("No tenant targets found. Fallback auto-absent in current datasource. updatedAbsentCount=" + absentCount);
        return absentCount;
    }

    int totalUpdated = 0;
    for (String tenantDb : tenantTargets) {
        TenantContext.setTenantDb(tenantDb);
        try {
            int updatedForTenant = runAutoAbsentForDate(today);
            totalUpdated += updatedForTenant;
            System.out.println("Auto absent processed tenantDb=" + tenantDb + " updatedAbsentCount=" + updatedForTenant);
        } catch (Exception ex) {
            System.out.println("Auto absent failed for tenantDb=" + tenantDb + " error=" + ex.getMessage());
        } finally {
            TenantContext.clear();
        }
    }
    return totalUpdated;
}

public int runAutoAbsentForDate(LocalDate targetDate) {
    if (targetDate == null) {
        return 0;
    }
    List<Employee> allEmployees = employeeRepository.findAll();
    int absentCount = 0;
    for (Employee employee : allEmployees) {
        absentCount += ensureAbsentForEmployeeOnDate(employee, targetDate);
    }
    return absentCount;
}

public int ensureAbsentForEmployeeDateRange(Employee employee, LocalDate from, LocalDate to) {
    if (employee == null || from == null || to == null || from.isAfter(to)) {
        return 0;
    }

    LocalDate today = nowInBusinessZone().toLocalDate();
    LocalDate safeEnd = to.isAfter(today) ? today : to;
    int absentCount = 0;
    for (LocalDate cursor = from; !cursor.isAfter(safeEnd); cursor = cursor.plusDays(1)) {
        absentCount += ensureAbsentForEmployeeOnDate(employee, cursor);
    }
    return absentCount;
}

private int ensureAbsentForEmployeeOnDate(Employee employee, LocalDate targetDate) {
    if (employee == null || targetDate == null) {
        return 0;
    }

    if (isApprovedSwapWeekoffOnDate(employee, targetDate)) {
        return markApprovedSwapWeekoff(employee, targetDate);
    }

    ZonedDateTime now = nowInBusinessZone();
    if (targetDate.equals(now.toLocalDate()) && now.toLocalTime().isBefore(LocalTime.of(15, 0))) {
        return 0;
    }

    if (!isScheduledWorkingDay(employee, targetDate)) {
        return 0;
    }
    if (isHoliday(employee, targetDate)) {
        return 0;
    }
    if (hasLeaveOnDate(employee, targetDate)) {
        return 0;
    }

    String targetDateText = targetDate.format(formatter);
    List<AttendanceRecord> attendanceRecords =
            attendanceRecordRepository.findByEmployeeIdAndDate(employee.getId(), targetDateText);

    if (!attendanceRecords.isEmpty()) {
        AttendanceRecord existing = attendanceRecords.get(0);
        String status = existing.getAttendanceStatus() == null ? "" : existing.getAttendanceStatus().trim();
        if ("Leave".equalsIgnoreCase(status) || "On Leave".equalsIgnoreCase(status)) {
            return 0;
        }
        if ("Absent".equalsIgnoreCase(status)) {
            return 0;
        }
        boolean hasTimeIn = existing.getTimeIn() != null;
        boolean isPresent = "Present".equalsIgnoreCase(status);
        if (hasTimeIn && isPresent) {
            return 0;
        }
        existing.setAttendanceStatus("Absent");
        existing.setDayStatus("Auto Absent - No Time In");
        attendanceRecordRepository.save(existing);
        return 1;
    }

    AttendanceRecord absentRecord = new AttendanceRecord();
    absentRecord.setEmployee(employee);
    absentRecord.setAttendanceStatus("Absent");
    absentRecord.setDate(targetDateText);
    absentRecord.setDayStatus("Auto Absent - No Time In");
    attendanceRecordRepository.save(absentRecord);
    return 1;
}

private int markApprovedSwapWeekoff(Employee employee, LocalDate targetDate) {
    String targetDateText = targetDate.format(formatter);
    List<AttendanceRecord> attendanceRecords =
            attendanceRecordRepository.findByEmployeeIdAndDate(employee.getId(), targetDateText);
    AttendanceRecord record = attendanceRecords.isEmpty()
            ? new AttendanceRecord()
            : attendanceRecords.get(0);

    boolean changed =
            !"Week Off".equalsIgnoreCase(record.getAttendanceStatus())
                    || !"Week Off".equalsIgnoreCase(record.getDayStatus());

    record.setEmployee(employee);
    record.setDate(targetDateText);
    record.setAttendanceStatus("Week Off");
    record.setDayStatus("Week Off");
    record.setLocation("Swap Weekoff Approved");
    record.setAttendancelocation("Swap Weekoff Approved");
    record.setMissedTimes(0);
    record.setWorkedHours(0.0);
    record.setOvertime(0.0);
    record.setPermissionUsed(0.0);
    attendanceRecordRepository.save(record);

    for (int i = 1; i < attendanceRecords.size(); i++) {
        AttendanceRecord duplicate = attendanceRecords.get(i);
        if (duplicate.getTimeIn() == null && duplicate.getTimeOut() == null) {
            attendanceRecordRepository.delete(duplicate);
        }
    }

    return changed ? 1 : 0;
}

private boolean isApprovedSwapWeekoffOnDate(Employee employee, LocalDate targetDate) {
    if (employee == null || employee.getId() == null || targetDate == null) {
        return false;
    }

    List<LeavePermission> approvedLeaves =
            leavePermissionRepository.findByEmployeeIdAndStatus(employee.getId(), "approved");
    for (LeavePermission leave : approvedLeaves) {
        if (!isSwapWeekoffType(leave.getLeaveType())) {
            continue;
        }
        LocalDate start = parseLeaveDate(leave.getStartDate() != null ? leave.getStartDate() : leave.getDate());
        LocalDate end = parseLeaveDate(leave.getEndDate() != null ? leave.getEndDate() : leave.getStartDate());
        if (start == null) {
            continue;
        }
        if (end == null) {
            end = start;
        }
        if (!targetDate.isBefore(start) && !targetDate.isAfter(end)) {
            return true;
        }
    }
    return false;
}

private boolean hasLeaveOnDate(Employee employee, LocalDate targetDate) {
    if (employee == null || targetDate == null) {
        return false;
    }
    List<LeavePermission> leaves = leavePermissionRepository.findByEmployeeId(employee.getId());
    return leaves.stream().anyMatch(leave -> {
        String status = leave.getStatus() == null ? "" : leave.getStatus().trim();
        if (!"approved".equalsIgnoreCase(status)) {
            return false;
        }
        try {
            LocalDate start = LocalDate.parse(leave.getStartDate(), formatter);
            LocalDate end = LocalDate.parse(leave.getEndDate(), formatter);
            return !targetDate.isBefore(start) && !targetDate.isAfter(end);
        } catch (Exception e) {
            return false;
        }
    });
}

private boolean isScheduledWorkingDay(Employee employee, LocalDate targetDate) {
    if (employee == null) {
        return false;
    }
    DayOfWeek dayOfWeek = targetDate.getDayOfWeek();

    EmployeeAdditionalWorkingDay override = resolveAdditionalOverride(employee, targetDate);
    if (override != null) {
        return true;
    }

    String policyRaw = employee.getLeavePolicyType();
    String weekOffRaw = employee.getWeekOff();

    boolean weekendOff = false;
    if (policyRaw != null && !policyRaw.isBlank()) {
        String normalized = policyRaw.trim().toUpperCase();
        weekendOff = normalized.contains("WEEKEND") || (normalized.contains("SAT") && normalized.contains("SUN"));
    } else if (weekOffRaw != null) {
        String normalized = weekOffRaw.trim().toUpperCase();
        weekendOff = normalized.contains("SAT") && normalized.contains("SUN");
    }

    if (weekendOff) {
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    DayOfWeek weekOffDay = parseDayOfWeek(weekOffRaw);
    if (weekOffDay == null) {
        weekOffDay = DayOfWeek.SUNDAY;
    }
    return dayOfWeek != weekOffDay;
}

private boolean isHoliday(Employee employee, LocalDate targetDate) {
    if (employee == null || employee.getClientId() == null) {
        return false;
    }
    try {
        List<Holiday> holidays = holidayRepository
                .findByClientIdAndHolidayDateBetweenOrderByHolidayDateAsc(employee.getClientId(), targetDate, targetDate);
        return !holidays.isEmpty();
    } catch (Exception ex) {
        // If holidays table doesn't exist in tenant DB, treat as no holiday.
        return false;
    }
}

private EmployeeAdditionalWorkingDay resolveAdditionalOverride(Employee employee, LocalDate date) {
    if (employee.getAdditionalWorkingDays() == null || employee.getAdditionalWorkingDays().isEmpty()) {
        return null;
    }
    Map<AdditionalWorkingDayType, EmployeeAdditionalWorkingDay> additionalMap =
            new EnumMap<>(AdditionalWorkingDayType.class);
    for (EmployeeAdditionalWorkingDay day : employee.getAdditionalWorkingDays()) {
        if (day != null && day.getDayType() != null) {
            additionalMap.putIfAbsent(day.getDayType(), day);
        }
    }

    DayOfWeek dayOfWeek = date.getDayOfWeek();
    if (dayOfWeek == DayOfWeek.SATURDAY) {
        int index = weekdayIndexInMonth(date, DayOfWeek.SATURDAY);
        AdditionalWorkingDayType type = (index % 2 == 1)
                ? AdditionalWorkingDayType.ODD_SATURDAY
                : AdditionalWorkingDayType.EVEN_SATURDAY;
        return additionalMap.get(type);
    }
    if (dayOfWeek == DayOfWeek.SUNDAY) {
        int index = weekdayIndexInMonth(date, DayOfWeek.SUNDAY);
        AdditionalWorkingDayType type = (index % 2 == 1)
                ? AdditionalWorkingDayType.ODD_SUNDAY
                : AdditionalWorkingDayType.EVEN_SUNDAY;
        return additionalMap.get(type);
    }
    return null;
}

private int weekdayIndexInMonth(LocalDate date, DayOfWeek target) {
    int count = 0;
    LocalDate cursor = date.withDayOfMonth(1);
    while (!cursor.isAfter(date)) {
        if (cursor.getDayOfWeek() == target) {
            count++;
        }
        cursor = cursor.plusDays(1);
    }
    return count;
}

private DayOfWeek parseDayOfWeek(String raw) {
    if (raw == null || raw.isBlank()) {
        return null;
    }
    String normalized = raw.trim().toUpperCase();
    if (normalized.length() >= 3) {
        normalized = normalized.substring(0, 3);
    }
    return switch (normalized) {
        case "MON" -> DayOfWeek.MONDAY;
        case "TUE" -> DayOfWeek.TUESDAY;
        case "WED" -> DayOfWeek.WEDNESDAY;
        case "THU" -> DayOfWeek.THURSDAY;
        case "FRI" -> DayOfWeek.FRIDAY;
        case "SAT" -> DayOfWeek.SATURDAY;
        case "SUN" -> DayOfWeek.SUNDAY;
        default -> null;
    };
}

private boolean isSwapWeekoffType(String leaveTypeRaw) {
    if (leaveTypeRaw == null) {
        return false;
    }
    return leaveTypeRaw.trim().toLowerCase(Locale.ROOT).equals(SWAP_WEEKOFF_TYPE);
}

private LocalDate parseLeaveDate(String raw) {
    if (raw == null || raw.isBlank()) {
        return null;
    }
    try {
        return LocalDate.parse(raw.trim(), formatter);
    } catch (DateTimeParseException ex) {
        return null;
    }
}

private List<String> resolveTenantTargets() {
    List<String> activeTargets = masterJdbcTemplate.queryForList(
            "SELECT DISTINCT tenant_db_name FROM clients "
                    + "WHERE tenant_db_name IS NOT NULL AND tenant_db_name <> '' "
                    + "AND provisioning_status = 'ACTIVE'",
            String.class);

    if (!activeTargets.isEmpty()) {
        return activeTargets;
    }

    // Fallback for legacy clients where provisioning_status may be null/empty.
    return masterJdbcTemplate.queryForList(
            "SELECT DISTINCT tenant_db_name FROM clients "
                    + "WHERE tenant_db_name IS NOT NULL AND tenant_db_name <> ''",
            String.class);
}

private ZonedDateTime nowInBusinessZone() {
    return ZonedDateTime.now(BUSINESS_ZONE);
}

}
