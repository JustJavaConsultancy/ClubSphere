package com.justjava.mycommunity.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.justjava.mycommunity.organization.TownHall;

public interface TownHallRepository extends JpaRepository<TownHall, Long> {
}