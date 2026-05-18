package com.justjava.mycommunity.organization;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.dto.CreateCommunityVO;
import com.justjava.mycommunity.chat.dto.CreateOrgDTO;
import com.justjava.mycommunity.chat.entity.User;
import com.justjava.mycommunity.chat.repository.CommunityRepository;
import com.justjava.mycommunity.chat.repository.OrganizationRepository;
import com.justjava.mycommunity.chat.repository.SupportChannelRepository;
import com.justjava.mycommunity.community.CommunityService;
import com.justjava.mycommunity.userManagement.UserRepository;
import com.justjava.mycommunity.userManagement.UserDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.justjava.mycommunity.util.MappingUtils.mapUsersToDTO;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final SupportChannelRepository supportChannelRepository;
    private final CommunityRepository communityRepository;
    private final CommunityService communityService;
    private final TownHallChannelService townHallChannelService;

    public Organization createDefault(){
        Optional<Organization> optionalOrganization = organizationRepository.findByDescription("Default");
        Organization organization = new Organization();
        if(optionalOrganization.isPresent())
            organization= optionalOrganization.get();
        else {
            organization.setDescription("Default");
            organization.setName("Default Organization");
            organization=organizationRepository.save(organization);
        }
        return organization;

    }

    @Transactional
    public Organization createOrganization(CreateOrgDTO dto){
        User user;
        if (dto.getAdminEmail() == null || dto.getAdminEmail().isEmpty()) {
            String userId = (String) authenticationManager.get("sub");
            if (userId == null) {
                // Fallback when no authenticated user is present (e.g., during startup)
                user = userRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new EntityNotFoundException("No users found to assign as organization admin"));
            } else {
                user = userRepository.findByUserId(userId);
            }
            if (user == null) {
                throw new EntityNotFoundException("User not found with ID: " + userId);
            }
        }else {
            user = userRepository.findByEmail(dto.getAdminEmail());
        }
        List<User> adminUsers = userRepository.findAllAdminUsers();
        List<User> users = new ArrayList<>();
        Channel channel = townHallChannelService.createChannel(dto.getChannelName(), dto.getChannelDescription());

        TownHall townHall = townHallChannelService.createTownHall(dto.getTownHallName(), dto.getTownHallDescription());

        SupportChannel supportChannel = new SupportChannel();
        supportChannel.setName(dto.getSupportChannelName());
        supportChannel.setDescription(dto.getSupportChannelDescription());
        supportChannel = supportChannelRepository.save(supportChannel);

        Organization organization = new Organization();
        organization.setName(dto.getOrgName());
        organization.setDescription(dto.getOrgDescription());
        organization.setChannel(channel);
        organization.setTownHall(townHall);
        organization.setSupportChannel(supportChannel);
        organization = organizationRepository.save(organization);

        user.setOrganization(organization);
        user.setIsOrgAdmin(true);
        for (User u : adminUsers){
            u.setOrganization(organization);
            u.setIsOrgAdmin(true);
            users.add(u);
        }
        users.add(user);
        userRepository.saveAll(users);
        return organization;
    }

    @Transactional
    public void createDefaultOrgAndCommunity (){
        User user = userRepository.findByEmail("idea@gmail.com");
        if (organizationRepository.count() < 1) {
            createOrganization(CreateOrgDTO.builder()
                    .orgName("Default Organization")
                    .orgDescription("Default Description")
                    .channelName("Default Channel")
                    .channelDescription("Default Channel Description")
                    .supportChannelName("Default Support Channel")
                    .supportChannelDescription("Default Support Channel Description")
                    .townHallName("Default Town Hall")
                    .townHallDescription("Default Town Hall Description")
                    .adminEmail("idea@gmail.com")
                    .build());
        }
        if (!communityRepository.existsByOrganization_Id(user.getOrganization().getId())) {
            communityService.createCommunity(CreateCommunityVO.builder()
                    .communityName("Default Club")
                    .communityDescription("Default Description")
                    .channelName("Default Channel Description")
                    .channelDescription("Default Channel Description")
                    .townHallName("Default Town Hall Name")
                    .townHallDescription("Default Town Hall Description")
                    .userEmail("idea@gmail.com")
                    .build());
            System.out.println("Done creating Default Organization");
        }
    }

    public void addUserToOrganization(String email, Long orgId){
        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new EntityNotFoundException("Organization does not exist"));
        User user = userRepository.findByEmail(email);
        user.setOrganization(organization);
        userRepository.save(user);
    }

    public Object getOrgMembers(Long orgId) {
        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new EntityNotFoundException("Organization does not exist"));
        Set<User> users = organization.getUsers();
        return mapUsersToDTO(users.stream().toList());
    }

    public List<Organization> getOrganizations(){

        List<Organization> orgs =  organizationRepository.findAll();
//        for (Organization org : orgs) {
//            System.out.println(org.getOrganizationAdmin().getId());
//            org.setOrganizationAdmin(null);
//            org.setUsers(null);
//        }
        return orgs;
    }

    public List<UserDTO> getEligibleMeetingParticipants(Long communityId) {
        return communityService.getEligibleMeetingParticipants(communityId);
    }
}
