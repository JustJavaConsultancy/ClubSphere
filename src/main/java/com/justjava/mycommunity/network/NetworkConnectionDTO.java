package com.justjava.mycommunity.network;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for an accepted connection — used on the messages page.
 * Tells the frontend: "You're connected to [connectedUserName] via [networkName]".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkConnectionDTO {

    private Long connectionId;

    private Long networkId;
    private String networkName;

    private Long communityId;
    private String communityName;

    private String connectedUserId;
    private String connectedUserName;

    private Long conversationId;
}


