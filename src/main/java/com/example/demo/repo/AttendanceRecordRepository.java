package com.example.demo.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.MODELS.AttendanceRecord;
import com.example.demo.MODELS.Employee;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    Optional<AttendanceRecord> findById(Long id);

    static Optional<AttendanceRecord> findTopByEmployeeIdOrderByIdDesc(Long employeeId) {
        throw new UnsupportedOperationException("Unimplemented method 'findTopByEmployeeIdOrderByIdDesc'");
    }
    List<AttendanceRecord> findByEmployeeIdAndDate(Long employeeId, String date);
    List<AttendanceRecord> findByDate(String date);
    List<AttendanceRecord> findByEmployeeIdAndDateBetween(Long employeeId, String startDate, String endDate);
    List<AttendanceRecord> findByEmployeeId(Long employeeId);
        Optional<AttendanceRecord> findByDateAndEmployeeId(String date, Long employeeId);
List<AttendanceRecord> findByEmployeeIdOrderByIdDesc(Long employeeId);
    List<AttendanceRecord> findByDateAndAttendanceStatus(String date, String attendanceStatus);
    // Get latest record by employee (based on id descending or date descending)
    @Query("SELECT a FROM AttendanceRecord a WHERE a.employee = :employee ORDER BY a.id DESC LIMIT 1")
    Optional<AttendanceRecord> findLatestByEmployee(Employee employee);
    @Query("SELECT a FROM AttendanceRecord a WHERE a.date = :date AND a.timeIn IS NOT NULL")
List<AttendanceRecord> findAllTimeInByDate(@Param("date") String date);

@Query("SELECT a FROM AttendanceRecord a WHERE a.date = :today AND a.attendanceStatus = 'Absent'")
List<AttendanceRecord> findTodayAbsent(@Param("today") String today);

    // You can add custom query methods here if needed, e.g.,
    // findByEmployeeIdAndDate(Long employeeId, LocalDate date);

    List<AttendanceRecord> findByAttendanceStatus(String status);
    List<AttendanceRecord> findByDayStatus(String dayStatus);
    List<AttendanceRecord> findByLocationContainingIgnoreCase(String location);
    List<AttendanceRecord> findByDateBetween(String startDate, String endDate);
    // List<AttendanceRecord> findByMissedTimesBetween(Integer min, Integer max);
    List<AttendanceRecord> findByLeavePermissions_Status(String status);


List<AttendanceRecord> findByAttendanceStatusAndTimeOutIsNull(String attendanceStatus);
// Find by employeeId and month pattern in date (e.g., /06/2025)
@Query("SELECT a FROM AttendanceRecord a WHERE a.employee.id = :employeeId AND a.date LIKE %:monthPattern")
List<AttendanceRecord> findByEmployeeIdAndMonthPattern(@Param("employeeId") Long employeeId, @Param("monthPattern") String monthPattern);

// Find by employee branch
@Query("SELECT a FROM AttendanceRecord a WHERE a.employee.branch = :branch")
List<AttendanceRecord> findByEmployeeBranch(@Param("branch") String branch);

// Find by month pattern and date
@Query("SELECT a FROM AttendanceRecord a WHERE a.date LIKE %:monthPattern AND a.date = :date")
List<AttendanceRecord> findByMonthPatternAndDate(@Param("monthPattern") String monthPattern, @Param("date") String date);
@Query("SELECT DISTINCT a.employee.branch FROM AttendanceRecord a")
List<String> findAllDistinctBranches();
// Already present in your repo:
// Optional<AttendanceRecord> findByDateAndEmployeeId(String date, Long employeeId);
// List<AttendanceRecord> findByEmployeeId(Long employeeId);
// List<AttendanceRecord> findByDateAndAttendanceStatus(String date, String attendanceStatus);




@Query("SELECT a FROM AttendanceRecord a WHERE a.attendanceStatus = :attendanceStatus AND a.date LIKE %:monthPattern")
List<AttendanceRecord> findByAttendanceStatusAndMonthPattern(@Param("attendanceStatus") String attendanceStatus, @Param("monthPattern") String monthPattern);

@Query("SELECT a FROM AttendanceRecord a WHERE a.date LIKE %:monthPattern AND a.attendanceStatus = :attendanceStatus AND a.employee.branch = :branch")
List<AttendanceRecord> findByMonthStatusBranch(@Param("monthPattern") String monthPattern, @Param("attendanceStatus") String attendanceStatus, @Param("branch") String branch);


    /** One record for one employee on one date (dd/MM/yyyy) */
    Optional<AttendanceRecord> findByEmployee_IdAndDate(Long employeeId, String date);

    /** All records for employee between two dates (inclusive) */
    @Query("SELECT a FROM AttendanceRecord a " +
           "WHERE a.employee.id = :employeeId AND a.date BETWEEN :start AND :end")
    List<AttendanceRecord> findMonthlySlice(
            @Param("employeeId") Long employeeId,
            @Param("start") String startDate,   // dd/MM/yyyy
            @Param("end")   String endDate);    // dd/MM/yyyy
Long countByDateAndDayStatusNot(String date, String dayStatus);



}