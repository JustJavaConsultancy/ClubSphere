package com.justjava.mycommunity.community.delegate;

import com.justjava.mycommunity.community.MembershipSubscription;
import com.justjava.mycommunity.community.repository.MembershipSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component("updateSubscriptionBillingDelegate")
@RequiredArgsConstructor
public class UpdateSubscriptionBillingDelegate implements JavaDelegate {

    private final MembershipSubscriptionRepository subscriptionRepo;

    @Override
    public void execute(DelegateExecution execution) {

        String userId = (String) execution.getVariable("userId");
        Long communityId = (Long) execution.getVariable("communityId");

        MembershipSubscription sub =
                subscriptionRepo.findByUserIdAndCommunityId(userId,communityId)
                        .orElseThrow();

        // 🔥 Move next billing forward
        LocalDateTime currentNext = sub.getNextBillingDate();

        if (currentNext == null) {
            currentNext = LocalDateTime.now();
        }

        LocalDateTime nextBilling = currentNext.plusMonths(1);

        sub.setNextBillingDate(nextBilling);

        subscriptionRepo.save(sub);
    }
}