package com.justjava.mycommunity.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.justjava.mycommunity.organization.SupportChannel;

public interface SupportChannelRepository extends JpaRepository<SupportChannel, Long> {
}