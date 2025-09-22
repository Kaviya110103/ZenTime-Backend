package com.example.demo.controller;



import java.util.List;

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

import com.example.demo.MODELS.ClientsRequest;
import com.example.demo.service.ClientsRequestService;

@RestController
@RequestMapping("/api/clients-requests")
@CrossOrigin(origins = "*") // Allow frontend to access

public class ClientsRequestController {

    private final ClientsRequestService service;

    public ClientsRequestController(ClientsRequestService service) {
        this.service = service;
    }

    // Create
    @PostMapping
    public ResponseEntity<ClientsRequest> create(@RequestBody ClientsRequest request) {
        return ResponseEntity.ok(service.save(request));
    }

    // Read All
    @GetMapping
    public ResponseEntity<List<ClientsRequest>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    // Read One
    @GetMapping("/{id}")
    public ResponseEntity<ClientsRequest> getById(@PathVariable Long id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Update
    @PutMapping("/{id}")
    public ResponseEntity<ClientsRequest> update(@PathVariable Long id, @RequestBody ClientsRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    // Delete
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
