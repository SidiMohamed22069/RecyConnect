package com.project.RecyConnect.Model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ProductStatus {
    AVAILABLE("available"),
    PENDING("pending"),
    RECYCLED("recycled"),
    ARCHIVED("archived");

    private final String value;

    ProductStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ProductStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        for (ProductStatus status : values()) {
            if (status.value.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }

        throw new IllegalArgumentException("Unknown product status: " + value);
    }
}