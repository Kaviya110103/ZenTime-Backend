package com.example.demo.controller;

import com.example.demo.MODELS.EmployeeSalaryDetails;
import com.example.demo.MODELS.Employee;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.repo.EmployeeSalaryDetailsRepository;
import com.example.demo.service.SchemaMaintenanceService;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.List;
import java.util.Map;
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

    @Autowired
    private SchemaMaintenanceService schemaMaintenanceService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
            @RequestParam(required = false, defaultValue = "0") Double others,
            @RequestParam(required = false, defaultValue = "0") Double pfAmount,
            @RequestParam(required = false, defaultValue = "0") Double pfPercentage,
            @RequestParam(required = false) String additionalAllowancesJson,
            @RequestParam(required = false, defaultValue = "0") Double additionalAllowancesTotal
    ) {
        schemaMaintenanceService.ensureEmployeeSchema();
        Optional<Employee> employeeOpt = resolveEmployeeByRef(employeeId, clientId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Long resolvedEmployeeId = employeeOpt.get().getId();

        double resolvedAdditionalTotal = resolveAdditionalAllowancesTotal(
                additionalAllowancesTotal,
                additionalAllowancesJson);
        double resolvedPfAmount = resolvePfAmount(salary, pfAmount, pfPercentage);
        double normalizedSalary = roundCurrency(salary == null ? 0.0 : salary);
        double normalizedConvenience = roundCurrency(convienceAmount == null ? 0.0 : convienceAmount);
        double normalizedIncentive = roundCurrency(incentive == null ? 0.0 : incentive);
        double normalizedOverTime = roundCurrency(overTime == null ? 0.0 : overTime);
        double normalizedLossOfPay = roundCurrency(lossOfPay == null ? 0.0 : lossOfPay);
        double normalizedAdvance = roundCurrency(advance == null ? 0.0 : advance);
        double normalizedOthers = roundCurrency(others == null ? 0.0 : others);
        double normalizedAdditionalTotal = roundCurrency(resolvedAdditionalTotal);
        double normalizedPfAmount = roundCurrency(resolvedPfAmount);

        EmployeeSalaryDetails details = new EmployeeSalaryDetails();
        details.setEmployeeId(resolvedEmployeeId);
        details.setPosition(position);
        details.setBranch(branch);
        details.setSalary(normalizedSalary);
        details.setConvienceAmount(normalizedConvenience);
        details.setIncentive(normalizedIncentive);
        details.setOverTime(normalizedOverTime);
        details.setLossOfPay(normalizedLossOfPay);
        details.setAdvance(normalizedAdvance);
        details.setOthers(normalizedOthers);
        details.setPfAmount(normalizedPfAmount);
        details.setPfPercentage(roundCurrency(pfPercentage == null ? 0.0 : pfPercentage));
        details.setAdditionalAllowancesTotal(normalizedAdditionalTotal);
        details.setAdditionalAllowancesJson(additionalAllowancesJson);

        // Calculate net salary
        BigDecimal netSalary = toMoney(normalizedSalary)
                .add(toMoney(normalizedConvenience))
                .add(toMoney(normalizedIncentive))
                .add(toMoney(normalizedOverTime))
                .add(toMoney(normalizedAdditionalTotal))
                .subtract(toMoney(normalizedLossOfPay))
                .subtract(toMoney(normalizedAdvance))
                .subtract(toMoney(normalizedOthers))
                .subtract(toMoney(normalizedPfAmount))
                .setScale(2, RoundingMode.HALF_UP);

        details.setNetSalary(netSalary.doubleValue());

        salaryDetailsRepository.save(details);
        return ResponseEntity.ok(details);
    }

    private double resolveAdditionalAllowancesTotal(Double providedTotal, String json) {
        double fallback = providedTotal == null ? 0.0 : providedTotal;
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            List<Map<String, Object>> items = OBJECT_MAPPER.readValue(
                    json,
                    new TypeReference<List<Map<String, Object>>>() {});
            double total = 0.0;
            for (Map<String, Object> item : items) {
                if (item == null) {
                    continue;
                }
                Object amountRaw = item.get("amount");
                if (amountRaw instanceof Number number) {
                    total += number.doubleValue();
                } else if (amountRaw instanceof String text) {
                    try {
                        total += Double.parseDouble(text.trim());
                    } catch (NumberFormatException ignored) {
                        // Skip invalid amounts.
                    }
                }
            }
            return total;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private double resolvePfAmount(Double salary, Double pfAmount, Double pfPercentage) {
        double baseSalary = salary == null ? 0.0 : salary;
        if (pfAmount != null && pfAmount > 0) {
            return pfAmount;
        }
        if (pfPercentage != null && pfPercentage > 0) {
            return Math.round((baseSalary * pfPercentage / 100.0) * 100.0) / 100.0;
        }
        return 0.0;
    }

    private double roundCurrency(double value) {
        return toMoney(value).doubleValue();
    }

    private BigDecimal toMoney(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
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
