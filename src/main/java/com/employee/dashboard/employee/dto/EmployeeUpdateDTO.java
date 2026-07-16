package com.employee.dashboard.employee.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

import java.util.List;

/**
 * Payload for PATCH updates. Every field is optional: a null value means
 * "leave this field unchanged". Only fields a client is allowed to change
 * are exposed here (no id, timestamps, or audit fields).
 */
@Data
public class EmployeeUpdateDTO {

    private String firstName;

    private String lastName;

    @Email(message = "Enter a valid email")
    private String email;

    private String phoneNumber;

    private List<Long> managerIds;
}
