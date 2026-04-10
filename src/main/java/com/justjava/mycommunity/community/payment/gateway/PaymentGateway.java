package com.justjava.mycommunity.community.payment.gateway;


import java.math.BigDecimal;
import java.util.Map;

public interface PaymentGateway {

    String getName(); // PAYSTACK, FLUTTERWAVE

    Map<String, Object> initializePayment(
            String userId,
            Long communityId,
            BigDecimal amount,
            String reference,
            String callbackUrl
    );

    boolean verifyPayment(String reference);
}