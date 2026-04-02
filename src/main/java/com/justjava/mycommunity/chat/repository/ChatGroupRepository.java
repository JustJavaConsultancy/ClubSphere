package com.justjava.mycommunity.chat.repository;

import com.justjava.mycommunity.chat.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import com.justjava.mycommunity.network.ChatGroup;

import java.util.List;
import java.util.Optional;

public interface ChatGroupRepository extends JpaRepository<ChatGroup, Long> {
    Optional<ChatGroup> findByAdminUser_UserId(String adminUserUserId);

    boolean existsByAdminUser(User adminUser);

    List<ChatGroup> findAllByCommunity_Id(Long communityId);

    void deleteByAdminUser(User adminUser);
}