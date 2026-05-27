package com.justjava.mycommunity.community.repository;


import com.justjava.mycommunity.community.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    List<SubscriptionPlan> findByCommunityId(Long communityId);

    List<SubscriptionPlan> findByCommunityIdAndActiveTrue(Long communityId);

    Optional<SubscriptionPlan> findByCommunityIdAndNameIgnoreCase(Long communityId, String name);
}
