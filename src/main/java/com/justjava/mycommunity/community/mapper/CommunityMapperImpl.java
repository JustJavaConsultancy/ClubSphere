package com.justjava.mycommunity.community.mapper;

import com.justjava.mycommunity.community.Community;
import com.justjava.mycommunity.community.dto.CommunityDTO;
import org.springframework.stereotype.Component;

@Component
public class CommunityMapperImpl {

    public CommunityDTO toDto(Community community) {
        if (community == null) {
            return null;
        }

        CommunityDTO dto = new CommunityDTO();
        dto.setId(community.getId());
        dto.setName(community.getName());
        dto.setDescription(community.getDescription());
        dto.setCommunityPrivacy(community.getCommunityPrivacy());
        dto.setIsPrivate(community.isPrivate());
        dto.setChannelId(community.getChannel() != null ? community.getChannel().getId() : null);
        dto.setTownHallId(community.getTownHall() != null ? community.getTownHall().getId() : null);
        dto.setOrganizationId(community.getOrganization() != null ? community.getOrganization().getId() : null);
        return dto;
    }

    public Community toEntity(CommunityDTO dto) {
        if (dto == null) {
            return null;
        }

        Community community = new Community();
        community.setId(dto.getId());
        community.setName(dto.getName());
        community.setDescription(dto.getDescription());
        community.setCommunityPrivacy(dto.getCommunityPrivacy());
        community.setPrivate(dto.getIsPrivate() != null ? dto.getIsPrivate() : false);
        return community;
    }

    public void updateEntity(Community community, CommunityDTO dto) {
        if (community == null || dto == null) {
            return;
        }

        community.setName(dto.getName());
        community.setDescription(dto.getDescription());
        community.setCommunityPrivacy(dto.getCommunityPrivacy());
        if (dto.getIsPrivate() != null) {
            community.setPrivate(dto.getIsPrivate());
        }
    }
}
