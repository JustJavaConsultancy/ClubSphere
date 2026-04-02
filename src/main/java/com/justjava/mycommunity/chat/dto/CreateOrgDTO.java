package com.justjava.mycommunity.chat.dto;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class CreateOrgDTO {

    private String orgName;
    private String orgDescription;
    private String channelName;
    private String channelDescription;
    private String supportChannelName;
    private String supportChannelDescription;
    private String townHallName;
    private String townHallDescription;
    private String adminEmail;

}
