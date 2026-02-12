package com.example.demo.controller;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.MODELS.EmailDetails;
import com.example.demo.MODELS.Employee;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.service.EmailService;

@RestController
@RequestMapping("/api/employees")
@CrossOrigin(origins = "*")
public class EmployeeController {

    private static final String ERROR_KEY = "error";
    private static final String MESSAGE_KEY = "message";
    private static final String TIME_FORMAT = "HH:mm";
    private static final String ACTUAL_TIME_IN = "actualTimeIn";
    private static final String ACTUAL_TIME_OUT = "actualTimeOut";
    private static final String EMPLOYEE_NOT_FOUND = "Employee not found with ID: ";
    private static final String INVALID_TIME_FORMAT = "Invalid time format. Please use HH:mm format (e.g., 09:00, 17:30)";

    private final PasswordEncoder passwordEncoder;
    private final EmployeeRepository employeeRepository;
    private final EmailService emailService;

    public EmployeeController(PasswordEncoder passwordEncoder, EmployeeRepository employeeRepository, EmailService emailService) {
        this.passwordEncoder = passwordEncoder;
        this.employeeRepository = employeeRepository;
        this.emailService = emailService;
    }

    // ... (All existing methods remain exactly the same until the new methods below)

    // =============== NEW ENDPOINTS FOR ACTUAL TIME IN/OUT ===============
    
    /**
     * POST: Set/Update actual time in and time out for an employee
     * Endpoint: POST /api/employees/{id}/time-schedule
     */
    @PutMapping("/{id}/time-schedule")
    public ResponseEntity<Map<String, Object>> setEmployeeTimeSchedule(
            @PathVariable Long id,
            @RequestBody Map<String, String> timeData) {
        
        Optional<Employee> optionalEmployee = employeeRepository.findById(id);
        if (optionalEmployee.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(ERROR_KEY, EMPLOYEE_NOT_FOUND + id));
        }

        Employee employee = optionalEmployee.get();
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(TIME_FORMAT);

