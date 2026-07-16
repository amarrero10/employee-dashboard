package com.employee.dashboard.employee.dto;

import com.employee.dashboard.manager.dto.ManagerResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class EmployeeResponseDTO {

    private Long id;

    private String firstName;

    private String lastName;

    private String email;

    private String role;

    private List<ManagerResponseDTO> managers;

}
