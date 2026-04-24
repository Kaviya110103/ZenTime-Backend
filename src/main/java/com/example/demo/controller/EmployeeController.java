package com.example.demo.controller;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.example.demo.MODELS.EmailDetails;
import com.example.demo.MODELS.Employee;
import com.example.demo.MODELS.EmployeeAdditionalWorkingDay;
import com.example.demo.MODELS.EmployeeNetPayment;
import com.example.demo.repo.EmployeeNetPaymentRepository;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.service.EmailService;
import com.example.demo.service.EmployeeService;
import com.example.demo.service.PushNotificationService;
import com.example.demo.tenant.TenantContext;

@RestController
@RequestMapping("/api/employees")   // https://test.zentime.co.in
@CrossOrigin(origins = "*") // Allow frontend to access
public class EmployeeController {
    private static final Logger logger = LoggerFactory.getLogger(EmployeeController.class);
    private static final String EMPLOYEE_LIMIT_EXCEEDED_MESSAGE =
            "Employee limit exceeded. Kindly contact Super Admin.";

    private final PasswordEncoder passwordEncoder;
    private final EmployeeService employeeService;
    private final EmployeeNetPaymentRepository employeeNetPaymentRepository;
    private final EmployeeRepository employeeRepository;
    private final JdbcTemplate masterJdbcTemplate;
    private final EmailService emailService;
    private final PushNotificationService pushNotificationService;
    private final com.example.demo.service.SchemaMaintenanceService schemaMaintenanceService;

