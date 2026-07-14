package com.employee.dashboard.employee.repository;

import com.employee.dashboard.employee.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository
        extends JpaRepository<Employee, Long> {

    boolean existsByEmail(String email);

}