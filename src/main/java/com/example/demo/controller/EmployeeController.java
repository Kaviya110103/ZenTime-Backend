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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.MODELS.EmailDetails;
import com.example.demo.MODELS.Employee;
import com.example.demo.MODELS.EmployeeNetPayment;
import com.example.demo.repo.EmployeeNetPaymentRepository;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.service.EmailService;
import com.example.demo.service.EmployeeService;


@RestController
@RequestMapping("/api/employees")   // http://localhost:8080
@CrossOrigin(origins = "*") // Allow frontend to access
public class EmployeeController {

@Autowired
private PasswordEncoder passwordEncoder;

    @Autowired
    private EmployeeService employeeService;

@Autowired
private EmployeeNetPaymentRepository employeeNetPaymentRepository;
   
    @Autowired
    private EmployeeRepository employeeRepository;


@Autowired
private EmailService emailService;

   
//   @PostMapping
// public ResponseEntity<Employee> createEmployee(@RequestBody Employee employee) {
//     // Step 1: Generate random password (optional)
//     String plainPassword = employee.getPassword(); // or generate new one

//     // Step 2: Encrypt the password before saving
//     String encryptedPassword = passwordEncoder.encode(plainPassword);
//     employee.setPassword(encryptedPassword);

//     // Step 3: Save employee with encrypted password
//     Employee createdEmployee = employeeService.saveEmployee(employee);

//     // Step 4: Send email with plain password
//     EmailDetails emailDetails = new EmailDetails();
//     emailDetails.setSender("b.inba.ips444@gmail.com");
//     emailDetails.setReceiver(employee.getEmail());
//     emailDetails.setSubject("Employee Account Credentials - Indra Institute Of Education");

//     String message = String.format(
//         "Dear %s,\n\n" +
//         "Welcome to Indra Institute of Education (IIE)! We are delighted to have you as part of our team.\n\n" +
//         "Login Credentials:\n" +
//         "Username: %s\n" +
//         "Password: %s\n\n" +
//         "Please update your password after logging in.\n\n" +
//         "Regards,\n" +
//         "Admin, IIE",
//         employee.getFirstName(),
//         employee.getUsername(),
//         plainPassword // only email the unhashed version
//     );

//     emailDetails.setMessage(message);
//     String emailResponse = emailService.sendEmail(emailDetails);
//     System.out.println(emailResponse);

//     return ResponseEntity.ok(createdEmployee);
// }


    @PostMapping
    public ResponseEntity<Employee> createEmployee( @RequestBody Employee employee) {
        if (employee.getClientId() == null) {
            return ResponseEntity.badRequest().build(); // must supply clientId
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

        // Step 3: Save (service will assign clientEmployeeId)
        Employee createdEmployee = employeeService.saveEmployee(employee);

        // Step 4: Send email with plain password
        EmailDetails emailDetails = new EmailDetails();
        emailDetails.setSender("b.inba.ips444@gmail.com");
        emailDetails.setReceiver(createdEmployee.getEmail());
        emailDetails.setSubject("Employee Account Credentials - Indra Institute Of Education");

        String message = String.format(
            "Dear %s,\n\n" +
            "Welcome to Indra Institute of Education (IIE)! We are delighted to have you as part of our team.\n\n" +
            "Login Credentials:\n" +
            "Username: %s\n" +
            "Password: %s\n\n" +
            "Your Company Code: %s\n\n" +
            "Please update your password after logging in.\n\n" +
            "Regards,\n" +
            "Admin, IIE",
            createdEmployee.getFirstName(),
            createdEmployee.getUsername(),
            createdEmployee.getCompanyCode(),
            plainPassword // unhashed version
        );

        emailDetails.setMessage(message);
        String emailResponse = emailService.sendEmail(emailDetails);
        System.out.println(emailResponse);

        return ResponseEntity.ok(createdEmployee);
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



    // Create or Update Employee
    // @PostMapping
    // public ResponseEntity<Employee> saveEmployee(@RequestBody Employee employee) {
    //     Employee savedEmployee = employeeService.saveEmployee(employee);
    //     return ResponseEntity.ok(savedEmployee);
    // }


    // Get Employee by ID
    @GetMapping("/{id}")
    public ResponseEntity<Employee> getEmployeeById(@PathVariable Long id) {
        return employeeService.getEmployeeById(id)
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
    public ResponseEntity<List<Employee>> getAllEmployees() {
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
public ResponseEntity<?> updateEmployee(
        @PathVariable Long id,
        @RequestBody Employee updatedEmployeeData) {

    Optional<Employee> optionalEmployee = employeeRepository.findById(id);
    if (optionalEmployee.isEmpty()) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Employee not found with ID: " + id);
    }

    Employee existingEmployee = optionalEmployee.get();

    // Update fields except password
    existingEmployee.setFirstName(updatedEmployeeData.getFirstName());
    existingEmployee.setLastName(updatedEmployeeData.getLastName());
    existingEmployee.setMobile(updatedEmployeeData.getMobile());
    existingEmployee.setGender(updatedEmployeeData.getGender());
    existingEmployee.setPosition(updatedEmployeeData.getPosition());
    existingEmployee.setBranch(updatedEmployeeData.getBranch());
    existingEmployee.setUsername(updatedEmployeeData.getUsername());
    existingEmployee.setDob(updatedEmployeeData.getDob());
    existingEmployee.setEmail(updatedEmployeeData.getEmail());
    existingEmployee.setProfileImage(updatedEmployeeData.getProfileImage());
    existingEmployee.setAddress(updatedEmployeeData.getAddress());
    existingEmployee.setAlternativeMobile(updatedEmployeeData.getAlternativeMobile());
    existingEmployee.setDateOfJoining(updatedEmployeeData.getDateOfJoining());
    existingEmployee.setResetToken(updatedEmployeeData.getResetToken());
    existingEmployee.setSalary(updatedEmployeeData.getSalary());
    existingEmployee.setWeekOff(updatedEmployeeData.getWeekOff());

    employeeRepository.save(existingEmployee);
    return ResponseEntity.ok(existingEmployee);
}



    // Delete Employee by ID
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployeeById(@PathVariable Long id) {
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
public ResponseEntity<?> loginEmployee(@RequestBody Map<String, String> loginData) {
    String username = loginData.get("username");
    String rawPassword = loginData.get("password");

    Optional<Employee> employeeOptional = employeeRepository.findByUsername(username);

    if (employeeOptional.isPresent()) {
        Employee employee = employeeOptional.get();
        if (passwordEncoder.matches(rawPassword, employee.getPassword())) {
            return ResponseEntity.ok(employee); // success = valid JSON
        }
    }

    // failure = also return JSON
    return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
}







    // Upload image
    @PutMapping("/{id}/upload-image")
    public ResponseEntity<?> uploadImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        Optional<Employee> optionalEmployee = employeeRepository.findById(id);
        if (!optionalEmployee.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        try {
            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path uploadPath = Paths.get("uploads");
            Files.createDirectories(uploadPath);
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String imageUrl = "http://192.168.1.4:8080/api/employees/image/" + fileName;

            Employee employee = optionalEmployee.get();
            employee.setProfileImage(imageUrl);
            employeeRepository.save(employee);

            return ResponseEntity.ok(imageUrl);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Upload failed");
        }
    }

    // Serve image
    @GetMapping("/emp-image/{filename}")
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


}







