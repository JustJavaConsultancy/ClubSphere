package com.justjava.mycommunity.community.payment.orchestrator;

import com.justjava.mycommunity.community.PaymentTransaction;
import com.justjava.mycommunity.community.dto.PaymentStatus;
import com.justjava.mycommunity.community.dto.PaymentType;
import com.justjava.mycommunity.community.payment.gateway.PaymentGateway;
import com.justjava.mycommunity.community.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentOrchestratorService {

    private final List<PaymentGateway> gateways;
    private final PaymentTransactionRepository transactionRepo;

    // 🔥 Default gateway
    private static final String DEFAULT_GATEWAY = "PAYSTACK";

    private PaymentGateway resolveGateway(String gatewayName) {
        return gateways.stream()
                .filter(g -> g.getName().equalsIgnoreCase(gatewayName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Payment gateway not found"));
    }

    public Map<String, Object> initiatePayment(String userId,
                                               Long communityId,
                                               BigDecimal amount,
                                               PaymentType paymentType,
                                               String gatewayName) {

        String reference = UUID.randomUUID().toString();

        // 🔹 Save transaction FIRST (important)
        PaymentTransaction tx = new PaymentTransaction();
        tx.setUserId(userId);
        tx.setCommunityId(communityId);
        tx.setAmount(amount);
        tx.setStatus(PaymentStatus.PENDING);
        tx.setType(PaymentType.SUBSCRIPTION);
        tx.setType(paymentType);
        tx.setProviderRef(reference);
        tx.setCreatedAt(LocalDateTime.now());

        transactionRepo.save(tx);

        // 🔹 Resolve gateway
        PaymentGateway gateway = resolveGateway(
                gatewayName != null ? gatewayName : DEFAULT_GATEWAY
        );

        // 🔹 Initialize payment
        Map<String, Object> response = gateway.initializePayment(
                userId,
                communityId,
                amount,
                reference,
                "http://localhost:8080/payment/callback"
        );

        return response;
    }

    public boolean verifyPayment(String reference, String gatewayName) {

        PaymentGateway gateway = resolveGateway(gatewayName);

        return gateway.verifyPayment(reference);
    }

}