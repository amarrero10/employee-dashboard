package com.employee.dashboard.manager.controller;

import com.employee.dashboard.manager.dto.ManagerCreateDTO;
import com.employee.dashboard.manager.dto.ManagerResponseDTO;
import com.employee.dashboard.manager.dto.ManagerUpdateDTO;
import com.employee.dashboard.manager.service.ManagerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/managers")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class ManagerController {

    private final ManagerService service;

    @GetMapping
    public List<ManagerResponseDTO> getManagers() {
        return service.allManagers();
    }

    @GetMapping("/{id}")
    public ManagerResponseDTO getManager(@PathVariable Long id) {
        return service.getManager(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ManagerResponseDTO createManager(
            @Valid @RequestBody ManagerCreateDTO dto) {
        return service.createManager(dto);
    }

    @PatchMapping("/{id}")
    public ManagerResponseDTO updateManager(
            @PathVariable Long id,
            @Valid @RequestBody ManagerUpdateDTO dto) {
        return service.updateManager(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteManager(@PathVariable Long id) {
        boolean deleted = service.deleteManager(id);
        return deleted
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