        try {
            // Parse and set actual time in
            if (timeData.containsKey(ACTUAL_TIME_IN) && timeData.get(ACTUAL_TIME_IN) != null) {
                LocalTime actualTimeIn = LocalTime.parse(timeData.get(ACTUAL_TIME_IN), timeFormatter);
                employee.setActualTimeIn(actualTimeIn);
            }

            // Parse and set actual time out
            if (timeData.containsKey(ACTUAL_TIME_OUT) && timeData.get(ACTUAL_TIME_OUT) != null) {
                LocalTime actualTimeOut = LocalTime.parse(timeData.get(ACTUAL_TIME_OUT), timeFormatter);
                employee.setActualTimeOut(actualTimeOut);
            }

            employeeRepository.save(employee);

            Map<String, Object> response = new HashMap<>();
            response.put(MESSAGE_KEY, "Time schedule updated successfully");
            response.put("employeeId", employee.getId());
            response.put(ACTUAL_TIME_IN, employee.getActualTimeIn() != null ? 
                employee.getActualTimeIn().format(timeFormatter) : null);
            response.put(ACTUAL_TIME_OUT, employee.getActualTimeOut() != null ? 
                employee.getActualTimeOut().format(timeFormatter) : null);

            return ResponseEntity.ok(response);

        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of(ERROR_KEY, INVALID_TIME_FORMAT));
        }
    }

    /**
     * GET: Get actual time in and time out for an employee
     * Endpoint: GET /api/employees/{id}/time-schedule
     */
    @GetMapping("/{id}/time-schedule")
    public ResponseEntity<Map<String, Object>> getEmployeeTimeSchedule(@PathVariable Long id) {
        Optional<Employee> optionalEmployee = employeeRepository.findById(id);
        if (optionalEmployee.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(ERROR_KEY, EMPLOYEE_NOT_FOUND + id));
        }

        Employee employee = optionalEmployee.get();
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(TIME_FORMAT);

        Map<String, Object> response = new HashMap<>();
        response.put("employeeId", employee.getId());
        response.put("employeeName", employee.getFirstName() + " " + employee.getLastName());
        response.put(ACTUAL_TIME_IN, employee.getActualTimeIn() != null ? 
            employee.getActualTimeIn().format(timeFormatter) : null);
        response.put(ACTUAL_TIME_OUT, employee.getActualTimeOut() != null ? 
            employee.getActualTimeOut().format(timeFormatter) : null);
        response.put(MESSAGE_KEY, employee.getActualTimeIn() == null && employee.getActualTimeOut() == null ?
            "No fixed time schedule set for this employee" : "Time schedule retrieved successfully");

        return ResponseEntity.ok(response);
    }

    /**
     * GET: Get all employees
     * Endpoint: GET /api/employees
     */
    @GetMapping
    public ResponseEntity<List<Employee>> getAllEmployees() {
        List<Employee> employees = employeeRepository.findAll();
        return ResponseEntity.ok(employees);
    }

    /**
     * GET: Get all employees with their time schedules
     * Endpoint: GET /api/employees/time-schedules
     */
    @GetMapping("/time-schedules")
    public ResponseEntity<List<Employee>> getAllEmployeesWithTimeSchedules() {
        List<Employee> employees = employeeRepository.findAll();
        return ResponseEntity.ok(employees);
    }

    /**
     * POST: Create employee with actual time in/out (updated create endpoint)
     */
    @PostMapping("/create-with-schedule")
    public ResponseEntity<Map<String, Object>> createEmployeeWithTimeSchedule(@RequestBody Map<String, Object> employeeData) {
        try {
            // Extract employee basic info
            Employee employee = new Employee();
            employee.setFirstName((String) employeeData.get("firstName"));
            employee.setLastName((String) employeeData.get("lastName"));
            employee.setMobile((String) employeeData.get("mobile"));
            employee.setGender((String) employeeData.get("gender"));
            employee.setPosition((String) employeeData.get("position"));
            employee.setBranch((String) employeeData.get("branch"));
            employee.setUsername((String) employeeData.get("username"));
            employee.setDob((String) employeeData.get("dob"));
            employee.setEmail((String) employeeData.get("email"));
            employee.setAddress((String) employeeData.get("address"));
            employee.setAlternativeMobile((String) employeeData.get("alternativeMobile"));
            employee.setDateOfJoining((String) employeeData.get("dateOfJoining"));
            employee.setSalary(Double.valueOf(employeeData.get("salary").toString()));
            employee.setWeekOff((String) employeeData.get("weekOff"));
            
            if (employeeData.get("clientId") != null) {
                employee.setClientId(Long.valueOf(employeeData.get("clientId").toString()));
            }
            if (employeeData.get("companyCode") != null) {
                employee.setCompanyCode((String) employeeData.get("companyCode"));
            }

            // Set actual time in/out if provided
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(TIME_FORMAT);
            
            if (employeeData.containsKey(ACTUAL_TIME_IN) && employeeData.get(ACTUAL_TIME_IN) != null) {
                LocalTime actualTimeIn = LocalTime.parse(employeeData.get(ACTUAL_TIME_IN).toString(), timeFormatter);
                employee.setActualTimeIn(actualTimeIn);
            }
            
            if (employeeData.containsKey(ACTUAL_TIME_OUT) && employeeData.get(ACTUAL_TIME_OUT) != null) {
                LocalTime actualTimeOut = LocalTime.parse(employeeData.get(ACTUAL_TIME_OUT).toString(), timeFormatter);
                employee.setActualTimeOut(actualTimeOut);
            }

            // Handle password
            String plainPassword = (String) employeeData.get("password");
            if (plainPassword == null || plainPassword.isBlank()) {
                plainPassword = UUID.randomUUID().toString().substring(0, 8);
            }
            employee.setPassword(passwordEncoder.encode(plainPassword));

            // Save employee
            Employee savedEmployee = employeeRepository.save(employee);

            // Send email notification
            EmailDetails emailDetails = new EmailDetails();
            emailDetails.setSender("b.inba.ips444@gmail.com");
            emailDetails.setReceiver(savedEmployee.getEmail());
            emailDetails.setSubject("Employee Account Credentials - Indra Institute Of Education");

            String message = String.format(
                """
                Dear %s,

                Welcome to Indra Institute of Education (IIE)! We are delighted to have you as part of our team.

                Login Credentials:
                Username: %s
                Password: %s

                Your Company Code: %s

                Your Fixed Schedule:
                Time In: %s
                Time Out: %s

                Please update your password after logging in.

                Regards,
                Admin, IIE
                """,
                savedEmployee.getFirstName(),
                savedEmployee.getUsername(),
                plainPassword,
                savedEmployee.getCompanyCode(),
                savedEmployee.getActualTimeIn() != null ? savedEmployee.getActualTimeIn().format(timeFormatter) : "Not Set",
                savedEmployee.getActualTimeOut() != null ? savedEmployee.getActualTimeOut().format(timeFormatter) : "Not Set"
            );

            emailDetails.setMessage(message);
            emailService.sendEmail(emailDetails);

            Map<String, Object> response = new HashMap<>();
            response.put(MESSAGE_KEY, "Employee created successfully with time schedule");
            response.put("employee", savedEmployee);
            response.put(ACTUAL_TIME_IN, savedEmployee.getActualTimeIn());
            response.put(ACTUAL_TIME_OUT, savedEmployee.getActualTimeOut());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of(ERROR_KEY, "Invalid input data: " + e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, "Failed to create employee: " + e.getMessage()));
        }
    }

    /**
     * GET: Get employees by time schedule (filtering)
     * Endpoint: GET /api/employees/filter-by-time?timeIn=09:00&timeOut=17:00
     */
    @GetMapping("/filter-by-time")
    public ResponseEntity<List<Employee>> getEmployeesByTimeSchedule(
            @RequestParam(required = false) String timeIn,
            @RequestParam(required = false) String timeOut) {
        
        List<Employee> allEmployees = employeeRepository.findAll();
        
        if (timeIn != null || timeOut != null) {
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(TIME_FORMAT);
            
            List<Employee> filteredEmployees = allEmployees.stream()
                    .filter(employee -> {
                        boolean matches = true;
                        
                        if (timeIn != null && employee.getActualTimeIn() != null) {
                            LocalTime filterTimeIn = LocalTime.parse(timeIn, timeFormatter);
                            matches = matches && employee.getActualTimeIn().equals(filterTimeIn);
                        }
                        
                        if (timeOut != null && employee.getActualTimeOut() != null) {
                            LocalTime filterTimeOut = LocalTime.parse(timeOut, timeFormatter);
                            matches = matches && employee.getActualTimeOut().equals(filterTimeOut);
                        }
                        
                        return matches;
                    })
                    .toList();
            
            return ResponseEntity.ok(filteredEmployees);
        }
        
        return ResponseEntity.ok(allEmployees);
    }

    // ... (All existing methods after this remain exactly the same)
}