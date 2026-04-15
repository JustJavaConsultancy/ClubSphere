package com.justjava.mycommunity.network;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for a pending connection request — shown to the receiver so they can accept/reject.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionRequestDTO {

    private Long id;

    private Long networkId;
    private String networkName;

    private String requesterUserId;
    private String requesterName;

    private String receiverUserId;
    private String receiverName;

    private String status;
}

