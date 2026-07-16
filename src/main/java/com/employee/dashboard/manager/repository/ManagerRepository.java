package com.employee.dashboard.manager.repository;

import com.employee.dashboard.manager.entity.Manager;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagerRepository
        extends JpaRepository<Manager, Long> {
    boolean existsByEmail(String email);
}
