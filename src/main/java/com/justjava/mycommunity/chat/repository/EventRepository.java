package com.justjava.mycommunity.chat.repository;

import com.justjava.mycommunity.event.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import com.justjava.mycommunity.event.Event;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("SELECT DISTINCT e FROM Event e JOIN e.participants p WHERE p.user.userId = ?1")
    List<Event> findUserEvents(String userId);

    List<Event> findAllByParticipants_(List<Participant> participants);

    List<Event> findByParticipants_User_UserId(String userId);

    List<Event> findAllByApproved(Boolean approved);

    List<Event> findAllByCommunity_Id(Long communityId);

    List<Event> findAllByChatGroup_Id(Long chatGroupId);

    List<Event> findAllByCommunity_IdAndEventType(Long communityId, String eventType);

    List<Event> findAllByEventType(String eventType);
}