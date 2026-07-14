package com.employee.dashboard.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Entity
@Data
@Table(name = "employees")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email")
    @Column(unique = true,  nullable = false)
    private String email;

    public Employee() {
    }

    public Employee(String firstName,
                    String lastName,
                    String email) {

        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    // getters and setters
}