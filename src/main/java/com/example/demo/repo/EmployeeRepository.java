package com.example.demo.repo;



import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.MODELS.Employee;


public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByUsername(String username);
    Optional<Employee> findByUsernameIgnoreCaseAndIdNot(String username, Long id);
    Optional<Employee> findByEmployeeCode(String employeeCode);
    Optional<Employee> findByEmail(String email);
    Optional<Employee> findByEmailIgnoreCaseAndIdNot(String email, Long id);
    Optional<Employee> findByIdAndClientId(Long id, Long clientId);

     Optional<Employee> findByUsernameAndPassword(String username, String password);

     List<Employee> findByBranch(String branch);
        //  List<Employee> findByBranch(String branchName);
List<Employee> findByGuestNameIsNotNullAndGuestStartDateBefore(LocalDateTime cutoffTime);
List<Employee> findByClientId(Long clientId);
List<Employee> findByCompanyCode(String companyCode);
long countByClientId(Long clientId);

}


