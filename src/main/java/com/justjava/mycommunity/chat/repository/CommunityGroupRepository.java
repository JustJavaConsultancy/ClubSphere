package com.justjava.mycommunity.chat.repository;

import com.justjava.mycommunity.community.CommunityGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunityGroupRepository extends JpaRepository<CommunityGroup, Long> {
    List<CommunityGroup> findByCommunity_Id(Long communityId);
}