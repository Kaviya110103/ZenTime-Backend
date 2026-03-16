package com.example.demo.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.demo.MODELS.HolidayType;
import com.example.demo.MODELS.PublicHoliday;
import com.example.demo.repo.PublicHolidayRepository;

@Service
public class PublicHolidayService {

    private final PublicHolidayRepository publicHolidayRepository;

    public PublicHolidayService(PublicHolidayRepository publicHolidayRepository) {
        this.publicHolidayRepository = publicHolidayRepository;
    }

    public List<PublicHoliday> getHolidaysForClient(Long clientId) {
        return publicHolidayRepository.findByClientIdOrderByHolidayDateAsc(clientId);
    }

    public Optional<PublicHoliday> getHoliday(Long clientId, LocalDate date) {
        if (clientId == null || date == null) {
            return Optional.empty();
        }
        return publicHolidayRepository.findByClientIdAndHolidayDate(clientId, date);
    }

    public boolean isHoliday(Long clientId, LocalDate date) {
        return publicHolidayRepository.existsByClientIdAndHolidayDate(clientId, date);
    }

    public List<PublicHoliday> getHolidaysByDate(LocalDate date) {
        return publicHolidayRepository.findByHolidayDate(date);
    }

    public String buildDayStatus(PublicHoliday holiday) {
        if (holiday == null) {
            return "Public Holiday";
        }
        String typeLabel = holiday.getHolidayType() == HolidayType.HALF ? "Half Day" : "Full Day";
        return "Public Holiday - " + holiday.getHolidayName() + " (" + typeLabel + ")";
    }
}
