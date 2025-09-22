package com.example.demo.controller;

import com.example.demo.MODELS.Client;
import com.example.demo.MODELS.EmailDetails;
import com.example.demo.service.ClientService;
import com.example.demo.service.EmailService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@RestController
@CrossOrigin(origins = "*") // Allow frontend to access

@RequestMapping(value = "/api/clients", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
public class ClientController {

    private final ClientService clientService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public ClientController(ClientService clientService,
                            PasswordEncoder passwordEncoder,
                            EmailService emailService) {
        this.clientService = clientService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    // DTOs

    public static class ClientLoginRequest {
        private String username;
        private String password;
        public String getUsername() { return username; }
        public void setUsername(String u) { this.username = u; }
        public String getPassword() { return password; }
        public void setPassword(String p) { this.password = p; }
    }

    public static class ClientResponse {
        private Long id;
        private String clientName;
        private String companyName;
        private String companyCode;
        private String mobileNumber;
        private String emailAddress;
        private String address;
        private Integer employeeCount;
        private String registeredDate;
        private String workingHours;
        private String username;

        public static ClientResponse fromEntity(Client c) {
            ClientResponse r = new ClientResponse();
            r.id = c.getId();
            r.clientName = c.getClientName();
            r.companyName = c.getCompanyName();
            r.companyCode = c.getCompanyCode();
            r.mobileNumber = c.getMobileNumber();
            r.emailAddress = c.getEmailAddress();
            r.address = c.getAddress();
            r.employeeCount = c.getEmployeeCount();
            r.registeredDate = c.getRegisteredDate() != null ? c.getRegisteredDate().toString() : null;
            r.workingHours = c.getWorkingHours();
            r.username = c.getUsername();
            return r;
        }

        // getters (if needed)
        public Long getId() { return id; }
        public String getClientName() { return clientName; }
        public String getCompanyName() { return companyName; }
        public String getCompanyCode() { return companyCode; }
        public String getMobileNumber() { return mobileNumber; }
        public String getEmailAddress() { return emailAddress; }
        public String getAddress() { return address; }
        public Integer getEmployeeCount() { return employeeCount; }
        public String getRegisteredDate() { return registeredDate; }
        public String getWorkingHours() { return workingHours; }
        public String getUsername() { return username; }
    }

    // Create client
    @PostMapping
    public ResponseEntity<ClientResponse> createClient(@Valid @RequestBody Client client) {
        if (client.getUsername() == null || client.getUsername().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (client.getPassword() == null || client.getPassword().isBlank()) {
            String generated = java.util.UUID.randomUUID().toString().substring(0, 8);
            client.setPassword(generated);
        }

        String plainPassword = client.getPassword();
        client.setPassword(passwordEncoder.encode(plainPassword));

        Client saved = clientService.createClient(client);

        // send welcome email
        EmailDetails emailDetails = new EmailDetails();
        emailDetails.setSender("b.inba.ips444@gmail.com");
        emailDetails.setReceiver(saved.getEmailAddress());
        emailDetails.setSubject("Client Account Created");

        String message = String.format(
                "Dear %s,\n\n" +
                        "Your client account has been created.\n\n" +
                        "Login Credentials:\n" +
                        "Username: %s\n" +
                        "Password: %s\n\n" +
                        "Your Company Code: %s\n\n" +
                        "Please change your password after first login.\n\n" +
                        "Regards,\nAdmin",
                saved.getClientName(),
                saved.getUsername(),
                saved.getCompanyCode(),
                plainPassword
        );
        emailDetails.setMessage(message);
        emailService.sendEmail(emailDetails);

        return ResponseEntity.created(URI.create("/api/clients/" + saved.getId()))
                .body(ClientResponse.fromEntity(saved));
    }

    // Login
    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ClientResponse> login(@RequestBody ClientLoginRequest req) {
        Optional<Client> opt = clientService.findByUsername(req.getUsername());
        if (opt.isPresent()) {
            Client c = opt.get();
            if (passwordEncoder.matches(req.getPassword(), c.getPassword())) {
                return ResponseEntity.ok(ClientResponse.fromEntity(c));
            }
        }
        return ResponseEntity.status(401).build();
    }

    // Get all
    @GetMapping(path = "", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<List<ClientResponse>> getAll() {
        List<ClientResponse> list = clientService.listAll().stream()
                .map(ClientResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }

    // Get by ID
    @GetMapping(path = "/{id}", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<ClientResponse> getById(@PathVariable Long id) {
        return clientService.getById(id)
                .map(ClientResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Update
    @PutMapping("/{id}")
    public ResponseEntity<ClientResponse> updateClient(@PathVariable Long id, @Valid @RequestBody Client client) {
        try {
            Client updated = clientService.update(id, client);
            return ResponseEntity.ok(ClientResponse.fromEntity(updated));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Delete
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClient(@PathVariable Long id) {
        clientService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
