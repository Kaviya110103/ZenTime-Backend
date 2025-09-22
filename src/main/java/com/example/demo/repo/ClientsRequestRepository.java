package com.example.demo.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.MODELS.ClientsRequest;

@Repository
public interface ClientsRequestRepository extends JpaRepository<ClientsRequest, Long> {
}