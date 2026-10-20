package com.example.restaurant.dto.gateway;

import com.example.restaurant.entity.PaymentGatewayProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateCheckoutSessionRequest {

    @NotBlank(message = "Bill ID is required")
    private String billId;

    @NotNull(message = "Payment gateway provider is required")
    private PaymentGatewayProvider gatewayProvider = PaymentGatewayProvider.STRIPE;

    private String notes;

    public CreateCheckoutSessionRequest() {
    }

    public CreateCheckoutSessionRequest(String billId, PaymentGatewayProvider gatewayProvider, String notes) {
        this.billId = billId;
        this.gatewayProvider = gatewayProvider;
        this.notes = notes;
    }

    public String getBillId() {
        return billId;
    }

    public void setBillId(String billId) {
        this.billId = billId;
    }

    public PaymentGatewayProvider getGatewayProvider() {
        return gatewayProvider;
    }

    public void setGatewayProvider(PaymentGatewayProvider gatewayProvider) {
        this.gatewayProvider = gatewayProvider;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
