package com.employee.dashboard.manager.service;

import com.employee.dashboard.exception.EmailAlreadyExistsException;
import com.employee.dashboard.exception.ResourceNotFoundException;
import com.employee.dashboard.manager.dto.ManagerCreateDTO;
import com.employee.dashboard.manager.dto.ManagerResponseDTO;
import com.employee.dashboard.manager.dto.ManagerUpdateDTO;
import com.employee.dashboard.manager.entity.Manager;
import com.employee.dashboard.manager.mapper.ManagerMapper;
import com.employee.dashboard.manager.repository.ManagerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ManagerService {

    private final ManagerRepository repository;
    private final ManagerMapper managerMapper;

    @Transactional(readOnly = true)
    public List<ManagerResponseDTO> allManagers() {
        return repository.findAll()
                .stream()
                .map(managerMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ManagerResponseDTO getManager(Long id) {
        return managerMapper.toResponse(getManagerOrThrow(id));
    }

    @Transactional
    public ManagerResponseDTO createManager(ManagerCreateDTO dto) {

        if (repository.existsByEmail(dto.getEmail())) {
            throw new EmailAlreadyExistsException("Email is already registered");
        }

        Manager manager = new Manager();
        manager.setFirstName(dto.getFirstName());
        manager.setLastName(dto.getLastName());
        manager.setEmail(dto.getEmail());
        manager.setPhoneNumber(dto.getPhoneNumber());
        manager.setRole(dto.getRole());

        Manager saved = repository.save(manager);
        return managerMapper.toResponse(saved);
    }

    @Transactional
    public ManagerResponseDTO updateManager(Long id, ManagerUpdateDTO dto) {

        Manager manager = getManagerOrThrow(id);

        if (dto.getFirstName() != null) {
            manager.setFirstName(dto.getFirstName());
        }
        if (dto.getLastName() != null) {
            manager.setLastName(dto.getLastName());
        }
        if (dto.getEmail() != null) {
            manager.setEmail(dto.getEmail());
        }
        if (dto.getPhoneNumber() != null) {
            manager.setPhoneNumber(dto.getPhoneNumber());
        }
        if (dto.getRole() != null) {
            manager.setRole(dto.getRole());
        }

        Manager saved = repository.save(manager);
        return managerMapper.toResponse(saved);
    }

    @Transactional
    public boolean deleteManager(Long id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return true;
        }
        return false;
    }

    private Manager getManagerOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Manager not found with id " + id));
    }
}
