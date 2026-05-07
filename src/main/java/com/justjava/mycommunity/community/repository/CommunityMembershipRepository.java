package com.justjava.mycommunity.community.repository;

import com.justjava.mycommunity.community.CommunityMembership;
import com.justjava.mycommunity.community.MembershipStatus;
import com.justjava.mycommunity.community.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
        SELECT cm
        FROM CommunityMembership cm
        WHERE cm.communityId = :communityId
        AND (
            cm.status = 'APPROVED'
            OR (cm.status = 'SUSPENDED' AND cm.suspendedUntil IS NOT NULL AND cm.suspendedUntil <= CURRENT_TIMESTAMP)
        )
    """)
    List<CommunityMembership> findActiveByCommunityId(@Param("communityId") Long communityId);

    @Query("""
        SELECT cm
        FROM CommunityMembership cm
        WHERE cm.userId = :userId
        AND (
            cm.status = 'APPROVED'
            OR (cm.status = 'SUSPENDED' AND cm.suspendedUntil IS NOT NULL AND cm.suspendedUntil <= CURRENT_TIMESTAMP)
        )
    """)
    List<CommunityMembership> findActiveByUserId(@Param("userId") String userId);

    @Query("""
        SELECT CASE WHEN COUNT(cm) > 0 THEN true ELSE false END
        FROM CommunityMembership cm
        WHERE cm.userId = :userId
        AND cm.communityId = :communityId
        AND (
            cm.status = 'APPROVED'
            OR (cm.status = 'SUSPENDED' AND cm.suspendedUntil IS NOT NULL AND cm.suspendedUntil <= CURRENT_TIMESTAMP)
        )
    """)
    boolean existsActiveMembership(@Param("userId") String userId, @Param("communityId") Long communityId);

    @Query("""
        SELECT cm.userId
        FROM CommunityMembership cm
        WHERE cm.communityId = :communityId
        AND cm.role = 'ADMIN'
        AND (
            cm.status = 'APPROVED'
            OR (cm.status = 'SUSPENDED' AND cm.suspendedUntil IS NOT NULL AND cm.suspendedUntil <= CURRENT_TIMESTAMP)
        )
    """)
    List<String> findAdminsByCommunityId(Long communityId);

    @Query("""
        SELECT DISTINCT cm.communityId
        FROM CommunityMembership cm
        WHERE cm.userId = :userId
        AND (
            cm.status = 'APPROVED'
            OR (cm.status = 'SUSPENDED' AND cm.suspendedUntil IS NOT NULL AND cm.suspendedUntil <= CURRENT_TIMESTAMP)
        )
    """)
    List<Long> findApprovedCommunityIdsByUserId(String userId);

    @Query("""
        SELECT DISTINCT cm.userId
        FROM CommunityMembership cm
        WHERE (
            cm.status = 'APPROVED'
            OR (cm.status = 'SUSPENDED' AND cm.suspendedUntil IS NOT NULL AND cm.suspendedUntil <= CURRENT_TIMESTAMP)
        )
    """)
    List<String> findApprovedUserIds();

    boolean existsByUserIdAndStatus(String userId, MembershipStatus status);

    @Query("""
        SELECT CASE WHEN COUNT(cm) > 0 THEN true ELSE false END
        FROM CommunityMembership cm
        WHERE cm.userId = :userId
        AND cm.communityId = :communityId
        AND (
            cm.status = 'APPROVED'
            OR (cm.status = 'SUSPENDED' AND cm.suspendedUntil IS NOT NULL AND cm.suspendedUntil <= CURRENT_TIMESTAMP)
        )
        AND (cm.role = 'ADMIN' OR cm.role = 'CREATOR')
    """)
    boolean isUserCommunityAdmin(String userId, Long communityId);

    @Query("""
        SELECT CASE WHEN COUNT(cm) > 0 THEN true ELSE false END
        FROM CommunityMembership cm
        WHERE cm.userId = :userId
        AND (
            cm.status = 'APPROVED'
            OR (cm.status = 'SUSPENDED' AND cm.suspendedUntil IS NOT NULL AND cm.suspendedUntil <= CURRENT_TIMESTAMP)
        )
        AND (cm.role = 'ADMIN' OR cm.role = 'CREATOR')
    """)
    boolean isUserAdminOfAnyCommunity(String userId);

    @Query("""
        SELECT DISTINCT cm.communityId
        FROM CommunityMembership cm
        WHERE cm.userId = :userId
        AND (
            cm.status = 'APPROVED'
            OR (cm.status = 'SUSPENDED' AND cm.suspendedUntil IS NOT NULL AND cm.suspendedUntil <= CURRENT_TIMESTAMP)
        )
        AND (cm.role = 'ADMIN' OR cm.role = 'CREATOR')
    """)
    List<Long> findAdminCommunityIdsByUserId(String userId);

    @Query("""
        SELECT CASE WHEN COUNT(cm1) > 0 THEN true ELSE false END
        FROM CommunityMembership cm1, CommunityMembership cm2
        WHERE cm1.userId = :userId1
        AND cm2.userId = :userId2
        AND cm1.communityId = cm2.communityId
        AND (
            cm1.status = 'APPROVED'
            OR (cm1.status = 'SUSPENDED' AND cm1.suspendedUntil IS NOT NULL AND cm1.suspendedUntil <= CURRENT_TIMESTAMP)
        )
        AND (
            cm2.status = 'APPROVED'
            OR (cm2.status = 'SUSPENDED' AND cm2.suspendedUntil IS NOT NULL AND cm2.suspendedUntil <= CURRENT_TIMESTAMP)
        )
    """)
    boolean areUsersInSameCommunity(String userId1, String userId2);

    default Optional<String> findFirstAdmin(Long communityId) {
        List<String> admins = findAdminsByCommunityId(communityId);
        return admins.isEmpty() ? Optional.empty() : Optional.of(admins.get(0));
    }
}
