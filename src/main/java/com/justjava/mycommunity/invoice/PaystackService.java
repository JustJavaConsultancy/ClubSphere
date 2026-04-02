package com.justjava.mycommunity.invoice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
public class PaystackService {

    @Value("${paystack.secret.key:sk_test_your_secret_key}")
    private String paystackSecretKey;

    @Value("${paystack.public.key:pk_test_your_public_key}")
    private String paystackPublicKey;

    private final RestTemplate restTemplate;
    private static final String PAYSTACK_BASE_URL = "https://api.paystack.co";

    public PaystackService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Check if Paystack is properly configured
     */
    public boolean isConfigured() {
        return paystackSecretKey != null &&
                !paystackSecretKey.equals("sk_test_your_secret_key") &&
                (paystackSecretKey.startsWith("sk_test_") || paystackSecretKey.startsWith("sk_live_"));
    }

    /**
     * Validate Paystack configuration
     */
    private void validateConfiguration() {
        if (paystackSecretKey == null || paystackSecretKey.equals("sk_test_your_secret_key")) {
            throw new RuntimeException("Paystack secret key is not configured. Please set paystack.secret.key in application.properties");
        }

        if (!paystackSecretKey.startsWith("sk_test_") && !paystackSecretKey.startsWith("sk_live_")) {
            throw new RuntimeException("Invalid Paystack secret key format. Key should start with 'sk_test_' or 'sk_live_'");
        }

        System.out.println("Paystack configuration validated successfully");
    }

    /**
     * Initialize a payment transaction with Paystack
     */
    public Map<String, Object> initializePayment(String email, BigDecimal amount, String reference,
                                                 String callbackUrl, Map<String, Object> metadata) {
        try {
            // Validate configuration before making API call
            validateConfiguration();

            String url = PAYSTACK_BASE_URL + "/transaction/initialize";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(paystackSecretKey);

            // Log the request details (without sensitive data)
            System.out.println("Initializing Paystack payment for email: " + email + ", amount: " + amount);
            System.out.println("Using API key starting with: " + paystackSecretKey.substring(0, Math.min(10, paystackSecretKey.length())) + "...");

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("email", email);
            requestBody.put("amount", amount.multiply(new BigDecimal("100")).intValue()); // Convert to kobo
            requestBody.put("reference", reference);
            requestBody.put("callback_url", callbackUrl);
            requestBody.put("metadata", metadata);

            System.out.println("Request body: " + requestBody);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            System.out.println("Paystack response status: " + response.getStatusCode());
            System.out.println("Paystack response body: " + response.getBody());

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody != null && Boolean.TRUE.equals(responseBody.get("status"))) {
                    return (Map<String, Object>) responseBody.get("data");
                } else {
                    throw new RuntimeException("Paystack API returned error: " + responseBody);
                }
            }

            throw new RuntimeException("Failed to initialize payment with Paystack. Status: " + response.getStatusCode());

        } catch (Exception e) {
            System.err.println("Detailed Paystack error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error initializing Paystack payment: " + e.getMessage(), e);
        }
    }

    /**
     * Verify a payment transaction
     */
    public Map<String, Object> verifyPayment(String reference) {
        try {
            String url = PAYSTACK_BASE_URL + "/transaction/verify/" + reference;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(paystackSecretKey);

            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody != null && Boolean.TRUE.equals(responseBody.get("status"))) {
                    return (Map<String, Object>) responseBody.get("data");
                }
            }

            throw new RuntimeException("Failed to verify payment with Paystack");

        } catch (Exception e) {
            throw new RuntimeException("Error verifying Paystack payment: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a unique payment reference
     */
    public String generatePaymentReference(String processId) {
        return "INV_" + processId + "_" + System.currentTimeMillis();
    }

    /**
     * Create payment metadata
     */
    public Map<String, Object> createPaymentMetadata(String processId, String businessName, String clientName) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("process_id", processId);
        metadata.put("business_name", businessName);
        metadata.put("client_name", clientName);
        metadata.put("payment_type", "invoice");
        return metadata;
    }
}
