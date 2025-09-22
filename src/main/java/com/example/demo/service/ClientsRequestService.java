package com.example.demo.service;

import com.example.demo.MODELS.ClientsRequest;
import com.example.demo.repo.ClientsRequestRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ClientsRequestService {

    private final ClientsRequestRepository repository;

    public ClientsRequestService(ClientsRequestRepository repository) {
        this.repository = repository;
    }

    public ClientsRequest save(ClientsRequest clientsRequest) {
        return repository.save(clientsRequest);
    }

    public List<ClientsRequest> getAll() {
        return repository.findAll();
    }

    public Optional<ClientsRequest> getById(Long id) {
        return repository.findById(id);
    }

    public ClientsRequest update(Long id, ClientsRequest updated) {
        return repository.findById(id).map(existing -> {
            existing.setClientName(updated.getClientName());
            existing.setCompanyName(updated.getCompanyName());
            existing.setCompanyCode(updated.getCompanyCode());
            existing.setMobileNumber(updated.getMobileNumber());
            existing.setEmailAddress(updated.getEmailAddress());
            existing.setAddress(updated.getAddress());
            existing.setEmployeeCount(updated.getEmployeeCount());
            existing.setRegisteredDate(updated.getRegisteredDate());
            existing.setWorkingHours(updated.getWorkingHours());
            return repository.save(existing);
        }).orElseThrow(() -> new RuntimeException("Client request not found with id " + id));
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}
