package com.justjava.mycommunity.chat.repository;

import com.justjava.mycommunity.community.CommunityInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityInvitationRepository extends JpaRepository<CommunityInvitation, Long> {

    /**
     * Find invitation by user ID and mycommunity ID
     */
    @Query("SELECT ci FROM CommunityInvitation ci WHERE ci.user.userId = :userId AND ci.community.id = :communityId")
    CommunityInvitation findByUser_UserIdAndCommunity_Id(@Param("userId") String userId, @Param("communityId") Long communityId);

    /**
     * Find invitations by user ID and status
     */
    @Query("SELECT ci FROM CommunityInvitation ci WHERE ci.user.userId = :userId AND ci.status = :status")
    List<CommunityInvitation> findByUser_UserIdAndStatus(@Param("userId") String userId, @Param("status") String status);

    /**
     * Find invitation by ID
     */
    Optional<CommunityInvitation> findById(Long id);

    /**
     * Find all invitations by status
     */
    List<CommunityInvitation> findByStatus(String status);

    /**
     * Find all invitations for a specific mycommunity
     */
    @Query("SELECT ci FROM CommunityInvitation ci WHERE ci.community.id = :communityId")
    List<CommunityInvitation> findByCommunityId(@Param("communityId") Long communityId);

    /**
     * Find all invitations for a specific user
     */
    @Query("SELECT ci FROM CommunityInvitation ci WHERE ci.user.userId = :userId")
    List<CommunityInvitation> findByUserId(@Param("userId") String userId);

    /**
     * Delete invitation by user ID and mycommunity ID
     */
    @Query("DELETE FROM CommunityInvitation ci WHERE ci.user.userId = :userId AND ci.community.id = :communityId")
    void deleteByUser_UserIdAndCommunity_Id(@Param("userId") String userId, @Param("communityId") Long communityId);
}
