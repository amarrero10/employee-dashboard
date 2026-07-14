package com.employee.dashboard.service;

import com.employee.dashboard.entity.Employee;
import com.employee.dashboard.exception.EmailAlreadyExistsException;
import com.employee.dashboard.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmployeeService {

    @Autowired
    private EmployeeRepository repository;

    public List<Employee> allEmployees() {
        return repository.findAll();
    }

    public Employee createEmployee(Employee employee) {

        if(repository.existsByEmail(employee.getEmail())) {
            throw new EmailAlreadyExistsException(
                    "Email is already registered"
            );
        }

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

        employee.setFirstName(updatedEmployee.getFirstName());
        employee.setLastName(updatedEmployee.getLastName());
        employee.setEmail(updatedEmployee.getEmail());

        return repository.save(employee);
    }
}