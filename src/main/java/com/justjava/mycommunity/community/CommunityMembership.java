package com.justjava.mycommunity.community;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "community_id"})
})
public class CommunityMembership {

    @Id
    @GeneratedValue
    private Long id;

    private String userId;

    private Long communityId;

    @Enumerated(EnumType.STRING)
    private Role role; // ADMIN, MEMBER

    @Enumerated(EnumType.STRING)
    private MembershipStatus status; // PENDING, APPROVED
}