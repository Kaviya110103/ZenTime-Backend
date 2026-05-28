package com.example.demo.service;

import com.example.demo.MODELS.*;
import com.example.demo.repo.AttendanceRecordRepository;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.repo.HolidayRepository;
import com.example.demo.repo.LeavePolicyRepository;
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
    private static final String SWAP_WEEKOFF_TYPE = "swap weekoff";

    private final EmployeeRepository employeeRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final LeavePermissionRepository leavePermissionRepository;
    private final HolidayRepository holidayRepository;
    private final LeavePolicyRepository leavePolicyRepository;
    private final SchemaMaintenanceService schemaMaintenanceService;
    private final OvertimeRequestRepository overtimeRequestRepository;

    public PayrollCalculationService(
            EmployeeRepository employeeRepository,
            AttendanceRecordRepository attendanceRecordRepository,
            LeavePermissionRepository leavePermissionRepository,
            HolidayRepository holidayRepository,
            LeavePolicyRepository leavePolicyRepository,
            SchemaMaintenanceService schemaMaintenanceService,
            OvertimeRequestRepository overtimeRequestRepository) {
        this.employeeRepository = employeeRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.leavePermissionRepository = leavePermissionRepository;
        this.holidayRepository = holidayRepository;
        this.leavePolicyRepository = leavePolicyRepository;
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
        Set<LocalDate> approvedSwapWeekoffDates =
                resolveApprovedSwapWeekoffDates(employeeId, month, year);

        SchedulePolicy policy = resolveSchedulePolicy(employee);
        LeaveAllowancePolicy leaveAllowancePolicy = resolveLeaveAllowancePolicy(employee);
        Map<LocalDate, ShiftWindow> shiftWindowsByDate =
                buildShiftWindows(employee, policy, firstDate, lastDate, approvedSwapWeekoffDates);
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
        int casualBalance = leaveAllowancePolicy.casualLeaveAllowed();
        leaveBuckets.applyCasualBalance(scheduledMinutesByDate, casualBalance);

        WorkSummary workSummary =
                buildWorkedMinutesByDate(employee, month, year);
        Map<LocalDate, Integer> workedMinutesByDate = workSummary.workedMinutesByDate;

        int overtimeMinutes = 0;
        for (Map.Entry<LocalDate, Integer> entry : workedMinutesByDate.entrySet()) {
            LocalDate date = entry.getKey();
            WorkEntry workEntry = workSummary.workEntries.get(date);
            ShiftWindow window = shiftWindowsByDate.get(date);
            if (workEntry != null
                    && workEntry.recordedOvertimeMinutes > 0
                    && workSummary.overtimeApprovedDates.contains(date)) {
                overtimeMinutes += workEntry.recordedOvertimeMinutes;
                continue;
            }
            if (window == null || window.minutes <= 0) {
                continue; // Week-off work counts as regular payable, not overtime.
            }
            if (!workSummary.overtimeApprovedDates.contains(date)) {
                continue;
            }
            if (workEntry == null || workEntry.earliestTimeInTime == null || workEntry.latestTimeOutTime == null) {
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
        int paidCasualDays = 0;
        int unpaidCasualDays = 0;
        int workedDays = 0;

        int expectedMinutes = 0;

        Set<LocalDate> workedDates = new HashSet<>();
        Set<LocalDate> weekOffDates = new HashSet<>();
        Set<LocalDate> publicHolidayDates = new HashSet<>();
        Set<LocalDate> paidLeaveDates = new HashSet<>();
        Set<LocalDate> paidCasualDates = new HashSet<>();

        for (Map.Entry<LocalDate, Integer> entry : scheduledMinutesByDate.entrySet()) {
            LocalDate date = entry.getKey();
            int scheduledMinutes = Math.max(0, entry.getValue() == null ? 0 : entry.getValue());

            if (scheduledMinutes > 0) {
                scheduledDays++;
                expectedMinutes += scheduledMinutes;
            } else {
                weekOffDates.add(date);
            }

            Double holidayFraction = holidayFractionByDate.get(date);
            if (holidayFraction != null && holidayFraction > 0) {
                if (holidayFraction >= 1.0) {
                    holidayDaysFull++;
                } else {
                    holidayDaysHalf++;
                }
                publicHolidayDates.add(date);
            }

            if (leaveBuckets.paidLeaveDates.contains(date)) {
                paidLeaveDays++;
                paidLeaveDates.add(date);
            }
            if (leaveBuckets.paidCasualDates.contains(date)) {
                paidCasualDays++;
                paidCasualDates.add(date);
            }
            if (leaveBuckets.unpaidCasualDates.contains(date)) {
                unpaidCasualDays++;
            }
        }

        for (Map.Entry<LocalDate, Integer> entry : workedMinutesByDate.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                workedDates.add(entry.getKey());
            }
        }
        workedDays = workedDates.size();

        Set<LocalDate> payableDates = new HashSet<>();
        payableDates.addAll(workedDates);
        payableDates.addAll(weekOffDates);
        payableDates.addAll(publicHolidayDates);
        payableDates.addAll(paidCasualDates);
        // Keep approved paid-leave days paid as well.
        payableDates.addAll(paidLeaveDates);

        int daysInMonth = firstDate.lengthOfMonth();
        int payableDays = Math.min(daysInMonth, payableDates.size());
        int derivedUnpaidLeaveDays = Math.max(0, daysInMonth - payableDays);

        int absentDays = derivedUnpaidLeaveDays;
        int absentMinutes = 0;
        int payableMinutes = 0;
        for (LocalDate date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
            int scheduledMinutes = Math.max(0, scheduledMinutesByDate.getOrDefault(date, 0));
            if (payableDates.contains(date)) {
                payableMinutes += scheduledMinutes;
            } else {
                absentMinutes += scheduledMinutes;
            }
        }

        int approvedPermissionMinutes = Math.max(
                calculateApprovedPermissionMinutes(employeeId, month, year),
                workSummary.totalPermissionUsedMinutes());
        int allowedPermissionMinutes = resolveAllowedPermissionMinutes(employee);
        int payablePermissionMinutes = Math.min(approvedPermissionMinutes, allowedPermissionMinutes);
        int actualWorkedMinutes = Math.max(0, workSummary.totalWorkedMinutes());
        int adjustedWorkedMinutes = Math.min(expectedMinutes, actualWorkedMinutes + payablePermissionMinutes);

        double expectedHours = minutesToHours(expectedMinutes);
        double payableHours = minutesToHours(Math.min(expectedMinutes, payableMinutes + payablePermissionMinutes));
        double workedHours = minutesToHours(adjustedWorkedMinutes);
        double overtimeHours = minutesToHours(overtimeMinutes);
        double salary = employee.getSalary() == null ? 0.0 : employee.getSalary();
        double perDaySalary = daysInMonth > 0 ? salary / daysInMonth : 0.0;
        double perMinuteSalary = expectedMinutes > 0 ? salary / expectedMinutes : 0.0;
        double perHourSalary = perMinuteSalary * 60.0;
        double netSalary = salary - (derivedUnpaidLeaveDays * perDaySalary);
        if (netSalary < 0) {
            netSalary = 0.0;
        }

        double missingHours = Math.max(0.0, minutesToHours(absentMinutes));

        return new PayrollResult(
                employeeId,
                year,
                month,
                daysInMonth,
                scheduledDays,
                holidayDaysFull,
                holidayDaysHalf,
                paidLeaveDays,
                paidCasualDays,
                unpaidCasualDays,
                derivedUnpaidLeaveDays,
                workedDays,
                absentDays,
                absentMinutes,
                expectedHours,
                payableHours,
                workedHours,
                perMinuteSalary,
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
        Set<LocalDate> approvedSwapWeekoffDates =
                resolveApprovedSwapWeekoffDates(employeeId, month, year);

        SchedulePolicy policy = resolveSchedulePolicy(employee);
        LeaveAllowancePolicy leaveAllowancePolicy = resolveLeaveAllowancePolicy(employee);
        Map<LocalDate, ShiftWindow> shiftWindowsByDate =
                buildShiftWindows(employee, policy, firstDate, lastDate, approvedSwapWeekoffDates);
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
        int casualBalance = leaveAllowancePolicy.casualLeaveAllowed();
        leaveBuckets.applyCasualBalance(scheduledMinutesByDate, casualBalance);

        WorkSummary workSummary = buildWorkedMinutesByDate(employee, month, year);

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
            int workedMinutes = workSummary.workedMinutesByDate.getOrDefault(date, 0);
            boolean workedDay = workedMinutes > 0;
            boolean weekOff = scheduledMinutes <= 0;
            boolean payableDay = workedDay || weekOff || holiday || paidLeave || paidCasual;
            int payableMinutes = 0;
            int overtimeMinutes = 0;

            if (payableDay) {
                payableMinutes = Math.max(0, scheduledMinutes);
            }

            if (workEntry != null
                    && workEntry.recordedOvertimeMinutes > 0
                    && workSummary.overtimeApprovedDates.contains(date)) {
                overtimeMinutes = workEntry.recordedOvertimeMinutes;
            } else if (workSummary.overtimeApprovedDates.contains(date)
                    && window != null
                    && window.end != null
                    && workEntry != null
                    && workEntry.earliestTimeInTime != null
                    && workEntry.latestTimeOutTime != null
                    && workEntry.latestTimeOutTime.isAfter(window.end)) {
                LocalTime overtimeStart = workEntry.earliestTimeInTime.isAfter(window.end)
                        ? workEntry.earliestTimeInTime
                        : window.end;
                if (workEntry.latestTimeOutTime.isAfter(overtimeStart)) {
                    overtimeMinutes = (int) Duration.between(overtimeStart, workEntry.latestTimeOutTime).toMinutes();
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
                    (unpaidLeave || unpaidCasual) && !payableDay
            ));
        }

        return rows;
    }

    private Map<LocalDate, Integer> buildSchedule(
            Employee employee,
            SchedulePolicy policy,
            LocalDate start,
            LocalDate end) {
        Map<AdditionalWorkingDayType, EmployeeAdditionalWorkingDay> additionalMap = new EnumMap<>(AdditionalWorkingDayType.class);
        if (employee != null && employee.getAdditionalWorkingDays() != null) {
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
                LocalTime shiftStart = PayrollCompatibilityDefaults.resolveShiftStart(employee);
                LocalTime shiftEnd = PayrollCompatibilityDefaults.resolveShiftEnd(employee);
                scheduledMinutes = Math.max((int) Duration.between(shiftStart, shiftEnd).toMinutes(), 0);
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
            Employee employee,
            int month,
            int year) {
        Long employeeId = employee == null ? null : employee.getId();
        if (employeeId == null) {
            return new WorkSummary(new HashMap<>(), new HashSet<>(), new HashMap<>(), 0, 0);
        }
        List<AttendanceRecord> records = attendanceRecordRepository.findByEmployeeId(employeeId);
        Map<LocalDate, Integer> workedMinutesByDate = new HashMap<>();
        Set<LocalDate> overtimeApprovedDates = new HashSet<>();
        Map<LocalDate, WorkEntry> workEntries = new HashMap<>();

        for (AttendanceRecord record : records) {
            LocalDate date = parseDbDate(record.getDate());
            if (date == null || date.getMonthValue() != month || date.getYear() != year) {
                continue;
            }
            LocalDateTime normalizedIn = normalizeToMinute(record.getTimeIn());
            LocalDateTime normalizedOut = normalizeToMinute(record.getTimeOut());
            int minutes = resolveWorkedMinutes(record, normalizedIn, normalizedOut);

            if (minutes <= 0) {
                continue;
            }
            workedMinutesByDate.merge(date, minutes, Integer::sum);
            WorkEntry entry = workEntries.getOrDefault(date, new WorkEntry());
            if (normalizedIn != null && (entry.earliestTimeIn == null || normalizedIn.isBefore(entry.earliestTimeIn))) {
                entry.earliestTimeIn = normalizedIn;
                entry.earliestTimeInTime = normalizedIn.toLocalTime();
            }
            if (normalizedOut != null && (entry.latestTimeOut == null || normalizedOut.isAfter(entry.latestTimeOut))) {
                entry.latestTimeOut = normalizedOut;
                entry.latestTimeOutTime = normalizedOut.toLocalTime();
            }
            entry.totalWorkedMinutes += minutes;
            entry.recordedOvertimeMinutes += resolveRecordedOvertimeMinutes(record);
            entry.permissionUsedMinutes += resolvePermissionUsedMinutes(record);
            workEntries.put(date, entry);
            if (Boolean.TRUE.equals(record.getOvertimeApproved()) && entry.recordedOvertimeMinutes > 0) {
                overtimeApprovedDates.add(date);
            }
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

        int totalWorkedMinutes = 0;
        int totalPermissionUsedMinutes = 0;
        for (Integer minutes : workedMinutesByDate.values()) {
            totalWorkedMinutes += minutes == null ? 0 : minutes;
        }
        for (WorkEntry entry : workEntries.values()) {
            totalPermissionUsedMinutes += entry == null ? 0 : entry.permissionUsedMinutes;
        }
        return new WorkSummary(workedMinutesByDate, overtimeApprovedDates, workEntries, totalWorkedMinutes, totalPermissionUsedMinutes);
    }

    private int resolveWorkedMinutes(AttendanceRecord record, LocalDateTime normalizedIn, LocalDateTime normalizedOut) {
        if (record == null) {
            return 0;
        }
        // New attendance rows may carry worked_hours directly. Legacy rows fall back to timeIn/timeOut.
        if (record.getWorkedHours() != null && record.getWorkedHours() > 0) {
            return (int) Math.round(record.getWorkedHours() * 60.0);
        }
        if (normalizedIn == null || normalizedOut == null || !normalizedOut.isAfter(normalizedIn)) {
            return 0;
        }
        return (int) Duration.between(normalizedIn, normalizedOut).toMinutes();
    }

    private int resolveRecordedOvertimeMinutes(AttendanceRecord record) {
        if (record == null || record.getOvertime() == null || record.getOvertime() <= 0) {
            return 0;
        }
        return (int) Math.round(record.getOvertime() * 60.0);
    }

    private int resolvePermissionUsedMinutes(AttendanceRecord record) {
        if (record == null || record.getPermissionUsed() == null || record.getPermissionUsed() <= 0) {
            return 0;
        }
        return (int) Math.round(record.getPermissionUsed() * 60.0);
    }

    private int calculateApprovedPermissionMinutes(Long employeeId, int month, int year) {
        if (employeeId == null) {
            return 0;
        }
        List<LeavePermission> approvedLeaves =
                leavePermissionRepository.findByEmployeeIdAndStatus(employeeId, "approved");
        int totalMinutes = 0;
        for (LeavePermission leave : approvedLeaves) {
            if (leave == null || !isPermissionType(leave.getLeaveType())) {
                continue;
            }
            LocalDate permissionDate = parseDbDate(leave.getDate());
            if (permissionDate == null) {
                permissionDate = parseDbDate(leave.getStartDate());
            }
            if (permissionDate == null
                    || permissionDate.getMonthValue() != month
                    || permissionDate.getYear() != year) {
                continue;
            }
            Optional<LocalTime> startOpt = parseFlexibleTime(leave.getStartTime());
            Optional<LocalTime> endOpt = parseFlexibleTime(leave.getEndTime());
            if (startOpt.isEmpty() || endOpt.isEmpty()) {
                continue;
            }
            LocalTime start = startOpt.get();
            LocalTime end = endOpt.get();
            if (!end.isAfter(start)) {
                continue;
            }
            totalMinutes += (int) Duration.between(start, end).toMinutes();
        }
        return Math.max(0, totalMinutes);
    }

    private int resolveAllowedPermissionMinutes(Employee employee) {
        return (int) Math.round(PayrollCompatibilityDefaults.resolvePermissionHoursAllowed(employee) * 60.0);
    }

    private SchedulePolicy resolveSchedulePolicy(Employee employee) {
        String policyRaw = employee == null ? null : employee.getLeavePolicyType();
        String weekOffRaw = employee == null ? null : employee.getWeekOff();

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
        return new SchedulePolicy(policyType, weekOffDay);
    }

    private LeaveAllowancePolicy resolveLeaveAllowancePolicy(Employee employee) {
        int fallbackCasual = employee != null && employee.getCasualLeaveBalance() != null
                ? Math.max(0, employee.getCasualLeaveBalance())
                : PayrollCompatibilityDefaults.DEFAULT_CASUAL_LEAVE_ALLOWED;
        int fallbackSick = PayrollCompatibilityDefaults.DEFAULT_SICK_LEAVE_ALLOWED;
        int fallbackEarned = PayrollCompatibilityDefaults.DEFAULT_EARNED_LEAVE_ALLOWED;

        if (employee == null || employee.getId() == null) {
            return new LeaveAllowancePolicy(fallbackCasual, fallbackSick, fallbackEarned);
        }

        return leavePolicyRepository.findByEmployee_Id(employee.getId())
                .map(policy -> new LeaveAllowancePolicy(
                        policy.getCasualLeaveAllowed() == null ? fallbackCasual : Math.max(0, policy.getCasualLeaveAllowed()),
                        policy.getSickLeaveAllowed() == null ? fallbackSick : Math.max(0, policy.getSickLeaveAllowed()),
                        policy.getEarnedLeaveAllowed() == null ? fallbackEarned : Math.max(0, policy.getEarnedLeaveAllowed())))
                .orElseGet(() -> new LeaveAllowancePolicy(fallbackCasual, fallbackSick, fallbackEarned));
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
        if (isSwapWeekoffType(raw)) {
            return LeaveCategory.IGNORE;
        }
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
        LocalTime start = parseFlexibleTime(startRaw).orElse(PayrollCompatibilityDefaults.DEFAULT_SHIFT_START);
        LocalTime end = parseFlexibleTime(endRaw).orElse(PayrollCompatibilityDefaults.DEFAULT_SHIFT_END);
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

    private record SchedulePolicy(LeavePolicyType type, DayOfWeek weekOffDay) {
        boolean isWeekOff(DayOfWeek day) {
            if (type == LeavePolicyType.WEEKEND_OFF) {
                return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
            }
            return day == weekOffDay;
        }
    }

    private record LeaveAllowancePolicy(int casualLeaveAllowed, int sickLeaveAllowed, int earnedLeaveAllowed) {
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
            for (LocalDate date : casualLeaveDates) {
                Integer scheduledMinutes = scheduledMinutesByDate.get(date);
                if (scheduledMinutes == null || scheduledMinutes <= 0) {
                    continue;
                }
                // Business rule: approved casual leaves are always paid.
                paidCasualDates.add(date);
            }
        }
    }

    private static class WorkSummary {
        private final Map<LocalDate, Integer> workedMinutesByDate;
        private final Set<LocalDate> overtimeApprovedDates;
        private final Map<LocalDate, WorkEntry> workEntries;
        private final int totalWorkedMinutes;
        private final int totalPermissionUsedMinutes;

        private WorkSummary(Map<LocalDate, Integer> workedMinutesByDate,
                            Set<LocalDate> overtimeApprovedDates,
                            Map<LocalDate, WorkEntry> workEntries,
                            int totalWorkedMinutes,
                            int totalPermissionUsedMinutes) {
            this.workedMinutesByDate = workedMinutesByDate;
            this.overtimeApprovedDates = overtimeApprovedDates;
            this.workEntries = workEntries;
            this.totalWorkedMinutes = totalWorkedMinutes;
            this.totalPermissionUsedMinutes = totalPermissionUsedMinutes;
        }

        private int totalWorkedMinutes() {
            return totalWorkedMinutes;
        }

        private int totalPermissionUsedMinutes() {
            return totalPermissionUsedMinutes;
        }
    }

    private static class WorkEntry {
        private LocalDateTime earliestTimeIn;
        private LocalDateTime latestTimeOut;
        private LocalTime earliestTimeInTime;
        private LocalTime latestTimeOutTime;
        private int totalWorkedMinutes = 0;
        private int recordedOvertimeMinutes = 0;
        private int permissionUsedMinutes = 0;
    }

    private Map<LocalDate, ShiftWindow> buildShiftWindows(
            Employee employee,
            SchedulePolicy policy,
            LocalDate start,
            LocalDate end,
            Set<LocalDate> approvedSwapWeekoffDates) {
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
            boolean isSwapWeekoffDate =
                    approvedSwapWeekoffDates != null && approvedSwapWeekoffDates.contains(date);

            EmployeeAdditionalWorkingDay override = resolveAdditionalOverride(
                    date,
                    saturdayIndex,
                    sundayIndex,
                    additionalMap);

            if (isSwapWeekoffDate) {
                schedule.put(date, new ShiftWindow(null, null, 0));
                continue;
            }

            if (override != null) {
                LocalTime startTime = parseFlexibleTime(override.getTimeIn()).orElse(PayrollCompatibilityDefaults.DEFAULT_SHIFT_START);
                LocalTime endTime = parseFlexibleTime(override.getTimeOut()).orElse(PayrollCompatibilityDefaults.DEFAULT_SHIFT_END);
                int minutes = Math.max((int) Duration.between(startTime, endTime).toMinutes(), 0);
                schedule.put(date, new ShiftWindow(startTime, endTime, minutes));
                continue;
            }

            if (isWeekOff) {
                schedule.put(date, new ShiftWindow(null, null, 0));
                continue;
            }

            LocalTime startTime = PayrollCompatibilityDefaults.resolveShiftStart(employee);
            LocalTime endTime = PayrollCompatibilityDefaults.resolveShiftEnd(employee);
            int minutes = Math.max((int) Duration.between(startTime, endTime).toMinutes(), 0);
            schedule.put(date, new ShiftWindow(startTime, endTime, minutes));
        }
        return schedule;
    }

    private Set<LocalDate> resolveApprovedSwapWeekoffDates(Long employeeId, int month, int year) {
        if (employeeId == null) {
            return Collections.emptySet();
        }
        List<LeavePermission> approvedLeaves =
                leavePermissionRepository.findByEmployeeIdAndStatus(employeeId, "approved");
        Set<LocalDate> swapWeekoffDates = new HashSet<>();
        for (LeavePermission leave : approvedLeaves) {
            if (!isSwapWeekoffType(leave.getLeaveType())) {
                continue;
            }
            swapWeekoffDates.addAll(expandLeaveDates(leave, month, year));
        }
        return swapWeekoffDates;
    }

    private boolean isSwapWeekoffType(String leaveTypeRaw) {
        if (leaveTypeRaw == null) {
            return false;
        }
        return leaveTypeRaw.trim().toLowerCase(Locale.ROOT).equals(SWAP_WEEKOFF_TYPE);
    }

    private boolean isPermissionType(String leaveTypeRaw) {
        if (leaveTypeRaw == null) {
            return false;
        }
        return leaveTypeRaw.trim().equalsIgnoreCase("Permission");
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
            int absentDays,
            int absentMinutes,
            double expectedHours,
            double payableHours,
            double workedHours,
            double perMinuteSalary,
            double perHourSalary,
            double missingHours,
            double netSalary,
            double overtimeHours) {
        public static PayrollResult empty() {
            return new PayrollResult(null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
