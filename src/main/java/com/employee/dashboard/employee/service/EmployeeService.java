package com.employee.dashboard.employee.service;

import com.employee.dashboard.employee.dto.EmployeeResponseDTO;
import com.employee.dashboard.employee.entity.Employee;
import com.employee.dashboard.employee.exception.EmailAlreadyExistsException;
import com.employee.dashboard.employee.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmployeeService {

    @Autowired
    private EmployeeRepository repository;

    public List<EmployeeResponseDTO> allEmployees() {

        return repository.findAll()
                .stream()
                .map(employee -> new EmployeeResponseDTO(
                        employee.getId(),
                        employee.getFirstName(),
                        employee.getLastName(),
                        employee.getEmail(),
                        employee.getRole()
                ))
                .toList();
    }

    public Employee createEmployee(Employee employee) {

        if(repository.existsByEmail(employee.getEmail())) {
            throw new EmailAlreadyExistsException(
                    "Email is already registered"
            );
        }

        employee.setRole("Child");

        return repository.save(employee);
    }

    public Employee findEmployee(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
    }

    public boolean deleteEmployee(Long id) {

        if(repository.existsById(id)) {
            repository.deleteById(id);
            return true;
        }

        return false;
    }

    public Employee updateEmployee(Long id, Employee updatedEmployee) {

        Employee employee = repository.findById(id)
                .orElseThrow();

        if (updatedEmployee.getFirstName() != null) {
            employee.setFirstName(updatedEmployee.getFirstName());
        }

        if (updatedEmployee.getLastName() != null) {
            employee.setLastName(updatedEmployee.getLastName());
        }

        if (updatedEmployee.getEmail() != null) {
            employee.setEmail(updatedEmployee.getEmail());
        }

        if(updatedEmployee.getPhoneNumber() != null) {
            employee.setPhoneNumber(updatedEmployee.getPhoneNumber());
        }

        if(updatedEmployee.getRole() != null) {
            employee.setRole(updatedEmployee.getRole());
        }


        return repository.save(employee);
    }
}