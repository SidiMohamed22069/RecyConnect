package com.project.RecyConnect.Controller;

import com.project.RecyConnect.DTO.SearchAlertDTO;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Service.SearchAlertService;
import com.project.RecyConnect.Service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Les veilles de recherche de l'utilisateur connecte.
 *
 * <p>Comme les favoris, elles ne sont jamais lisibles par un tiers: ce qu'un
 * recycleur surveille, et jusqu'a quel prix, est une information commerciale.
 */
@RestController
@RequestMapping("/api/search-alerts")
public class SearchAlertController {

    private final SearchAlertService service;
    private final UserService userService;

    public SearchAlertController(SearchAlertService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<SearchAlertDTO>> myAlerts() {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(service.findByUser(currentUser.getId()));
    }

    @PostMapping
    public ResponseEntity<SearchAlertDTO> create(@RequestBody SearchAlertDTO dto) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(currentUser.getId(), dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SearchAlertDTO> update(@PathVariable Long id,
                                                 @RequestBody SearchAlertDTO dto) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Une veille qui n'est pas la sienne est traitee comme inexistante:
        // repondre 403 confirmerait qu'elle existe.
        return service.update(id, currentUser.getId(), dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return service.delete(id, currentUser.getId())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
