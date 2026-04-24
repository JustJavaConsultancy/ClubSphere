package com.justjava.mycommunity.community;

import com.justjava.mycommunity.chat.entity.AuditableEntity;
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
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(
        name = "community_group_membership",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"community_group_id", "community_membership_id"})
        }
)
public class CommunityGroupMembership extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_group_id", nullable = false)
    private CommunityGroup communityGroup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_membership_id", nullable = false)
    private CommunityMembership communityMembership;

    @Enumerated(EnumType.STRING)
    private Role role; // ADMIN, MEMBER
}
