package com.justjava.mycommunity.chat.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.justjava.mycommunity.network.ChatGroup;
import com.justjava.mycommunity.organization.Organization;
import com.justjava.mycommunity.userManagement.UserGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(nullable = false, unique = true)
    private String userId;

    private String firstName;

    private String lastName;

    private String email;

    private Boolean status;

    @Transient
    private Boolean online;

    private Boolean isOrgAdmin = false;

    private String level = "1";

    private Boolean privacy = false;

    private String realm;

    @JsonManagedReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ORG_ID")
    private Organization organization;

    @ManyToMany(mappedBy = "users", fetch = FetchType.LAZY)
    private Set<ChatGroup> chatGroup = new HashSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "USER_USERGROUPS",
            joinColumns = @JoinColumn(name = "USER_ID"),
            inverseJoinColumns = @JoinColumn(name = "USERGROUP_ID"))
    private Set<UserGroup> userGroup = new HashSet<>();

    @ManyToMany(mappedBy = "members", fetch = FetchType.LAZY)
    private List<Conversation> conversations = new ArrayList<>();

    public String getFullName() {
        return firstName+" "+lastName;
    }

    public String getStatus() {
        return status?"Enabled":"Disabled";
    }

    public String getAvatar() {
        return String.valueOf(this.firstName.charAt(0));
    }

    public List<UserGroup> getUserGroupAsList() {
        return new ArrayList<>(userGroup);
    }

    // Add the missing getUserGroup method that AuthenticationManager expects
    public Set<UserGroup> getUserGroup() {
        return userGroup != null ? userGroup : new HashSet<>();
    }
}
