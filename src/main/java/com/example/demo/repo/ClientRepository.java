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
}