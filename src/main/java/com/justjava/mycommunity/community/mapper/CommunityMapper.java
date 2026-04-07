package com.justjava.mycommunity.community.mapper;

import com.justjava.mycommunity.community.Community;
import com.justjava.mycommunity.community.dto.CommunityDTO;
import com.justjava.mycommunity.community.repository.CommunityMembershipRepository;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CommunityMapper {

    @Mapping(target = "isPrivate", source = "private")
    @Mapping(target = "channelId", source = "channel.id")
    @Mapping(target = "townHallId", source = "townHall.id")
    @Mapping(target = "organizationId", source = "organization.id")
    @Mapping(target = "adminUserId", ignore = true)
    CommunityDTO toDto(Community community, @Context CommunityMembershipRepository communityMembershipRepository);

    @AfterMapping
    default void setAdminUserId(@MappingTarget CommunityDTO dto, Community community, @Context CommunityMembershipRepository communityMembershipRepository) {
        if (community != null && community.getId() != null) {
            dto.setAdminUserId(communityMembershipRepository.findFirstAdmin(community.getId()).orElse(null));
        }
    }

    @Mapping(target = "private", source = "isPrivate", defaultValue = "false")
    @Mapping(target = "channel", ignore = true)
    @Mapping(target = "townHall", ignore = true)
    @Mapping(target = "organization", ignore = true)
    Community toEntity(CommunityDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "private", source = "isPrivate")
    @Mapping(target = "channel", ignore = true)
    @Mapping(target = "townHall", ignore = true)
    @Mapping(target = "organization", ignore = true)
    void updateEntity(@MappingTarget Community community, CommunityDTO dto);
}
