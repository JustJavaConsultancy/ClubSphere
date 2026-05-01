package com.justjava.mycommunity.support;

import com.justjava.mycommunity.chat.dto.ConversationDto;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class TicketDTO {
    private Long id;
    private String subject;

    private String description;
    private String priority;
    private String status;
    private String attachmentUrl;
    private OffsetDateTime dateCreated;
    private OffsetDateTime lastUpdated;
    private String userId;
    private String agentUserId;
    private Long communityId;
    private Long communityGroupId;
    private String communityName;
    private String groupName;
    private ConversationDto conversation;
}
