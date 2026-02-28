package com.example.demo.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.MODELS.Location;

public interface LocationRepository extends JpaRepository<Location, Long> {
    List<Location> findByClientId(Long clientId);
    Optional<Location> findByIdAndClientId(Long id, Long clientId);
    boolean existsByIdAndClientId(Long id, Long clientId);
}
