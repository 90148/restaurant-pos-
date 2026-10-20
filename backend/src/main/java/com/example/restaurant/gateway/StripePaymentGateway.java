package com.example.restaurant.gateway;

import com.example.restaurant.dto.gateway.CheckoutSessionResponse;
import com.example.restaurant.dto.gateway.GatewayWebhookResult;
import com.example.restaurant.entity.*;
import com.example.restaurant.exception.BusinessException;
import com.example.restaurant.repository.GatewayWebhookEventRepository;
import com.example.restaurant.repository.PaymentRepository;
import com.example.restaurant.repository.UserRepository;
import com.example.restaurant.service.BillingService;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

@Component
public class StripePaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGateway.class);

    @Value("${app.payment.stripe.secret-key}")
    private String secretKey;

    @Value("${app.payment.stripe.publishable-key}")
    private String publishableKey;

    @Value("${app.payment.stripe.webhook-secret}")
    private String webhookSecret;

    @Value("${app.payment.stripe.currency:sgd}")
    private String defaultCurrency;

    @Value("${app.payment.stripe.success-url:http://localhost:5173/billing?status=success&session_id={CHECKOUT_SESSION_ID}}")
    private String defaultSuccessUrl;

    @Value("${app.payment.stripe.cancel-url:http://localhost:5173/billing?status=cancelled&session_id={CHECKOUT_SESSION_ID}}")
    private String defaultCancelUrl;

    private final BillingService billingService;
    private final PaymentRepository paymentRepository;
    private final GatewayWebhookEventRepository webhookEventRepository;
    private final UserRepository userRepository;

    public StripePaymentGateway(BillingService billingService,
                                PaymentRepository paymentRepository,
                                GatewayWebhookEventRepository webhookEventRepository,
                                UserRepository userRepository) {
        this.billingService = billingService;
        this.paymentRepository = paymentRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.userRepository = userRepository;
    }

    @Override
    public PaymentGatewayProvider getProvider() {
        return PaymentGatewayProvider.STRIPE;
    }

    @Override
    @Transactional
    public CheckoutSessionResponse createCheckoutSession(Bill bill, BigDecimal amount, String currency,
                                                          String returnUrl, String cancelUrl,
                                                          String notes, String currentUsername) {
        if (secretKey == null || secretKey.isBlank() || "sk_test_placeholder".equals(secretKey)) {
            throw new BusinessException("Stripe API Secret Key is not configured. Please set the STRIPE_SECRET_KEY environment variable in your .env file or environment.");
        }

        Stripe.apiKey = secretKey;

        String chargeCurrency = (currency != null && !currency.isBlank())
                ? currency.trim().toLowerCase()
                : defaultCurrency.toLowerCase();

        long amountInCents = amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();

        String successUrl = (returnUrl != null && !returnUrl.isBlank()) ? returnUrl : defaultSuccessUrl;
        String cancel = (cancelUrl != null && !cancelUrl.isBlank()) ? cancelUrl : defaultCancelUrl;

        // Ensure session placeholder and billId are present in success URL
        if (!successUrl.contains("{CHECKOUT_SESSION_ID}")) {
            successUrl += (successUrl.contains("?") ? "&" : "?") + "session_id={CHECKOUT_SESSION_ID}&billId=" + bill.getId();
        } else if (!successUrl.contains("billId=")) {
            successUrl += (successUrl.contains("?") ? "&" : "?") + "billId=" + bill.getId();
        }

        try {
            String tableInfo = "";
            if (bill.getOrder() != null && bill.getOrder().getTable() != null) {
                tableInfo = " (Table " + bill.getOrder().getTable().getTableNumber() + ")";
            }

            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancel)
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency(chargeCurrency)
                                                    .setUnitAmount(amountInCents)
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName("Bill " + bill.getBillNumber())
                                                                    .setDescription("Settlement for " + bill.getOutlet().getName() + tableInfo)
                                                                    .build()
                                                    )
                                                    .build()
                                    )
                                    .build()
                    )
                    .putMetadata("billId", bill.getId())
                    .putMetadata("billNumber", bill.getBillNumber())
                    .putMetadata("outletId", bill.getOutlet().getId())
                    .build();

            Session session = Session.create(params);

            User user = null;
            if (currentUsername != null) {
                user = userRepository.findByUsername(currentUsername).orElse(null);
            }

            Payment pendingPayment = Payment.createGatewayPending(
                    UUID.randomUUID().toString(),
                    bill,
                    bill.getOutlet(),
                    PaymentGatewayProvider.STRIPE,
                    PaymentMethod.STRIPE,
                    amount,
                    chargeCurrency.toUpperCase(),
                    session.getId(),
                    user,
                    notes != null ? notes : "Stripe Checkout Initiated"
            );
            paymentRepository.save(pendingPayment);

            return new CheckoutSessionResponse(
                    PaymentGatewayProvider.STRIPE,
                    pendingPayment.getId(),
                    session.getId(),
                    session.getUrl(),
                    publishableKey,
                    amount,
                    amountInCents,
                    chargeCurrency.toUpperCase(),
                    bill.getId(),
                    bill.getBillNumber(),
                    bill.getOutlet().getName()
            );
        } catch (Exception e) {
            log.error("Failed to create Stripe checkout session for bill {}: {}", bill.getBillNumber(), e.getMessage(), e);
            throw new BusinessException("Failed to initiate Stripe Checkout: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public GatewayWebhookResult processWebhook(String rawPayload, Map<String, String> headers) {
        String sigHeader = headers.get("stripe-signature");
        if (sigHeader == null) {
            sigHeader = headers.get("Stripe-Signature");
        }
        if (sigHeader == null || sigHeader.isBlank()) {
            throw new BusinessException("Missing Stripe-Signature header");
        }

        Event event;
        try {
            event = Webhook.constructEvent(rawPayload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("Stripe webhook HMAC signature verification failed: {}", e.getMessage());
            throw new BusinessException("Invalid Stripe webhook signature");
        }

        String eventId = event.getId();
        if (webhookEventRepository.existsByGatewayProviderAndEventId(PaymentGatewayProvider.STRIPE, eventId)) {
            log.info("Duplicate Stripe webhook event {} ignored.", eventId);
            return GatewayWebhookResult.ignored(eventId, event.getType(), "Event already processed");
        }

        GatewayWebhookEvent auditEvent = new GatewayWebhookEvent(
                UUID.randomUUID().toString(),
                PaymentGatewayProvider.STRIPE,
                eventId,
                event.getType(),
                "PROCESSED",
                rawPayload
        );
        webhookEventRepository.save(auditEvent);

        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        switch (event.getType()) {
            case "checkout.session.completed":
                if (dataObjectDeserializer.getObject().isPresent()) {
                    Session session = (Session) dataObjectDeserializer.getObject().get();
                    handleCheckoutSessionCompleted(session);
                }
                break;
            case "checkout.session.expired":
                if (dataObjectDeserializer.getObject().isPresent()) {
                    Session session = (Session) dataObjectDeserializer.getObject().get();
                    handleCheckoutSessionExpired(session);
                }
                break;
            default:
                log.debug("Unhandled Stripe webhook event: {}", event.getType());
        }

        return GatewayWebhookResult.processed(eventId, event.getType(), "Successfully processed");
    }

    @Override
    @Transactional
    public void verifyPayment(Map<String, String> params) {
        String sessionId = params.get("sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException("sessionId is required for Stripe verification");
        }

        if (secretKey == null || secretKey.isBlank() || "sk_test_placeholder".equals(secretKey)) {
            throw new BusinessException("Stripe API Secret Key is not configured. Please set the STRIPE_SECRET_KEY environment variable in your .env file or environment.");
        }

        Stripe.apiKey = secretKey;
        try {
            Session session = Session.retrieve(sessionId);
            if ("paid".equalsIgnoreCase(session.getPaymentStatus())) {
                handleCheckoutSessionCompleted(session);
            } else {
                log.info("Stripe session {} status is '{}', not paid yet", sessionId, session.getPaymentStatus());
                throw new BusinessException("Stripe session status is '" + session.getPaymentStatus() + "'. Payment is not yet settled.");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to verify Stripe session {}: {}", sessionId, e.getMessage());
            throw new BusinessException("Failed to verify Stripe payment: " + e.getMessage());
        }
    }

    private void handleCheckoutSessionCompleted(Session session) {
        String billId = session.getMetadata() != null ? session.getMetadata().get("billId") : null;
        if (billId == null) {
            Payment p = paymentRepository.findByGatewayOrderId(session.getId()).orElse(null);
            if (p != null && p.getBill() != null) {
                billId = p.getBill().getId();
            }
        }

        if (billId == null) {
            log.warn("Cannot reconcile Stripe session {}: billId missing from metadata", session.getId());
            return;
        }

        BigDecimal amountPaid = session.getAmountTotal() != null
                ? BigDecimal.valueOf(session.getAmountTotal()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String currency = session.getCurrency() != null ? session.getCurrency().toUpperCase() : "SGD";

        billingService.reconcileGatewayPayment(
                billId,
                null,
                PaymentGatewayProvider.STRIPE,
                session.getId(),
                session.getPaymentIntent(),
                null,
                amountPaid,
                currency,
                null,
                "Stripe Checkout settlement (" + session.getId() + ")"
        );
    }

    private void handleCheckoutSessionExpired(Session session) {
        paymentRepository.findByGatewayOrderId(session.getId()).ifPresent(p -> {
            if (p.getStatus() == PaymentStatus.PENDING) {
                p.setStatus(PaymentStatus.EXPIRED);
                paymentRepository.save(p);
                log.info("Marked pending payment for Stripe session {} as EXPIRED", session.getId());
            }
        });
    }

    public CheckoutSessionResponse createCustomerCheckoutSession(
            BigDecimal amount,
            String currency,
            String orderNumber,
            String customerName,
            String customerEmail,
            String customerPhone,
            String returnUrl,
            String cancelUrl,
            String notes) {

        if (secretKey == null || secretKey.isBlank() || "sk_test_placeholder".equals(secretKey)) {
            throw new BusinessException("Stripe API Secret Key is not configured. Please set the STRIPE_SECRET_KEY environment variable in your .env file.");
        }

        Stripe.apiKey = secretKey;

        String chargeCurrency = (currency != null && !currency.isBlank())
                ? currency.trim().toLowerCase()
                : defaultCurrency.toLowerCase();

        long amountInCents = amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();

        String successUrl = (returnUrl != null && !returnUrl.isBlank()) ? returnUrl : defaultSuccessUrl;
        String cancel = (cancelUrl != null && !cancelUrl.isBlank()) ? cancelUrl : defaultCancelUrl;

        try {
            SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancel)
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency(chargeCurrency)
                                                    .setUnitAmount(amountInCents)
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName("Restaurant Order #" + (orderNumber != null ? orderNumber : "Online"))
                                                                    .setDescription("Online Food Order • " + (customerName != null ? customerName : "Customer"))
                                                                    .build()
                                                    )
                                                    .build()
                                    )
                                    .build()
                    );

            if (customerEmail != null && !customerEmail.isBlank() && customerEmail.contains("@")) {
                paramsBuilder.setCustomerEmail(customerEmail.trim());
            }

            if (orderNumber != null) {
                paramsBuilder.putMetadata("orderNumber", orderNumber);
            }
            if (customerName != null) {
                paramsBuilder.putMetadata("customerName", customerName);
            }
            if (customerPhone != null) {
                paramsBuilder.putMetadata("customerPhone", customerPhone);
            }

            Session session = Session.create(paramsBuilder.build());

            return new CheckoutSessionResponse(
                    PaymentGatewayProvider.STRIPE,
                    UUID.randomUUID().toString(),
                    session.getId(),
                    session.getUrl(),
                    publishableKey,
                    amount,
                    amountInCents,
                    chargeCurrency.toUpperCase(),
                    orderNumber != null ? orderNumber : session.getId(),
                    orderNumber != null ? orderNumber : "ONLINE",
                    "Restro Customer Portal"
            );
        } catch (Exception e) {
            log.error("Failed to create Stripe customer checkout session for order {}: {}", orderNumber, e.getMessage(), e);
            throw new BusinessException("Failed to initiate Stripe Checkout: " + e.getMessage());
        }
    }
}
