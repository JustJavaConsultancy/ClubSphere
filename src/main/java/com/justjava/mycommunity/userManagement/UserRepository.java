package com.justjava.mycommunity.userManagement;

import com.justjava.mycommunity.chat.entity.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

    // Duplicate-email tolerant lookup — some sync paths can produce two rows
    // for the same address. Callers that only have the email as a lookup key
    // want *any* match rather than a NonUniqueResultException.
    User findFirstByEmailOrderByIdAsc(String email);

    @Query("""
        SELECT u
        FROM User u
        WHERE u.userId IN (
            SELECT cm.userId
            FROM CommunityMembership cm
            WHERE cm.communityId = :communityId
            AND cm.status = 'APPROVED'
        )
    """)
    List<User> findByCommunityId(@Param("communityId") Long communityId);

    List<User> findAllByUserIdNotIn(Collection<String> userIds);

    @Query("select u from User u join u.userGroup g where g.groupName = 'admin'")
    List<User> findAllAdminUsers();

    List<User> findAllByRealm(String realm);

    // ✅ ADD THIS
    List<User> findByUserIdIn(List<String> userIds);
}
