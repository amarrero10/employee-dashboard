package com.employee.dashboard.controller;

import com.employee.dashboard.entity.Employee;
import com.employee.dashboard.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
@CrossOrigin(origins = "http://localhost:5173")
public class EmployeeController {

    @Autowired
    private EmployeeService service;

    @GetMapping
    public List<Employee> getEmployees() {
        return service.allEmployees();
    }

    @GetMapping("/{id}")
    public Employee getEmployee(@PathVariable Long id) {
        return service.findEmployee(id);
    }

    @PostMapping
    public Employee createEmployee(
            @Valid @RequestBody Employee employee) {

        return service.createEmployee(employee);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteEmployee(@PathVariable Long id) {

        boolean deleted = service.deleteEmployee(id);

        if(deleted) {
            return ResponseEntity.ok("Employee removed successfully");
        }

        return ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}")
    public Employee updateEmployee(
            @PathVariable Long id,
            @RequestBody Employee employee) {

        return service.updateEmployee(id, employee);
    }
}