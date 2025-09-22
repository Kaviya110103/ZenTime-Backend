package com.example.demo.service;


import com.example.demo.MODELS.Employee;
import com.example.demo.repo.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class GuestCleanupService {

    @Autowired
    private EmployeeRepository employeeRepository;

    // Run every 1 minute
    @Scheduled(fixedRate = 10800000) // 60000 ms = 1 minute
    public void deleteExpiredGuests() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(3);
        List<Employee> oldGuests = employeeRepository.findByGuestNameIsNotNullAndGuestStartDateBefore(cutoffTime);

        for (Employee guest : oldGuests) {
            System.out.println("Deleting guest: " + guest.getGuestName() + " (Start: " + guest.getGuestStartDate() + ")");
            employeeRepository.delete(guest);
        }
    }
}
