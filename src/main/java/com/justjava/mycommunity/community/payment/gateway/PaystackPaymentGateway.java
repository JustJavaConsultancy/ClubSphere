package com.justjava.mycommunity.community.payment.gateway;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PaystackPaymentGateway implements PaymentGateway {

    private final RestTemplate restTemplate;

    @Value("${paystack.secret.key}")
    private String secretKey;

    @Override
    public String getName() {
        return "PAYSTACK";
    }

    @Override
    public Map<String, Object> initializePayment(String userId,
                                                 Long communityId,
                                                 BigDecimal amount,
                                                 String reference,
                                                 String callbackUrl) {

        String url = "https://api.paystack.co/transaction/initialize";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(secretKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount.multiply(BigDecimal.valueOf(100))); // kobo
        body.put("reference", reference);
        body.put("callback_url", callbackUrl);
        body.put("email", userId + "@app.com"); // adjust later

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        return response.getBody();
    }

    @Override
    public boolean verifyPayment(String reference) {

        String url = "https://api.paystack.co/transaction/verify/" + reference;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(secretKey);

        HttpEntity<?> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                Map.class
        );

        Map body = response.getBody();
        Map data = (Map) body.get("data");

        return "success".equalsIgnoreCase((String) data.get("status"));
    }
}