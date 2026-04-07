package com.justjava.mycommunity.chat.repository;

import com.justjava.mycommunity.community.Community;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface CommunityRepository extends JpaRepository<Community, Long> {

    @Query("""
        SELECT c FROM Community c
        WHERE c.id IN (
            SELECT cm.communityId
            FROM CommunityMembership cm
            WHERE cm.userId = :userId
            AND cm.status = 'APPROVED'
        )
        ORDER BY c.name
    """)
    List<Community> findCommunitiesByUserId(String userId);

    List<Community> findByIsPrivateFalse();

    boolean existsByOrganization_Id(Long organizationId);

}
