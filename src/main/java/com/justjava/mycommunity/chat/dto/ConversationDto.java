package com.justjava.mycommunity.chat.dto;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import lombok.Data;

import java.io.Serializable;
import java.util.List;


@Data
public class ConversationDto implements Serializable {
    Long id;
    String title;
    Boolean group;
    String receiverId;
    String receiverName;
    String createdAt;

    @Embedded
    List<MessageDto> messages;

        @Data
        @Embeddable
    public static class MessageDto implements Serializable {
        String content;
        String attachmentUrl;
        Boolean sender;
        String sentAt;
    }
}