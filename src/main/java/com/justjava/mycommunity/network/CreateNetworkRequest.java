package com.justjava.mycommunity.network;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for creating or updating a Network.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateNetworkRequest {

    @NotBlank(message = "Network name is required")
    private String name;

    private String description;

    @NotNull(message = "Community ID is required")
    private Long communityId;
}

