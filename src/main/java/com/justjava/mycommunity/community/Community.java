package com.justjava.mycommunity.community;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.justjava.mycommunity.chat.entity.AuditableEntity;
import com.justjava.mycommunity.organization.Channel;
import com.justjava.mycommunity.organization.Organization;
import com.justjava.mycommunity.organization.TownHall;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@ToString(exclude = {"communityGroups", "organization", "channel", "townHall"})
@Entity
public class Community extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String description;

    private Boolean communityPrivacy = false;

    private Boolean isPrivate = false;

    @OneToOne
    private Channel channel;

    @OneToOne
    private TownHall townHall;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ORG_ID")
    private Organization organization;

    @JsonIgnore
    @OneToMany(mappedBy = "community", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CommunityGroup> communityGroups = new HashSet<>();

    public void addCommunityGroup(CommunityGroup communityGroup) {
        communityGroups.add(communityGroup);
        communityGroup.setCommunity(this);
    }

    public void removeCommunityGroup(CommunityGroup communityGroup) {
        communityGroups.remove(communityGroup);
        communityGroup.setCommunity(null);
    }

    // Custom methods for isPrivate field
    public Boolean isPrivate() {
        return this.isPrivate != null ? this.isPrivate : false;
    }

    public void setPrivate(Boolean isPrivate) {
        this.isPrivate = isPrivate;
    }
}
