package com.justjava.mycommunity.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.justjava.mycommunity.chat.entity.Conversation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    @Query("""
  SELECT c FROM Conversation c
  JOIN c.members m
  WHERE m.userId IN :userIds
  GROUP BY c.id
  HAVING COUNT(m.userId) = :size AND COUNT(m.userId) = SIZE(c.members)
""")
    Optional<Conversation> findConversationByExactUserIds(List<String> userIds, int size);

    @Query("select c from Conversation c join c.members m where m.userId in ?1 and c.title = 'Support' " +
            "group by c.id having count(m.userId) = ?2 and count(m.userId) = size(c.members) ")
    Optional<Conversation> findSupportConversationByExactUserIds(Collection<String> userIds, int size);

    List<Conversation> findAllByMembers_UserId(String members_userId);
}