package com.project.RecyConnect.Controller;

import com.project.RecyConnect.DTO.UserStatsDTO;
import com.project.RecyConnect.Service.UserStatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/user-stats")
public class UserStatsController {
    private final UserStatsService service;
    public UserStatsController(UserStatsService service) { this.service = service; }

    @GetMapping
    public List<UserStatsDTO> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<UserStatsDTO> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<UserStatsDTO> getByUserId(@PathVariable Long userId) {
        return service.findByUserId(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public UserStatsDTO create(@RequestBody UserStatsDTO dto) {
        return service.save(dto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserStatsDTO> update(@PathVariable Long id, @RequestBody UserStatsDTO dto) {
        try {
            return ResponseEntity.ok(service.update(id, dto));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
