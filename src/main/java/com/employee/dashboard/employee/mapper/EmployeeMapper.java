package com.employee.dashboard.employee.mapper;

import com.employee.dashboard.employee.dto.EmployeeResponseDTO;
import com.employee.dashboard.employee.entity.Employee;
import com.employee.dashboard.manager.dto.ManagerResponseDTO;
import com.employee.dashboard.manager.mapper.ManagerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class EmployeeMapper {

    private final ManagerMapper managerMapper;

    public EmployeeResponseDTO toResponse(Employee employee) {

        List<ManagerResponseDTO> managers = employee.getManagers()
                .stream()
                .map(managerMapper::toResponse)
                .toList();

        return new EmployeeResponseDTO(
                employee.getId(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getEmail(),
                employee.getRole(),
                managers
        );
    }
}
