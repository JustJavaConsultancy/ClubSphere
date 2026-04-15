package com.justjava.mycommunity.network;

import com.justjava.mycommunity.chat.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NetworkConnectionRepository extends JpaRepository<NetworkConnection, Long> {

    /** Are these two users already connected in this network? (either direction) */
    @Query("""
        SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
        FROM NetworkConnection c
        WHERE c.network.id = :networkId
          AND (
                (c.user1.userId = :userId1 AND c.user2.userId = :userId2)
             OR (c.user1.userId = :userId2 AND c.user2.userId = :userId1)
          )
    """)
    boolean existsBetween(
            @Param("networkId") Long networkId,
            @Param("userId1") String userId1,
            @Param("userId2") String userId2);

    /** All connections for a user across all networks. Used to populate the messages page. */
    @Query("""
        SELECT c FROM NetworkConnection c
        WHERE c.user1.userId = :userId OR c.user2.userId = :userId
    """)
    List<NetworkConnection> findAllByUser(@Param("userId") String userId);

    /** All connections for a user within a specific network. */
    @Query("""
        SELECT c FROM NetworkConnection c
        WHERE c.network.id = :networkId
          AND (c.user1.userId = :userId OR c.user2.userId = :userId)
    """)
    List<NetworkConnection> findByNetworkAndUser(
            @Param("networkId") Long networkId,
            @Param("userId") String userId);

    /** Look up the network connection by conversation ID — used on the messages page. */
    Optional<NetworkConnection> findByConversation_Id(Long conversationId);

    /** Look up the network connection by conversation — used during mapping. */
    Optional<NetworkConnection> findByConversation(Conversation conversation);
}

