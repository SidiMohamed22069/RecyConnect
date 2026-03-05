package com.project.RecyConnect.DTO;

import com.project.RecyConnect.Model.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String username;
    private Long phone;
    private Role role;
    private String imageData;
}