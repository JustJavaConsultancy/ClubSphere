package com.justjava.mycommunity.chat.dto;

import com.justjava.mycommunity.organization.Channel;
import com.justjava.mycommunity.organization.Organization;
import com.justjava.mycommunity.organization.SupportChannel;
import com.justjava.mycommunity.organization.TownHall;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Value;

import java.io.Serializable;

/**
 * DTO for {@link Organization}
 */
@Value
@Data
@AllArgsConstructor
public class OrganizationDto implements Serializable {
    Long id;
    String name;
    String description;
    ChannelDto channel;
    SupportChannelDto supportChannel;
    TownHallDto townHall;


    /**
     * DTO for {@link Channel}
     */
    @Value
    @Data
    @AllArgsConstructor
    public static class ChannelDto implements Serializable {
        Long id;
        String name;
        String description;
    }

    /**
     * DTO for {@link SupportChannel}
     */
    @Value
    @Data
    @AllArgsConstructor
    public static class SupportChannelDto implements Serializable {
        Long id;
        String name;
        String description;
    }

    /**
     * DTO for {@link TownHall}
     */
    @Value
    @Data
    @AllArgsConstructor
    public static class TownHallDto implements Serializable {
        Long id;
        String name;
        String description;
    }
}