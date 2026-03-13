package com.example.demo.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.demo.MODELS.Announcement;
import com.example.demo.MODELS.Employee;
import com.example.demo.MODELS.EmployeePushToken;
import com.example.demo.MODELS.LeavePermission;
import com.example.demo.repo.EmployeePushTokenRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PushNotificationService {
    private static final Logger logger = LoggerFactory.getLogger(PushNotificationService.class);

    private final EmployeePushTokenRepository tokenRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${push.enabled:true}")
    private boolean pushEnabled;

    @Value("${push.expo.url:https://exp.host/--/api/v2/push/send}")
    private String expoPushUrl;

    public PushNotificationService(EmployeePushTokenRepository tokenRepository, ObjectMapper objectMapper) {
        this.tokenRepository = tokenRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    public void registerToken(Employee employee, String token) {
        if (employee == null || employee.getId() == null || !isValidExpoToken(token)) {
            return;
        }
        if (tokenRepository.findByEmployeeIdAndToken(employee.getId(), token).isPresent()) {
            return;
        }
        EmployeePushToken entity = new EmployeePushToken();
        entity.setEmployee(employee);
        entity.setToken(token);
        tokenRepository.save(entity);
    }

    public void notifyAnnouncement(Announcement announcement) {
        if (announcement == null) return;
        String title = announcement.getTitle() == null || announcement.getTitle().isBlank()
                ? "Announcement"
                : announcement.getTitle();
        String body = announcement.getMessage() == null ? "" : announcement.getMessage();
        Map<String, Object> data = Map.of(
                "type", "announcement",
                "announcementId", announcement.getId()
        );
        sendToTokens(tokenRepository.findAllTokens(), title, body, data);
    }

    public void notifyLeaveStatus(LeavePermission leave) {
        if (leave == null || leave.getEmployee() == null) return;
        String leaveType = leave.getLeaveType() == null ? "Leave" : leave.getLeaveType();
        String status = leave.getStatus() == null ? "updated" : leave.getStatus();
        String title = leaveType + " Status";
        String body = "Your " + leaveType + " request is " + status + ".";
        Map<String, Object> data = Map.of(
                "type", "leave_status",
                "leaveId", leave.getId(),
                "status", status
        );
        sendToEmployee(leave.getEmployee(), title, body, data);
    }

    public void notifyLogin(Employee employee) {
        if (employee == null) return;
        String name = employee.getFirstName() == null || employee.getFirstName().isBlank()
                ? "Employee"
                : employee.getFirstName();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        String title = "Login Time";
        String body = "Hi " + name + ", you logged in at " + timestamp + ".";
        Map<String, Object> data = Map.of(
                "type", "login",
                "employeeId", employee.getId()
        );
        sendToEmployee(employee, title, body, data);
    }

    public void sendTest(Employee employee, String title, String body) {
        if (employee == null) return;
        String safeTitle = (title == null || title.isBlank()) ? "Test Notification" : title;
        String safeBody = (body == null) ? "" : body;
        Map<String, Object> data = Map.of(
                "type", "test",
                "employeeId", employee.getId()
        );
        sendToEmployee(employee, safeTitle, safeBody, data);
    }

    private void sendToEmployee(Employee employee, String title, String body, Map<String, Object> data) {
        if (employee == null || employee.getId() == null) return;
        List<EmployeePushToken> tokens = tokenRepository.findByEmployeeId(employee.getId());
        List<String> tokenStrings = new ArrayList<>();
        for (EmployeePushToken token : tokens) {
            tokenStrings.add(token.getToken());
        }
        sendToTokens(tokenStrings, title, body, data);
    }

    private void sendToTokens(List<String> tokens, String title, String body, Map<String, Object> data) {
        if (!pushEnabled || tokens == null || tokens.isEmpty()) {
            return;
        }

        Set<String> uniqueTokens = new HashSet<>();
        for (String token : tokens) {
            if (isValidExpoToken(token)) {
                uniqueTokens.add(token);
            }
        }
        if (uniqueTokens.isEmpty()) {
            return;
        }

        List<Map<String, Object>> payloads = new ArrayList<>();
        for (String token : uniqueTokens) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("to", token);
            payload.put("title", title);
            payload.put("body", body);
            if (data != null && !data.isEmpty()) {
                payload.put("data", data);
            }
            payloads.add(payload);
        }

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payloads);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize push payload.", e);
            return;
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(expoPushUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                logger.warn("Push notification failed: status={}, body={}", response.statusCode(), response.body());
            }
        } catch (IOException | InterruptedException e) {
            logger.warn("Push notification request failed.", e);
            Thread.currentThread().interrupt();
        }
    }

    private boolean isValidExpoToken(String token) {
        if (token == null) return false;
        if (!token.endsWith("]")) return false;
        return token.startsWith("ExpoPushToken[") || token.startsWith("ExponentPushToken[");
    }
}
