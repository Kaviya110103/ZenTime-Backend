// noinspection all
package com.example.demo.MODELS;



import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id; 
import jakarta.persistence.OneToMany;

// i want a one table to caluclate a net payment for employee . so in this table i have a employeeId from Employeee table branch salary weekoff ,paindleavedaycounts,casualleaveday count , holiday count ,paind leave type ,and total paidleave count=paindleavedaycounts+,casualleaveday count+holiday count
@SuppressWarnings("all")
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

    @Column(name = "shift_start_time")
    private String shiftStartTime;

    @Column(name = "shift_end_time")
    private String shiftEndTime;

    @Column(name = "shift_start", nullable = true)
    private String shiftStart;

    @Column(name = "shift_end", nullable = true)
    private String shiftEnd;

    @Column(name = "leave_policy_type")
    private String leavePolicyType;

    @Column(name = "casual_leave_balance")
    private Integer casualLeaveBalance;

    @Column(name = "permission_allowance_per_month")
    private Integer permissionAllowancePerMonth;

    @Column(name = "permission_hours_allowed", nullable = true)
    private Double permissionHoursAllowed;

    @Column(name = "additional_working_days", nullable = true)
    private String additionalWorkingDaysConfig;

@Column(name = "company_code")
    private String companyCode;

    @Column(name = "employee_code", unique = true)
    private String employeeCode;

    // Getters and Setters
@Column(name = "guest_name")
private String guestName;

public String getGuestName() {
    return guestName;
}


public void setGuestName(String guestName) {
    this.guestName = guestName;
}


public LocalDateTime getGuestStartDate() {
    return guestStartDate;
}


public void setGuestStartDate(LocalDateTime guestStartDate) {
    this.guestStartDate = guestStartDate;
}
@Column(name = "client_id", nullable = false)
    private Long clientId;


@Column(name = "guest_start_date")
private LocalDateTime guestStartDate;


    public Long getId() {
        return id;
    }


    public String getCompanyCode() {
        return companyCode;
    }


    public void setCompanyCode(String companyCode) {
        this.companyCode = companyCode;
    }
    public String getEmployeeCode() {
        return employeeCode;
    }

    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }


    public Long getClientId() {
        return clientId;
    }


    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }


    public Employee(String firstName, String lastName, String username, String password, String email, Double salary, String branch) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.username = username;
        this.password = password;
        this.email = email;
        this.salary = salary;
        this.branch = branch;
    }

    // Full constructor for internal use
    @SuppressWarnings("java:S107")
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

    public String getShiftStartTime() {
        return shiftStartTime;
    }

    public void setShiftStartTime(String shiftStartTime) {
        this.shiftStartTime = shiftStartTime;
    }

    public String getShiftEndTime() {
        return shiftEndTime;
    }

    public void setShiftEndTime(String shiftEndTime) {
        this.shiftEndTime = shiftEndTime;
    }

    public String getShiftStart() {
        return shiftStart;
    }

    public void setShiftStart(String shiftStart) {
        this.shiftStart = shiftStart;
    }

    public String getShiftEnd() {
        return shiftEnd;
    }

    public void setShiftEnd(String shiftEnd) {
        this.shiftEnd = shiftEnd;
    }

    public String getLeavePolicyType() {
        return leavePolicyType;
    }

    public void setLeavePolicyType(String leavePolicyType) {
        this.leavePolicyType = leavePolicyType;
    }

    public Integer getCasualLeaveBalance() {
        return casualLeaveBalance;
    }

    public void setCasualLeaveBalance(Integer casualLeaveBalance) {
        this.casualLeaveBalance = casualLeaveBalance;
    }

    public Integer getPermissionAllowancePerMonth() {
        return permissionAllowancePerMonth;
    }

    public void setPermissionAllowancePerMonth(Integer permissionAllowancePerMonth) {
        this.permissionAllowancePerMonth = permissionAllowancePerMonth;
    }

    public Double getPermissionHoursAllowed() {
        return permissionHoursAllowed;
    }

    public void setPermissionHoursAllowed(Double permissionHoursAllowed) {
        this.permissionHoursAllowed = permissionHoursAllowed;
    }

    public String getAdditionalWorkingDaysConfig() {
        return additionalWorkingDaysConfig;
    }

    public void setAdditionalWorkingDaysConfig(String additionalWorkingDaysConfig) {
        this.additionalWorkingDaysConfig = additionalWorkingDaysConfig;
    }


    public void setId(Long id) {
        this.id = id;
    }
    public Employee() {
        // Default constructor
    }
    // other getters and setters...



      // Optional: mappedBy for bi-directional mapping
    @JsonIgnore
    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AttendanceRecord> attendanceRecords;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmployeeAdditionalWorkingDay> additionalWorkingDays;

    public List<AttendanceRecord> getAttendanceRecords() {
        return attendanceRecords;
    }

    public void setAttendanceRecords(List<AttendanceRecord> attendanceRecords) {
        this.attendanceRecords = attendanceRecords;
    }

    public List<EmployeeAdditionalWorkingDay> getAdditionalWorkingDays() {
        return additionalWorkingDays;
    }

    public void setAdditionalWorkingDays(List<EmployeeAdditionalWorkingDay> additionalWorkingDays) {
        this.additionalWorkingDays = additionalWorkingDays;
    }
}






