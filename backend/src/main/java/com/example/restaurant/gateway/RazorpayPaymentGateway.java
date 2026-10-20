package com.example.restaurant.gateway;

import com.example.restaurant.dto.gateway.CheckoutSessionResponse;
import com.example.restaurant.dto.gateway.GatewayWebhookResult;
import com.example.restaurant.entity.*;
import com.example.restaurant.exception.BusinessException;
import com.example.restaurant.repository.GatewayWebhookEventRepository;
import com.example.restaurant.repository.PaymentRepository;
import com.example.restaurant.repository.UserRepository;
import com.example.restaurant.service.BillingService;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import org.json.JSONObject;
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
public class RazorpayPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentGateway.class);

    @Value("${app.payment.razorpay.key-id}")
    private String keyId;

    @Value("${app.payment.razorpay.key-secret}")
    private String keySecret;

    @Value("${app.payment.razorpay.webhook-secret}")
    private String webhookSecret;

    @Value("${app.payment.razorpay.currency:INR}")
    private String defaultCurrency;

    private final BillingService billingService;
    private final PaymentRepository paymentRepository;
    private final GatewayWebhookEventRepository webhookEventRepository;
    private final UserRepository userRepository;

    public RazorpayPaymentGateway(BillingService billingService,
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
        return PaymentGatewayProvider.RAZORPAY;
    }

    @Override
    @Transactional
    public CheckoutSessionResponse createCheckoutSession(Bill bill, BigDecimal amount, String currency,
                                                          String returnUrl, String cancelUrl,
                                                          String notes, String currentUsername) {
        String chargeCurrency = (currency != null && !currency.isBlank())
                ? currency.trim().toUpperCase()
                : defaultCurrency.toUpperCase();

        long amountInSubunits = amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();

        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInSubunits);
            orderRequest.put("currency", chargeCurrency);
            orderRequest.put("receipt", bill.getBillNumber());

            JSONObject notesObj = new JSONObject();
            notesObj.put("billId", bill.getId());
            notesObj.put("billNumber", bill.getBillNumber());
            notesObj.put("outletId", bill.getOutlet().getId());
            orderRequest.put("notes", notesObj);

            Order razorpayOrder = client.orders.create(orderRequest);
            String rzpOrderId = razorpayOrder.get("id");

            User user = null;
            if (currentUsername != null) {
                user = userRepository.findByUsername(currentUsername).orElse(null);
            }

            Payment pendingPayment = Payment.createGatewayPending(
                    UUID.randomUUID().toString(),
                    bill,
                    bill.getOutlet(),
                    PaymentGatewayProvider.RAZORPAY,
                    PaymentMethod.RAZORPAY,
                    amount,
                    chargeCurrency,
                    rzpOrderId,
                    user,
                    notes != null ? notes : "Razorpay Checkout Initiated"
            );
            paymentRepository.save(pendingPayment);

            return new CheckoutSessionResponse(
                    PaymentGatewayProvider.RAZORPAY,
                    pendingPayment.getId(),
                    rzpOrderId,
                    null, // Razorpay opens standard client checkout modal using keyId & orderId
                    keyId,
                    amount,
                    amountInSubunits,
                    chargeCurrency,
                    bill.getId(),
                    bill.getBillNumber(),
                    bill.getOutlet().getName()
            );
        } catch (Exception e) {
            log.error("Failed to create Razorpay order for bill {}: {}", bill.getBillNumber(), e.getMessage(), e);
            throw new BusinessException("Failed to initiate Razorpay order: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void verifyPayment(Map<String, String> params) {
        String razorpayOrderId = params.get("razorpayOrderId");
        String razorpayPaymentId = params.get("razorpayPaymentId");
        String razorpaySignature = params.get("razorpaySignature");
        String billId = params.get("billId");
        String paymentId = params.get("paymentId");
        String username = params.get("username");

        if (razorpayOrderId == null || razorpayPaymentId == null || razorpaySignature == null) {
            throw new BusinessException("Missing required Razorpay payment signature parameters");
        }

        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", razorpayOrderId);
            options.put("razorpay_payment_id", razorpayPaymentId);
            options.put("razorpay_signature", razorpaySignature);

            boolean isSignatureValid = Utils.verifyPaymentSignature(options, keySecret);
            if (!isSignatureValid) {
                log.error("Razorpay signature validation failed for order {} and payment {}", razorpayOrderId, razorpayPaymentId);
                throw new BusinessException("Invalid Razorpay payment signature");
            }

            Payment payment = paymentRepository.findByGatewayOrderId(razorpayOrderId)
                    .orElse(null);

            BigDecimal amount = payment != null ? payment.getAmount() : null;
            String currency = payment != null ? payment.getCurrency() : defaultCurrency;

            if (amount == null && billId != null) {
                Bill bill = billingService.getBillEntity(billId);
                amount = bill.getBalanceAmount();
            }

            billingService.reconcileGatewayPayment(
                    billId,
                    paymentId,
                    PaymentGatewayProvider.RAZORPAY,
                    razorpayOrderId,
                    razorpayPaymentId,
                    razorpaySignature,
                    amount != null ? amount : BigDecimal.ZERO,
                    currency,
                    username,
                    "Razorpay verified payment (" + razorpayPaymentId + ")"
            );
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("Error verifying Razorpay payment: {}", e.getMessage(), e);
            throw new BusinessException("Failed to verify Razorpay payment: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public GatewayWebhookResult processWebhook(String rawPayload, Map<String, String> headers) {
        String signature = headers.get("x-razorpay-signature");
        if (signature == null) {
            signature = headers.get("X-Razorpay-Signature");
        }

        if (signature == null || signature.isBlank()) {
            throw new BusinessException("Missing X-Razorpay-Signature header");
        }

        final String finalSignature = signature;

        try {
            boolean isSignatureValid = Utils.verifyWebhookSignature(rawPayload, finalSignature, webhookSecret);
            if (!isSignatureValid) {
                log.error("Razorpay webhook signature verification failed.");
                throw new BusinessException("Invalid Razorpay webhook signature");
            }
        } catch (Exception e) {
            log.error("Failed to verify Razorpay webhook signature: {}", e.getMessage());
            throw new BusinessException("Razorpay webhook signature verification failed");
        }

        JSONObject payloadJson = new JSONObject(rawPayload);
        String eventType = payloadJson.optString("event", "unknown");

        // Event identifier for deduplication
        String eventId = payloadJson.optString("id", finalSignature);

        if (webhookEventRepository.existsByGatewayProviderAndEventId(PaymentGatewayProvider.RAZORPAY, eventId)) {
            log.info("Duplicate Razorpay webhook event {} ignored.", eventId);
            return GatewayWebhookResult.ignored(eventId, eventType, "Event already processed");
        }

        GatewayWebhookEvent auditEvent = new GatewayWebhookEvent(
                UUID.randomUUID().toString(),
                PaymentGatewayProvider.RAZORPAY,
                eventId,
                eventType,
                "PROCESSED",
                rawPayload
        );
        webhookEventRepository.save(auditEvent);

        if ("order.paid".equalsIgnoreCase(eventType) || "payment.captured".equalsIgnoreCase(eventType)) {
            JSONObject payloadObj = payloadJson.optJSONObject("payload");
            if (payloadObj != null) {
                JSONObject paymentEntity = payloadObj.optJSONObject("payment") != null
                        ? payloadObj.getJSONObject("payment").optJSONObject("entity")
                        : null;

                if (paymentEntity != null) {
                    String rzpOrderId = paymentEntity.optString("order_id");
                    String rzpPaymentId = paymentEntity.optString("id");
                    long amountPaise = paymentEntity.optLong("amount", 0);
                    BigDecimal amountPaid = BigDecimal.valueOf(amountPaise).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    String currency = paymentEntity.optString("currency", "INR").toUpperCase();

                    paymentRepository.findByGatewayOrderId(rzpOrderId).ifPresent(p -> {
                        billingService.reconcileGatewayPayment(
                                p.getBill().getId(),
                                p.getId(),
                                PaymentGatewayProvider.RAZORPAY,
                                rzpOrderId,
                                rzpPaymentId,
                                finalSignature,
                                amountPaid,
                                currency,
                                null,
                                "Razorpay Webhook (" + eventType + ")"
                        );
                    });
                }
            }
        }

        return GatewayWebhookResult.processed(eventId, eventType, "Successfully processed");
    }
}
