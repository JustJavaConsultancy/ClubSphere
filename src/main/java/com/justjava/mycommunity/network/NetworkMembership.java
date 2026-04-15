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
 * Represents a user's membership in a Network.
 *
 * Relationships:
 *   - ManyToOne → Network  (which network the user belongs to)
 *   - ManyToOne → User     (which user)
 *
 * The unique constraint ensures a user can only have ONE membership row per network.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "network_memberships",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"network_id", "user_id"})
        }
)
public class NetworkMembership extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "network_id", nullable = false)
    private Network network;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NetworkRole role = NetworkRole.MEMBER;

    // ─── Factory helpers ────────────────────────────────────────────

    public static NetworkMembership of(Network network, User user, NetworkRole role) {
        NetworkMembership m = new NetworkMembership();
        m.setNetwork(network);
        m.setUser(user);
        m.setRole(role);
        return m;
    }
}

