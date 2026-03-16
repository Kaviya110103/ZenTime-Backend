package com.example.demo.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.MODELS.PublicHoliday;

@Repository
public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, Long> {
    List<PublicHoliday> findByClientIdOrderByHolidayDateAsc(Long clientId);
    Optional<PublicHoliday> findByClientIdAndHolidayDate(Long clientId, LocalDate holidayDate);
    boolean existsByClientIdAndHolidayDate(Long clientId, LocalDate holidayDate);
    List<PublicHoliday> findByHolidayDate(LocalDate holidayDate);
}
