package com.justjava.mycommunity.chat.repository;

import com.justjava.mycommunity.community.Community;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CommunityRepository extends JpaRepository<Community, Long> {

    @Query("SELECT c FROM Community c JOIN c.users u WHERE u.userId = :userId ORDER BY c.name")
    List<Community> findCommunitiesByUserId(String userId);

    @Query("SELECT c FROM Community c JOIN FETCH c.users WHERE c.id = :id")
    Optional<Community> findByIdWithUsers(Long id);

    List<Community> findByIsPrivateFalse();

    boolean existsByIdAndUsers_UserId(Long communityId, String userId);
    boolean existsByOrganization_Id(Long organizationId);

}