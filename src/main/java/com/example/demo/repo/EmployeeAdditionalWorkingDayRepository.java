package com.example.demo.repo;

import com.example.demo.MODELS.EmployeeAdditionalWorkingDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface EmployeeAdditionalWorkingDayRepository extends JpaRepository<EmployeeAdditionalWorkingDay, Long> {
    List<EmployeeAdditionalWorkingDay> findByEmployee_Id(Long employeeId);

    @Modifying
    @Transactional
    void deleteByEmployee_Id(Long employeeId);

    @Query(value = """
            SELECT day_type AS dayType, time_in AS timeIn, time_out AS timeOut
            FROM employee_additional_working_day
            WHERE employee_id = :employeeId
            """, nativeQuery = true)
    List<AdditionalWorkingDayRaw> findRawByEmployeeId(@Param("employeeId") Long employeeId);

    interface AdditionalWorkingDayRaw {
        String getDayType();
        String getTimeIn();
        String getTimeOut();
    }
}
