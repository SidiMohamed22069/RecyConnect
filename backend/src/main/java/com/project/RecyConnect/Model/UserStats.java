package com.project.RecyConnect.Model;


import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private OffsetDateTime createdAt;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    private Integer totalProducts;
    private Integer recycledCount;
    private Integer availableCount;
    private String recyclingRate;
}
