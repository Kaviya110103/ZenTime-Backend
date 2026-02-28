package com.example.demo.service;

import com.example.demo.MODELS.Client;
import com.example.demo.repo.ClientRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ClientService {
    private static final Set<String> REQUIRED_TENANT_TABLES = Set.of(
            "employee",
            "attendance_record",
            "leave_permission",
            "locations"
    );

    private final ClientRepository repo;
    private final TenantDatabaseProvisioningService tenantDatabaseProvisioningService;

    public ClientService(ClientRepository repo,
                         TenantDatabaseProvisioningService tenantDatabaseProvisioningService) {
        this.repo = repo;
        this.tenantDatabaseProvisioningService = tenantDatabaseProvisioningService;
    }

    public Client createClient(Client c) {
        String normalizedCompanyCode = normalizeCompanyCode(c.getCompanyCode());
        c.setCompanyCode(normalizedCompanyCode);
        c.setRegisteredDate(c.getRegisteredDate() == null ? LocalDate.now() : c.getRegisteredDate());

        String tenantDbName = tenantDatabaseProvisioningService.buildTenantDatabaseName(normalizedCompanyCode);
        c.setTenantDbName(tenantDbName);
        c.setProvisioningStatus("PROVISIONING");

        validateClientUniquenessForCreate(c);

        tenantDatabaseProvisioningService.provisionTenantDatabase(normalizedCompanyCode);
        boolean schemaReady = tenantDatabaseProvisioningService.hasRequiredTables(tenantDbName, REQUIRED_TENANT_TABLES);
        if (!schemaReady) {
            throw new IllegalStateException("Tenant schema validation failed for " + tenantDbName);
        }

        c.setProvisioningStatus("ACTIVE");
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
            String incomingCompanyCode = normalizeCompanyCode(updated.getCompanyCode());
            if (!existing.getCompanyCode().equalsIgnoreCase(incomingCompanyCode)) {
                throw new IllegalStateException("Company code cannot be changed after client creation.");
            }
            existing.setCompanyCode(incomingCompanyCode);
            existing.setMobileNumber(updated.getMobileNumber());
            existing.setEmailAddress(updated.getEmailAddress());
            existing.setAddress(updated.getAddress());
            existing.setEmployeeCount(updated.getEmployeeCount());
            existing.setRegisteredDate(updated.getRegisteredDate());
            existing.setWorkingHours(updated.getWorkingHours());
            existing.setTenantDbName(existing.getTenantDbName() == null || existing.getTenantDbName().isBlank()
                    ? tenantDatabaseProvisioningService.buildTenantDatabaseName(existing.getCompanyCode())
                    : existing.getTenantDbName());
            existing.setProvisioningStatus(existing.getProvisioningStatus() == null ? "ACTIVE" : existing.getProvisioningStatus());
            if (updated.getUsername() != null && !updated.getUsername().isBlank()) {
                existing.setUsername(updated.getUsername());
            }
            if (updated.getPassword() != null && !updated.getPassword().isBlank()) {
                existing.setPassword(updated.getPassword()); // assume already hashed if changed
            }
            validateClientUniquenessForUpdate(existing);
            return repo.save(existing);
        }).orElseThrow(() -> new RuntimeException("Client not found with id " + id));
    }

    public Client retryTenantProvisioning(Long clientId) {
        Client existing = repo.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found with id " + clientId));

        String normalizedCompanyCode = normalizeCompanyCode(existing.getCompanyCode());
        existing.setCompanyCode(normalizedCompanyCode);
        existing.setProvisioningStatus("PROVISIONING");

        String tenantDbName = tenantDatabaseProvisioningService.buildTenantDatabaseName(normalizedCompanyCode);
        existing.setTenantDbName(tenantDbName);

        tenantDatabaseProvisioningService.provisionTenantDatabase(normalizedCompanyCode);
        boolean schemaReady = tenantDatabaseProvisioningService.hasRequiredTables(tenantDbName, REQUIRED_TENANT_TABLES);
        existing.setProvisioningStatus(schemaReady ? "ACTIVE" : "PROVISION_FAILED");
        return repo.save(existing);
    }

    public boolean isTenantHealthy(Long clientId) {
        Client existing = repo.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found with id " + clientId));
        if (existing.getTenantDbName() == null || existing.getTenantDbName().isBlank()) {
            return false;
        }

        boolean databaseExists = tenantDatabaseProvisioningService.tenantDatabaseExists(existing.getTenantDbName());
        if (!databaseExists) {
            return false;
        }

        return tenantDatabaseProvisioningService.hasRequiredTables(existing.getTenantDbName(), REQUIRED_TENANT_TABLES);
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }

    private String normalizeCompanyCode(String companyCode) {
        if (companyCode == null || companyCode.isBlank()) {
            throw new IllegalArgumentException("Company code is required");
        }
        return companyCode.trim().toLowerCase();
    }

    private void validateClientUniquenessForCreate(Client client) {
        if (repo.findByUsernameIgnoreCase(client.getUsername()).isPresent()) {
            throw new IllegalStateException("Username already exists.");
        }
        if (repo.findByEmailAddressIgnoreCase(client.getEmailAddress()).isPresent()) {
            throw new IllegalStateException("Email address already exists.");
        }
        if (repo.findByCompanyCodeIgnoreCase(client.getCompanyCode()).isPresent()) {
            throw new IllegalStateException("Company code already exists.");
        }
        if (repo.findByTenantDbName(client.getTenantDbName()).isPresent()) {
            throw new IllegalStateException("Tenant database mapping already exists.");
        }
    }

    private void validateClientUniquenessForUpdate(Client client) {
        repo.findByUsernameIgnoreCase(client.getUsername())
                .filter(conflict -> !conflict.getId().equals(client.getId()))
                .ifPresent(conflict -> {
                    throw new IllegalStateException("Username already exists.");
                });

        repo.findByEmailAddressIgnoreCase(client.getEmailAddress())
                .filter(conflict -> !conflict.getId().equals(client.getId()))
                .ifPresent(conflict -> {
                    throw new IllegalStateException("Email address already exists.");
                });

        repo.findByCompanyCodeIgnoreCase(client.getCompanyCode())
                .filter(conflict -> !conflict.getId().equals(client.getId()))
                .ifPresent(conflict -> {
                    throw new IllegalStateException("Company code already exists.");
                });

        if (client.getTenantDbName() != null) {
            repo.findByTenantDbName(client.getTenantDbName())
                    .filter(conflict -> !conflict.getId().equals(client.getId()))
                    .ifPresent(conflict -> {
                        throw new IllegalStateException("Tenant database mapping already exists.");
                    });
        }
    }
}
