package com.example.restaurant.gateway;

import com.example.restaurant.dto.gateway.CheckoutSessionResponse;
import com.example.restaurant.dto.gateway.GatewayWebhookResult;
import com.example.restaurant.entity.Bill;
import com.example.restaurant.entity.PaymentGatewayProvider;

import java.math.BigDecimal;
import java.util.Map;

public interface PaymentGateway {

    PaymentGatewayProvider getProvider();

    CheckoutSessionResponse createCheckoutSession(Bill bill, BigDecimal amount, String currency,
                                                  String returnUrl, String cancelUrl,
                                                  String notes, String currentUsername);

    GatewayWebhookResult processWebhook(String rawPayload, Map<String, String> headers);

    void verifyPayment(Map<String, String> params);
}
