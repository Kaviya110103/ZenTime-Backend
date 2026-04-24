package com.example.demo.service;


import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.demo.MODELS.AdditionalWorkingDayType;
import com.example.demo.MODELS.AttendanceRecord;
import com.example.demo.MODELS.Client;
import com.example.demo.MODELS.Employee;
import com.example.demo.MODELS.EmployeeAdditionalWorkingDay;
import com.example.demo.MODELS.Holiday;
import com.example.demo.MODELS.LeavePermission;
import com.example.demo.repo.AttendanceRecordRepository;
import com.example.demo.repo.ClientRepository;
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

    @Autowired
    private ClientRepository clientRepository;

    @Value("${tenant.routing.enabled:false}")
    private boolean routingEnabled;

private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

   // 🕐 Scheduled to run every day at 3:00 PM
@Scheduled(cron = "0 0 15 * * ?", zone = "Asia/Kolkata")
public void autoMarkAbsentAt3PM() {
    int updated = runAutoAbsentForToday();
    System.out.println("Auto absent scheduler completed for " + LocalDate.now() + ". updatedAbsentCount=" + updated);
}

public int runAutoAbsentForToday() {
    LocalDate today = LocalDate.now();
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

    LocalDate today = LocalDate.now();
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

    if (targetDate.equals(LocalDate.now()) && LocalTime.now().isBefore(LocalTime.of(15, 0))) {
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

private List<String> resolveTenantTargets() {
    List<Client> clients = clientRepository.findAll();

    List<String> activeTargets = clients.stream()
            .filter(client -> client != null
                    && client.getTenantDbName() != null
                    && !client.getTenantDbName().isBlank()
                    && "ACTIVE".equalsIgnoreCase(client.getProvisioningStatus()))
            .map(Client::getTenantDbName)
            .distinct()
            .collect(Collectors.toList());

    if (!activeTargets.isEmpty()) {
        return activeTargets;
    }

    // Fallback for legacy clients where provisioning_status may be null/empty.
    return clients.stream()
            .filter(client -> client != null
                    && client.getTenantDbName() != null
                    && !client.getTenantDbName().isBlank())
            .map(Client::getTenantDbName)
            .distinct()
            .collect(Collectors.toList());
}

}
