package com.justjava.mycommunity.network;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NetworkConnectionRequestRepository extends JpaRepository<NetworkConnectionRequest, Long> {

    /** Find a specific request. */
    Optional<NetworkConnectionRequest> findByNetwork_IdAndRequester_UserIdAndReceiver_UserId(
            Long networkId, String requesterId, String receiverId);

    /** Does a pending request already exist between these two users in this network? (either direction) */
    @Query("""
        SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
        FROM NetworkConnectionRequest r
        WHERE r.network.id = :networkId
          AND r.status = 'PENDING'
          AND (
                (r.requester.userId = :userId1 AND r.receiver.userId = :userId2)
             OR (r.requester.userId = :userId2 AND r.receiver.userId = :userId1)
          )
    """)
    boolean existsPendingBetween(
            @Param("networkId") Long networkId,
            @Param("userId1") String userId1,
            @Param("userId2") String userId2);

    /** All pending requests where this user is the receiver (incoming invitations). */
    List<NetworkConnectionRequest> findByReceiver_UserIdAndStatus(
            String receiverUserId, NetworkConnectionRequest.ConnectionStatus status);

    /** All pending requests where this user is the receiver, in a specific network. */
    List<NetworkConnectionRequest> findByNetwork_IdAndReceiver_UserIdAndStatus(
            Long networkId, String receiverUserId, NetworkConnectionRequest.ConnectionStatus status);
}

