package com.justjava.mycommunity.chat.repository;

import com.justjava.mycommunity.event.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {
    @Query("select p from Participant p where p.user.userId = ?1")
    List<Participant> findByUser_UserId(String userId);


}