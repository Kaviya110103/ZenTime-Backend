package com.example.demo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.MODELS.EmailDetails;
import com.example.demo.service.EmailService;

@RestController
@RequestMapping("/api/test")
public class EmailTestController {

    @Autowired
    private EmailService emailService;

    @GetMapping("/mail")
    public String testMail() {

        EmailDetails emailDetails = new EmailDetails();
        emailDetails.setSender("wingrootechnologies@gmail.com"); // same as spring.mail.username
        emailDetails.setReceiver("kaavuyaa1122@gmail.com"); // 🔴 change this
        emailDetails.setSubject("ZenTime Email Test");
        emailDetails.setMessage(
            "Hello 👋\n\n" +
            "This is a test email from ZenTime Spring Boot application.\n\n" +
            "If you received this, email configuration is working ✅\n\n" +
            "Regards,\nZenTime Team"
        );

        return emailService.sendEmail(emailDetails);
    }
}
