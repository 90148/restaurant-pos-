package com.example.restaurant.gateway;

import com.example.restaurant.entity.PaymentGatewayProvider;
import com.example.restaurant.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentGatewayFactory {

    private final Map<PaymentGatewayProvider, PaymentGateway> gateways = new EnumMap<>(PaymentGatewayProvider.class);

    public PaymentGatewayFactory(List<PaymentGateway> gatewayList) {
        for (PaymentGateway gateway : gatewayList) {
            gateways.put(gateway.getProvider(), gateway);
        }
    }

    public PaymentGateway getGateway(PaymentGatewayProvider provider) {
        if (provider == null) {
            throw new BusinessException("Payment gateway provider must be specified");
        }
        PaymentGateway gateway = gateways.get(provider);
        if (gateway == null) {
            throw new BusinessException("Unsupported payment gateway provider: " + provider);
        }
        return gateway;
    }
}
