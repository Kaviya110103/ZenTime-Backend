package com.example.demo.controller;




import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.demo.MODELS.Employee;
import com.example.demo.MODELS.LeavePermission;
import com.example.demo.repo.EmployeeRepository;
import com.example.demo.repo.LeavePermissionRepository;

import java.util.List;
import java.util.Optional;


@RestController
@RequestMapping("/api/leaves")
@CrossOrigin(origins = "*") // Allow frontend to access

public class LeavePermissionController {


    @Autowired
    private LeavePermissionRepository leavePermissionRepository;


    @Autowired
    private EmployeeRepository employeeRepository;


    // ✅ 1. POST Leave Permission
    @PostMapping("/create")
    public ResponseEntity<String> createLeave(@RequestParam Long employeeId,
                                              @RequestBody LeavePermission leavePermission) {
        Optional<Employee> employeeOpt = employeeRepository.findById(employeeId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Employee not found.");
        }
        leavePermission.setEmployee(employeeOpt.get());
        leavePermission.setStatus("pending"); // default
        leavePermissionRepository.save(leavePermission);
        return ResponseEntity.ok("Leave permission submitted successfully.");
    }


    // ✅ 2. GET all leave permissions
    @GetMapping("/all")
    public List<LeavePermission> getAllLeaves() {
        return leavePermissionRepository.findAll();
    }


    // ✅ 3. GET leave permissions by Employee ID
    @GetMapping("/employee/{employeeId}")
    public List<LeavePermission> getLeavesByEmployee(@PathVariable Long employeeId) {
        return leavePermissionRepository.findByEmployeeId(employeeId);
    }


    // ✅ 4. PUT update leave status by Leave ID
    @PutMapping("/status/{leaveId}")
    public ResponseEntity<String> updateLeaveStatus(@PathVariable Long leaveId,
                                                    @RequestParam String status) {
        Optional<LeavePermission> leaveOpt = leavePermissionRepository.findById(leaveId);
        if (leaveOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Leave ID not found.");
        }
        LeavePermission leave = leaveOpt.get();
        leave.setStatus(status.toLowerCase());
        leavePermissionRepository.save(leave);
        return ResponseEntity.ok("Leave status updated to " + status);
    }


    // ✅ 5. GET leave by status (pending/approved/rejected)
    @GetMapping("/status/{status}")
    public List<LeavePermission> getLeavesByStatus(@PathVariable String status) {
        return leavePermissionRepository.findByStatusIgnoreCase(status);
    }
}


