package com.justjava.mycommunity.community.repository;


import com.justjava.mycommunity.community.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    List<SubscriptionPlan> findByCommunityId(Long communityId);

    List<SubscriptionPlan> findByCommunityIdAndActiveTrue(Long communityId);
}