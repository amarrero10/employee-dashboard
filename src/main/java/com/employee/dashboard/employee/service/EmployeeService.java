package com.employee.dashboard.employee.service;

import com.employee.dashboard.employee.dto.EmployeeCreateDTO;
import com.employee.dashboard.employee.dto.EmployeeResponseDTO;
import com.employee.dashboard.employee.dto.EmployeeUpdateDTO;
import com.employee.dashboard.employee.entity.Employee;
import com.employee.dashboard.employee.mapper.EmployeeMapper;
import com.employee.dashboard.employee.repository.EmployeeRepository;
import com.employee.dashboard.exception.EmailAlreadyExistsException;
import com.employee.dashboard.exception.ResourceNotFoundException;
import com.employee.dashboard.manager.entity.Manager;
import com.employee.dashboard.manager.repository.ManagerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository repository;
    private final ManagerRepository managerRepository;
    private final EmployeeMapper employeeMapper;

    @Transactional(readOnly = true)
    public List<EmployeeResponseDTO> allEmployees() {
        return repository.findAll()
                .stream()
                .map(employeeMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeeResponseDTO findEmployee(Long id) {
        return employeeMapper.toResponse(getEmployeeOrThrow(id));
    }

    @Transactional
    public EmployeeResponseDTO createEmployee(EmployeeCreateDTO dto) {

        if (repository.existsByEmail(dto.getEmail())) {
            throw new EmailAlreadyExistsException("Email is already registered");
        }

        Employee employee = new Employee();
        employee.setFirstName(dto.getFirstName());
        employee.setLastName(dto.getLastName());
        employee.setEmail(dto.getEmail());
        employee.setPhoneNumber(dto.getPhoneNumber());
        employee.setRole("Child");
        employee.setManagers(resolveManagers(dto.getManagerIds()));

        Employee saved = repository.save(employee);
        return employeeMapper.toResponse(saved);
    }

    @Transactional
    public EmployeeResponseDTO updateEmployee(Long id, EmployeeUpdateDTO dto) {

        Employee employee = getEmployeeOrThrow(id);

        if (dto.getFirstName() != null) {
            employee.setFirstName(dto.getFirstName());
        }
        if (dto.getLastName() != null) {
            employee.setLastName(dto.getLastName());
        }
        if (dto.getEmail() != null) {
            employee.setEmail(dto.getEmail());
        }
        if (dto.getPhoneNumber() != null) {
            employee.setPhoneNumber(dto.getPhoneNumber());
        }
        if (dto.getManagerIds() != null) {
            Set<Manager> managers = resolveManagers(dto.getManagerIds());
            employee.getManagers().clear();
            employee.getManagers().addAll(managers);
        }

        Employee saved = repository.save(employee);
        return employeeMapper.toResponse(saved);
    }

    @Transactional
    public boolean deleteEmployee(Long id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return true;
        }
        return false;
    }

    private Employee getEmployeeOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with id " + id));
    }

    /**
     * Resolves the given manager ids to managed entities. Returns an empty set
     * when no ids are supplied, and fails loudly if any id does not exist so
     * callers never silently drop an invalid reference.
     */
    private Set<Manager> resolveManagers(List<Long> managerIds) {

        if (managerIds == null || managerIds.isEmpty()) {
            return new HashSet<>();
        }

        Set<Long> distinctIds = new HashSet<>(managerIds);
        List<Manager> found = managerRepository.findAllById(distinctIds);

        if (found.size() != distinctIds.size()) {
            throw new ResourceNotFoundException(
                    "One or more managers were not found for the given ids");
        }

        return new HashSet<>(found);
    }
}
