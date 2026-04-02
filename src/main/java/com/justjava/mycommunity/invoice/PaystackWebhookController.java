package com.justjava.mycommunity.invoice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

@RestController
@RequestMapping("/webhook")
public class PaystackWebhookController {

    @Value("${paystack.secret.key:sk_test_your_secret_key}")
    private String paystackSecretKey;

    private final PaystackService paystackService;
    private final InvoiceService invoiceService;
    private final ObjectMapper objectMapper;

    public PaystackWebhookController(PaystackService paystackService, InvoiceService invoiceService) {
        this.paystackService = paystackService;
        this.invoiceService = invoiceService;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping("/paystack")
    public ResponseEntity<String> handlePaystackWebhook(
            @RequestBody String payload,
            @RequestHeader("x-paystack-signature") String signature) {

        try {
            // Verify webhook signature
            if (!verifySignature(payload, signature)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
            }

            // Parse webhook payload
            Map<String, Object> webhookData = objectMapper.readValue(payload, Map.class);
            String event = (String) webhookData.get("event");

            if ("charge.success".equals(event)) {
                handleSuccessfulPayment(webhookData);
            }

            return ResponseEntity.ok("Webhook processed successfully");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing webhook: " + e.getMessage());
        }
    }

    private boolean verifySignature(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKeySpec = new SecretKeySpec(paystackSecretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString().equals(signature);

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void handleSuccessfulPayment(Map<String, Object> webhookData) {
        try {
            Map<String, Object> data = (Map<String, Object>) webhookData.get("data");
            String reference = (String) data.get("reference");
            Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");

            if (metadata != null && metadata.containsKey("process_id")) {
                String processId = (String) metadata.get("process_id");

                System.out.println("Payment successful for invoice: " + processId + " with reference: " + reference);
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error handling successful payment: " + e.getMessage());
        }
    }
}
