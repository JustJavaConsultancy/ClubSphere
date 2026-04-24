package com.justjava.mycommunity.community.repository;

import com.justjava.mycommunity.community.CommunityGroup;
import com.justjava.mycommunity.community.CommunityGroupMembership;
import com.justjava.mycommunity.community.CommunityMembership;
import com.justjava.mycommunity.community.MembershipStatus;
import com.justjava.mycommunity.community.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommunityGroupMembershipRepository extends JpaRepository<CommunityGroupMembership, Long> {

    List<CommunityGroupMembership> findByCommunityGroup_Id(Long communityGroupId);

    Optional<CommunityGroupMembership> findByCommunityGroupAndCommunityMembership(
            CommunityGroup communityGroup,
            CommunityMembership communityMembership
    );

    long countByCommunityGroup_Id(Long communityGroupId);

    void deleteByCommunityGroup_Id(Long communityGroupId);

    @Query("""
        SELECT DISTINCT gm.communityGroup
        FROM CommunityGroupMembership gm
        WHERE gm.communityMembership.userId = :userId
        AND gm.communityMembership.status = :status
    """)
    List<CommunityGroup> findGroupsByUserIdAndStatus(@Param("userId") String userId, @Param("status") MembershipStatus status);

    @Query("""
        SELECT CASE WHEN COUNT(gm) > 0 THEN true ELSE false END
        FROM CommunityGroupMembership gm
        WHERE gm.communityMembership.userId = :userId
        AND gm.communityGroup.id = :groupId
        AND (gm.role = 'ADMIN' OR gm.role = 'CREATOR')
    """)
    boolean isUserGroupAdmin(@Param("userId") String userId, @Param("groupId") Long groupId);
}
