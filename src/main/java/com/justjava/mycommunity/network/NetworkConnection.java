package com.justjava.mycommunity.network;

import com.justjava.mycommunity.chat.entity.AuditableEntity;
import com.justjava.mycommunity.chat.entity.Conversation;
import com.justjava.mycommunity.chat.entity.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An accepted connection between two users within a Network.
 * Created when a NetworkConnectionRequest is ACCEPTED.
 * Links the two users AND their Conversation, so the messages page
 * can display which network a contact belongs to.
 *
 * Relationships:
 *   - ManyToOne → Network       (the network where they connected)
 *   - ManyToOne → User (user1)  (one side of the connection)
 *   - ManyToOne → User (user2)  (other side of the connection)
 *   - OneToOne  → Conversation  (their private chat)
 *
 * Unique constraint: only one connection per user-pair per network.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "network_connections",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"network_id", "user1_id", "user2_id"})
        }
)
public class NetworkConnection extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "network_id", nullable = false)
    private Network network;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user1_id", nullable = false)
    private User user1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user2_id", nullable = false)
    private User user2;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    /**
     * Check if a given userId is part of this connection.
     */
    public boolean involves(String userId) {
        return user1.getUserId().equals(userId) || user2.getUserId().equals(userId);
    }

    /**
     * Get the OTHER user in the connection (not the one passed in).
     */
    public User getOtherUser(String userId) {
        return user1.getUserId().equals(userId) ? user2 : user1;
    }
}

