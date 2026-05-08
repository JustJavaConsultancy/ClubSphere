package com.justjava.mycommunity.community.repository;


import com.justjava.mycommunity.community.MembershipSubscription;
import com.justjava.mycommunity.community.dto.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MembershipSubscriptionRepository extends JpaRepository<MembershipSubscription, Long> {

    Optional<MembershipSubscription> findByUserIdAndCommunityId(String userId, Long communityId);

    List<MembershipSubscription> findByStatus(SubscriptionStatus status);

    List<MembershipSubscription> findByNextBillingDateBefore(LocalDateTime date);
    List<MembershipSubscription> findByStatusAndNextBillingDateLessThanEqual(SubscriptionStatus status, LocalDateTime date);

    boolean existsByUserIdAndCommunityIdAndStatus(
            String userId,
            Long communityId,
            SubscriptionStatus status
    );

    List<MembershipSubscription> findByCommunityId(Long communityId);

    List<MembershipSubscription> findByUserId(String userId);

    List<MembershipSubscription> findByUserIdAndStatus(String userId, SubscriptionStatus status);

    List<MembershipSubscription> findByCommunityIdAndStatus(Long communityId, SubscriptionStatus status);
}
