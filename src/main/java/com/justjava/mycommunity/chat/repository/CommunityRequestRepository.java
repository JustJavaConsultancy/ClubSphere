package com.justjava.mycommunity.chat.repository;

import com.justjava.mycommunity.community.CommunityRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunityRequestRepository extends JpaRepository<CommunityRequest, Long> {
    CommunityRequest findByUser_UserIdAndCommunity_Id(String userUserId, Long communityId);

    List<CommunityRequest> findByUser_UserIdAndStatus(String userUserId, String status);

    List<CommunityRequest> findByStatus(String status);
}
