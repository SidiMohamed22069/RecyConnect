package com.project.RecyConnect.Model;

public final class NegotiationStatus {

    private NegotiationStatus() {
        // Utility class
    }

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_AUTO_CANCELLED_STOCK = "auto_cancelled_stock";
}
