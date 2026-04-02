package com.justjava.mycommunity.chat.dto;

import lombok.Data;

@Data
public class CreateChatDTO {
    private Long id; // Add ID field for database operations
    private String groupName;
    private String groupDescription;
    private Long communityId;
    private String channelName;
    private String channelDescription;
    private String townHallName;
    private String townHallDescription;
    private Integer memberCount;
}
