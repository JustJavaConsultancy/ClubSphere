package com.justjava.mycommunity.community.delegate;

import com.justjava.mycommunity.community.CommunityMembership;
import com.justjava.mycommunity.community.MembershipStatus;
import com.justjava.mycommunity.community.repository.CommunityMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("rejectMembershipDelegate")
@RequiredArgsConstructor
public class RejectMembershipDelegate implements JavaDelegate {

    private final CommunityMembershipRepository membershipRepo;

    @Override
    public void execute(DelegateExecution execution) {

        String userId = (String) execution.getVariable("userId");
        Long communityId = (Long) execution.getVariable("communityId");

        CommunityMembership membership = membershipRepo
                .findByUserIdAndCommunityId(userId, communityId)
                .orElseThrow();

        membership.setStatus(MembershipStatus.REJECTED);
        membershipRepo.save(membership);
    }
}