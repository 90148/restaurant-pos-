package com.example.restaurant.dto.gateway;

import com.example.restaurant.entity.PaymentGatewayProvider;
import java.math.BigDecimal;

public class CheckoutSessionResponse {

    private PaymentGatewayProvider gatewayProvider;
    private String paymentId;
    private String gatewayOrderId;
    private String checkoutUrl;
    private String keyId;
    private BigDecimal amount;
    private Long amountInSubunits;
    private String currency;
    private String billId;
    private String billNumber;
    private String outletName;

    public CheckoutSessionResponse() {
    }

    public CheckoutSessionResponse(PaymentGatewayProvider gatewayProvider, String paymentId, String gatewayOrderId,
                                   String checkoutUrl, String keyId, BigDecimal amount, Long amountInSubunits,
                                   String currency, String billId, String billNumber, String outletName) {
        this.gatewayProvider = gatewayProvider;
        this.paymentId = paymentId;
        this.gatewayOrderId = gatewayOrderId;
        this.checkoutUrl = checkoutUrl;
        this.keyId = keyId;
        this.amount = amount;
        this.amountInSubunits = amountInSubunits;
        this.currency = currency;
        this.billId = billId;
        this.billNumber = billNumber;
        this.outletName = outletName;
    }

    public PaymentGatewayProvider getGatewayProvider() {
        return gatewayProvider;
    }

    public void setGatewayProvider(PaymentGatewayProvider gatewayProvider) {
        this.gatewayProvider = gatewayProvider;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getGatewayOrderId() {
        return gatewayOrderId;
    }

    public void setGatewayOrderId(String gatewayOrderId) {
        this.gatewayOrderId = gatewayOrderId;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public void setCheckoutUrl(String checkoutUrl) {
        this.checkoutUrl = checkoutUrl;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Long getAmountInSubunits() {
        return amountInSubunits;
    }

    public void setAmountInSubunits(Long amountInSubunits) {
        this.amountInSubunits = amountInSubunits;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getBillId() {
        return billId;
    }

    public void setBillId(String billId) {
        this.billId = billId;
    }

    public String getBillNumber() {
        return billNumber;
    }

    public void setBillNumber(String billNumber) {
        this.billNumber = billNumber;
    }

    public String getOutletName() {
        return outletName;
    }

    public void setOutletName(String outletName) {
        this.outletName = outletName;
    }
}
