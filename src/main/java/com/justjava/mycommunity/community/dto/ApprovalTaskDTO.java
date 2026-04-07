package com.justjava.mycommunity.community.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ApprovalTaskDTO {

    private String taskId;
    private String taskName;

    private String userId;
    private Long communityId;
    private String lastName;
    private String firstName;

    // 🔥 NEW FIELDS
    private String communityName;
    private String communityDescription;
    private boolean isPrivate;
}