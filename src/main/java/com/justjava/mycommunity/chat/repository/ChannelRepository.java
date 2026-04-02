package com.justjava.mycommunity.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.justjava.mycommunity.organization.Channel;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
}