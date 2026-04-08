package com.justjava.mycommunity.community.delegate;

import com.justjava.mycommunity.community.MembershipSubscription;
import com.justjava.mycommunity.community.dto.SubscriptionStatus;
import com.justjava.mycommunity.community.repository.MembershipSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("cancelSubscriptionDelegate")
@RequiredArgsConstructor
public class CancelSubscriptionDelegate implements JavaDelegate {

    private final MembershipSubscriptionRepository subscriptionRepo;

    @Override
    public void execute(DelegateExecution execution) {

        String userId = (String) execution.getVariable("userId");
        Long communityId = (Long) execution.getVariable("communityId");

        MembershipSubscription sub =
                subscriptionRepo.findByUserIdAndCommunityId(userId, communityId)
                        .orElseThrow();

        sub.setStatus(SubscriptionStatus.CANCELLED);

        subscriptionRepo.save(sub);
    }
}