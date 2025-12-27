package com.project.RecyConnect.Controller;

import com.project.RecyConnect.DTO.NegotiationDTO;
import com.project.RecyConnect.Service.NegotiationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/negotiations")
public class NegotiationController {

    private final NegotiationService service;

    public NegotiationController(NegotiationService service) { this.service = service; }

    @GetMapping
    public List<NegotiationDTO> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<NegotiationDTO> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sender/{senderId}")
    public List<NegotiationDTO> getBySender(@PathVariable Long senderId) {
        return service.findBySenderId(senderId);
    }

    @GetMapping("/receiver/{receiverId}")
    public List<NegotiationDTO> getByReceiver(@PathVariable Long receiverId) {
        return service.findByReceiverId(receiverId);
    }

    @GetMapping("/product/{productId}")
    public List<NegotiationDTO> getByProduct(
            @PathVariable Long productId,
            @RequestParam(required = false) String status) {
        return service.findByProductId(productId, status);
    }

    @PostMapping
    public NegotiationDTO create(@RequestBody NegotiationDTO dto) {
        return service.save(dto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<NegotiationDTO> update(@PathVariable Long id, @RequestBody NegotiationDTO dto) {
        try {
            return ResponseEntity.ok(service.update(id, dto));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<NegotiationDTO> patch(@PathVariable Long id, @RequestBody NegotiationDTO dto) {
        try {
            return ResponseEntity.ok(service.patch(id, dto));
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
