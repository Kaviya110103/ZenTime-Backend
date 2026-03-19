package com.example.demo.controller;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.beans.factory.annotation.Autowired;
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

import com.example.demo.MODELS.Holiday;
import com.example.demo.MODELS.HolidayRequest;
import com.example.demo.service.HolidayService;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping({"/api/admin/holidays", "/admin/holidays"})
public class AdminHolidayController {

    @Autowired
    private HolidayService holidayService;

    @GetMapping
    public ResponseEntity<?> getAllByClient(@RequestParam(value = "clientId", required = false) Long clientId) {
        if (clientId == null) {
            return ResponseEntity.badRequest().body("clientId is required.");
        }
        List<Holiday> holidays = holidayService.getByClientId(clientId);
        return ResponseEntity.ok(holidays);
    }

    @PostMapping
    public ResponseEntity<?> createHoliday(
            @RequestParam(value = "clientId", required = false) Long clientId,
            @RequestBody HolidayRequest request) {

        Long effectiveClientId = resolveClientId(clientId, request);
        if (effectiveClientId == null) {
            return ResponseEntity.badRequest().body("clientId is required.");
        }
        try {
            Holiday created = holidayService.createHoliday(effectiveClientId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateHoliday(
            @PathVariable Long id,
            @RequestParam(value = "clientId", required = false) Long clientId,
            @RequestBody HolidayRequest request) {

        Long effectiveClientId = resolveClientId(clientId, request);
        if (effectiveClientId == null) {
            return ResponseEntity.badRequest().body("clientId is required.");
        }
        try {
            Holiday updated = holidayService.updateHoliday(id, effectiveClientId, request);
            return ResponseEntity.ok(updated);
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteHoliday(
            @PathVariable Long id,
            @RequestParam(value = "clientId", required = false) Long clientId) {
        if (clientId == null) {
            return ResponseEntity.badRequest().body("clientId is required.");
        }
        try {
            holidayService.deleteHoliday(id, clientId);
            return ResponseEntity.ok("Holiday deleted.");
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    private Long resolveClientId(Long queryClientId, HolidayRequest request) {
        if (request != null && request.getClientId() != null) {
            return request.getClientId();
        }
        return queryClientId;
    }
}
