package com.example.demo.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.MODELS.HolidayType;
import com.example.demo.MODELS.PublicHoliday;
import com.example.demo.repo.PublicHolidayRepository;
import com.example.demo.service.PublicHolidayService;

@RestController
@RequestMapping({"/api/admin/holidays", "/admin/holidays"})
@CrossOrigin(origins = "*")
public class PublicHolidayController {

    private final PublicHolidayRepository publicHolidayRepository;
    private final PublicHolidayService publicHolidayService;

    public PublicHolidayController(PublicHolidayRepository publicHolidayRepository,
                                   PublicHolidayService publicHolidayService) {
        this.publicHolidayRepository = publicHolidayRepository;
        this.publicHolidayService = publicHolidayService;
    }

    @PostMapping
    public ResponseEntity<?> createHoliday(@RequestBody PublicHolidayRequest request,
                                           @RequestParam(value = "clientId", required = false) Long clientIdParam) {
        if (request != null && request.clientId() == null && clientIdParam != null) {
            request = new PublicHolidayRequest(clientIdParam, request.holidayDate(), request.holidayName(), request.holidayType());
        }
        if (request != null && clientIdParam != null && request.clientId() != null
                && !clientIdParam.equals(request.clientId())) {
            return ResponseEntity.badRequest().body("clientId mismatch between query param and body.");
        }
        String error = validateRequest(request);
        if (error != null) {
            return ResponseEntity.badRequest().body(error);
        }

        if (publicHolidayRepository.existsByClientIdAndHolidayDate(request.clientId(), request.holidayDate())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Holiday already exists for this client and date.");
        }

        PublicHoliday holiday = new PublicHoliday();
        holiday.setClientId(request.clientId());
        holiday.setHolidayDate(request.holidayDate());
        holiday.setHolidayName(request.holidayName().trim());
        holiday.setHolidayType(request.holidayType());

        return ResponseEntity.status(HttpStatus.CREATED).body(publicHolidayRepository.save(holiday));
    }

    @GetMapping
    public ResponseEntity<?> getHolidays(@RequestParam("clientId") Long clientId) {
        if (clientId == null) {
            return ResponseEntity.badRequest().body("clientId is required.");
        }
        List<PublicHoliday> holidays = publicHolidayService.getHolidaysForClient(clientId);
        return ResponseEntity.ok(holidays);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateHoliday(@PathVariable Long id,
                                           @RequestBody PublicHolidayRequest request,
                                           @RequestParam(value = "clientId", required = false) Long clientIdParam) {
        if (request != null && request.clientId() == null && clientIdParam != null) {
            request = new PublicHolidayRequest(clientIdParam, request.holidayDate(), request.holidayName(), request.holidayType());
        }
        if (request != null && clientIdParam != null && request.clientId() != null
                && !clientIdParam.equals(request.clientId())) {
            return ResponseEntity.badRequest().body("clientId mismatch between query param and body.");
        }
        String error = validateRequest(request);
        if (error != null) {
            return ResponseEntity.badRequest().body(error);
        }

        Optional<PublicHoliday> holidayOpt = publicHolidayRepository.findById(id);
        if (holidayOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Holiday not found.");
        }

        PublicHoliday holiday = holidayOpt.get();
        if (!holiday.getClientId().equals(request.clientId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Holiday does not belong to this client.");
        }

        Optional<PublicHoliday> duplicate = publicHolidayRepository
                .findByClientIdAndHolidayDate(request.clientId(), request.holidayDate());
        if (duplicate.isPresent() && !duplicate.get().getId().equals(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Holiday already exists for this client and date.");
        }

        holiday.setHolidayDate(request.holidayDate());
        holiday.setHolidayName(request.holidayName().trim());
        holiday.setHolidayType(request.holidayType());

        return ResponseEntity.ok(publicHolidayRepository.save(holiday));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteHoliday(@PathVariable Long id,
                                           @RequestParam(value = "clientId", required = false) Long clientId) {
        Optional<PublicHoliday> holidayOpt = publicHolidayRepository.findById(id);
        if (holidayOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Holiday not found.");
        }

        PublicHoliday holiday = holidayOpt.get();
        if (clientId != null && !clientId.equals(holiday.getClientId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Holiday does not belong to this client.");
        }

        publicHolidayRepository.delete(holiday);
        return ResponseEntity.noContent().build();
    }

    private String validateRequest(PublicHolidayRequest request) {
        if (request == null) {
            return "Request body is required.";
        }
        if (request.clientId == null) {
            return "clientId is required.";
        }
        if (request.holidayDate == null) {
            return "holidayDate is required.";
        }
        if (request.holidayName == null || request.holidayName.isBlank()) {
            return "holidayName is required.";
        }
        if (request.holidayType == null) {
            return "holidayType is required.";
        }
        return null;
    }

    public record PublicHolidayRequest(
            Long clientId,
            LocalDate holidayDate,
            String holidayName,
            HolidayType holidayType
    ) {
    }
}
