package com.example.restaurant.repository;

import com.example.restaurant.entity.GatewayWebhookEvent;
import com.example.restaurant.entity.PaymentGatewayProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GatewayWebhookEventRepository extends JpaRepository<GatewayWebhookEvent, String> {

    boolean existsByGatewayProviderAndEventId(PaymentGatewayProvider gatewayProvider, String eventId);

    Optional<GatewayWebhookEvent> findByGatewayProviderAndEventId(PaymentGatewayProvider gatewayProvider, String eventId);
}
