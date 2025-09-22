package com.example.demo.controller;

import com.example.demo.MODELS.EmployeeSalaryDetails;
import com.example.demo.repo.EmployeeSalaryDetailsRepository;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/salary-details")
@CrossOrigin(origins = "*") // Allow frontend to access

public class EmployeeSalaryDetailsController {

    @Autowired
    private EmployeeSalaryDetailsRepository salaryDetailsRepository;

    @PostMapping("/calculate")
    public ResponseEntity<EmployeeSalaryDetails> calculateNetSalary(
            @RequestParam Long employeeId,
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
        EmployeeSalaryDetails details = new EmployeeSalaryDetails();
        details.setEmployeeId(employeeId);
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

    
}