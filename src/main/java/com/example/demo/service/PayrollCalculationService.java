package com.example.demo.service;

import com.example.demo.MODELS.*;
import com.example.demo.repo.AttendanceRecordRepository;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.repo.HolidayRepository;
import com.example.demo.repo.LeavePermissionRepository;
import com.example.demo.repo.OvertimeRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class PayrollCalculationService {
    private static final DateTimeFormatter DB_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER_HH_MM = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter TIME_FORMATTER_HH_MM_SS = DateTimeFormatter.ofPattern("H:mm:ss");
    private static final LocalTime DEFAULT_SHIFT_START = LocalTime.of(10, 0);
    private static final LocalTime DEFAULT_SHIFT_END = LocalTime.of(19, 0);

    private final EmployeeRepository employeeRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final LeavePermissionRepository leavePermissionRepository;
    private final HolidayRepository holidayRepository;
    private final SchemaMaintenanceService schemaMaintenanceService;
    private final OvertimeRequestRepository overtimeRequestRepository;

    public PayrollCalculationService(
            EmployeeRepository employeeRepository,
            AttendanceRecordRepository attendanceRecordRepository,
            LeavePermissionRepository leavePermissionRepository,
            HolidayRepository holidayRepository,
            SchemaMaintenanceService schemaMaintenanceService,
            OvertimeRequestRepository overtimeRequestRepository) {
        this.employeeRepository = employeeRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.leavePermissionRepository = leavePermissionRepository;
        this.holidayRepository = holidayRepository;
        this.schemaMaintenanceService = schemaMaintenanceService;
        this.overtimeRequestRepository = overtimeRequestRepository;
    }

    @Transactional(readOnly = true)
    public PayrollResult calculateMonthlyPayroll(Long employeeId, int month, int year) {
        return calculateMonthlyPayroll(employeeId, month, year, null);
    }

    @Transactional(readOnly = true)
    public PayrollResult calculateMonthlyPayroll(Long employeeId, int month, int year, Long clientIdOverride) {
        schemaMaintenanceService.ensureEmployeeSchema();
        Optional<Employee> employeeOpt = employeeRepository.findById(employeeId);
        if (employeeOpt.isEmpty()) {
            return PayrollResult.empty();
        }

        Employee employee = employeeOpt.get();
        Long effectiveClientId = clientIdOverride != null ? clientIdOverride : employee.getClientId();
        LocalDate firstDate = LocalDate.of(year, month, 1);
        LocalDate lastDate = firstDate.withDayOfMonth(firstDate.lengthOfMonth());

        LeavePolicy policy = resolveLeavePolicy(employee);
        Map<LocalDate, ShiftWindow> shiftWindowsByDate =
                buildShiftWindows(employee, policy, firstDate, lastDate);
        Map<LocalDate, Integer> scheduledMinutesByDate = new HashMap<>();
        for (Map.Entry<LocalDate, ShiftWindow> entry : shiftWindowsByDate.entrySet()) {
            scheduledMinutesByDate.put(entry.getKey(), entry.getValue().minutes);
        }

        Map<LocalDate, Double> holidayFractionByDate =
                buildHolidayMap(effectiveClientId, firstDate, lastDate);

        LeaveBuckets leaveBuckets = buildLeaveBuckets(
                employeeId,
                month,
                year,
                holidayFractionByDate,
                scheduledMinutesByDate);
        int casualBalance = employee.getCasualLeaveBalance() == null ? 0 : employee.getCasualLeaveBalance();
        leaveBuckets.applyCasualBalance(scheduledMinutesByDate, casualBalance);

        WorkSummary workSummary =
                buildWorkedMinutesByDate(employeeId, month, year);
        Map<LocalDate, Integer> workedMinutesByDate = workSummary.workedMinutesByDate;

        int workedMinutesTotal = 0;
        for (int minutes : workedMinutesByDate.values()) {
            workedMinutesTotal += minutes;
        }

        int overtimeMinutes = 0;
        for (Map.Entry<LocalDate, Integer> entry : workedMinutesByDate.entrySet()) {
            LocalDate date = entry.getKey();
            WorkEntry workEntry = workSummary.workEntries.get(date);
            ShiftWindow window = shiftWindowsByDate.get(date);
            if (window == null || window.minutes <= 0) {
                continue; // Week-off work counts as regular payable, not overtime.
            }
            if (!workSummary.overtimeApprovedDates.contains(date)) {
                continue;
            }
            if (workEntry == null || workEntry.earliestTimeIn == null || workEntry.latestTimeOut == null) {
                continue;
            }
            if (workEntry.earliestTimeInTime == null || workEntry.latestTimeOutTime == null) {
                continue;
            }
            LocalTime actualOutTime = workEntry.latestTimeOutTime;
            if (actualOutTime.isAfter(window.end)) {
                LocalTime overtimeStart = workEntry.earliestTimeInTime.isAfter(window.end)
                        ? workEntry.earliestTimeInTime
                        : window.end;
                overtimeMinutes += (int) Duration.between(overtimeStart, actualOutTime).toMinutes();
            }
        }

        int scheduledDays = 0;
        int holidayDaysFull = 0;
        int holidayDaysHalf = 0;
        int paidLeaveDays = 0;
        int unpaidLeaveDays = 0;
        int paidCasualDays = 0;
        int unpaidCasualDays = 0;
        int workedDays = 0;

        int expectedMinutes = 0;
        int payableMinutes = 0;

        for (Map.Entry<LocalDate, Integer> entry : scheduledMinutesByDate.entrySet()) {
            LocalDate date = entry.getKey();
            ShiftWindow window = shiftWindowsByDate.get(date);
            int scheduledMinutes = window == null ? 0 : window.minutes;
            if (scheduledMinutes > 0) {
                scheduledDays++;
                expectedMinutes += scheduledMinutes;
            }

            Double holidayFraction = holidayFractionByDate.get(date);
            if (holidayFraction != null && holidayFraction > 0) {
                if (holidayFraction >= 1.0) {
                    holidayDaysFull++;
                } else {
                    holidayDaysHalf++;
                }
                // Holidays are not payable per current rule.
                continue;
            }

            if (leaveBuckets.paidLeaveDates.contains(date)) {
                paidLeaveDays++;
                // Approved leave is not payable per current rule.
                continue;
            }

            if (leaveBuckets.unpaidLeaveDates.contains(date)) {
                unpaidLeaveDays++;
                continue;
            }

            if (leaveBuckets.paidCasualDates.contains(date)) {
                paidCasualDays++;
                // Casual leave is not payable per current rule.
                continue;
            }

            if (leaveBuckets.unpaidCasualDates.contains(date)) {
                unpaidCasualDays++;
                continue;
            }

            WorkEntry workEntry = workSummary.workEntries.get(date);
            int workedMinutes = workedMinutesByDate.getOrDefault(date, 0);
            if (workedMinutes > 0) {
                workedDays++;
            }

            if (scheduledMinutes <= 0) {
                // Week-off work counts as regular payable hours.
                payableMinutes += workedMinutes;
                continue;
            }
            if (workEntry == null || workEntry.earliestTimeIn == null || workEntry.latestTimeOut == null) {
                continue;
            }
            if (workEntry.earliestTimeInTime == null || workEntry.latestTimeOutTime == null) {
                continue;
            }
            LocalTime overlapStart = workEntry.earliestTimeInTime.isAfter(window.start)
                    ? workEntry.earliestTimeInTime
                    : window.start;
            LocalTime overlapEnd = workEntry.latestTimeOutTime.isBefore(window.end)
                    ? workEntry.latestTimeOutTime
                    : window.end;
            if (overlapEnd.isAfter(overlapStart)) {
                payableMinutes += (int) Duration.between(overlapStart, overlapEnd).toMinutes();
            }
        }

        double expectedHours = minutesToHours(expectedMinutes);
        double payableHours = minutesToHours(payableMinutes);
        double workedHours = minutesToHours(workedMinutesTotal);
        double overtimeHours = minutesToHours(overtimeMinutes);
        double salary = employee.getSalary() == null ? 0.0 : employee.getSalary();

        double perHourSalary = expectedHours > 0 ? salary / expectedHours : 0.0;
        double netSalary = expectedHours > 0 ? payableHours * perHourSalary : 0.0;
        if (netSalary < 0) {
            netSalary = 0.0;
        }

        double missingHours = Math.max(0.0, expectedHours - payableHours);

        return new PayrollResult(
                employeeId,
                year,
                month,
                firstDate.lengthOfMonth(),
                scheduledDays,
                holidayDaysFull,
                holidayDaysHalf,
                paidLeaveDays,
                paidCasualDays,
                unpaidCasualDays,
                unpaidLeaveDays,
                workedDays,
                expectedHours,
                payableHours,
                workedHours,
                perHourSalary,
                missingHours,
                netSalary,
                overtimeHours);
    }

    @Transactional(readOnly = true)
    public List<PayrollDebugEntry> calculateMonthlyPayrollDebug(Long employeeId, int month, int year, Long clientIdOverride) {
        schemaMaintenanceService.ensureEmployeeSchema();
        Optional<Employee> employeeOpt = employeeRepository.findById(employeeId);
        if (employeeOpt.isEmpty()) {
            return List.of();
        }

        Employee employee = employeeOpt.get();
        Long effectiveClientId = clientIdOverride != null ? clientIdOverride : employee.getClientId();
        LocalDate firstDate = LocalDate.of(year, month, 1);
        LocalDate lastDate = firstDate.withDayOfMonth(firstDate.lengthOfMonth());

        LeavePolicy policy = resolveLeavePolicy(employee);
        Map<LocalDate, ShiftWindow> shiftWindowsByDate =
                buildShiftWindows(employee, policy, firstDate, lastDate);
        Map<LocalDate, Integer> scheduledMinutesByDate = new HashMap<>();
        for (Map.Entry<LocalDate, ShiftWindow> entry : shiftWindowsByDate.entrySet()) {
            scheduledMinutesByDate.put(entry.getKey(), entry.getValue().minutes);
        }

        Map<LocalDate, Double> holidayFractionByDate =
                buildHolidayMap(effectiveClientId, firstDate, lastDate);
        LeaveBuckets leaveBuckets = buildLeaveBuckets(
                employeeId,
                month,
                year,
                holidayFractionByDate,
                scheduledMinutesByDate);
        int casualBalance = employee.getCasualLeaveBalance() == null ? 0 : employee.getCasualLeaveBalance();
        leaveBuckets.applyCasualBalance(scheduledMinutesByDate, casualBalance);

        WorkSummary workSummary = buildWorkedMinutesByDate(employeeId, month, year);

        List<PayrollDebugEntry> rows = new ArrayList<>();
        for (Map.Entry<LocalDate, ShiftWindow> entry : shiftWindowsByDate.entrySet()) {
            LocalDate date = entry.getKey();
            ShiftWindow window = entry.getValue();
            int scheduledMinutes = window == null ? 0 : window.minutes;

            boolean holiday = holidayFractionByDate.containsKey(date);
            boolean paidLeave = leaveBuckets.paidLeaveDates.contains(date);
            boolean unpaidLeave = leaveBuckets.unpaidLeaveDates.contains(date);
            boolean paidCasual = leaveBuckets.paidCasualDates.contains(date);
            boolean unpaidCasual = leaveBuckets.unpaidCasualDates.contains(date);

            WorkEntry workEntry = workSummary.workEntries.get(date);
            int payableMinutes = 0;
            int overtimeMinutes = 0;

            if (!holiday && !paidLeave && !unpaidLeave && !paidCasual && !unpaidCasual) {
                int workedMinutes = workSummary.workedMinutesByDate.getOrDefault(date, 0);
                if (scheduledMinutes <= 0) {
                    payableMinutes = workedMinutes;
                } else if (workEntry != null && workEntry.earliestTimeInTime != null && workEntry.latestTimeOutTime != null) {
                    LocalTime overlapStart = workEntry.earliestTimeInTime.isAfter(window.start)
                            ? workEntry.earliestTimeInTime
                            : window.start;
                    LocalTime overlapEnd = workEntry.latestTimeOutTime.isBefore(window.end)
                            ? workEntry.latestTimeOutTime
                            : window.end;
                    if (overlapEnd.isAfter(overlapStart)) {
                        payableMinutes = (int) Duration.between(overlapStart, overlapEnd).toMinutes();
                    }

                    if (workSummary.overtimeApprovedDates.contains(date) && workEntry.latestTimeOutTime.isAfter(window.end)) {
                        LocalTime overtimeStart = workEntry.earliestTimeInTime.isAfter(window.end)
                                ? workEntry.earliestTimeInTime
                                : window.end;
                        overtimeMinutes = (int) Duration.between(overtimeStart, workEntry.latestTimeOutTime).toMinutes();
                    }
                }
            }

            rows.add(new PayrollDebugEntry(
                    date.toString(),
                    window != null && window.start != null ? window.start.toString() : null,
                    window != null && window.end != null ? window.end.toString() : null,
                    workEntry != null && workEntry.earliestTimeInTime != null ? workEntry.earliestTimeInTime.toString() : null,
                    workEntry != null && workEntry.latestTimeOutTime != null ? workEntry.latestTimeOutTime.toString() : null,
                    scheduledMinutes,
                    payableMinutes,
                    overtimeMinutes,
                    workSummary.overtimeApprovedDates.contains(date),
                    holiday,
                    paidLeave || paidCasual,
                    unpaidLeave || unpaidCasual
            ));
        }

        return rows;
    }

    private Map<LocalDate, Integer> buildSchedule(
            Employee employee,
            LeavePolicy policy,
            LocalDate start,
            LocalDate end) {
        Map<AdditionalWorkingDayType, EmployeeAdditionalWorkingDay> additionalMap = new EnumMap<>(AdditionalWorkingDayType.class);
        if (employee.getAdditionalWorkingDays() != null) {
            for (EmployeeAdditionalWorkingDay day : employee.getAdditionalWorkingDays()) {
                if (day != null && day.getDayType() != null) {
                    additionalMap.putIfAbsent(day.getDayType(), day);
                }
            }
        }

        Map<LocalDate, Integer> schedule = new LinkedHashMap<>();
        Map<LocalDate, Integer> saturdayIndex = buildWeekdayIndex(start, end, DayOfWeek.SATURDAY);
        Map<LocalDate, Integer> sundayIndex = buildWeekdayIndex(start, end, DayOfWeek.SUNDAY);

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            boolean isWeekOff = policy.isWeekOff(dayOfWeek);

            EmployeeAdditionalWorkingDay override = resolveAdditionalOverride(
                    date,
                    saturdayIndex,
                    sundayIndex,
                    additionalMap);

            int scheduledMinutes;
            if (override != null) {
                scheduledMinutes = resolveShiftMinutes(override.getTimeIn(), override.getTimeOut());
            } else if (isWeekOff) {
                scheduledMinutes = 0;
            } else {
                scheduledMinutes = resolveShiftMinutes(employee.getShiftStartTime(), employee.getShiftEndTime());
            }

            schedule.put(date, scheduledMinutes);
        }
        return schedule;
    }

    private EmployeeAdditionalWorkingDay resolveAdditionalOverride(
            LocalDate date,
            Map<LocalDate, Integer> saturdayIndex,
            Map<LocalDate, Integer> sundayIndex,
            Map<AdditionalWorkingDayType, EmployeeAdditionalWorkingDay> additionalMap) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY) {
            Integer index = saturdayIndex.get(date);
            if (index != null) {
                boolean isOdd = index % 2 == 1;
                AdditionalWorkingDayType type = isOdd
                        ? AdditionalWorkingDayType.ODD_SATURDAY
                        : AdditionalWorkingDayType.EVEN_SATURDAY;
                return additionalMap.get(type);
            }
        } else if (dayOfWeek == DayOfWeek.SUNDAY) {
            Integer index = sundayIndex.get(date);
            if (index != null) {
                boolean isOdd = index % 2 == 1;
                AdditionalWorkingDayType type = isOdd
                        ? AdditionalWorkingDayType.ODD_SUNDAY
                        : AdditionalWorkingDayType.EVEN_SUNDAY;
                return additionalMap.get(type);
            }
        }
        return null;
    }

    private Map<LocalDate, Integer> buildWeekdayIndex(LocalDate start, LocalDate end, DayOfWeek target) {
        Map<LocalDate, Integer> indexMap = new HashMap<>();
        int count = 0;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == target) {
                count++;
                indexMap.put(date, count);
            }
        }
        return indexMap;
    }

    private Map<LocalDate, Double> buildHolidayMap(Long clientId, LocalDate start, LocalDate end) {
        if (clientId == null) {
            return Collections.emptyMap();
        }
        Map<LocalDate, Double> holidayMap = new HashMap<>();
        List<Holiday> holidays = holidayRepository.findByClientIdAndHolidayDateBetweenOrderByHolidayDateAsc(
                clientId,
                start,
                end);
        for (Holiday holiday : holidays) {
            if (holiday == null || holiday.getHolidayDate() == null) {
                continue;
            }
            double fraction = "HALF".equalsIgnoreCase(holiday.getHolidayType()) ? 0.5 : 1.0;
            holidayMap.put(holiday.getHolidayDate(), fraction);
        }
        return holidayMap;
    }

    private LeaveBuckets buildLeaveBuckets(
            Long employeeId,
            int month,
            int year,
            Map<LocalDate, Double> holidayMap,
            Map<LocalDate, Integer> scheduledMinutesByDate) {
        List<LeavePermission> approvedLeaves =
                leavePermissionRepository.findByEmployeeIdAndStatus(employeeId, "approved");

        Set<LocalDate> paidLeaveDates = new HashSet<>();
        Set<LocalDate> unpaidLeaveDates = new HashSet<>();
        List<LocalDate> casualLeaveDates = new ArrayList<>();

        for (LeavePermission leave : approvedLeaves) {
            LeaveCategory category = resolveLeaveCategory(leave.getLeaveType());
            if (category == LeaveCategory.IGNORE) {
                continue;
            }

            for (LocalDate date : expandLeaveDates(leave, month, year)) {
                if (holidayMap.containsKey(date)) {
                    continue;
                }
                if (scheduledMinutesByDate != null) {
                    Integer scheduledMinutes = scheduledMinutesByDate.get(date);
                    if (scheduledMinutes == null || scheduledMinutes <= 0) {
                        continue;
                    }
                }
                if (category == LeaveCategory.CASUAL) {
                    casualLeaveDates.add(date);
                } else if (category == LeaveCategory.PAID) {
                    paidLeaveDates.add(date);
                } else if (category == LeaveCategory.UNPAID) {
                    unpaidLeaveDates.add(date);
                }
            }
        }

        Collections.sort(casualLeaveDates);
        List<LocalDate> uniqueCasualDates = new ArrayList<>(new LinkedHashSet<>(casualLeaveDates));
        return new LeaveBuckets(paidLeaveDates, unpaidLeaveDates, uniqueCasualDates);
    }

    private WorkSummary buildWorkedMinutesByDate(
            Long employeeId,
            int month,
            int year) {
        List<AttendanceRecord> records = attendanceRecordRepository.findByEmployeeId(employeeId);
        Map<LocalDate, Integer> workedMinutesByDate = new HashMap<>();
        Set<LocalDate> overtimeApprovedDates = new HashSet<>();
        Map<LocalDate, WorkEntry> workEntries = new HashMap<>();

        for (AttendanceRecord record : records) {
            LocalDate date = parseDbDate(record.getDate());
            if (date == null || date.getMonthValue() != month || date.getYear() != year) {
                continue;
            }
            if (record.getTimeIn() == null || record.getTimeOut() == null) {
                continue;
            }
            if (!record.getTimeOut().isAfter(record.getTimeIn())) {
                continue;
            }
            LocalDateTime normalizedIn = normalizeToMinute(record.getTimeIn());
            LocalDateTime normalizedOut = normalizeToMinute(record.getTimeOut());
            if (normalizedIn == null || normalizedOut == null) {
                continue;
            }
            int minutes = (int) Duration.between(normalizedIn, normalizedOut).toMinutes();

            if (minutes <= 0) {
                continue;
            }
            workedMinutesByDate.merge(date, minutes, Integer::sum);
            WorkEntry entry = workEntries.getOrDefault(date, new WorkEntry());
            if (entry.earliestTimeIn == null || normalizedIn.isBefore(entry.earliestTimeIn)) {
                entry.earliestTimeIn = normalizedIn;
                entry.earliestTimeInTime = normalizedIn.toLocalTime();
            }
            if (entry.latestTimeOut == null || normalizedOut.isAfter(entry.latestTimeOut)) {
                entry.latestTimeOut = normalizedOut;
                entry.latestTimeOutTime = normalizedOut.toLocalTime();
            }
            entry.totalWorkedMinutes += minutes;
            workEntries.put(date, entry);
        }

        List<OvertimeRequest> approvedRequests =
                overtimeRequestRepository.findByEmployeeIdAndStatus(employeeId, OvertimeRequestStatus.APPROVED);
        for (OvertimeRequest request : approvedRequests) {
            if (request == null || request.getDate() == null) {
                continue;
            }
            LocalDate requestDate = parseDbDate(request.getDate());
            if (requestDate == null || requestDate.getMonthValue() != month || requestDate.getYear() != year) {
                continue;
            }
            overtimeApprovedDates.add(requestDate);
        }

        return new WorkSummary(workedMinutesByDate, overtimeApprovedDates, workEntries);
    }

    private LeavePolicy resolveLeavePolicy(Employee employee) {
        String policyRaw = employee.getLeavePolicyType();
        String weekOffRaw = employee.getWeekOff();

        LeavePolicyType policyType = LeavePolicyType.WEEKOFF;
        if (policyRaw != null && !policyRaw.isBlank()) {
            String normalized = policyRaw.trim().toUpperCase();
            if (normalized.contains("WEEKEND")) {
                policyType = LeavePolicyType.WEEKEND_OFF;
            } else if (normalized.contains("SAT") && normalized.contains("SUN")) {
                policyType = LeavePolicyType.WEEKEND_OFF;
            }
        } else if (weekOffRaw != null) {
            String normalized = weekOffRaw.trim().toUpperCase();
            if (normalized.contains("SAT") && normalized.contains("SUN")) {
                policyType = LeavePolicyType.WEEKEND_OFF;
            } else if ("WEEKEND".equals(normalized) || "WEEKEND_OFF".equals(normalized)) {
                policyType = LeavePolicyType.WEEKEND_OFF;
            }
        }

        DayOfWeek weekOffDay = parseDayOfWeek(weekOffRaw);
        if (weekOffDay == null) {
            weekOffDay = DayOfWeek.SUNDAY;
        }
        return new LeavePolicy(policyType, weekOffDay);
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

    private LeaveCategory resolveLeaveCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return LeaveCategory.UNPAID;
        }
        String normalized = raw.trim().toUpperCase();
        if (normalized.contains("PERMISSION")) {
            return LeaveCategory.IGNORE;
        }
        if ("CL".equals(normalized) || normalized.contains("CASUAL")) {
            return LeaveCategory.CASUAL;
        }
        if (normalized.contains("PAID")) {
            return LeaveCategory.PAID;
        }
        if (normalized.contains("UNPAID") || normalized.contains("LOP") || normalized.contains("LOSS")) {
            return LeaveCategory.UNPAID;
        }
        return LeaveCategory.UNPAID;
    }

    private List<LocalDate> expandLeaveDates(LeavePermission leave, int month, int year) {
        List<LocalDate> dates = new ArrayList<>();

        LocalDate start = parseDbDate(leave.getStartDate());
        LocalDate end = parseDbDate(leave.getEndDate());
        if (start == null || end == null) {
            LocalDate single = parseDbDate(leave.getDate());
            if (single != null) {
                dates.add(single);
            }
        } else {
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                dates.add(date);
            }
        }

        List<LocalDate> filtered = new ArrayList<>();
        for (LocalDate date : dates) {
            if (date.getMonthValue() == month && date.getYear() == year) {
                filtered.add(date);
            }
        }
        return filtered;
    }

    private LocalDate parseDbDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), DB_DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private int resolveShiftMinutes(String startRaw, String endRaw) {
        LocalTime start = parseFlexibleTime(startRaw).orElse(DEFAULT_SHIFT_START);
        LocalTime end = parseFlexibleTime(endRaw).orElse(DEFAULT_SHIFT_END);
        int minutes = (int) Duration.between(start, end).toMinutes();
        return Math.max(minutes, 0);
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

    private double minutesToHours(int minutes) {
        return Math.round((minutes / 60.0) * 100.0) / 100.0;
    }

    private enum LeavePolicyType {
        WEEKOFF,
        WEEKEND_OFF
    }

    private enum LeaveCategory {
        CASUAL,
        PAID,
        UNPAID,
        IGNORE
    }

    private record LeavePolicy(LeavePolicyType type, DayOfWeek weekOffDay) {
        boolean isWeekOff(DayOfWeek day) {
            if (type == LeavePolicyType.WEEKEND_OFF) {
                return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
            }
            return day == weekOffDay;
        }
    }

    private static class LeaveBuckets {
        private final Set<LocalDate> paidLeaveDates;
        private final Set<LocalDate> unpaidLeaveDates;
        private final List<LocalDate> casualLeaveDates;
        private final Set<LocalDate> paidCasualDates = new HashSet<>();
        private final Set<LocalDate> unpaidCasualDates = new HashSet<>();

        private LeaveBuckets(Set<LocalDate> paidLeaveDates,
                             Set<LocalDate> unpaidLeaveDates,
                             List<LocalDate> casualLeaveDates) {
            this.paidLeaveDates = paidLeaveDates;
            this.unpaidLeaveDates = unpaidLeaveDates;
            this.casualLeaveDates = casualLeaveDates;
        }

        void applyCasualBalance(Map<LocalDate, Integer> scheduledMinutesByDate, int balance) {
            int used = 0;
            for (LocalDate date : casualLeaveDates) {
                Integer scheduledMinutes = scheduledMinutesByDate.get(date);
                if (scheduledMinutes == null || scheduledMinutes <= 0) {
                    continue;
                }
                if (used < balance) {
                    paidCasualDates.add(date);
                    used++;
                } else {
                    unpaidCasualDates.add(date);
                }
            }
        }
    }

    private static class WorkSummary {
        private final Map<LocalDate, Integer> workedMinutesByDate;
        private final Set<LocalDate> overtimeApprovedDates;
        private final Map<LocalDate, WorkEntry> workEntries;

        private WorkSummary(Map<LocalDate, Integer> workedMinutesByDate,
                            Set<LocalDate> overtimeApprovedDates,
                            Map<LocalDate, WorkEntry> workEntries) {
            this.workedMinutesByDate = workedMinutesByDate;
            this.overtimeApprovedDates = overtimeApprovedDates;
            this.workEntries = workEntries;
        }
    }

    private static class WorkEntry {
        private LocalDateTime earliestTimeIn;
        private LocalDateTime latestTimeOut;
        private LocalTime earliestTimeInTime;
        private LocalTime latestTimeOutTime;
        private int totalWorkedMinutes = 0;
    }

    private Map<LocalDate, ShiftWindow> buildShiftWindows(
            Employee employee,
            LeavePolicy policy,
            LocalDate start,
            LocalDate end) {
        Map<AdditionalWorkingDayType, EmployeeAdditionalWorkingDay> additionalMap = new EnumMap<>(AdditionalWorkingDayType.class);
        if (employee.getAdditionalWorkingDays() != null) {
            for (EmployeeAdditionalWorkingDay day : employee.getAdditionalWorkingDays()) {
                if (day != null && day.getDayType() != null) {
                    additionalMap.putIfAbsent(day.getDayType(), day);
                }
            }
        }

        Map<LocalDate, ShiftWindow> schedule = new LinkedHashMap<>();
        Map<LocalDate, Integer> saturdayIndex = buildWeekdayIndex(start, end, DayOfWeek.SATURDAY);
        Map<LocalDate, Integer> sundayIndex = buildWeekdayIndex(start, end, DayOfWeek.SUNDAY);

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            boolean isWeekOff = policy.isWeekOff(dayOfWeek);

            EmployeeAdditionalWorkingDay override = resolveAdditionalOverride(
                    date,
                    saturdayIndex,
                    sundayIndex,
                    additionalMap);

            if (override != null) {
                LocalTime startTime = parseFlexibleTime(override.getTimeIn()).orElse(DEFAULT_SHIFT_START);
                LocalTime endTime = parseFlexibleTime(override.getTimeOut()).orElse(DEFAULT_SHIFT_END);
                int minutes = Math.max((int) Duration.between(startTime, endTime).toMinutes(), 0);
                schedule.put(date, new ShiftWindow(startTime, endTime, minutes));
                continue;
            }

            if (isWeekOff) {
                schedule.put(date, new ShiftWindow(null, null, 0));
                continue;
            }

            LocalTime startTime = parseFlexibleTime(employee.getShiftStartTime()).orElse(DEFAULT_SHIFT_START);
            LocalTime endTime = parseFlexibleTime(employee.getShiftEndTime()).orElse(DEFAULT_SHIFT_END);
            int minutes = Math.max((int) Duration.between(startTime, endTime).toMinutes(), 0);
            schedule.put(date, new ShiftWindow(startTime, endTime, minutes));
        }
        return schedule;
    }

    private static class ShiftWindow {
        private final LocalTime start;
        private final LocalTime end;
        private final int minutes;

        private ShiftWindow(LocalTime start, LocalTime end, int minutes) {
            this.start = start;
            this.end = end;
            this.minutes = minutes;
        }
    }

    public record PayrollDebugEntry(
            String date,
            String shiftStart,
            String shiftEnd,
            String actualIn,
            String actualOut,
            int scheduledMinutes,
            int payableMinutes,
            int overtimeMinutes,
            boolean overtimeApproved,
            boolean holiday,
            boolean paidLeave,
            boolean unpaidLeave
    ) {
    }

    private LocalDateTime normalizeToMinute(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.withSecond(0).withNano(0);
    }

    public record PayrollResult(
            Long employeeId,
            int year,
            int month,
            int daysInMonth,
            int scheduledDays,
            int holidayDaysFull,
            int holidayDaysHalf,
            int paidLeaveDays,
            int paidCasualDays,
            int unpaidCasualDays,
            int unpaidLeaveDays,
            int workedDays,
            double expectedHours,
            double payableHours,
            double workedHours,
            double perHourSalary,
            double missingHours,
            double netSalary,
            double overtimeHours) {
        public static PayrollResult empty() {
            return new PayrollResult(null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
