package com.example.demo.repo;



import java.util.Optional;


import org.springframework.data.jpa.repository.JpaRepository;


import com.example.demo.MODELS.Employee;


public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByUsername(String username);
}
