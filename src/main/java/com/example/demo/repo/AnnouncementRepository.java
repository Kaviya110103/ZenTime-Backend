package com.example.demo.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.MODELS.Announcement;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
}