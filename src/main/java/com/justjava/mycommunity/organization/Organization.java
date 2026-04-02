package com.justjava.mycommunity.organization;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.justjava.mycommunity.chat.entity.AuditableEntity;
import com.justjava.mycommunity.chat.entity.User;
import com.justjava.mycommunity.community.Community;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.Setter;
import net.minidev.json.annotate.JsonIgnore;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@Entity
@JsonIgnoreProperties({"organizationAdmin"})
public class Organization extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String description;

    @OneToOne(cascade =  CascadeType.REMOVE)
    private Channel channel;

    @OneToOne(cascade =  CascadeType.REMOVE)
    private SupportChannel supportChannel;

    @OneToOne(cascade =  CascadeType.REMOVE)
    private TownHall townHall;

    @JsonManagedReference
    @OneToMany(mappedBy = "organization", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private List<Community>  communities;

    @JsonBackReference
    @OneToMany(mappedBy = "organization", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<User> users;

    @JsonIgnore
    public Set<User> getOrganizationAdmins() {
        return users == null ? Set.of() :
                users.stream().filter(User::getIsOrgAdmin).collect(Collectors.toSet());
    }
}
