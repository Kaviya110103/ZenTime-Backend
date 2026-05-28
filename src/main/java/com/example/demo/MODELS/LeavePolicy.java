package com.example.demo.MODELS;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "leave_policy")
public class LeavePolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = true)
    private Employee employee;

    @Column(name = "casual_leave_allowed", nullable = true)
    private Integer casualLeaveAllowed;

    @Column(name = "sick_leave_allowed", nullable = true)
    private Integer sickLeaveAllowed;

    @Column(name = "earned_leave_allowed", nullable = true)
    private Integer earnedLeaveAllowed;

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

    public Integer getCasualLeaveAllowed() {
        return casualLeaveAllowed;
    }

    public void setCasualLeaveAllowed(Integer casualLeaveAllowed) {
        this.casualLeaveAllowed = casualLeaveAllowed;
    }

    public Integer getSickLeaveAllowed() {
        return sickLeaveAllowed;
    }

    public void setSickLeaveAllowed(Integer sickLeaveAllowed) {
        this.sickLeaveAllowed = sickLeaveAllowed;
    }

    public Integer getEarnedLeaveAllowed() {
        return earnedLeaveAllowed;
    }

    public void setEarnedLeaveAllowed(Integer earnedLeaveAllowed) {
        this.earnedLeaveAllowed = earnedLeaveAllowed;
    }
}
