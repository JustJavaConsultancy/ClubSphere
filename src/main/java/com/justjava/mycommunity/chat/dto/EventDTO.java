package com.justjava.mycommunity.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EventDTO {
        @NotBlank
        private String title;
        @NotBlank
        private String description;
        @NotBlank
        private Long organizationId;

    //    private LocalDateTime startDate;
    //    private LocalDateTime endDate;


        private Long communityId;
        private Long chatGroupId;
}
