package com.example.demo.controller;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.MODELS.Holiday;
import com.example.demo.service.HolidayService;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/employee/holidays")
public class EmployeeHolidayController {

    @Autowired
    private HolidayService holidayService;

    @GetMapping("/monthly/{employeeId}/{year}/{month}")
    public ResponseEntity<?> getMonthlyHolidays(
            @PathVariable Long employeeId,
            @PathVariable int year,
            @PathVariable int month,
            @RequestParam(value = "clientId", required = false) Long clientId) {
        try {
            List<Holiday> holidays = holidayService.getMonthlyHolidaysForEmployee(employeeId, year, month, clientId);
            return ResponseEntity.ok(holidays);
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }
}
