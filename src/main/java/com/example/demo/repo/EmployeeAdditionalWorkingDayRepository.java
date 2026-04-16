package com.example.demo.repo;

import com.example.demo.MODELS.EmployeeAdditionalWorkingDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeAdditionalWorkingDayRepository extends JpaRepository<EmployeeAdditionalWorkingDay, Long> {
    List<EmployeeAdditionalWorkingDay> findByEmployee_Id(Long employeeId);
}
