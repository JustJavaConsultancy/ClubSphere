package com.justjava.mycommunity.chat.dto;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class CreateCommunityVO {
    private String communityName;
    private String communityDescription;
    private String channelName;
    private String channelDescription;
    private String townHallName;
    private String townHallDescription;
    private String userEmail;
    private String userId;
    private Boolean isPrivate;

    // Custom getter method for isPrivate
    public Boolean getIsPrivate() {
        return this.isPrivate;
    }
}
