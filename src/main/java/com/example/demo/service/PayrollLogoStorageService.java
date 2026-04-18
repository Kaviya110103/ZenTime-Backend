package com.example.demo.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PayrollLogoStorageService {

    public record LogoData(byte[] bytes, String contentType, String fileName) {}

    private final Path baseDir = Paths.get("uploads", "payroll-logo");

    public void saveLogo(Long clientId, MultipartFile file) throws IOException {
        ensureBaseDir();
        Path logoPath = baseDir.resolve(clientId + ".logo");
        Path metaPath = baseDir.resolve(clientId + ".meta");
        Files.write(logoPath, file.getBytes());
        String meta = (file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                + "\n"
                + (file.getOriginalFilename() == null ? "logo" : file.getOriginalFilename());
        Files.write(metaPath, meta.getBytes(StandardCharsets.UTF_8));
    }

    public Optional<LogoData> getLogo(Long clientId) {
        try {
            Path logoPath = baseDir.resolve(clientId + ".logo");
            if (!Files.exists(logoPath)) {
                return Optional.empty();
            }
            byte[] bytes = Files.readAllBytes(logoPath);
            Path metaPath = baseDir.resolve(clientId + ".meta");
            String contentType = "application/octet-stream";
            String fileName = "logo";
            if (Files.exists(metaPath)) {
                List<String> lines = Files.readAllLines(metaPath, StandardCharsets.UTF_8);
                if (!lines.isEmpty() && lines.get(0) != null && !lines.get(0).isBlank()) {
                    contentType = lines.get(0).trim();
                }
                if (lines.size() > 1 && lines.get(1) != null && !lines.get(1).isBlank()) {
                    fileName = lines.get(1).trim();
                }
            }
            return Optional.of(new LogoData(bytes, contentType, fileName));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private void ensureBaseDir() throws IOException {
        if (!Files.exists(baseDir)) {
            Files.createDirectories(baseDir);
        }
    }
}

