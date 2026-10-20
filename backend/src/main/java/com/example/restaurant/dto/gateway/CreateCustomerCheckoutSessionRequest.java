package com.example.restaurant.dto.gateway;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class CreateCustomerCheckoutSessionRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.50", message = "Amount must be at least 0.50")
    private BigDecimal amount;

    private String currency = "SGD";
    private String orderNumber;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String returnUrl;
    private String cancelUrl;
    private String notes;

    public CreateCustomerCheckoutSessionRequest() {
    }

    public CreateCustomerCheckoutSessionRequest(BigDecimal amount, String currency, String orderNumber,
                                                String customerName, String customerEmail, String customerPhone,
                                                String returnUrl, String cancelUrl, String notes) {
        this.amount = amount;
        this.currency = currency;
        this.orderNumber = orderNumber;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.customerPhone = customerPhone;
        this.returnUrl = returnUrl;
        this.cancelUrl = cancelUrl;
        this.notes = notes;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
