package com.justjava.mycommunity.community.delegate;

import com.justjava.mycommunity.chat.entity.User;
import com.justjava.mycommunity.chat.repository.CommunityRepository;
import com.justjava.mycommunity.community.Community;
import com.justjava.mycommunity.community.CommunityMembership;
import com.justjava.mycommunity.community.MembershipStatus;
import com.justjava.mycommunity.community.repository.CommunityMembershipRepository;
import com.justjava.mycommunity.userManagement.UserRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("approveMembershipDelegate")
@RequiredArgsConstructor
public class ApproveMembershipDelegate implements JavaDelegate {

    private final CommunityMembershipRepository membershipRepo;
    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;

    @Override
    public void execute(DelegateExecution execution) {

        String userId = (String) execution.getVariable("userId");
        Long communityId = (Long) execution.getVariable("communityId");

        CommunityMembership membership = membershipRepo
                .findByUserIdAndCommunityId(userId, communityId)
                .orElseThrow();

        membership.setStatus(MembershipStatus.APPROVED);
        membershipRepo.save(membership);

        // ALSO update old ManyToMany (backward compatibility)
        User user = userRepository.findByUserId(userId);
        Community community = communityRepository.findById(communityId).orElseThrow();

        user.getCommunities().add(community);
        community.getUsers().add(user);

        userRepository.save(user);
    }
}