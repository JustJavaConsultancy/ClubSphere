package com.justjava.mycommunity.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.justjava.mycommunity.chat.entity.Message;

public interface MessageRepository extends JpaRepository<Message, Long> {
}