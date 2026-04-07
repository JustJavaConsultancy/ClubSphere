package com.justjava.mycommunity.community;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.justjava.mycommunity.chat.entity.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@ToString(exclude = {"memberships", "community"})
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class CommunityGroup extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String description;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "COMMUNITY_ID")
    private Community community;

    @JsonIgnore
    @OneToMany(mappedBy = "communityGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CommunityGroupMembership> memberships = new HashSet<>();

    public Integer getUserCount() {
        return memberships != null ? memberships.size() : 0;
    }
}
