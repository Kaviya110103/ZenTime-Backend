package com.example.demo.controller;



import java.util.List;
import java.util.Optional;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.MODELS.EmailDetails;
import com.example.demo.MODELS.Employee;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.service.EmailService;
import com.example.demo.service.EmployeeService;


@RestController
@RequestMapping("/api/employees")   // http://localhost:8080
@CrossOrigin(origins = "*") // Allow frontend to access
public class EmployeeController {


    @Autowired
    private EmployeeService employeeService;


   
    @Autowired
    private EmployeeRepository employeeRepository;


@Autowired
private EmailService emailService;

   
    @PostMapping
    public ResponseEntity<Employee> createEmployee(@RequestBody Employee employee) {
        Employee createdEmployee = employeeService.saveEmployee(employee);

        // Send email to the newly created employee
        EmailDetails emailDetails = new EmailDetails();
        emailDetails.setSender("kaviyagvg2023@gmail.com"); // Replace with your email
        emailDetails.setReceiver(employee.getEmail());
        emailDetails.setSubject("Employee Account Credentials - Indra Institute Of Education");

        String message = String.format(
            "Dear %s,\n\n" +
            "Welcome to Indra Institute of Education (IIE)! We are delighted to have you as part of our team and look forward to your valuable contributions.\n\n" +
            "To get started, please find your official login credentials below:\n\n" +
            "Username: %s\n" +
            "Password: %s\n\n" +
            "Please log in using these credentials and update your password upon your first login for security purposes. If you encounter any issues, feel free to reach out to the IT support team at [Support Email/Contact Number].\n\n" +
            "We are excited to embark on this journey with you and wish you success in your role.\n\n" +
            "Best Regards,\n" +
            "[Your Name]\n" +
            "Admin, Indra Institute of Education (IIE)\n" +
            "[Your Contact Information]",
            employee.getFirstName(),
            employee.getUsername(),
            employee.getPassword()
        );

        emailDetails.setMessage(message);

        String emailResponse = emailService.sendEmail(emailDetails);
        System.out.println(emailResponse); // Log the email response for debugging

        return ResponseEntity.ok(createdEmployee);
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

}

