package com.example.demo.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.MODELS.LocationRequest;

public interface LocationRequestRepository extends JpaRepository<LocationRequest, Long> {
    List<LocationRequest> findByEmployee_ClientIdOrderByRequestedAtDesc(Long clientId);
    List<LocationRequest> findByEmployee_ClientIdAndStatusIgnoreCaseOrderByRequestedAtDesc(Long clientId, String status);
}

