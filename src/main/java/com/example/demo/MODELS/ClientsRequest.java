package com.example.demo.MODELS;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "clients_requests") // Table name
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ClientsRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String clientName;
    private String companyName;
    private String companyCode;
    private String mobileNumber;
    private String emailAddress;
    private String address;
    private Integer employeeCount;
    private String registeredDate;  // You can change to LocalDate if preferred
    private String workingHours;
}
