package com.example.demo.service;

import com.example.demo.MODELS.Client;
import com.example.demo.repo.ClientRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ClientService {
    private final ClientRepository repo;

    public ClientService(ClientRepository repo) {
        this.repo = repo;
    }

    public Client createClient(Client c) {
        return repo.save(c);
    }

    public List<Client> listAll() {
        return repo.findAll();
    }

    public Optional<Client> getById(Long id) {
        return repo.findById(id);
    }

    public Optional<Client> findByUsername(String username) {
        return repo.findByUsername(username);
    }

    public Optional<Client> findByEmail(String email) {
        return repo.findByEmailAddress(email);
    }

    public Client update(Long id, Client updated) {
        return repo.findById(id).map(existing -> {
            existing.setClientName(updated.getClientName());
            existing.setCompanyName(updated.getCompanyName());
            existing.setCompanyCode(updated.getCompanyCode());
            existing.setMobileNumber(updated.getMobileNumber());
            existing.setEmailAddress(updated.getEmailAddress());
            existing.setAddress(updated.getAddress());
            existing.setEmployeeCount(updated.getEmployeeCount());
            existing.setRegisteredDate(updated.getRegisteredDate());
            existing.setWorkingHours(updated.getWorkingHours());
            if (updated.getUsername() != null && !updated.getUsername().isBlank()) {
                existing.setUsername(updated.getUsername());
            }
            if (updated.getPassword() != null && !updated.getPassword().isBlank()) {
                existing.setPassword(updated.getPassword()); // assume already hashed if changed
            }
            return repo.save(existing);
        }).orElseThrow(() -> new RuntimeException("Client not found with id " + id));
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }
}
