package com.employee.dashboard.manager.mapper;

import com.employee.dashboard.manager.dto.ManagerResponseDTO;
import com.employee.dashboard.manager.entity.Manager;
import org.springframework.stereotype.Component;

@Component
public class ManagerMapper {

    public ManagerResponseDTO toResponse(Manager manager) {
        return new ManagerResponseDTO(
                manager.getId(),
                manager.getFirstName(),
                manager.getLastName(),
                manager.getEmail(),
                manager.getPhoneNumber(),
                manager.getRole()
        );
    }
}
