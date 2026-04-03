package com.justjava.mycommunity.userManagement;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.justjava.mycommunity.chat.entity.User;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"realm", "groupName"}))
public class UserGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String  groupId;
    private String groupName;
    private String description;
    @Builder.Default
    private Integer members = 0;

    private String realm;

    @JsonIgnore
    @ManyToMany(mappedBy = "userGroup")
    Set<User> users = new HashSet<>();

    public String getGroupName() {
        if (groupName == null || groupName.isEmpty())
            return groupName;
        groupName = groupName.toLowerCase();
        return groupName.substring(0, 1).toUpperCase() + groupName.substring(1);
    }

    public String getDescription() {
        if (description == null || description.isEmpty())
            return "No description provided";
        return description;
    }

    public Integer getMembers() {
        return members!=null?members:0;
    }

    public List<User> getUsersAsList() {
        return new ArrayList<>(users);
    }
}
