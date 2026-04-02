package com.justjava.mycommunity.userManagement;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGroupRepository extends JpaRepository<UserGroup, Long> {
    UserGroup findByGroupNameIgnoreCase(String groupName);

    UserGroup findByGroupId(String groupId);

    void deleteByGroupId(String groupId);
}