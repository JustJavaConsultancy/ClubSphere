package com.justjava.mycommunity.community.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionInvoiceScheduler {

    private final SubscriptionBillingService subscriptionBillingService;

    @Scheduled(cron = "0 0 0 * * *")
    public void generateInvoicesAtCycleStart() {
        int generated = subscriptionBillingService.generateInvoicesForDueSubscriptions();
        if (generated > 0) {
            log.info("Generated {} subscription invoices for due billing cycles.", generated);
        }
    }
}
