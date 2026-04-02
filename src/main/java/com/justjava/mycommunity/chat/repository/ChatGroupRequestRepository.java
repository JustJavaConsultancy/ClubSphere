package com.justjava.mycommunity.chat.repository;

import com.justjava.mycommunity.network.ChatGroupRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatGroupRequestRepository extends JpaRepository<ChatGroupRequest, Long> {

    List<ChatGroupRequest> findAllByChatGroup_AdminUser_UserId(String userId);

    ChatGroupRequest findByChatGroup_Id(Long chatGroupId);

    List<ChatGroupRequest> findByChatGroup_AdminUser_UserIdAndStatus(String userId, String status);

    ChatGroupRequest findByChatGroup_IdAndUser_UserId(Long chatGroupId, String userUserId);

}