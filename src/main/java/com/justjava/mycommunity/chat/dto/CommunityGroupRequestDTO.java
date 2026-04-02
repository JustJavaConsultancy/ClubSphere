package com.justjava.mycommunity.chat.dto;

import lombok.Data;

@Data
public class CommunityGroupRequestDTO {

    private Long id;

    private String fullName;

    private String status;

    private String communityName;

    public void setStatus(String status) {
        if (status.equals("P"))
            this.status = "PENDING";
        else if (status.equals("A"))
            this.status = "APPROVED";
    }
}
