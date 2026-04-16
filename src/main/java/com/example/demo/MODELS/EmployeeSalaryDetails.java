package com.example.demo.MODELS;

import jakarta.persistence.*;

@Entity
public class EmployeeSalaryDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long employeeId;
    private String position;
    private String branch;
    private Double salary;

    private Double convienceAmount = 0.0;
    private Double incentive = 0.0;
    private Double overTime = 0.0;
    private Double lossOfPay = 0.0;
    private Double advance = 0.0;
    
    private Double others = 0.0;
    private Double pfAmount = 0.0;
    private Double pfPercentage = 0.0;
    private Double additionalAllowancesTotal = 0.0;
    @Column(columnDefinition = "TEXT")
    private String additionalAllowancesJson;
    private Double netSalary = 0.0;

    // Getters and setters
    public Long getId() { return id; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public Double getSalary() { return salary; }
    public void setSalary(Double salary) { this.salary = salary; }
    public Double getConvienceAmount() { return convienceAmount; }
    public void setConvienceAmount(Double convienceAmount) { this.convienceAmount = convienceAmount; }
    public Double getIncentive() { return incentive; }
    public void setIncentive(Double incentive) { this.incentive = incentive; }
    public Double getOverTime() { return overTime; }
    public void setOverTime(Double overTime) { this.overTime = overTime; }
    public Double getLossOfPay() { return lossOfPay; }
    public void setLossOfPay(Double lossOfPay) { this.lossOfPay = lossOfPay; }
    public Double getAdvance() { return advance; }
    public void setAdvance(Double advance) { this.advance = advance; }
    public Double getOthers() { return others; }
    public void setOthers(Double others) { this.others = others; }
    public Double getPfAmount() { return pfAmount; }
    public void setPfAmount(Double pfAmount) { this.pfAmount = pfAmount; }
    public Double getPfPercentage() { return pfPercentage; }
    public void setPfPercentage(Double pfPercentage) { this.pfPercentage = pfPercentage; }
    public Double getAdditionalAllowancesTotal() { return additionalAllowancesTotal; }
    public void setAdditionalAllowancesTotal(Double additionalAllowancesTotal) { this.additionalAllowancesTotal = additionalAllowancesTotal; }
    public String getAdditionalAllowancesJson() { return additionalAllowancesJson; }
    public void setAdditionalAllowancesJson(String additionalAllowancesJson) { this.additionalAllowancesJson = additionalAllowancesJson; }
    public Double getNetSalary() { return netSalary; }
    public void setNetSalary(Double netSalary) { this.netSalary = netSalary; }
    

}
