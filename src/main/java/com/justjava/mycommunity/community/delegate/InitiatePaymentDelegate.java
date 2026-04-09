package com.justjava.mycommunity.community.delegate;

import com.justjava.mycommunity.community.PaymentTransaction;
import com.justjava.mycommunity.community.dto.PaymentStatus;
import com.justjava.mycommunity.community.dto.PaymentType;
import com.justjava.mycommunity.community.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component("initiatePaymentDelegate")
@RequiredArgsConstructor
public class InitiatePaymentDelegate implements JavaDelegate {

    private final PaymentTransactionRepository transactionRepo;

    @Override
    public void execute(DelegateExecution execution) {

        String userId = (String) execution.getVariable("userId");
        Long communityId = (Long) execution.getVariable("communityId");
        BigDecimal amount = (BigDecimal) execution.getVariable("amount");

        PaymentTransaction tx = new PaymentTransaction();
        tx.setUserId(userId);
        tx.setCommunityId(communityId);
        tx.setAmount(amount);
        tx.setType(PaymentType.SUBSCRIPTION);
        tx.setStatus(PaymentStatus.PENDING);
        tx.setCreatedAt(LocalDateTime.now());

        transactionRepo.save(tx);

        // simulate provider ref
        String paymentRef = "TX-" + tx.getId();

        execution.setVariable("paymentRef", paymentRef);
    }
}