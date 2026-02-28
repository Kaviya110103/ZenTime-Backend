package com.example.demo.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.MODELS.Client;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    // Additional query methods if needed
       Optional<Client> findByUsername(String username);
    Optional<Client> findByEmailAddress(String email);
    Optional<Client> findByUsernameIgnoreCase(String username);
    Optional<Client> findByEmailAddressIgnoreCase(String email);
    Optional<Client> findByCompanyCodeIgnoreCase(String companyCode);
    Optional<Client> findByTenantDbName(String tenantDbName);
}
