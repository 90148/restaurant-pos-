package com.example.restaurant.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "gateway_webhook_events", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"gateway_provider", "event_id"})
})
public class GatewayWebhookEvent {

    @Id
    @Column(length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway_provider", nullable = false, length = 20)
    private PaymentGatewayProvider gatewayProvider;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, length = 20)
    private String status = "PROCESSED";

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public GatewayWebhookEvent() {
    }

    public GatewayWebhookEvent(String id, PaymentGatewayProvider gatewayProvider, String eventId, String eventType, String status, String payload) {
        this.id = id;
        this.gatewayProvider = gatewayProvider;
        this.eventId = eventId;
        this.eventType = eventType;
        this.status = status;
        this.payload = payload;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public PaymentGatewayProvider getGatewayProvider() {
        return gatewayProvider;
    }

    public void setGatewayProvider(PaymentGatewayProvider gatewayProvider) {
        this.gatewayProvider = gatewayProvider;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
