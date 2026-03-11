package com.example.demo.MODELS;



import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;


@Entity
public class LeavePermission {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // Employee ID as a foreign key (many leaves can belong to one employee)
    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

//        @Column(name = "missed_times")
// private Integer missedTimes;
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "attendance_record_id")
    private AttendanceRecord attendanceRecord;

    @Column(name = "date")
    private String date;


    @Column(name = "leave_type")
    private String leaveType;


    @Column(name = "start_date")
    private String startDate;


    @Column(name = "end_date")
    private String endDate;


    @Column(name = "reason")
    private String reason;


    @Column(name = "status")
    private String status = "pending"; // Default to pending


   @Column(name = "start_time")
private String startTime;

@Column(name = "end_time")
private String endTime;


    // Constructors
    public LeavePermission() {}


  public LeavePermission(Employee employee, String date, String leaveType, String startDate, String endDate, String reason, String startTime, String endTime) {
    this.employee = employee;
    this.date = date;
    this.leaveType = leaveType;
    this.startDate = startDate;
    this.endDate = endDate;
    this.reason = reason;
    this.startTime = startTime;
    this.endTime = endTime;
    this.status = "pending";
}


    // Getters and setters
    public Long getId() { return id; }


    public Employee getEmployee() { return employee; }


    public void setEmployee(Employee employee) { this.employee = employee; }


    public String getDate() { return date; }


    public void setDate(String date) { this.date = date; }


    public String getLeaveType() { return leaveType; }


    public void setLeaveType(String leaveType) { this.leaveType = leaveType; }


    public String getStartDate() { return startDate; }


    public void setStartDate(String startDate) { this.startDate = startDate; }


    public String getEndDate() { return endDate; }


    public void setEndDate(String endDate) { this.endDate = endDate; }


    public String getReason() { return reason; }


    public void setReason(String reason) { this.reason = reason; }


    public String getStatus() { return status; }


    public void setStatus(String status) { this.status = status; }

    // Getters & Setters
public String getStartTime() {
    return startTime;
}

public void setStartTime(String startTime) {
    this.startTime = startTime;
}

public String getEndTime() {
    return endTime;
}

public void setEndTime(String endTime) {
    this.endTime = endTime;
}






}
