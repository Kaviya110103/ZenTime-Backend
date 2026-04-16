package com.example.demo.service;


import java.time.DayOfWeek;
import java.time.LocalDate;
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
@Scheduled(cron = "0 0 15 * * ?")
public void autoMarkAbsentAt3PM() {
    if (routingEnabled) {
        List<Client> activeClients = clientRepository.findAll()
                .stream()
                .filter(client -> client != null
                        && client.getTenantDbName() != null
                        && !client.getTenantDbName().isBlank()
                        && "ACTIVE".equalsIgnoreCase(client.getProvisioningStatus()))
                .collect(Collectors.toList());

        for (Client client : activeClients) {
            TenantContext.setTenantDb(client.getTenantDbName());
            try {
                runAutoAbsentForToday();
            } finally {
                TenantContext.clear();
            }
        }
        return;
    }

    runAutoAbsentForToday();
}

public int runAutoAbsentForToday() {
    String todayDate = LocalDate.now().format(formatter);

    List<Employee> allEmployees = employeeRepository.findAll();
    int absentCount = 0;

    for (Employee employee : allEmployees) {
        if (!isScheduledWorkingDay(employee)) {
            continue;
        }
        if (isHoliday(employee)) {
            continue;
        }

        List<AttendanceRecord> attendanceRecords =
                attendanceRecordRepository.findByEmployeeIdAndDate(employee.getId(), todayDate);

        if (!attendanceRecords.isEmpty()) {
            AttendanceRecord existing = attendanceRecords.get(0);
            boolean hasTimeIn = existing.getTimeIn() != null;
            boolean isPresent = "Present".equalsIgnoreCase(existing.getAttendanceStatus());
            if (hasTimeIn && isPresent) {
                continue;
            }
            existing.setAttendanceStatus("Absent");
            existing.setDayStatus("Auto Absent - No Time In");
            attendanceRecordRepository.save(existing);
            absentCount++;
            continue;
        }

        List<LeavePermission> leaves = leavePermissionRepository.findByEmployeeId(employee.getId());
        boolean hasLeaveToday = leaves.stream().anyMatch(leave -> {
            try {
                LocalDate start = LocalDate.parse(leave.getStartDate(), formatter);
                LocalDate end = LocalDate.parse(leave.getEndDate(), formatter);
                LocalDate today = LocalDate.now();
                return !today.isBefore(start) && !today.isAfter(end);
            } catch (Exception e) {
                return false;
            }
        });

        if (!hasLeaveToday) {
            AttendanceRecord absentRecord = new AttendanceRecord();
            absentRecord.setEmployee(employee);
            absentRecord.setAttendanceStatus("Absent");
            absentRecord.setDate(todayDate);
            absentRecord.setDayStatus("Auto Absent - No Time In");
            attendanceRecordRepository.save(absentRecord);
            absentCount++;
        }
    }

    System.out.println(absentCount + " employees auto-marked as Absent at 3:00 PM.");
    return absentCount;
}

private boolean isScheduledWorkingDay(Employee employee) {
    if (employee == null) {
        return false;
    }
    LocalDate today = LocalDate.now();
    DayOfWeek dayOfWeek = today.getDayOfWeek();

    EmployeeAdditionalWorkingDay override = resolveAdditionalOverride(employee, today);
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

private boolean isHoliday(Employee employee) {
    if (employee == null || employee.getClientId() == null) {
        return false;
    }
    LocalDate today = LocalDate.now();
    try {
        List<Holiday> holidays = holidayRepository
                .findByClientIdAndHolidayDateBetweenOrderByHolidayDateAsc(employee.getClientId(), today, today);
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

}
