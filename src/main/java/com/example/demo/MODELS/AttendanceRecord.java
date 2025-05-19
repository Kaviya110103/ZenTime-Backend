package com.example.demo.MODELS;


import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import java.time.LocalDateTime;
import java.util.List;

@Entity
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    
  @ManyToOne
    @JoinColumn(name = "employee_id") // This is the join column
    private Employee employee;
    private LocalDateTime timeIn;

    @Lob
    private byte[] imageIn; // Store image as byte array (consider cloud storage for production)

    private LocalDateTime timeOut;

    @Lob
    private byte[] imageOut; // Store image as byte array (consider cloud storage for production)

    private String dayStatus; // "Completed"

    private String attendanceStatus; // "Present", "Absent"

    private String date;


    private Integer missedTimes; // Total missed minutes (timeIn + timeOut deviation)
    public AttendanceRecord(Integer missedtimes, String attendancelocation) {
        this.missedTimes = missedtimes;
        this.attendancelocation = attendancelocation;
    }

    

        public Integer getMissedtimes() {
        return missedTimes;
    }

    public void setMissedtimes(Integer missedtimes) {
        this.missedTimes = missedtimes;
    }

        private String attendancelocation;


    public String getAttendancelocation() {
        return attendancelocation;
    }

    public void setAttendancelocation(String attendancelocation) {
        this.attendancelocation = attendancelocation;
    }

   

    

  


    public String getDate() {
        return date;
    }

    public AttendanceRecord(String date) {
        this.date = date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    // Constructors, Getters, and Setters
    public AttendanceRecord() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

  

    public LocalDateTime getTimeIn() {
        return timeIn;
    }

    public void setTimeIn(LocalDateTime timeIn) {
        this.timeIn = timeIn;
    }

    public byte[] getImageIn() {
        return imageIn;
    }

    public void setImageIn(byte[] imageIn) {
        this.imageIn = imageIn;
    }

    public LocalDateTime getTimeOut() {
        return timeOut;
    }

    public void setTimeOut(LocalDateTime timeOut) {
        this.timeOut = timeOut;
    }

    public byte[] getImageOut() {
        return imageOut;
    }

    public void setImageOut(byte[] imageOut) {
        this.imageOut = imageOut;
    }
    

    public String getDayStatus() {
        return dayStatus;
    }

    public void setDayStatus(String dayStatus) {
        this.dayStatus = dayStatus;
    }

    public String getAttendanceStatus() {
        return attendanceStatus;
    }

    public void setAttendanceStatus(String attendanceStatus) {
        this.attendanceStatus = attendanceStatus;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    @OneToMany(mappedBy = "attendanceRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LeavePermission> leavePermissions;


}