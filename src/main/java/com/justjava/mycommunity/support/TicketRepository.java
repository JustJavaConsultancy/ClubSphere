package com.justjava.mycommunity.support;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByUserId(String userId);

    List<Ticket> findByUserIdOrderByLastUpdatedDesc(String userId);

    List<Ticket> findByAgentUserId(String agentUserId);

    List<Ticket> findByAgentUserIdOrderByLastUpdatedDesc(String agentUserId);


    List<Ticket> findByAgentUserIdIsNull();

    List<Ticket> findByAgentUserIdIsNullOrderByLastUpdatedDesc();
}