package com.project.RecyConnect.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.project.RecyConnect.DTO.UserDTO;
import com.project.RecyConnect.DTO.UserStatsDTO;
import com.project.RecyConnect.Model.Product;
import com.project.RecyConnect.Model.User;
import com.project.RecyConnect.Repository.ProductRepository;
import com.project.RecyConnect.Repository.UserRepo;

@Service
public class UserService implements UserDetailsService {
    private final UserRepo userRepository;
    private final ProductRepository productRepository;

    public UserService(UserRepo userRepository, ProductRepository productRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
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
        dto.setImageData(u.getImageData());
        return dto;
    }

    private User fromDTO(UserDTO dto) {
        return User.builder()
                .id(dto.getId())
                .username(dto.getUsername())
                .phone(dto.getPhone())
                .imageData(dto.getImageData())
                .build();
    }

    public List<UserDTO> findAll() {
        return userRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public Optional<UserDTO> findById(Long id) {
        return userRepository.findById(id).map(this::toDTO);
    }

    public UserDTO save(UserDTO dto) {
        if (dto.getId() != null) {
            return userRepository.findById(dto.getId()).map(existing -> {
                if (dto.getUsername() != null) existing.setUsername(dto.getUsername());
                if (dto.getPhone() != null) existing.setPhone(dto.getPhone());
                if (dto.getImageData() != null) existing.setImageData(dto.getImageData());
                User saved = userRepository.save(existing);
                return toDTO(saved);
            }).orElseThrow(() -> new RuntimeException("User not found"));
        }
        throw new RuntimeException("Cannot create user via this method - use register endpoint");
    }

    public UserDTO update(Long id, UserDTO dto) {
        return userRepository.findById(id).map(existing -> {
            if (dto.getUsername() != null) existing.setUsername(dto.getUsername());
            if (dto.getPhone() != null) existing.setPhone(dto.getPhone());
            if (dto.getImageData() != null) existing.setImageData(dto.getImageData());
            User saved = userRepository.save(existing);
            return toDTO(saved);
        }).orElseThrow(() -> new RuntimeException("User not found"));
    }

    public UserDTO patch(Long id, UserDTO dto) {
        return userRepository.findById(id).map(existing -> {
            if (dto.getUsername() != null) existing.setUsername(dto.getUsername());
            if (dto.getPhone() != null) existing.setPhone(dto.getPhone());
            if (dto.getImageData() != null) existing.setImageData(dto.getImageData());
            User saved = userRepository.save(existing);
            return toDTO(saved);
        }).orElseThrow(() -> new RuntimeException("User not found"));
    }

    public void delete(Long id) {
        userRepository.deleteById(id);
    }
    
    public Optional<UserDTO> findByPhone(Long phone) {
        User user = userRepository.findByPhone(phone);
        if (user == null) {
            return Optional.empty();
        }
        return Optional.of(toDTO(user));
    }

    public Optional<UserStatsDTO> getUserStats(Long userId) {
        return userRepository.findById(userId).map(user -> {
            List<Product> userProducts = productRepository.findByUserId(userId);
            
            int totalProducts = userProducts.size();
            int recycledCount = (int) userProducts.stream()
                    .filter(p -> "recycled".equalsIgnoreCase(p.getStatus()))
                    .count();
            int availableCount = (int) userProducts.stream()
                    .filter(p -> "available".equalsIgnoreCase(p.getStatus()))
                    .count();
            
            String recyclingRate = "0%";
            if (totalProducts > 0) {
                double rate = (recycledCount * 100.0) / totalProducts;
                recyclingRate = String.format("%.1f%%", rate);
            }
            
            UserStatsDTO statsDTO = new UserStatsDTO();
            statsDTO.setUserId(userId);
            statsDTO.setTotalProducts(totalProducts);
            statsDTO.setRecycledCount(recycledCount);
            statsDTO.setAvailableCount(availableCount);
            statsDTO.setRecyclingRate(recyclingRate);
            
            return statsDTO;
        });
    }
    
    public void updateFcmToken(Long userId, String fcmToken) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setFcmToken(fcmToken);
            userRepository.save(user);
        });
    }
}