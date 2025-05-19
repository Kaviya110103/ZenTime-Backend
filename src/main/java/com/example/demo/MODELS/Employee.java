package com.example.demo.MODELS;



import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;


@Entity
public class Employee {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Optional: Auto increment
    private Long id;


   
    @Column(name = "first_name")
    private String firstName;


    @Column(name = "last_name")
    private String lastName;


    @Column(name = "mobile")
    private String mobile;


    @Column(name = "gender")
    private String gender;


    @Column(name = "position")
    private String position;


    @Column(name = "branch")
    private String branch;


    @Column(name = "username")
    private String username;


    @Column(name = "password")
    private String password;


    @Column(name = "dob")
    private String dob;


    @Column(name = "email_id", nullable = false, unique = true)
    private String email;


    // New field for profile image
    @Column(name = "profile_image")
    private String profileImage; // URL of the uploaded image


    @Column(name = "address")
    private String address;


    @Column(name = "alternative_mobile")
    private String alternativeMobile;


    @Column(name = "date_of_joining")
    private String dateOfJoining;


    // New field for password reset token
    @Column(name = "reset_token")
    private String resetToken;


    // New fields for salary and week off
    @Column(name = "salary")
    private Double salary;


    @Column(name = "week_off")
    private String weekOff;


    // Getters and Setters


    public Long getId() {
        return id;
    }


    public Employee(Long id, String firstName, String lastName, String mobile, String gender, String position,
            String branch, String username, String password, String dob, String email, String profileImage,
            String address, String alternativeMobile, String dateOfJoining, String resetToken, Double salary,
            String weekOff) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.mobile = mobile;
        this.gender = gender;
        this.position = position;
        this.branch = branch;
        this.username = username;
        this.password = password;
        this.dob = dob;
        this.email = email;
        this.profileImage = profileImage;
        this.address = address;
        this.alternativeMobile = alternativeMobile;
        this.dateOfJoining = dateOfJoining;
        this.resetToken = resetToken;
        this.salary = salary;
        this.weekOff = weekOff;
    }


    public String getFirstName() {
        return firstName;
    }


    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }


    public String getLastName() {
        return lastName;
    }


    public void setLastName(String lastName) {
        this.lastName = lastName;
    }


    public String getMobile() {
        return mobile;
    }


    public void setMobile(String mobile) {
        this.mobile = mobile;
    }


    public String getGender() {
        return gender;
    }


    public void setGender(String gender) {
        this.gender = gender;
    }


    public String getPosition() {
        return position;
    }


    public void setPosition(String position) {
        this.position = position;
    }


    public String getBranch() {
        return branch;
    }


    public void setBranch(String branch) {
        this.branch = branch;
    }


    public String getUsername() {
        return username;
    }


    public void setUsername(String username) {
        this.username = username;
    }


    public String getPassword() {
        return password;
    }


    public void setPassword(String password) {
        this.password = password;
    }


    public String getDob() {
        return dob;
    }


    public void setDob(String dob) {
        this.dob = dob;
    }


    public String getEmail() {
        return email;
    }


    public void setEmail(String email) {
        this.email = email;
    }


    public String getProfileImage() {
        return profileImage;
    }


    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }


    public String getAddress() {
        return address;
    }


    public void setAddress(String address) {
        this.address = address;
    }


    public String getAlternativeMobile() {
        return alternativeMobile;
    }


    public void setAlternativeMobile(String alternativeMobile) {
        this.alternativeMobile = alternativeMobile;
    }


    public String getDateOfJoining() {
        return dateOfJoining;
    }


    public void setDateOfJoining(String dateOfJoining) {
        this.dateOfJoining = dateOfJoining;
    }


    public String getResetToken() {
        return resetToken;
    }


    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
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


    public void setId(Long id) {
        this.id = id;
    }
    public Employee() {
        // Default constructor
    }
    // other getters and setters...



      // Optional: mappedBy for bi-directional mapping
    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AttendanceRecord> attendanceRecords;
}
