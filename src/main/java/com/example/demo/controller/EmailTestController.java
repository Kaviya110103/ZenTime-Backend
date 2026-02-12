package com.example.demo.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.MODELS.EmailDetails;
import com.example.demo.service.EmailService;

@RestController
@RequestMapping("/api/test")
public class EmailTestController {

    private static final String SUCCESS = "success";
    private static final String MESSAGE = "message";
    private static final String RECEIVER = "receiver";
    private static final String SENDER = "sender";
    private static final String SUBJECT = "subject";
    private static final String ERROR = "error";
    private static final String PREVIEW = "preview";
    
    private final EmailService emailService;

    public EmailTestController(EmailService emailService) {
        this.emailService = emailService;
    }

    @GetMapping("/mail")
    public String testMail() {

        EmailDetails emailDetails = new EmailDetails();
        emailDetails.setSender("wingrootechnologies@gmail.com"); // same as spring.mail.username
        emailDetails.setReceiver("kaavuyaa1122@gmail.com"); // 🔴 change this
        emailDetails.setSubject("ZenTime Email Test");
        emailDetails.setMessage(
            """
            Hello 👋

            This is a test email from ZenTime Spring Boot application.

            If you received this, email configuration is working ✅

            Regards,
            ZenTime Team"""
        );

        return emailService.sendEmail(emailDetails);
    }

    /**
     * POST: Test email sending with custom parameters
     * Endpoint: POST /api/test/send-email
     * 
     * Request Body Example:
     * {
     *     "sender": "wingrootechnologies@gmail.com",
     *     "receiver": "test@example.com",
     *     "subject": "Test Subject",
     *     "message": "Test message body"
     * }
     */
    @PostMapping("/send-email")
    public ResponseEntity<Map<String, Object>> sendTestEmail(@RequestBody EmailDetails emailDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Validate inputs
            if (emailDetails.getSender() == null || emailDetails.getSender().isBlank()) {
                response.put(SUCCESS, false);
                response.put(MESSAGE, "Sender email is required");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (emailDetails.getReceiver() == null || emailDetails.getReceiver().isBlank()) {
                response.put(SUCCESS, false);
                response.put(MESSAGE, "Receiver email is required");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (emailDetails.getSubject() == null || emailDetails.getSubject().isBlank()) {
                response.put(SUCCESS, false);
                response.put(MESSAGE, "Subject is required");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (emailDetails.getMessage() == null || emailDetails.getMessage().isBlank()) {
                response.put(SUCCESS, false);
                response.put(MESSAGE, "Message is required");
                return ResponseEntity.badRequest().body(response);
            }

            // Send email
            String result = emailService.sendEmail(emailDetails);
            
            if (result.contains("successfully")) {
                response.put(SUCCESS, true);
                response.put(MESSAGE, "Email sent successfully!");
                response.put(SENDER, emailDetails.getSender());
                response.put(RECEIVER, emailDetails.getReceiver());
                response.put(SUBJECT, emailDetails.getSubject());
                return ResponseEntity.ok(response);
            } else {
                response.put(SUCCESS, false);
                response.put(MESSAGE, result);
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            response.put(SUCCESS, false);
            response.put(MESSAGE, "Error sending email: " + e.getMessage());
            response.put(ERROR, e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * POST: Test employee credentials email format
     * Endpoint: POST /api/test/send-credentials-email
     * 
     * Request Body Example:
     * {
     *     "receiver": "employee@example.com",
     *     "firstName": "John",
     *     "username": "john.doe",
     *     "password": "TempPass123",
     *     "companyCode": "IIE-001",
     *     "actualTimeIn": "09:00",
     *     "actualTimeOut": "17:30"
     * }
     */
    @PostMapping("/send-credentials-email")
    public ResponseEntity<Map<String, Object>> sendCredentialsEmail(@RequestBody Map<String, String> data) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Validate inputs
            String receiver = data.get(RECEIVER);
            String firstName = data.get("firstName");
            String username = data.get("username");
            String password = data.get("password");
            String companyCode = data.get("companyCode");
            String timeIn = data.getOrDefault("actualTimeIn", "Not Set");
            String timeOut = data.getOrDefault("actualTimeOut", "Not Set");

            if (receiver == null || receiver.isBlank()) {
                response.put(SUCCESS, false);
                response.put(MESSAGE, "Receiver email is required");
                return ResponseEntity.badRequest().body(response);
            }

            // Create email details
            EmailDetails emailDetails = new EmailDetails();
            emailDetails.setSender("wingrootechnologies@gmail.com");
            emailDetails.setReceiver(receiver);
            emailDetails.setSubject("Employee Account Credentials - Indra Institute Of Education");

            // Format message (as per EmployeeController)
            String message = String.format(
                """
                Dear %s,

                Welcome to Indra Institute of Education (IIE)! We are delighted to have you as part of our team.

                Login Credentials:
                Username: %s
                Password: %s

                Your Company Code: %s

                Your Fixed Schedule:
                Time In: %s
                Time Out: %s

                Please update your password after logging in.

                Regards,
                Admin, IIE
                """,
                firstName != null ? firstName : "Employee",
                username != null ? username : "N/A",
                password != null ? password : "N/A",
                companyCode != null ? companyCode : "N/A",
                timeIn,
                timeOut
            );

            emailDetails.setMessage(message);
            
            // Send email
            String result = emailService.sendEmail(emailDetails);
            
            if (result.contains("successfully")) {
                response.put(SUCCESS, true);
                response.put(MESSAGE, "Credentials email sent successfully!");
                response.put(RECEIVER, receiver);
                response.put(SUBJECT, emailDetails.getSubject());
                response.put(PREVIEW, message);
                return ResponseEntity.ok(response);
            } else {
                response.put(SUCCESS, false);
                response.put(MESSAGE, result);
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            response.put(SUCCESS, false);
            response.put(MESSAGE, "Error sending credentials email: " + e.getMessage());
            response.put(ERROR, e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(response);
        }
    }
}
