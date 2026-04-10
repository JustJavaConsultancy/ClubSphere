package com.justjava.mycommunity.community.delegate;

import com.justjava.mycommunity.community.PaymentTransaction;
import com.justjava.mycommunity.community.dto.PaymentStatus;
import com.justjava.mycommunity.community.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("recordDonationDelegate")
@RequiredArgsConstructor
public class RecordDonationDelegate implements JavaDelegate {

    private final PaymentTransactionRepository transactionRepo;

    @Override
    public void execute(DelegateExecution execution) {

        String paymentRef = (String) execution.getVariable("paymentRef");

        PaymentTransaction tx = transactionRepo.findByProviderRef(paymentRef)
                .orElseThrow();

        tx.setStatus(PaymentStatus.SUCCESS);

        transactionRepo.save(tx);
    }
}