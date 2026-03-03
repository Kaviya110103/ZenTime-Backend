package com.example.demo.controller;

import com.example.demo.MODELS.EmployeeSalaryDetails;
import com.example.demo.MODELS.Employee;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.repo.EmployeeSalaryDetailsRepository;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@RestController
@RequestMapping("/api/salary-details")
@CrossOrigin(origins = "*") // Allow frontend to access

public class EmployeeSalaryDetailsController {

    @Autowired
    private EmployeeSalaryDetailsRepository salaryDetailsRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @PostMapping("/calculate")
    public ResponseEntity<?> calculateNetSalary(
            @RequestParam String employeeId,
            @RequestParam(value = "clientId", required = false) Long clientId,
            @RequestParam String position,
            @RequestParam String branch,
            @RequestParam Double salary,
            @RequestParam(required = false, defaultValue = "0") Double convienceAmount,
            @RequestParam(required = false, defaultValue = "0") Double incentive,
            @RequestParam(required = false, defaultValue = "0") Double overTime,
            @RequestParam(required = false, defaultValue = "0") Double lossOfPay,
            @RequestParam(required = false, defaultValue = "0") Double advance,
            @RequestParam(required = false, defaultValue = "0") Double others
    ) {
        Optional<Employee> employeeOpt = resolveEmployeeByRef(employeeId, clientId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Long resolvedEmployeeId = employeeOpt.get().getId();

        EmployeeSalaryDetails details = new EmployeeSalaryDetails();
        details.setEmployeeId(resolvedEmployeeId);
        details.setPosition(position);
        details.setBranch(branch);
        details.setSalary(salary);
        details.setConvienceAmount(convienceAmount);
        details.setIncentive(incentive);
        details.setOverTime(overTime);
        details.setLossOfPay(lossOfPay);
        details.setAdvance(advance);
        details.setOthers(others);

        // Calculate net salary
        double netSalary = salary
                + convienceAmount
                + incentive
                + overTime
                - lossOfPay
                - advance
                - others;

        details.setNetSalary(netSalary);

        salaryDetailsRepository.save(details);
        return ResponseEntity.ok(details);
    }

    private Optional<Employee> resolveEmployeeByRef(String employeeRef, Long clientId) {
        if (employeeRef == null || employeeRef.isBlank()) {
            return Optional.empty();
        }

        String normalized = employeeRef.trim();

        try {
            Long id = Long.parseLong(normalized);
            return clientId == null
                    ? employeeRepository.findById(id)
                    : employeeRepository.findByIdAndClientId(id, clientId);
        } catch (NumberFormatException ignored) {
            // Continue with employee-code lookup.
        }

        Optional<Employee> byCode = employeeRepository.findByEmployeeCode(normalized.toUpperCase());
        if (byCode.isPresent()
                && (clientId == null || clientId.equals(byCode.get().getClientId()))) {
            return byCode;
        }

        Matcher matcher = Pattern.compile("(?i)(?:^|\\.)EMP(\\d+)$").matcher(normalized);
        if (matcher.find()) {
            try {
                Long id = Long.parseLong(matcher.group(1));
                return clientId == null
                        ? employeeRepository.findById(id)
                        : employeeRepository.findByIdAndClientId(id, clientId);
            } catch (NumberFormatException ignored) {
                // Keep empty below.
            }
        }

        return Optional.empty();
    }

    
}
