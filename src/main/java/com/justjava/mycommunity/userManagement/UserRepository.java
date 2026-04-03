package com.justjava.mycommunity.userManagement;

import com.justjava.mycommunity.chat.entity.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUserId(String userId);

    User findByUserId(String userId);

    Set<User> findAllByUserIdIn(Collection<String> userIds);

    @Override
    default List<User> findAll() {
        return findAll(Sort.by(Sort.Direction.ASC, "firstName"));
    }

    User findByEmail(String email);

    @Query("SELECT u FROM User u JOIN u.communities c WHERE c.id = :communityId")
    List<User> findByCommunityId(@Param("communityId") Long communityId);

    List<User> findAllByUserIdNotIn(Collection<String> userIds);

    @Query("select u from User u join u.userGroup g where g.groupName = 'admin'")
    List<User> findAllAdminUsers();

    List<User> findAllByRealm(String realm);
}
