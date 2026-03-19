package com.example.demo.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.demo.MODELS.Employee;
import com.example.demo.MODELS.Holiday;
import com.example.demo.MODELS.HolidayRequest;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.repo.HolidayRepository;

@Service
public class HolidayService {

    @Autowired
    private HolidayRepository holidayRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<Holiday> getByClientId(Long clientId) {
        ensureHolidayTableExists();
        return holidayRepository.findByClientIdOrderByHolidayDateAsc(clientId);
    }

    public Holiday createHoliday(Long clientId, HolidayRequest request) {
        ensureHolidayTableExists();
        LocalDate holidayDate = parseIsoDate(request.getHolidayDate());
        String holidayName = validateHolidayName(request.getHolidayName());
        String holidayType = normalizeHolidayType(request.getHolidayType());

        Optional<Holiday> existing = holidayRepository.findByClientIdAndHolidayDate(clientId, holidayDate);
        if (existing.isPresent()) {
            throw new IllegalStateException("Holiday already exists for this date.");
        }

        Holiday holiday = new Holiday();
        holiday.setClientId(clientId);
        holiday.setHolidayDate(holidayDate);
        holiday.setHolidayName(holidayName);
        holiday.setHolidayType(holidayType);
        return holidayRepository.save(holiday);
    }

    public Holiday updateHoliday(Long holidayId, Long clientId, HolidayRequest request) {
        ensureHolidayTableExists();
        Holiday holiday = holidayRepository.findById(holidayId)
                .orElseThrow(() -> new NoSuchElementException("Holiday not found."));

        if (!holiday.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Holiday does not belong to this client.");
        }

        LocalDate holidayDate = parseIsoDate(request.getHolidayDate());
        String holidayName = validateHolidayName(request.getHolidayName());
        String holidayType = normalizeHolidayType(request.getHolidayType());

        Optional<Holiday> duplicate = holidayRepository.findByClientIdAndHolidayDate(clientId, holidayDate);
        if (duplicate.isPresent() && !duplicate.get().getId().equals(holidayId)) {
            throw new IllegalStateException("Holiday already exists for this date.");
        }

        holiday.setHolidayDate(holidayDate);
        holiday.setHolidayName(holidayName);
        holiday.setHolidayType(holidayType);
        return holidayRepository.save(holiday);
    }

    public void deleteHoliday(Long holidayId, Long clientId) {
        ensureHolidayTableExists();
        Holiday holiday = holidayRepository.findById(holidayId)
                .orElseThrow(() -> new NoSuchElementException("Holiday not found."));

        if (!holiday.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Holiday does not belong to this client.");
        }
        holidayRepository.delete(holiday);
    }

    public List<Holiday> getMonthlyHolidaysForEmployee(
            Long employeeId,
            int year,
            int month,
            Long requestedClientId) {
        ensureHolidayTableExists();

        Long effectiveClientId = resolveClientId(employeeId, requestedClientId);
        try {
            LocalDate firstDate = LocalDate.of(year, month, 1);
            LocalDate lastDate = firstDate.withDayOfMonth(firstDate.lengthOfMonth());
            return holidayRepository.findByClientIdAndHolidayDateBetweenOrderByHolidayDateAsc(
                    effectiveClientId,
                    firstDate,
                    lastDate);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Invalid month or year.");
        }
    }

    private Long resolveClientId(Long employeeId, Long requestedClientId) {
        if (requestedClientId == null) {
            Employee employee = employeeRepository.findById(employeeId)
                    .orElseThrow(() -> new NoSuchElementException("Employee not found."));
            return employee.getClientId();
        }

        employeeRepository.findByIdAndClientId(employeeId, requestedClientId)
                .orElseThrow(() -> new NoSuchElementException("Employee not found for provided client."));
        return requestedClientId;
    }

    private LocalDate parseIsoDate(String holidayDate) {
        if (holidayDate == null || holidayDate.isBlank()) {
            throw new IllegalArgumentException("holidayDate is required.");
        }
        try {
            return LocalDate.parse(holidayDate.trim());
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("holidayDate must be in yyyy-MM-dd format.");
        }
    }

    private String validateHolidayName(String holidayName) {
        if (holidayName == null || holidayName.isBlank()) {
            throw new IllegalArgumentException("holidayName is required.");
        }
        return holidayName.trim();
    }

    private String normalizeHolidayType(String holidayType) {
        String value = holidayType == null ? "FULL" : holidayType.trim().toUpperCase();
        if (!"FULL".equals(value) && !"HALF".equals(value)) {
            throw new IllegalArgumentException("holidayType must be FULL or HALF.");
        }
        return value;
    }

    private void ensureHolidayTableExists() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS holidays (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    client_id BIGINT NOT NULL,
                    holiday_date DATE NOT NULL,
                    holiday_name VARCHAR(255) NOT NULL,
                    holiday_type VARCHAR(16) NOT NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_holidays_client_date (client_id, holiday_date)
                )
                """);
    }
}
