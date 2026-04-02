package com.justjava.mycommunity.chat.repository;

import com.justjava.mycommunity.community.CommunityGroupRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunityGroupRequestRepository extends JpaRepository<CommunityGroupRequest, Long> {
    CommunityGroupRequest findByUser_UserIdAndGroup_Id(String userUserId, Long groupId);

    List<CommunityGroupRequest> findByUser_UserIdAndStatus(String userUserId, String status);

    List<CommunityGroupRequest> findByStatus(String status);
}
