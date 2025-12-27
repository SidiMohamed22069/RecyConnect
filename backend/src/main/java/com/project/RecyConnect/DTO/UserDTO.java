package com.project.RecyConnect.DTO;


import lombok.Data;
import java.util.UUID;
import java.time.OffsetDateTime;


@Data
public class UserDTO {
    private Long id;
    private String username;
    private Long phone;
    private String imageData;
}