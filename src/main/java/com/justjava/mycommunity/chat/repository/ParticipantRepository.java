package com.justjava.mycommunity.chat.repository;

import com.justjava.mycommunity.event.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {
    @Query("select p from Participant p where p.user.userId = ?1")
    List<Participant> findByUser_UserId(String userId);

    @Modifying(flushAutomatically = false, clearAutomatically = false)
    @Query(value = "DELETE FROM event_participants WHERE participants_id IN (SELECT id FROM participant WHERE user_id = ?1)", nativeQuery = true)
    void deleteEventParticipantsByUserId(Long userId);

    @Modifying(flushAutomatically = false, clearAutomatically = false)
    @Query("delete from Participant p where p.user.id = ?1")
    void deleteAllByUserId(Long userId);
}