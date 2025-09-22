package com.example.demo.MODELS;

import jakarta.persistence.*;

@Entity
public class EmployeeNetPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;

    private String branch;
    private Double salary;
    private String weekOff;

    private int paidLeaveDayCount;
    private int casualLeaveDayCount;
    private int holidayCount;
    private String paidLeaveType;

    private int totalPaidLeaveCount;
    private int totalWorkingDays;
    private int presentDays;
    private Double netSalary;

    private int month;
    private int year;
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public Employee getEmployee() {
        return employee;
    }
    public void setEmployee(Employee employee) {
        this.employee = employee;
    }
    public String getBranch() {
        return branch;
    }
    public void setBranch(String branch) {
        this.branch = branch;
    }
    public Double getSalary() {
        return salary;
    }
    public void setSalary(Double salary) {
        this.salary = salary;
    }
    public String getWeekOff() {
        return weekOff;
    }
    public void setWeekOff(String weekOff) {
        this.weekOff = weekOff;
    }
    public int getPaidLeaveDayCount() {
        return paidLeaveDayCount;
    }
    public void setPaidLeaveDayCount(int paidLeaveDayCount) {
        this.paidLeaveDayCount = paidLeaveDayCount;
    }
    public int getCasualLeaveDayCount() {
        return casualLeaveDayCount;
    }
    public void setCasualLeaveDayCount(int casualLeaveDayCount) {
        this.casualLeaveDayCount = casualLeaveDayCount;
    }
    public int getHolidayCount() {
        return holidayCount;
    }
    public void setHolidayCount(int holidayCount) {
        this.holidayCount = holidayCount;
    }
    public String getPaidLeaveType() {
        return paidLeaveType;
    }
    public void setPaidLeaveType(String paidLeaveType) {
        this.paidLeaveType = paidLeaveType;
    }
    public int getTotalPaidLeaveCount() {
        return totalPaidLeaveCount;
    }
    public void setTotalPaidLeaveCount(int totalPaidLeaveCount) {
        this.totalPaidLeaveCount = totalPaidLeaveCount;
    }
    public int getTotalWorkingDays() {
        return totalWorkingDays;
    }
    public void setTotalWorkingDays(int totalWorkingDays) {
        this.totalWorkingDays = totalWorkingDays;
    }
    public int getPresentDays() {
        return presentDays;
    }
    public void setPresentDays(int presentDays) {
        this.presentDays = presentDays;
    }
    public Double getNetSalary() {
        return netSalary;
    }
    public void setNetSalary(Double netSalary) {
        this.netSalary = netSalary;
    }
    public int getMonth() {
        return month;
    }
    public void setMonth(int month) {
        this.month = month;
    }
    public int getYear() {
        return year;
    }
    public void setYear(int year) {
        this.year = year;
    }

    // Getters and setters...
}