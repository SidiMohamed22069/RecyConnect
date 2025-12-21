package com.project.RecyConnect.Controller;

import com.project.RecyConnect.DTO.PhoneVerificationDTO;
import com.project.RecyConnect.Service.PhoneVerificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/phone-verifications")
public class PhoneVerificationController {

    private final PhoneVerificationService service;
    public PhoneVerificationController(PhoneVerificationService service) { this.service = service; }

    @GetMapping
    public List<PhoneVerificationDTO> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<PhoneVerificationDTO> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<PhoneVerificationDTO> getByUserId(@PathVariable Long userId) {
        return service.findByUserId(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/verify")
    public ResponseEntity<PhoneVerificationDTO> verifyCode(
            @RequestParam String phone,
            @RequestParam String code) {
        return service.verifyCode(phone, code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public PhoneVerificationDTO create(@RequestBody PhoneVerificationDTO dto) {
        return service.save(dto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PhoneVerificationDTO> update(@PathVariable Long id, @RequestBody PhoneVerificationDTO dto) {
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
