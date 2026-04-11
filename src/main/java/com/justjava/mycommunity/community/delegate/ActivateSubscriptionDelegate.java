package com.justjava.mycommunity.community.delegate;

import com.justjava.mycommunity.community.MembershipSubscription;
import com.justjava.mycommunity.community.dto.SubscriptionStatus;
import com.justjava.mycommunity.community.repository.MembershipSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component("activateSubscriptionDelegate")
@RequiredArgsConstructor
public class ActivateSubscriptionDelegate implements JavaDelegate {

    private final MembershipSubscriptionRepository subscriptionRepo;

    @Override
    public void execute(DelegateExecution execution) {

        String userId = (String) execution.getVariable("userId");
        Long communityId = (Long) execution.getVariable("communityId");
        BigDecimal amount = (BigDecimal) execution.getVariable("amount");

        MembershipSubscription sub = new MembershipSubscription();
        sub.setUserId(userId);
        sub.setCommunityId(communityId);
        sub.setAmount(amount);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartDate(LocalDateTime.now());
        sub.setNextBillingDate(LocalDateTime.now().plusMonths(1));

        subscriptionRepo.save(sub);
    }
}