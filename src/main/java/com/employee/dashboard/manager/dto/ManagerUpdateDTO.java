package com.employee.dashboard.manager.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

/**
 * Payload for PATCH updates. Every field is optional: a null value means
 * "leave this field unchanged".
 */
@Data
public class ManagerUpdateDTO {

    private String firstName;

    private String lastName;

    @Email(message = "Enter a valid email")
    private String email;

    private String phoneNumber;

    private String role;
}
