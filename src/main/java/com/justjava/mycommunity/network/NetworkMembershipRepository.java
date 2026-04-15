package com.justjava.mycommunity.network;

import com.justjava.mycommunity.chat.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NetworkMembershipRepository extends JpaRepository<NetworkMembership, Long> {

    /** Find a specific user's membership in a specific network. */
    Optional<NetworkMembership> findByNetwork_IdAndUser_UserId(Long networkId, String userId);

    /** Does this user already belong to this network? */
    boolean existsByNetwork_IdAndUser_UserId(Long networkId, String userId);

    /** All memberships for a given network. */
    List<NetworkMembership> findByNetwork_Id(Long networkId);

    /** Count members in a network. */
    long countByNetwork_Id(Long networkId);

    /** All User entities that are members of a given network. */
    @Query("""
        SELECT m.user FROM NetworkMembership m
        WHERE m.network.id = :networkId
    """)
    List<User> findUsersByNetworkId(@Param("networkId") Long networkId);

    /** All memberships for a user across all networks. */
    List<NetworkMembership> findByUser_UserId(String userId);

    /** Delete a user's membership in a network. */
    void deleteByNetwork_IdAndUser_UserId(Long networkId, String userId);
}

