package com.example.demo.controller;


import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.demo.MODELS.Announcement;
import com.example.demo.repo.AnnouncementRepository;
import com.example.demo.service.PushNotificationService;

@RestController
@CrossOrigin(origins = "*")

@RequestMapping("/api/announcements")

public class AnnouncementController {

    @Autowired
    private AnnouncementRepository announcementRepository;

    @Autowired
    private PushNotificationService pushNotificationService;

    // POST - Create Announcement (Admin only)
    @PostMapping
    public ResponseEntity<Announcement> createAnnouncement(@RequestBody Announcement announcement) {
        announcement.setPostedDate(LocalDate.now());
        Announcement saved = announcementRepository.save(announcement);
        pushNotificationService.notifyAnnouncement(saved);
        return ResponseEntity.ok(saved);
    }

    // GET - Get all announcements
    @GetMapping
    public ResponseEntity<List<Announcement>> getAllAnnouncements() {
        List<Announcement> announcements = announcementRepository.findAll();
        return ResponseEntity.ok(announcements);
    }

    // PUT - Update announcement (by ID)
    @PutMapping("/{id}")
    public ResponseEntity<Announcement> updateAnnouncement(@PathVariable Long id, @RequestBody Announcement updated) {
        Optional<Announcement> optional = announcementRepository.findById(id);
        if (optional.isPresent()) {
            Announcement announcement = optional.get();
            announcement.setTitle(updated.getTitle());
            announcement.setMessage(updated.getMessage());
            announcement.setPostedBy(updated.getPostedBy());
            Announcement saved = announcementRepository.save(announcement);
            return ResponseEntity.ok(saved);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Announcement> getAnnouncementById(@PathVariable Long id) {
        Optional<Announcement> announcement = announcementRepository.findById(id);

        if (announcement.isPresent()) {
            return ResponseEntity.ok(announcement.get());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    // DELETE - Delete announcement
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteAnnouncement(@PathVariable Long id) {
        if (announcementRepository.existsById(id)) {
            announcementRepository.deleteById(id);
            return ResponseEntity.ok("Deleted successfully");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Announcement not found");
        }
    }


//  @PostMapping("/register")
// public ResponseEntity<String> registerUser(@RequestBody RegisterRequest request) {
//     String dbName = "userdb_" + request.getUsername().toLowerCase();
    
//     try (Connection connection = DriverManager.getConnection(
//             "jdbc:mysql://<your-rds-endpoint>:3306/",
//             "root", "yourpassword")) {

//         Statement stmt = connection.createStatement();
//         stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + dbName);

//         // Optional: Run schema creation or migration
//         // runFlywayMigrations(dbName);

//         return ResponseEntity.ok("User registered and DB created");
//     } catch (SQLException e) {
//         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
//     }
// }

}
