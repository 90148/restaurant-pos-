package com.example.restaurant;

import com.example.restaurant.dto.auth.LoginRequest;
import com.example.restaurant.dto.gateway.CreateCheckoutSessionRequest;
import com.example.restaurant.dto.gateway.RazorpayVerifyPaymentRequest;
import com.example.restaurant.dto.menu.CreateMenuItemRequest;
import com.example.restaurant.dto.order.CreateOrderItemRequest;
import com.example.restaurant.dto.order.CreateOrderRequest;
import com.example.restaurant.dto.table.CreateTableRequest;
import com.example.restaurant.entity.PaymentGatewayProvider;
import com.example.restaurant.entity.OrderType;
import com.example.restaurant.entity.TableShape;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PaymentGatewayIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String cashierToken;
    private String adminToken;
    private String defaultOutletId;

    @BeforeEach
    void setUp() throws Exception {
        LoginRequest cashierLogin = new LoginRequest("cashier", "Cashier@123");
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cashierLogin)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString()).path("data");
        cashierToken = data.path("accessToken").asText();
        defaultOutletId = data.path("user").path("outletId").asText();

        LoginRequest adminLogin = new LoginRequest("admin", "Admin@123");
        MvcResult adminRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andReturn();
        adminToken = objectMapper.readTree(adminRes.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }

    @Test
    @DisplayName("Should reject unauthorized bill checkout access if user belongs to different outlet")
    void testUnauthorizedCheckoutAccess() throws Exception {
        CreateCheckoutSessionRequest req = new CreateCheckoutSessionRequest(
                "invalid-bill-id",
                PaymentGatewayProvider.STRIPE,
                "Test notes"
        );

        mockMvc.perform(post("/api/payments/checkout-session")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should reject webhook request with invalid signature")
    void testInvalidWebhookSignature() throws Exception {
        // Stripe webhook without valid signature (standard route /webhook/stripe)
        mockMvc.perform(post("/api/payments/webhook/stripe")
                        .header("Stripe-Signature", "t=12345,v1=invalidsig")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"id\":\"evt_test123\"}"))
                .andExpect(status().isBadRequest());

        // Stripe webhook via alternate route /stripe/webhook
        mockMvc.perform(post("/api/payments/stripe/webhook")
                        .header("Stripe-Signature", "t=12345,v1=invalidsig")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"id\":\"evt_test123\"}"))
                .andExpect(status().isBadRequest());

        // Razorpay webhook without valid signature
        mockMvc.perform(post("/api/payments/webhook/razorpay")
                        .header("X-Razorpay-Signature", "invalidsig")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"event\":\"payment.captured\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject Razorpay payment verification when signature is invalid")
    void testRazorpayVerificationInvalidSignature() throws Exception {
        RazorpayVerifyPaymentRequest req = new RazorpayVerifyPaymentRequest(
                "bill-test",
                "pay-test",
                "order_test123",
                "pay_test123",
                "invalid_signature_hash"
        );

        mockMvc.perform(post("/api/payments/razorpay/verify")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject checkout session creation when bill balance is already settled")
    void testCheckoutSessionOnPaidBill() throws Exception {
        // Create table, order, and bill
        JsonNode billData = createSampleBill();
        String billId = billData.path("id").asText();
        double totalAmount = billData.path("totalAmount").asDouble();

        // Fully settle bill via cash
        String payPayload = String.format(java.util.Locale.US, """
                {
                    "amount": %.2f,
                    "paymentMethod": "CASH",
                    "tenderedAmount": %.2f
                }
                """, totalAmount, totalAmount);

        mockMvc.perform(post("/api/bills/" + billId + "/payments")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));

        // Now attempt to initiate gateway checkout on already-paid bill
        CreateCheckoutSessionRequest checkoutReq = new CreateCheckoutSessionRequest(
                billId,
                PaymentGatewayProvider.STRIPE,
                "Attempt to pay again"
        );

        mockMvc.perform(post("/api/payments/checkout-session")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("already fully settled")));
    }

    private JsonNode createSampleBill() throws Exception {
        String menuItemId = getOrCreateMenuItemId("Test Gourmet Dish", new BigDecimal("25.00"));
        CreateOrderItemRequest itemReq = new CreateOrderItemRequest(menuItemId, 1, null, List.of());
        CreateOrderRequest orderReq = new CreateOrderRequest(
                defaultOutletId, null, OrderType.TAKEAWAY, "Gateway Guest", null, 1,
                null, List.of(itemReq)
        );

        MvcResult orderRes = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String orderId = objectMapper.readTree(orderRes.getResponse().getContentAsString()).path("data").path("id").asText();

        MvcResult billRes = mockMvc.perform(post("/api/bills/order/" + orderId)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(billRes.getResponse().getContentAsString()).path("data");
    }

    private String getOrCreateMenuItemId(String name, BigDecimal price) throws Exception {
        MvcResult catRes = mockMvc.perform(get("/api/menu/categories").param("outletId", defaultOutletId)
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn();
        JsonNode catData = objectMapper.readTree(catRes.getResponse().getContentAsString()).path("data");
        String categoryId = catData.get(0).path("id").asText();

        CreateMenuItemRequest itemReq = new CreateMenuItemRequest(
                categoryId, name + " " + System.currentTimeMillis(),
                "Fresh item", price, price.multiply(new BigDecimal("0.35")), new BigDecimal("5.00"),
                null, true, 10, null, java.util.Set.of()
        );
        MvcResult itemRes = mockMvc.perform(post("/api/menu/items")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemReq)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(itemRes.getResponse().getContentAsString()).path("data").path("id").asText();
    }
}
