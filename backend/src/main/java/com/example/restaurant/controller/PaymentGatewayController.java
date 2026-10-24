package com.example.restaurant.controller;

import com.example.restaurant.dto.billing.BillDto;
import com.example.restaurant.dto.gateway.CheckoutSessionResponse;
import com.example.restaurant.dto.gateway.CreateCheckoutSessionRequest;
import com.example.restaurant.dto.gateway.CreateCustomerCheckoutSessionRequest;
import com.example.restaurant.dto.gateway.GatewayWebhookResult;
import com.example.restaurant.dto.gateway.RazorpayVerifyPaymentRequest;
import com.example.restaurant.dto.response.ApiResponse;
import com.example.restaurant.entity.Bill;
import com.example.restaurant.entity.BillStatus;
import com.example.restaurant.entity.PaymentGatewayProvider;
import com.example.restaurant.exception.BusinessException;
import com.example.restaurant.gateway.PaymentGateway;
import com.example.restaurant.gateway.PaymentGatewayFactory;
import com.example.restaurant.gateway.StripePaymentGateway;
import com.example.restaurant.security.UserPrincipal;
import com.example.restaurant.service.BillingService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentGatewayController {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayController.class);

    private final PaymentGatewayFactory gatewayFactory;
    private final BillingService billingService;

    public PaymentGatewayController(PaymentGatewayFactory gatewayFactory, BillingService billingService) {
        this.gatewayFactory = gatewayFactory;
        this.billingService = billingService;
    }

    @PostMapping("/checkout-session")
    @PreAuthorize("hasAnyAuthority('PAYMENT_CREATE', 'BILL_CREATE', 'ORDER_CREATE', 'ROLE_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CheckoutSessionResponse>> createCheckoutSession(
            @Valid @RequestBody CreateCheckoutSessionRequest request,
            @RequestParam(required = false) String returnUrl,
            @RequestParam(required = false) String cancelUrl,
            Authentication authentication) {

        Bill bill = billingService.getBillEntity(request.getBillId());

        // Validate outlet boundary
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            String userOutletId = principal.getOutletId();
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ADMIN"));

            if (!isAdmin && userOutletId != null && !userOutletId.equals(bill.getOutlet().getId())) {
                throw new BusinessException("You are not authorized to process bills for another outlet");
            }
        }

        if (bill.getStatus() == BillStatus.PAID) {
            throw new BusinessException("Bill " + bill.getBillNumber() + " is already fully settled");
        }
        if (bill.getStatus() == BillStatus.CANCELLED) {
            throw new BusinessException("Cannot process payment for a cancelled bill");
        }

        BigDecimal balanceAmount = bill.getBalanceAmount();
        if (balanceAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Outstanding balance is zero. Bill is settled.");
        }

        PaymentGateway gateway = gatewayFactory.getGateway(request.getGatewayProvider());
        String username = authentication != null ? authentication.getName() : null;

        CheckoutSessionResponse sessionResponse = gateway.createCheckoutSession(
                bill,
                balanceAmount,
                null,
                returnUrl,
                cancelUrl,
                request.getNotes(),
                username
        );

        return ResponseEntity.ok(ApiResponse.ok("Payment checkout session created successfully", sessionResponse));
    }

    @PostMapping("/customer/checkout-session")
    public ResponseEntity<ApiResponse<CheckoutSessionResponse>> createCustomerCheckoutSession(
            @Valid @RequestBody CreateCustomerCheckoutSessionRequest request) {

        StripePaymentGateway stripeGateway = (StripePaymentGateway) gatewayFactory.getGateway(PaymentGatewayProvider.STRIPE);

        CheckoutSessionResponse sessionResponse = stripeGateway.createCustomerCheckoutSession(
                request.getAmount(),
                request.getCurrency(),
                request.getOrderNumber(),
                request.getCustomerName(),
                request.getCustomerEmail(),
                request.getCustomerPhone(),
                request.getReturnUrl(),
                request.getCancelUrl(),
                request.getNotes()
        );

        return ResponseEntity.ok(ApiResponse.ok("Customer Stripe Checkout session created successfully", sessionResponse));
    }

    @PostMapping("/razorpay/verify")
    @PreAuthorize("hasAnyAuthority('PAYMENT_CREATE', 'BILL_CREATE', 'ORDER_CREATE', 'ROLE_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BillDto>> verifyRazorpayPayment(
            @Valid @RequestBody RazorpayVerifyPaymentRequest request,
            Authentication authentication) {

        String username = authentication != null ? authentication.getName() : null;

        Map<String, String> params = new HashMap<>();
        params.put("billId", request.getBillId());
        params.put("paymentId", request.getPaymentId());
        params.put("razorpayOrderId", request.getRazorpayOrderId());
        params.put("razorpayPaymentId", request.getRazorpayPaymentId());
        params.put("razorpaySignature", request.getRazorpaySignature());
        params.put("username", username);

        PaymentGateway razorpayGateway = gatewayFactory.getGateway(PaymentGatewayProvider.RAZORPAY);
        razorpayGateway.verifyPayment(params);

        BillDto updatedBill = billingService.getBillById(request.getBillId());
        return ResponseEntity.ok(ApiResponse.ok("Razorpay payment verified and settled successfully", updatedBill));
    }

    @PostMapping("/stripe/verify")
    @PreAuthorize("hasAnyAuthority('PAYMENT_CREATE', 'BILL_CREATE', 'ORDER_CREATE', 'ROLE_ADMIN', 'BILL_VIEW') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BillDto>> verifyStripePayment(
            @RequestParam String sessionId,
            @RequestParam(required = false) String billId,
            Authentication authentication) {

        PaymentGateway stripeGateway = gatewayFactory.getGateway(PaymentGatewayProvider.STRIPE);
        Map<String, String> params = new HashMap<>();
        params.put("sessionId", sessionId);
        if (billId != null) {
            params.put("billId", billId);
        }
        stripeGateway.verifyPayment(params);

        if (billId != null) {
            BillDto updatedBill = billingService.getBillById(billId);
            return ResponseEntity.ok(ApiResponse.ok("Stripe payment verified and settled successfully", updatedBill));
        }

        return ResponseEntity.ok(ApiResponse.ok("Stripe session verified successfully", null));
    }

    @PostMapping({"/webhook/{provider}", "/{provider}/webhook"})
    public ResponseEntity<String> handleGatewayWebhook(
            @PathVariable String provider,
            @RequestBody String rawPayload,
            @RequestHeader Map<String, String> headers) {

        PaymentGatewayProvider gatewayProvider;
        try {
            gatewayProvider = PaymentGatewayProvider.valueOf(provider.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Received webhook for unknown gateway provider: {}", provider);
            return ResponseEntity.badRequest().body("Unknown provider: " + provider);
        }

        PaymentGateway gateway = gatewayFactory.getGateway(gatewayProvider);
        GatewayWebhookResult result = gateway.processWebhook(rawPayload, headers);

        log.info("Processed {} webhook event {} with result: {}", gatewayProvider, result.getEventId(), result.getMessage());
        return ResponseEntity.ok("Webhook received and processed");
    }
}
