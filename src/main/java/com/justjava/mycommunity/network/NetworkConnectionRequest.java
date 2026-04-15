package com.justjava.mycommunity.network;

import com.justjava.mycommunity.chat.entity.AuditableEntity;
import com.justjava.mycommunity.chat.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A request from one network member to connect with another.
 * Once ACCEPTED, a Conversation is created and tracked via NetworkConnection.
 *
 * Relationships:
 *   - ManyToOne → Network   (the network context in which the connection is made)
 *   - ManyToOne → User      (requester — who sent it)
 *   - ManyToOne → User      (receiver — who must approve it)
 *
 * Unique constraint: only one active request per requester→receiver pair per network.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "network_connection_requests",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"network_id", "requester_id", "receiver_id"})
        }
)
public class NetworkConnectionRequest extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "network_id", nullable = false)
    private Network network;

    /** The user who sent the connection request. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    /** The user who needs to approve the request. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectionStatus status = ConnectionStatus.PENDING;

    public enum ConnectionStatus {
        PENDING,
        ACCEPTED,
        REJECTED
    }
}

