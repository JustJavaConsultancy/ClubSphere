package com.justjava.mycommunity.network;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NetworkRepository extends JpaRepository<Network, Long> {

    /** All networks in a given community. */
    List<Network> findByCommunity_Id(Long communityId);

    /** All networks created by a specific user. */
    List<Network> findByCreatedBy_UserId(String userId);

    /** All networks in a community that a specific user is a member of. */
    @Query("""
        SELECT n FROM Network n
        JOIN n.memberships m
        WHERE n.community.id = :communityId
          AND m.user.userId = :userId
    """)
    List<Network> findByMembershipInCommunity(
            @Param("userId") String userId,
            @Param("communityId") Long communityId
    );

    /** All networks a user belongs to, across all communities. */
    @Query("""
        SELECT n FROM Network n
        JOIN n.memberships m
        WHERE m.user.userId = :userId
    """)
    List<Network> findAllByMember(@Param("userId") String userId);

    /** Check if a network with this name already exists in the community. */
    boolean existsByNameAndCommunity_Id(String name, Long communityId);
}

