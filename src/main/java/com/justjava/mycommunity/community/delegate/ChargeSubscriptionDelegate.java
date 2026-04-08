package com.justjava.mycommunity.community.delegate;

import com.justjava.mycommunity.community.PaymentTransaction;
import com.justjava.mycommunity.community.dto.PaymentStatus;
import com.justjava.mycommunity.community.dto.PaymentType;
import com.justjava.mycommunity.community.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component("chargeSubscriptionDelegate")
@RequiredArgsConstructor
public class ChargeSubscriptionDelegate implements JavaDelegate {

    private final PaymentTransactionRepository transactionRepo;

    @Override
    public void execute(DelegateExecution execution) {

        String userId = (String) execution.getVariable("userId");
        Long communityId = (Long) execution.getVariable("communityId");

        PaymentTransaction tx = new PaymentTransaction();
        tx.setUserId(userId);
        tx.setCommunityId(communityId);
        tx.setType(PaymentType.SUBSCRIPTION);
        tx.setStatus(PaymentStatus.PENDING);
        tx.setCreatedAt(LocalDateTime.now());

        transactionRepo.save(tx);

        execution.setVariable("paymentRef", "TX-" + tx.getId());
    }
}