    public EmployeeController(PasswordEncoder passwordEncoder, EmployeeService employeeService,
            EmployeeNetPaymentRepository employeeNetPaymentRepository, EmployeeRepository employeeRepository,
            @org.springframework.beans.factory.annotation.Qualifier("masterDataSource") DataSource masterDataSource,
            EmailService emailService,
            PushNotificationService pushNotificationService,
            com.example.demo.service.SchemaMaintenanceService schemaMaintenanceService) {
        this.passwordEncoder = passwordEncoder;
        this.employeeService = employeeService;
        this.employeeNetPaymentRepository = employeeNetPaymentRepository;
        this.employeeRepository = employeeRepository;
        this.masterJdbcTemplate = new JdbcTemplate(masterDataSource);
        this.emailService = emailService;
        this.pushNotificationService = pushNotificationService;
        this.schemaMaintenanceService = schemaMaintenanceService;
    }

    
          @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createEmployee(@RequestBody Employee employee) {
        if (employee.getClientId() == null) {
            return ResponseEntity.badRequest().build(); // must supply clientId
        }

        Integer employeeLimit;
        try {
            employeeLimit = resolveEmployeeLimitForClient(employee.getClientId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        long currentEmployeeCount = employeeRepository.countByClientId(employee.getClientId());
        if (employeeLimit != null && employeeLimit > 0 && currentEmployeeCount >= employeeLimit) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(EMPLOYEE_LIMIT_EXCEEDED_MESSAGE);
        }

        String companyCode;
        try {
            companyCode = resolveCompanyCodeForClient(employee.getClientId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        employee.setCompanyCode(companyCode);

        if (employee.getEmployeeCode() != null && !employee.getEmployeeCode().isBlank()) {
            String normalizedCode = employee.getEmployeeCode().trim().toUpperCase();
            if (isEmployeeCodeForCompany(normalizedCode, companyCode)) {
                Optional<Employee> existingCodeOwner = employeeRepository.findByEmployeeCode(normalizedCode);
                if (existingCodeOwner.isPresent()) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body("Employee code already exists");
                }
                employee.setEmployeeCode(normalizedCode);
            } else {
                // Keep flow intact and force valid default code generation.
                employee.setEmployeeCode(null);
            }
        }

        // Step 1: Plain password (could be pre-set or generated)
        String plainPassword = employee.getPassword();
        if (plainPassword == null || plainPassword.isBlank()) {
            // Optionally generate a random one if not provided
            plainPassword = java.util.UUID.randomUUID().toString().substring(0, 8);
            employee.setPassword(plainPassword);
        }

        // Step 2: Encrypt password
        String encryptedPassword = passwordEncoder.encode(plainPassword);
        employee.setPassword(encryptedPassword);

        Employee createdEmployee;
        try {
            // Step 3: Save (service assigns default employeeCode if blank)
            createdEmployee = employeeService.saveEmployee(employee);
        } catch (DataIntegrityViolationException ex) {
            String msg = resolveUniqueConflictMessage(ex);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(msg);
        }

        // Step 4: Send email with plain password
        EmailDetails emailDetails = new EmailDetails();
        emailDetails.setSender("b.inba.ips444@gmail.com");
        emailDetails.setReceiver(createdEmployee.getEmail());
        emailDetails.setSubject("Employee Account Credentials - Indra Institute Of Education");

        String message = String.format(
            """
            Dear %s,
            
            Welcome to Indra Institute of Education (IIE)! We are delighted to have you as part of our team.
            
            Login Credentials:
            Username: %s
            Password: %s
            
            Your Company Code: %s
            
            Please update your password after logging in.
            
            Regards,
            Admin, IIE""",
            createdEmployee.getFirstName(),
            createdEmployee.getUsername(),
            plainPassword, // unhashed version
            createdEmployee.getCompanyCode()
        );

        emailDetails.setMessage(message);
        String emailResponse = emailService.sendEmail(emailDetails);
        logger.info(emailResponse);

        return ResponseEntity.ok(createdEmployee);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createEmployeeWithImage(
            @ModelAttribute Employee employee,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            if (file != null && !file.isEmpty()) {
                String imageUrl = saveImageAndBuildUrl(file);
                employee.setProfileImage(imageUrl);
            }
            return createEmployee(employee);
        } catch (IOException e) {
            logger.error("Failed to save image while creating employee", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Image upload failed");
        }
    }

    @GetMapping("/{id}/profile-image")
    public ResponseEntity<String> getProfileImage(@PathVariable Long id) {
        Optional<Employee> optionalEmployee = employeeRepository.findById(id);

        if (!optionalEmployee.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        String profileImageUrl = optionalEmployee.get().getProfileImage();
        return ResponseEntity.ok(profileImageUrl);
    }

    // Get Employee by ID
    @GetMapping("/{id}")
    public ResponseEntity<Employee> getEmployeeById(
            @PathVariable String id,
            @RequestParam(value = "clientId", required = false) Long clientId,
            @RequestHeader(value = "X-Client-Id", required = false) Long headerClientId) {
        Long effectiveClientId = resolveClientId(clientId, headerClientId);
        Optional<Employee> employeeOptional = resolveEmployeeByIdOrCode(id, effectiveClientId);

        return employeeOptional
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Get Employee by Username
    @GetMapping("/username/{username}")
    public ResponseEntity<Employee> getEmployeeByUsername(@PathVariable String username) {
        return employeeService.getEmployeeByUsername(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/salary/{username}")
    public ResponseEntity<Double> getSalaryByUsername(@PathVariable String username) {
        Optional<Employee> employeeOptional = employeeRepository.findByUsername(username);

        if (employeeOptional.isPresent()) {
            Employee employee = employeeOptional.get();
            return ResponseEntity.ok(employee.getSalary());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    // Get All Employees
    @GetMapping
    public ResponseEntity<List<Employee>> getAllEmployees(
            @RequestParam(value = "clientId", required = false) Long clientId,
            @RequestHeader(value = "X-Client-Id", required = false) Long headerClientId) {
        Long effectiveClientId = resolveClientId(clientId, headerClientId);
        if (effectiveClientId != null) {
            return ResponseEntity.ok(employeeService.getEmployeesByClientId(effectiveClientId));
        }
        return ResponseEntity.ok(employeeService.getAllEmployees());
    }

    @GetMapping("/details/{username}/{password}")
    public ResponseEntity<Employee> getEmployeeByUsernameAndPassword(@PathVariable String username, @PathVariable String password) {
        Optional<Employee> employeeOptional = employeeRepository.findByUsername(username);

        if (employeeOptional.isPresent()) {
            Employee employee = employeeOptional.get();
           
            // Check if the password matches
            if (employee.getPassword().equals(password)) {
                return ResponseEntity.ok(employee);  // Return the entire employee details
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);  // Password does not match
            }
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);  // Employee not found
        }
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<Object> updateEmployee(
            @PathVariable Long id,
            @RequestParam(value = "clientId", required = false) Long clientId,
            @RequestHeader(value = "X-Client-Id", required = false) Long headerClientId,
            @RequestBody Employee updatedEmployeeData) {

        Long effectiveClientId = resolveClientId(clientId, headerClientId);
        if (effectiveClientId == null) {
            effectiveClientId = updatedEmployeeData.getClientId();
        }

        String resolvedTenantDb = resolveTenantDbByClientId(effectiveClientId);
        if (resolvedTenantDb == null || resolvedTenantDb.isBlank()) {
            String fallbackCompanyCode = updatedEmployeeData.getCompanyCode();
            if ((fallbackCompanyCode == null || fallbackCompanyCode.isBlank()) && effectiveClientId != null) {
                try {
                    fallbackCompanyCode = resolveCompanyCodeForClient(effectiveClientId);
                } catch (IllegalArgumentException ignored) {
                    // keep fallback null
                }
            }
            resolvedTenantDb = resolveTenantDbByCompanyCode(fallbackCompanyCode);
        }

        if (resolvedTenantDb != null && !resolvedTenantDb.isBlank()) {
            TenantContext.setTenantDb(resolvedTenantDb);
            logger.info("Tenant context set for updateEmployee. tenantDb={}, employeeId={}", resolvedTenantDb, id);
        }

        try {
            Optional<Employee> optionalEmployee = effectiveClientId != null
                    ? employeeRepository.findByIdAndClientId(id, effectiveClientId)
                    : employeeRepository.findById(id);
            if (optionalEmployee.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Employee not found with ID: " + id);
            }

            Employee existingEmployee = optionalEmployee.get();
            Map<String, String> oldProfileSnapshot = snapshotEmployeeForNotification(existingEmployee);

            String requestedEmail = updatedEmployeeData.getEmail() == null
                    ? null
                    : updatedEmployeeData.getEmail().trim().toLowerCase();
            if (requestedEmail == null || requestedEmail.isBlank()) {
                return ResponseEntity.badRequest().body("Email is required");
            }
            Optional<Employee> existingEmailOwner = employeeRepository.findByEmailIgnoreCaseAndIdNot(requestedEmail, id);
            if (existingEmailOwner.isPresent() && !existingEmailOwner.get().getId().equals(id)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Email already exists");
            }
            String requestedUsername = updatedEmployeeData.getUsername() == null
                    ? null
                    : updatedEmployeeData.getUsername().trim();
            if (requestedUsername == null || requestedUsername.isBlank()) {
                return ResponseEntity.badRequest().body("Username is required");
            }
            Optional<Employee> existingUsernameOwner =
                    employeeRepository.findByUsernameIgnoreCaseAndIdNot(requestedUsername, id);
            if (existingUsernameOwner.isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Username already exists");
            }

            // Update fields (password handled separately below)
            existingEmployee.setFirstName(updatedEmployeeData.getFirstName());
            existingEmployee.setLastName(updatedEmployeeData.getLastName());
            existingEmployee.setMobile(updatedEmployeeData.getMobile());
            existingEmployee.setGender(updatedEmployeeData.getGender());
            existingEmployee.setPosition(updatedEmployeeData.getPosition());
            existingEmployee.setBranch(updatedEmployeeData.getBranch());
            existingEmployee.setUsername(requestedUsername);
            existingEmployee.setDob(updatedEmployeeData.getDob());
            existingEmployee.setEmail(requestedEmail);
            existingEmployee.setProfileImage(updatedEmployeeData.getProfileImage());
            existingEmployee.setAddress(updatedEmployeeData.getAddress());
            existingEmployee.setAlternativeMobile(updatedEmployeeData.getAlternativeMobile());
            existingEmployee.setDateOfJoining(updatedEmployeeData.getDateOfJoining());
            existingEmployee.setResetToken(updatedEmployeeData.getResetToken());
            existingEmployee.setSalary(updatedEmployeeData.getSalary());
            existingEmployee.setWeekOff(updatedEmployeeData.getWeekOff());
            existingEmployee.setShiftStartTime(updatedEmployeeData.getShiftStartTime());
            existingEmployee.setShiftEndTime(updatedEmployeeData.getShiftEndTime());
            existingEmployee.setLeavePolicyType(updatedEmployeeData.getLeavePolicyType());
            existingEmployee.setCasualLeaveBalance(updatedEmployeeData.getCasualLeaveBalance());

            // Strong password update handling:
            // If password is present and non-blank in payload, treat it as NEW raw password
            // and always persist a fresh encoded hash.
            String requestedPassword = updatedEmployeeData.getPassword();
            if (requestedPassword != null) {
                requestedPassword = requestedPassword.trim();
                if (!requestedPassword.isEmpty()) {
                    String encodedNewPassword = passwordEncoder.encode(requestedPassword);
                    existingEmployee.setPassword(encodedNewPassword);
                    mirrorPasswordToMaster(existingEmployee, encodedNewPassword);
                    logger.info("Password updated for employeeId={}", id);
                }
            }
        

            String companyCode = updatedEmployeeData.getCompanyCode();
            try {
                companyCode = resolveCompanyCodeForClient(existingEmployee.getClientId());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body("Invalid client mapping for employee");
            }
            existingEmployee.setCompanyCode(companyCode);

            String requestedCode = updatedEmployeeData.getEmployeeCode();
            String finalEmployeeCode;
            if (requestedCode == null || requestedCode.isBlank()) {
                finalEmployeeCode = buildDefaultEmployeeCode(companyCode, existingEmployee.getId());
            } else {
                String normalizedRequestedCode = requestedCode.trim().toUpperCase();
                finalEmployeeCode = isEmployeeCodeForCompany(normalizedRequestedCode, companyCode)
                        ? normalizedRequestedCode
                        : buildDefaultEmployeeCode(companyCode, existingEmployee.getId());
            }

            Optional<Employee> existingCodeOwner = employeeRepository.findByEmployeeCode(finalEmployeeCode);
            if (existingCodeOwner.isPresent() && !existingCodeOwner.get().getId().equals(id)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("employeeCode already exists");
            }

            existingEmployee.setEmployeeCode(finalEmployeeCode);

            if (updatedEmployeeData.getAdditionalWorkingDays() != null) {
                if (existingEmployee.getAdditionalWorkingDays() == null) {
                    existingEmployee.setAdditionalWorkingDays(new java.util.ArrayList<>());
                } else {
                    existingEmployee.getAdditionalWorkingDays().clear();
                }
                for (com.example.demo.MODELS.EmployeeAdditionalWorkingDay day : updatedEmployeeData.getAdditionalWorkingDays()) {
                    day.setEmployee(existingEmployee);
                    existingEmployee.getAdditionalWorkingDays().add(day);
                }
            }

            try {
                employeeRepository.save(existingEmployee);
                mirrorEmployeeProfileToMaster(existingEmployee);
                sendProfileUpdateEmail(existingEmployee, oldProfileSnapshot);
                return ResponseEntity.ok(existingEmployee);
            } catch (DataIntegrityViolationException ex) {
                String msg = resolveUniqueConflictMessage(ex);
                return ResponseEntity.status(HttpStatus.CONFLICT).body(msg);
            }
        } finally {
            TenantContext.clear();
        }
    }

    @PostMapping("/{id}/change-password")
    public ResponseEntity<Object> changePassword(
            @PathVariable Long id,
            @RequestParam(value = "clientId", required = false) Long clientId,
            @RequestHeader(value = "X-Client-Id", required = false) Long headerClientId,
            @RequestBody Map<String, String> payload) {

        Long effectiveClientId = resolveClientId(clientId, headerClientId);
        String tenantDb = resolveTenantDbByClientId(effectiveClientId);
        if (tenantDb != null && !tenantDb.isBlank()) {
            TenantContext.setTenantDb(tenantDb);
        }

        try {
            Optional<Employee> employeeOptional = effectiveClientId != null
                    ? employeeRepository.findByIdAndClientId(id, effectiveClientId)
                    : employeeRepository.findById(id);

            if (employeeOptional.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Employee not found with ID: " + id);
            }

            String newPassword = payload.get("newPassword");
            String currentPassword = payload.get("currentPassword");

            if (newPassword == null || newPassword.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("newPassword is required");
            }

            Employee employee = employeeOptional.get();
            String storedPassword = employee.getPassword();

            // If current password is provided, validate it.
            if (currentPassword != null && !currentPassword.isBlank()) {
                boolean hashMatch = storedPassword != null && passwordEncoder.matches(currentPassword, storedPassword);
                boolean plainMatch = storedPassword != null && currentPassword.equals(storedPassword);
                if (!hashMatch && !plainMatch) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Current password is incorrect");
                }
            }

            employee.setPassword(passwordEncoder.encode(newPassword.trim()));
            mirrorPasswordToMaster(employee, employee.getPassword());
            employeeRepository.save(employee);
            logger.info("Password changed via dedicated endpoint for employeeId={}", id);

            return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
        } finally {
            TenantContext.clear();
        }
    }

    private String resolveUniqueConflictMessage(DataIntegrityViolationException ex) {
        String message = ex == null ? null : ex.getMostSpecificCause() == null
                ? ex.getMessage()
                : ex.getMostSpecificCause().getMessage();
        if (message == null) {
            return "Duplicate value exists";
        }
        String normalized = message.toLowerCase();
        if (normalized.contains("email") || normalized.contains("email_id")) {
            return "Email already exists";
        }
        if (normalized.contains("username")) {
            return "Username already exists";
        }
        if (normalized.contains("employee_code")) {
            return "Employee code already exists";
        }
        return "Duplicate value exists";
    }

    @PutMapping("/{id}/employee-code")
    public ResponseEntity<Object> updateEmployeeCode(
            @PathVariable Long id,
            @RequestParam(value = "clientId", required = false) Long clientId,
            @RequestHeader(value = "X-Client-Id", required = false) Long headerClientId,
            @RequestBody Map<String, String> payload) {

        Long effectiveClientId = resolveClientId(clientId, headerClientId);
        Optional<Employee> optionalEmployee = effectiveClientId != null
                ? employeeRepository.findByIdAndClientId(id, effectiveClientId)
                : employeeRepository.findById(id);
        if (optionalEmployee.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Employee not found with ID: " + id);
        }

        String newEmployeeCode = payload.get("employeeCode");
        if (newEmployeeCode == null || newEmployeeCode.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("employeeCode is required");
        }

        String trimmedCode = newEmployeeCode.trim().toUpperCase();
        Employee employee = optionalEmployee.get();
        String expectedCompanyCode;
        try {
            expectedCompanyCode = resolveCompanyCodeForClient(employee.getClientId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid client mapping for employee");
        }
        if (!isEmployeeCodeForCompany(trimmedCode, expectedCompanyCode)) {
            return ResponseEntity.badRequest().body("employeeCode must start with " + expectedCompanyCode + ".");
        }

        Optional<Employee> existingCodeOwner = employeeRepository.findByEmployeeCode(trimmedCode);
        if (existingCodeOwner.isPresent() && !existingCodeOwner.get().getId().equals(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("employeeCode already exists");
        }

        employee.setEmployeeCode(trimmedCode);
        employeeRepository.save(employee);

        return ResponseEntity.ok(employee);
    }

    // Delete Employee by ID
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployeeById(
            @PathVariable Long id,
            @RequestParam(value = "clientId", required = false) Long clientId,
            @RequestHeader(value = "X-Client-Id", required = false) Long headerClientId) {
        Long effectiveClientId = resolveClientId(clientId, headerClientId);
        if (effectiveClientId != null) {
            Optional<Employee> employeeOptional = employeeRepository.findByIdAndClientId(id, effectiveClientId);
            if (employeeOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            employeeRepository.delete(employeeOptional.get());
            return ResponseEntity.noContent().build();
        }
        employeeService.deleteEmployeeById(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/delete/{username}")
    public ResponseEntity<Void> deleteEmployeeByUsername(@PathVariable String username) {
        Optional<Employee> employeeOptional = employeeRepository.findByUsername(username);

        if (employeeOptional.isPresent()) {
            employeeRepository.delete(employeeOptional.get());  // Delete the employee
            return ResponseEntity.noContent().build();  // Return 204 No Content after successful deletion
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();  // Return 404 if employee not found
        }
    }

    // Upload or update profile image URL
    @PutMapping("/{id}/profile-image")
    public ResponseEntity<Employee> updateProfileImage(
            @PathVariable Long id,
            @RequestParam("imageUrl") String imageUrl) {

        Optional<Employee> optionalEmployee = employeeRepository.findById(id);

        if (!optionalEmployee.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        Employee employee = optionalEmployee.get();
        employee.setProfileImage(imageUrl);
        employeeRepository.save(employee);

        return ResponseEntity.ok(employee);
    }

    // Delete profile image (set to null or default)
    @DeleteMapping("/{id}/profile-image")
    public ResponseEntity<Employee> deleteProfileImage(@PathVariable Long id) {
        Optional<Employee> optionalEmployee = employeeRepository.findById(id);

        if (!optionalEmployee.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        Employee employee = optionalEmployee.get();
        employee.setProfileImage(null);  // Or set a default image path
        employeeRepository.save(employee);

        return ResponseEntity.ok(employee);
    }

    @PostMapping("/login")
    public ResponseEntity<Object> loginEmployee(@RequestBody Map<String, String> loginData) {
        String username = loginData.get("username");
        String rawPassword = loginData.get("password");
        String companyCode = loginData.get("companyCode");
        String pushToken = loginData.get("pushToken");

        if (username != null) {
            username = username.trim();
        }
        if (companyCode != null) {
            companyCode = companyCode.trim();
        }
        if (rawPassword == null || rawPassword.isBlank() || username == null || username.isBlank()) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        }

        logger.info("Login attempt - Username: {}, companyCode: {}", username, companyCode);

        String tenantDb = resolveTenantDbByCompanyCode(companyCode);
        if (tenantDb != null && !tenantDb.isBlank()) {
            TenantContext.setTenantDb(tenantDb);
            schemaMaintenanceService.ensureEmployeeSchema();
        }

        try {
            Optional<Employee> employeeOptional = employeeRepository.findFirstByUsernameIgnoreCase(username);
            boolean tenantUserFound = employeeOptional.isPresent();

            if (employeeOptional.isPresent()) {
                Employee employee = employeeOptional.get();
                String storedPassword = employee.getPassword();
                boolean hashMatch = storedPassword != null && passwordEncoder.matches(rawPassword, storedPassword);
                boolean plainMatch = storedPassword != null && rawPassword.equals(storedPassword);

                if (hashMatch || plainMatch) {
                    // Auto-upgrade legacy/plain passwords to encoded on successful login.
                    if (plainMatch) {
                        employee.setPassword(passwordEncoder.encode(rawPassword));
                        employeeRepository.save(employee);
                        logger.info("Upgraded plain password to hash for user: {}", username);
                    }
                    logger.info("Login successful for user: {}", username);
                    if (pushToken != null && !pushToken.isBlank()) {
                        pushNotificationService.registerToken(employee, pushToken);
                    }
                    pushNotificationService.notifyLogin(employee);
                    return ResponseEntity.ok(employee);
                }
                logger.warn("Invalid password for user: {}", username);
            } else {
                logger.warn("User not found: {}", username);
            }
            // Fallback to master only when tenant user is NOT found.
            // Never fallback on password mismatch, otherwise old password can still pass.
            if (tenantDb != null && !tenantDb.isBlank() && !tenantUserFound) {
                TenantContext.clear();
                Optional<Employee> masterEmployeeOptional = employeeRepository.findFirstByUsernameIgnoreCase(username);
                if (masterEmployeeOptional.isPresent()) {
                    Employee employee = masterEmployeeOptional.get();
                    String storedPassword = employee.getPassword();
                    boolean hashMatch = storedPassword != null && passwordEncoder.matches(rawPassword, storedPassword);
                    boolean plainMatch = storedPassword != null && rawPassword.equals(storedPassword);
                    if (hashMatch || plainMatch) {
                        if (plainMatch) {
                            employee.setPassword(passwordEncoder.encode(rawPassword));
                            employeeRepository.save(employee);
                        }
                        if (pushToken != null && !pushToken.isBlank()) {
                            pushNotificationService.registerToken(employee, pushToken);
                        }
                        pushNotificationService.notifyLogin(employee);
                        logger.info("Login successful from master fallback for user: {}", username);
                        return ResponseEntity.ok(employee);
                    }
                }
            }

            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        } finally {
            TenantContext.clear();
        }
    }

    @PostMapping("/{id}/push-token")
    public ResponseEntity<Object> registerPushToken(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {
        String token = payload.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body("token is required");
        }

        Optional<Employee> employeeOptional = employeeRepository.findById(id);
        if (employeeOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Employee not found");
        }

        pushNotificationService.registerToken(employeeOptional.get(), token);
        return ResponseEntity.ok("Token registered");
    }

    @PostMapping("/{id}/push-test")
    public ResponseEntity<Object> sendTestPush(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> payload) {
        Optional<Employee> employeeOptional = employeeRepository.findById(id);
        if (employeeOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Employee not found");
        }

        String title = payload == null ? null : payload.get("title");
        String body = payload == null ? null : payload.get("body");
        pushNotificationService.sendTest(employeeOptional.get(), title, body);
        return ResponseEntity.ok("Test push sent");
    }

    // Upload image
    @PutMapping("/{id}/upload-image")
    public ResponseEntity<Object> uploadImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        Optional<Employee> optionalEmployee = employeeRepository.findById(id);
        if (!optionalEmployee.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        try {
            String imageUrl = saveImageAndBuildUrl(file);

            Employee employee = optionalEmployee.get();
            employee.setProfileImage(imageUrl);
            employeeRepository.save(employee);

            return ResponseEntity.ok(imageUrl);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Upload failed");
        }
    }

    private String saveImageAndBuildUrl(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
        String fileName = UUID.randomUUID() + "_" + originalName.replace(" ", "_");

        Path uploadPath = Paths.get("uploads");
        Files.createDirectories(uploadPath);
        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/employees/image/")
                .path(fileName)
                .toUriString();
    }

    // Serve image
    @GetMapping("/image/{filename}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {
        try {
            Path path = Paths.get("uploads").resolve(filename);
            Resource resource = new UrlResource(path.toUri());

            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/calculate-net-payment")
    public ResponseEntity<EmployeeNetPayment> calculateNetPayment(
            @RequestParam Long employeeId,
            @RequestParam int month,
            @RequestParam int year,
            @RequestParam int paidLeaveDayCount,
            @RequestParam int casualLeaveDayCount,
            @RequestParam int holidayCount,
            @RequestParam String paidLeaveType,
            @RequestParam int presentDays
    ) {
        Optional<Employee> employeeOpt = employeeRepository.findById(employeeId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Employee employee = employeeOpt.get();
        EmployeeNetPayment payment = employeeService.calculateNetPayment(
            employee, month, year, paidLeaveDayCount, casualLeaveDayCount, holidayCount, paidLeaveType, presentDays
        );
        employeeNetPaymentRepository.save(payment);
        return ResponseEntity.ok(payment);
    }

    // Additional endpoint to get employees by client ID - FIXED SYNTAX
    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<Employee>> getEmployeesByClientId(@PathVariable Long clientId) {
        List<Employee> employees = employeeRepository.findByClientId(clientId);
        return ResponseEntity.ok(employees);
    }

    // Test endpoint to check if controller is working
    @GetMapping("/test")
    public ResponseEntity<String> testEndpoint() {
        return ResponseEntity.ok("Employee Controller is working!");
    }

    private Long resolveClientId(Long requestClientId, Long headerClientId) {
        return requestClientId != null ? requestClientId : headerClientId;
    }

    private String resolveCompanyCodeForClient(Long clientId) {
        if (clientId == null) {
            throw new IllegalArgumentException("clientId is required");
        }
        List<String> rows = masterJdbcTemplate.query(
                "SELECT company_code FROM clients WHERE id = ?",
                (rs, rowNum) -> rs.getString(1),
                clientId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Client company code not found");
        }
        String code = rows.get(0);
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Client company code not found");
        }
        return code.trim().toLowerCase();
    }

    private Integer resolveEmployeeLimitForClient(Long clientId) {
        if (clientId == null) {
            throw new IllegalArgumentException("clientId is required");
        }
        List<Integer> rows = masterJdbcTemplate.query(
                "SELECT employee_count FROM clients WHERE id = ?",
                (rs, rowNum) -> rs.getObject(1, Integer.class),
                clientId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Client employee count not found");
        }
        return rows.get(0);
    }

    private boolean isEmployeeCodeForCompany(String employeeCode, String companyCode) {
        if (employeeCode == null || companyCode == null) {
            return false;
        }
        return employeeCode.toLowerCase().startsWith(companyCode.toLowerCase() + ".");
    }

    private String buildDefaultEmployeeCode(String companyCode, Long employeeId) {
        return companyCode + ".EMP" + employeeId;
    }

    private String resolveTenantDbByCompanyCode(String companyCode) {
        if (companyCode == null || companyCode.isBlank()) {
            return null;
        }
        List<String> tenantRows = masterJdbcTemplate.query(
                "SELECT tenant_db_name FROM clients WHERE LOWER(company_code) = ? AND provisioning_status = 'ACTIVE'",
                (rs, rowNum) -> rs.getString(1),
                companyCode.trim().toLowerCase()
        );
        if (tenantRows.isEmpty()) {
            return null;
        }
        return tenantRows.get(0);
    }

    private String resolveTenantDbByClientId(Long clientId) {
        if (clientId == null) {
            return null;
        }
        List<String> tenantRows = masterJdbcTemplate.query(
                "SELECT tenant_db_name FROM clients WHERE id = ? AND provisioning_status = 'ACTIVE'",
                (rs, rowNum) -> rs.getString(1),
                clientId
        );
        if (tenantRows.isEmpty()) {
            return null;
        }
        return tenantRows.get(0);
    }

    private void mirrorPasswordToMaster(Employee sourceEmployee, String encodedPassword) {
        if (sourceEmployee == null || encodedPassword == null || encodedPassword.isBlank()) {
            return;
        }

        String currentTenantDb = TenantContext.getTenantDb();
        try {
            TenantContext.clear();
            Optional<Employee> masterOpt = Optional.empty();

            if (sourceEmployee.getId() != null && sourceEmployee.getClientId() != null) {
                masterOpt = employeeRepository.findByIdAndClientId(sourceEmployee.getId(), sourceEmployee.getClientId());
            }
            if (masterOpt.isEmpty() && sourceEmployee.getUsername() != null && sourceEmployee.getClientId() != null) {
                masterOpt = employeeRepository.findByUsernameAndClientId(sourceEmployee.getUsername(), sourceEmployee.getClientId());
            }

            if (masterOpt.isPresent()) {
                Employee masterEmployee = masterOpt.get();
                masterEmployee.setPassword(encodedPassword);
                employeeRepository.save(masterEmployee);
                logger.info("Mirrored password hash to master for employeeId={}", masterEmployee.getId());
            }
        } catch (Exception ex) {
            logger.warn("Failed to mirror password to master for employeeId={}", sourceEmployee.getId(), ex);
        } finally {
            if (currentTenantDb != null && !currentTenantDb.isBlank()) {
                TenantContext.setTenantDb(currentTenantDb);
            }
        }
    }

    private void mirrorEmployeeProfileToMaster(Employee sourceEmployee) {
        if (sourceEmployee == null) {
            return;
        }

        String currentTenantDb = TenantContext.getTenantDb();
        try {
            TenantContext.clear();
            Optional<Employee> masterOpt = Optional.empty();

            if (sourceEmployee.getId() != null && sourceEmployee.getClientId() != null) {
                masterOpt = employeeRepository.findByIdAndClientId(sourceEmployee.getId(), sourceEmployee.getClientId());
            }
            if (masterOpt.isEmpty() && sourceEmployee.getUsername() != null && sourceEmployee.getClientId() != null) {
                masterOpt = employeeRepository.findByUsernameAndClientId(sourceEmployee.getUsername(), sourceEmployee.getClientId());
            }

            if (masterOpt.isEmpty()) {
                logger.warn("Master employee not found while mirroring profile for employeeId={}", sourceEmployee.getId());
                return;
            }

            Employee masterEmployee = masterOpt.get();
            masterEmployee.setFirstName(sourceEmployee.getFirstName());
            masterEmployee.setLastName(sourceEmployee.getLastName());
            masterEmployee.setMobile(sourceEmployee.getMobile());
            masterEmployee.setGender(sourceEmployee.getGender());
            masterEmployee.setPosition(sourceEmployee.getPosition());
            masterEmployee.setBranch(sourceEmployee.getBranch());
            masterEmployee.setUsername(sourceEmployee.getUsername());
            masterEmployee.setDob(sourceEmployee.getDob());
            masterEmployee.setEmail(sourceEmployee.getEmail());
            masterEmployee.setProfileImage(sourceEmployee.getProfileImage());
            masterEmployee.setAddress(sourceEmployee.getAddress());
            masterEmployee.setAlternativeMobile(sourceEmployee.getAlternativeMobile());
            masterEmployee.setDateOfJoining(sourceEmployee.getDateOfJoining());
            masterEmployee.setResetToken(sourceEmployee.getResetToken());
            masterEmployee.setSalary(sourceEmployee.getSalary());
            masterEmployee.setWeekOff(sourceEmployee.getWeekOff());
            masterEmployee.setShiftStartTime(sourceEmployee.getShiftStartTime());
            masterEmployee.setShiftEndTime(sourceEmployee.getShiftEndTime());
            masterEmployee.setLeavePolicyType(sourceEmployee.getLeavePolicyType());
            masterEmployee.setCasualLeaveBalance(sourceEmployee.getCasualLeaveBalance());
            masterEmployee.setCompanyCode(sourceEmployee.getCompanyCode());
            masterEmployee.setEmployeeCode(sourceEmployee.getEmployeeCode());
            masterEmployee.setClientId(sourceEmployee.getClientId());

            if (masterEmployee.getAdditionalWorkingDays() == null) {
                masterEmployee.setAdditionalWorkingDays(new ArrayList<>());
            } else {
                masterEmployee.getAdditionalWorkingDays().clear();
            }
            if (sourceEmployee.getAdditionalWorkingDays() != null) {
                for (EmployeeAdditionalWorkingDay sourceDay : sourceEmployee.getAdditionalWorkingDays()) {
                    EmployeeAdditionalWorkingDay copiedDay = new EmployeeAdditionalWorkingDay();
                    copiedDay.setDayType(sourceDay.getDayType());
                    copiedDay.setTimeIn(sourceDay.getTimeIn());
                    copiedDay.setTimeOut(sourceDay.getTimeOut());
                    copiedDay.setEmployee(masterEmployee);
                    masterEmployee.getAdditionalWorkingDays().add(copiedDay);
                }
            }

            employeeRepository.save(masterEmployee);
            logger.info("Mirrored profile to master for employeeId={}", masterEmployee.getId());
        } catch (Exception ex) {
            logger.warn("Failed to mirror profile to master for employeeId={}", sourceEmployee.getId(), ex);
        } finally {
            if (currentTenantDb != null && !currentTenantDb.isBlank()) {
                TenantContext.setTenantDb(currentTenantDb);
            }
        }
    }

    private Map<String, String> snapshotEmployeeForNotification(Employee employee) {
        Map<String, String> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("First Name", safeText(employee.getFirstName()));
        snapshot.put("Last Name", safeText(employee.getLastName()));
        snapshot.put("Email", safeText(employee.getEmail()));
        snapshot.put("Mobile", safeText(employee.getMobile()));
        snapshot.put("Alternative Mobile", safeText(employee.getAlternativeMobile()));
        snapshot.put("Branch", safeText(employee.getBranch()));
        snapshot.put("Position", safeText(employee.getPosition()));
        snapshot.put("Address", safeText(employee.getAddress()));
        snapshot.put("Date of Birth", safeText(employee.getDob()));
        snapshot.put("Date of Joining", safeText(employee.getDateOfJoining()));
        snapshot.put("Salary", safeText(employee.getSalary()));
        snapshot.put("Week Off", safeText(employee.getWeekOff()));
        snapshot.put("Shift Start Time", safeText(employee.getShiftStartTime()));
        snapshot.put("Shift End Time", safeText(employee.getShiftEndTime()));
        snapshot.put("Leave Policy", safeText(employee.getLeavePolicyType()));
        snapshot.put("Casual Leave Balance", safeText(employee.getCasualLeaveBalance()));
        snapshot.put("Employee Code", safeText(employee.getEmployeeCode()));
        snapshot.put("Username", safeText(employee.getUsername()));
        return snapshot;
    }

    private void sendProfileUpdateEmail(Employee employee, Map<String, String> oldProfileSnapshot) {
        if (employee == null || oldProfileSnapshot == null || employee.getEmail() == null || employee.getEmail().isBlank()) {
            return;
        }

        Map<String, String> newSnapshot = snapshotEmployeeForNotification(employee);
        List<String> changedLines = new ArrayList<>();

        for (Map.Entry<String, String> entry : oldProfileSnapshot.entrySet()) {
            String field = entry.getKey();
            String oldValue = entry.getValue();
            String newValue = newSnapshot.getOrDefault(field, "--");
            if (!oldValue.equals(newValue)) {
                changedLines.add(String.format("%s: %s -> %s", field, oldValue, newValue));
            }
        }

        if (changedLines.isEmpty()) {
            return;
        }

        String employeeName = (safeText(employee.getFirstName()) + " " + safeText(employee.getLastName())).trim();
        if (employeeName.isBlank() || employeeName.equals("-- --")) {
            employeeName = safeText(employee.getUsername());
        }

        StringBuilder message = new StringBuilder();
        message.append("Dear ").append(employeeName).append(",\n\n");
        message.append("Your profile details were updated by the admin.\n");
        message.append("Updated fields:\n");
        for (String line : changedLines) {
            message.append("- ").append(line).append("\n");
        }
        message.append("\nIf you did not expect these changes, please contact your administrator.\n\n");
        message.append("Regards,\nZenTime Admin");

        try {
            EmailDetails details = new EmailDetails();
            details.setSender("b.inba.ips444@gmail.com");
            details.setReceiver(employee.getEmail());
            details.setSubject("Profile Updated - ZenTime");
            details.setMessage(message.toString());
            emailService.sendEmail(details);
        } catch (Exception ex) {
            logger.warn("Failed to send profile update email for employeeId={}", employee.getId(), ex);
        }
    }

    private String safeText(Object value) {
        if (value == null) {
            return "--";
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? "--" : text;
    }

    private Optional<Employee> resolveEmployeeByIdOrCode(String idOrCode, Long clientId) {
        if (idOrCode == null || idOrCode.isBlank()) {
            return Optional.empty();
        }

        String normalized = idOrCode.trim();

        Optional<Employee> byNumericId = tryFindByNumericId(normalized, clientId);
        if (byNumericId.isPresent()) {
            return byNumericId;
        }

        Optional<Employee> byCode = employeeRepository.findByEmployeeCode(normalized.toUpperCase());
        if (byCode.isPresent() && (clientId == null || clientId.equals(byCode.get().getClientId()))) {
            return byCode;
        }

        Matcher matcher = Pattern.compile("(?i)(?:^|\\.)EMP(\\d+)$").matcher(normalized);
        if (matcher.find()) {
            return tryFindByNumericId(matcher.group(1), clientId);
        }

        return Optional.empty();
    }

    private Optional<Employee> tryFindByNumericId(String rawId, Long clientId) {
        try {
            Long parsedId = Long.parseLong(rawId);
            if (clientId != null) {
                return employeeRepository.findByIdAndClientId(parsedId, clientId);
            }
            return employeeRepository.findById(parsedId);
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}





