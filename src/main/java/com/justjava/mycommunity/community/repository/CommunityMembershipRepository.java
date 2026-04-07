package com.justjava.mycommunity.community.repository;

import com.justjava.mycommunity.community.CommunityMembership;
import com.justjava.mycommunity.community.MembershipStatus;
import com.justjava.mycommunity.community.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CommunityMembershipRepository extends JpaRepository<CommunityMembership, Long> {

    Optional<CommunityMembership> findByUserIdAndCommunityId(String userId, Long communityId);

    boolean existsByUserIdAndCommunityId(String userId, Long communityId);

    boolean existsByUserIdAndCommunityIdAndStatus(String userId, Long communityId, MembershipStatus status);

    boolean existsByUserIdAndCommunityIdAndRoleAndStatus(String userId, Long communityId, Role role, MembershipStatus status);

    List<CommunityMembership> findByCommunityIdAndStatus(Long communityId, MembershipStatus status);

    List<CommunityMembership> findByUserIdAndStatus(String userId, MembershipStatus status);

    List<CommunityMembership> findByUserId(String userId);

    List<CommunityMembership> findByCommunityId(Long communityId);

    @Query("""
        SELECT cm.userId
        FROM CommunityMembership cm
        WHERE cm.communityId = :communityId
        AND cm.role = 'ADMIN'
        AND cm.status = 'APPROVED'
    """)
    List<String> findAdminsByCommunityId(Long communityId);

    default Optional<String> findFirstAdmin(Long communityId) {
        List<String> admins = findAdminsByCommunityId(communityId);
        return admins.isEmpty() ? Optional.empty() : Optional.of(admins.get(0));
    }
}