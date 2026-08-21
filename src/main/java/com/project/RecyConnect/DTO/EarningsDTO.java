package com.project.RecyConnect.DTO;

import lombok.Data;

@Data
public class EarningsDTO {
    private Long userId;
    private Double totalAmount;
    private Long acceptedOffersCount;
}
