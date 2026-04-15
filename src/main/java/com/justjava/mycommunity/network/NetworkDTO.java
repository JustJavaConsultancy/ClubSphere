package com.justjava.mycommunity.network;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for transferring Network data to the frontend / API consumers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkDTO {

    private Long id;
    private String name;
    private String description;

    private Long communityId;
    private String communityName;

    private String createdByUserId;
    private String createdByName;

    private int memberCount;

    /** Role of the *current* user in this network (null if not a member). */
    private String currentUserRole;
}

