package com.justjava.mycommunity.community.dto;

import lombok.Data;

@Data
public class CommunityDTO {

    private Long id;

    private String name;

    private String description;

    private Boolean communityPrivacy;

    private Boolean isPrivate;

    private Long channelId;

    private Long townHallId;

    private Long organizationId;

    private String adminUserId;
}