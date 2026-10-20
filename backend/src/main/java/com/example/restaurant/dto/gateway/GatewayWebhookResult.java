package com.example.restaurant.dto.gateway;

public class GatewayWebhookResult {

    private boolean processed;
    private String eventId;
    private String eventType;
    private String message;

    public GatewayWebhookResult() {
    }

    public GatewayWebhookResult(boolean processed, String eventId, String eventType, String message) {
        this.processed = processed;
        this.eventId = eventId;
        this.eventType = eventType;
        this.message = message;
    }

    public static GatewayWebhookResult processed(String eventId, String eventType, String message) {
        return new GatewayWebhookResult(true, eventId, eventType, message);
    }

    public static GatewayWebhookResult ignored(String eventId, String eventType, String message) {
        return new GatewayWebhookResult(false, eventId, eventType, message);
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
