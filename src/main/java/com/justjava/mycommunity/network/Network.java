package com.justjava.mycommunity.network;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.justjava.mycommunity.chat.entity.AuditableEntity;
import com.justjava.mycommunity.chat.entity.User;
import com.justjava.mycommunity.community.Community;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * A Network is a shared space within a Community.
 * Multiple users can belong to the same Network.
 * Members can message each other, create posts, and comment on posts within the network.
 *
 * Relationships:
 *   - ManyToOne → Community  (every network lives inside one community)
 *   - ManyToOne → User       (the user who created / owns the network)
 *   - OneToMany → NetworkMembership (the roster of members + their roles)
 */
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"memberships", "community", "createdBy"})
@Entity
@Table(name = "networks")
public class Network extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** The community this network belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    /** The user who created / owns this network. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    /** All memberships (users) in this network. */
    @JsonIgnore
    @OneToMany(mappedBy = "network", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<NetworkMembership> memberships = new ArrayList<>();

    // ─── Convenience helpers ────────────────────────────────────────

    public int getMemberCount() {
        return memberships != null ? memberships.size() : 0;
    }

    public void addMembership(NetworkMembership membership) {
        memberships.add(membership);
        membership.setNetwork(this);
    }

    public void removeMembership(NetworkMembership membership) {
        memberships.remove(membership);
        membership.setNetwork(null);
    }
}

