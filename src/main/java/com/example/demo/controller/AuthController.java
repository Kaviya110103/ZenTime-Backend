// package com.example.demo.controller;

// import java.util.Optional;

// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.CrossOrigin;
// import org.springframework.web.bind.annotation.PostMapping;
// import org.springframework.web.bind.annotation.RequestBody;
// import org.springframework.web.bind.annotation.RequestMapping;
// import org.springframework.web.bind.annotation.RestController;

// import com.example.demo.MODELS.Employee;
// // import com.example.demo.dto.LoginRequest;
// import com.example.demo.repo.EmployeeRepository;

// @RestController
// @RequestMapping("/api/auth")
// @CrossOrigin(origins = "*") 
// public class AuthController {

//     @Autowired
//     private EmployeeRepository employeeRepository;

//     @PostMapping("/login")
//     public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
//         Optional<Employee> employeeOpt = employeeRepository.findByUsernameAndPassword(
//                 loginRequest.getUsername(), loginRequest.getPassword());

//         if (employeeOpt.isPresent()) {
//             Employee employee = employeeOpt.get();
//             return ResponseEntity.ok(employee); // You may return a DTO instead for better security
//         } else {
//             return ResponseEntity.status(401).body("Invalid username or password");
//         }
//     }
// }