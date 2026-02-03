package com.example.demo.controller;

import java.util.List;

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

import com.example.demo.MODELS.Location;
import com.example.demo.repo.LocationRepository;

@RestController
@RequestMapping("/api/locations")
@CrossOrigin(origins = "*") // Adjust as needed for your frontend
public class LocationController {

    private final LocationRepository repo;

    public LocationController(LocationRepository repo) {
        this.repo = repo;
    }

    /* ---------- CREATE ---------- */
    @PostMapping
    public ResponseEntity<Location> create(@RequestBody Location location) {
        Location saved = repo.save(location);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /* ---------- READ (optional helpers) ---------- */
    @GetMapping                      // GET /api/locations
    public List<Location> findAll() { return repo.findAll(); }

    @GetMapping("/{id}")             // GET /api/locations/1
    public ResponseEntity<Location> findOne(@PathVariable Long id) {
        return repo.findById(id)
                   .map(ResponseEntity::ok)
                   .orElse(ResponseEntity.notFound().build());
    }

    /* ---------- UPDATE ---------- */
    @PutMapping("/{id}")
    public ResponseEntity<Location> update(@PathVariable Long id,
                                           @RequestBody Location incoming) {
        return repo.findById(id).map(existing -> {
            existing.setLatitude(incoming.getLatitude());
            existing.setLongitude(incoming.getLongitude());
            existing.setName(incoming.getName());
            existing.setAddress(incoming.getAddress());
            existing.setRadius(incoming.getRadius());
            repo.save(existing);
            return ResponseEntity.ok(existing);
        }).orElse(ResponseEntity.notFound().build());
    }

    /* ---------- DELETE ---------- */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (repo.existsById(id)) {
            repo.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}