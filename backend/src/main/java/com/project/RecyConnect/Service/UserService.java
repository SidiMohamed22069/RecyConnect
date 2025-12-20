package com.project.RecyConnect.Service;

import com.project.RecyConnect.DTO.UserDTO;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.UserRepo;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService implements UserDetailsService {
    private final UserRepo userRepository;

    public UserService(UserRepo userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User optionalUser = userRepository.findByUsername(username);
        if(optionalUser==null) throw new UsernameNotFoundException("Username not found", null);
        return new org.springframework.security.core.userdetails.User(optionalUser.getUsername(), optionalUser.getPassword()
                , new ArrayList<>());
    }

    private UserDTO toDTO(User u) {
        UserDTO dto = new UserDTO();
        dto.setId(u.getId());
        dto.setUsername(u.getUsername());
        dto.setPhone(u.getPhone());
        return dto;
    }

    private User fromDTO(UserDTO dto) {
        return User.builder()
                .id(dto.getId())
                .username(dto.getUsername())
                .phone(dto.getPhone())
                .build();
    }

    public List<UserDTO> findAll() {
        return userRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public Optional<UserDTO> findById(Long id) {
        return userRepository.findById(id).map(this::toDTO);
    }

    public UserDTO save(UserDTO dto) {
        User saved = userRepository.save(fromDTO(dto));
        return toDTO(saved);
    }

    public UserDTO update(Long id, UserDTO dto) {
        return userRepository.findById(id).map(existing -> {
            existing.setUsername(dto.getUsername());
            existing.setPhone(dto.getPhone());
            User saved = userRepository.save(existing);
            return toDTO(saved);
        }).orElseThrow(() -> new RuntimeException("User not found"));
    }

    public void delete(Long id) {
        userRepository.deleteById(id);
    }
}