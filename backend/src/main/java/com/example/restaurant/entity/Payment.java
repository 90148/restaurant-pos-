package com.example.restaurant.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bill_payments")
public class Payment {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outlet_id", nullable = false)
    private Outlet outlet;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "tendered_amount", precision = 10, scale = 2)
    private BigDecimal tenderedAmount;

    @Column(name = "change_amount", precision = 10, scale = 2)
    private BigDecimal changeAmount = BigDecimal.ZERO;

    @Column(name = "transaction_ref", length = 100)
    private String transactionRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.SUCCESS;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway_provider", length = 20)
    private PaymentGatewayProvider gatewayProvider;

    @Column(name = "gateway_order_id", length = 100)
    private String gatewayOrderId;

    @Column(name = "gateway_payment_id", length = 100)
    private String gatewayPaymentId;

    @Column(name = "gateway_signature", length = 255)
    private String gatewaySignature;

    @Column(length = 10)
    private String currency = "SGD";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Payment() {
    }

    public Payment(String id, Bill bill, Outlet outlet, PaymentMethod paymentMethod,
                   BigDecimal amount, BigDecimal tenderedAmount, BigDecimal changeAmount,
                   String transactionRef, User createdBy, String notes) {
        this.id = id;
        this.bill = bill;
        this.outlet = outlet;
        this.paymentMethod = paymentMethod;
        this.amount = amount;
        this.tenderedAmount = tenderedAmount != null ? tenderedAmount : amount;
        this.changeAmount = changeAmount != null ? changeAmount : BigDecimal.ZERO;
        this.transactionRef = transactionRef;
        this.createdBy = createdBy;
        this.notes = notes;
        this.status = PaymentStatus.SUCCESS;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Bill getBill() {
        return bill;
    }

    public void setBill(Bill bill) {
        this.bill = bill;
    }

    public Outlet getOutlet() {
        return outlet;
    }

    public void setOutlet(Outlet outlet) {
        this.outlet = outlet;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getTenderedAmount() {
        return tenderedAmount;
    }

    public void setTenderedAmount(BigDecimal tenderedAmount) {
        this.tenderedAmount = tenderedAmount;
    }

    public BigDecimal getChangeAmount() {
        return changeAmount;
    }

    public void setChangeAmount(BigDecimal changeAmount) {
        this.changeAmount = changeAmount;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public void setTransactionRef(String transactionRef) {
        this.transactionRef = transactionRef;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public PaymentGatewayProvider getGatewayProvider() {
        return gatewayProvider;
    }

    public void setGatewayProvider(PaymentGatewayProvider gatewayProvider) {
        this.gatewayProvider = gatewayProvider;
    }

    public String getGatewayOrderId() {
        return gatewayOrderId;
    }

    public void setGatewayOrderId(String gatewayOrderId) {
        this.gatewayOrderId = gatewayOrderId;
    }

    public String getGatewayPaymentId() {
        return gatewayPaymentId;
    }

    public void setGatewayPaymentId(String gatewayPaymentId) {
        this.gatewayPaymentId = gatewayPaymentId;
    }

    public String getGatewaySignature() {
        return gatewaySignature;
    }

    public void setGatewaySignature(String gatewaySignature) {
        this.gatewaySignature = gatewaySignature;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public static Payment createGatewayPending(String id, Bill bill, Outlet outlet,
                                               PaymentGatewayProvider provider,
                                               PaymentMethod method,
                                               BigDecimal amount,
                                               String currency,
                                               String gatewayOrderId,
                                               User createdBy,
                                               String notes) {
        Payment p = new Payment();
        p.setId(id);
        p.setBill(bill);
        p.setOutlet(outlet);
        p.setGatewayProvider(provider);
        p.setPaymentMethod(method);
        p.setAmount(amount);
        p.setTenderedAmount(amount);
        p.setChangeAmount(BigDecimal.ZERO);
        p.setCurrency(currency != null ? currency : "SGD");
        p.setGatewayOrderId(gatewayOrderId);
        p.setTransactionRef(gatewayOrderId);
        p.setStatus(PaymentStatus.PENDING);
        p.setCreatedBy(createdBy);
        p.setNotes(notes);
        return p;
    }
}
