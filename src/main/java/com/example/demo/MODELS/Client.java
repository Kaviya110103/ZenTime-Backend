package com.example.demo.MODELS;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String clientName;

    @NotBlank
    private String companyName;
   @NotBlank
    private String companyCode;
    @NotBlank
    @Pattern(regexp = "^[0-9]{10,15}$", message = "Mobile number must be digits, length 10-15")
    private String mobileNumber;

    @NotBlank
    @Email
    private String emailAddress;

    @NotBlank
    private String address;

    @Min(1)
    private Integer employeeCount;

    @NotNull
    private LocalDate registeredDate;

    @NotBlank
    private String workingHours;

    @NotBlank
    @Column(unique = true)
    private String username;

    @NotBlank
    private String password; // stored hashed

    public Client() {}

    // Getters / setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCompanyCode() {
        return companyCode;
    }

    public void setCompanyCode(String companyCode) {
        this.companyCode = companyCode;
    }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }

    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Integer getEmployeeCount() { return employeeCount; }
    public void setEmployeeCount(Integer employeeCount) { this.employeeCount = employeeCount; }

    public LocalDate getRegisteredDate() { return registeredDate; }
    public void setRegisteredDate(LocalDate registeredDate) { this.registeredDate = registeredDate; }

    public String getWorkingHours() { return workingHours; }
    public void setWorkingHours(String workingHours) { this.workingHours = workingHours; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
