package com.justjava.mycommunity.community;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.dto.CommunityRequestDTO;
import com.justjava.mycommunity.chat.dto.CreateCommunityVO;
import com.justjava.mycommunity.chat.entity.User;
import com.justjava.mycommunity.chat.repository.CommunityRepository;
import com.justjava.mycommunity.chat.repository.CommunityRequestRepository;
import com.justjava.mycommunity.chat.repository.CommunityInvitationRepository;
import com.justjava.mycommunity.chat.repository.OrganizationRepository;
import com.justjava.mycommunity.organization.Channel;
import com.justjava.mycommunity.organization.Organization;
import com.justjava.mycommunity.organization.TownHall;
import com.justjava.mycommunity.organization.TownHallChannelService;
import com.justjava.mycommunity.userManagement.UserRepository;
import com.justjava.mycommunity.userManagement.UserDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.justjava.mycommunity.util.MappingUtils.mapUsersToDTO;

@Service
@RequiredArgsConstructor
public class CommunityService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;
    private final TownHallChannelService townHallChannelService;
    private final CommunityRequestRepository communityRequestRepository;
    private final CommunityInvitationRepository communityInvitationRepository;
    private final OrganizationRepository organizationRepository;

    public Community createCommunity(CreateCommunityVO dto) {
        User user;
        if (dto.getUserEmail() == null || dto.getUserEmail().isEmpty()) {
            String userId = (String) authenticationManager.get("sub");
            if (userId == null) {
                // Fallback when no authenticated user is present (e.g., during startup)
                user = userRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new EntityNotFoundException("No users found to assign as mycommunity owner"));
            } else {
                user = userRepository.findByUserId(userId);
            }
            if (user == null) {
                throw new EntityNotFoundException("User not found with ID: " + userId);
            }
        }else {
            user = Optional.ofNullable(userRepository.findByEmail(dto.getUserEmail()))
                    .orElseThrow(() -> new EntityNotFoundException("User not found "));
        }

        Channel channel = townHallChannelService.createChannel(dto.getChannelName(), dto.getChannelDescription());

        TownHall townHall = townHallChannelService.createTownHall(dto.getTownHallName(), dto.getTownHallDescription());
        Organization organization = organizationRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new EntityNotFoundException("No organizations found to assign as mycommunity owner"));
        Community community = new Community();
        community.setName(dto.getCommunityName());
        community.setDescription(dto.getCommunityDescription());
        community.setOrganization(user.getOrganization() != null ? user.getOrganization() : organization);
        community.setChannel(channel);
        community.setTownHall(townHall);
        community.setPrivate(dto.getIsPrivate() != null ? dto.getIsPrivate() : false);
        community = communityRepository.save(community);
        return community;
    }

    public Community getCommunity(){
        // Try to get the selected mycommunity ID from authentication context
        String selectedCommunityIdStr = (String) authenticationManager.get("selectedCommunityId");
        if (selectedCommunityIdStr != null) {
            try {
                Long selectedCommunityId = Long.parseLong(selectedCommunityIdStr);
                return communityRepository.findById(selectedCommunityId)
                        .orElseThrow(() -> new EntityNotFoundException("Selected mycommunity not found"));
            } catch (NumberFormatException e) {
                System.err.println("Invalid mycommunity ID format: " + selectedCommunityIdStr);
            }
        }

        // Fallback to first mycommunity if no selection
        Community community = Optional.ofNullable(communityRepository.findAll().getFirst())
                .orElseThrow(() -> new EntityNotFoundException("Community not found"));
        return community;
    }

    public List<Community> getAllCommunities(){
        return communityRepository.findAll();
    }

    public Community getCommunityById(Long id){
        return communityRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Community not found"));
    }

    public Community getSelectedCommunity(Long selectedCommunityId) {
        if (selectedCommunityId != null) {
            return getCommunityById(selectedCommunityId);
        }
        return getCommunity();
    }

    @Transactional
    public void updateCommunity(CreateCommunityVO vo) {
        String userId = (String) authenticationManager.get("sub");
        User user = Optional.ofNullable(userRepository.findByUserId(userId))
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Community community = communityRepository.findAll().getFirst();
        community.setName(vo.getCommunityName());
        community.setDescription(vo.getCommunityDescription());
        communityRepository.save(community);
    }

    @Transactional
    public void updateCommunity(CreateCommunityVO vo, Long communityId) {
        String userId = (String) authenticationManager.get("sub");
        User user = Optional.ofNullable(userRepository.findByUserId(userId))
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new EntityNotFoundException("Community not found"));

        // Verify user has access to this mycommunity (either admin or member)
        boolean isAdmin = authenticationManager.isAdmin();

        // Use a query to check membership instead of accessing lazy collection
        boolean isMember = false;
        if (!isAdmin) {
            // Check if user is a member of this mycommunity using a direct query
            isMember = communityRepository.existsByIdAndUsers_UserId(communityId, userId);
        }

        if (!isAdmin && !isMember) {
            throw new SecurityException("User does not have permission to update this mycommunity");
        }

        community.setName(vo.getCommunityName());
        community.setDescription(vo.getCommunityDescription());
        communityRepository.save(community);
    }

    @Transactional
    public void addUserToCommunity(String userId, Long communityId) {
        User user = Optional.ofNullable(userRepository.findByUserId(userId))
                .orElseThrow(() -> new EntityNotFoundException("User does not exist"));
        Community community = communityRepository.findById(communityId)
                .orElseGet(() -> createCommunity(CreateCommunityVO
                        .builder()
                        .communityName("Default")
                        .communityDescription("Default Desc")
                        .channelDescription("Default DEsc")
                        .channelName("Default")
                        .townHallName("Default")
                        .townHallDescription("Default Desc")
                        .userEmail("akinrindeakinkunmi2006@gmail.com")
                        .build()));

        // Add user to mycommunity and mycommunity to user (bidirectional many-to-many)
        if (!user.getCommunities().contains(community)) {
            user.getCommunities().add(community);
            community.getUsers().add(user);
            userRepository.save(user);
            communityRepository.save(community);
        }
    }

    public Object getAllCommunityUsers(Long communityId) {

        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new EntityNotFoundException("Community does not exist"));
        Set<User> users = community.getUsers();
        return mapUsersToDTO(users);
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getCommunityMembers(Long communityId) {
        List<UserDTO> communityMembers = new ArrayList<>();
        try {
            Community community = communityRepository.findById(communityId)
                    .orElseThrow(() -> new EntityNotFoundException("Community does not exist"));

            Set<User> members = community.getUsers();

            for (User user : members) {
                UserDTO userDTO = new UserDTO();
                userDTO.setUserId(user.getUserId());
                userDTO.setEmail(user.getEmail());
                userDTO.setFirstName(user.getFirstName());
                userDTO.setLastName(user.getLastName());
                communityMembers.add(userDTO);
            }
        } catch (Exception e) {
            System.err.println("Error getting mycommunity members: " + e.getMessage());
            e.printStackTrace();
        }
        return communityMembers;
    }

    public void requestToJoinCommunity(String requestingUserId, Long communityId) {

        User user = Optional.ofNullable(userRepository.findByUserId(requestingUserId))
                .orElseThrow(() -> new EntityNotFoundException("User does not exist"));
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new EntityNotFoundException("Community does not exist"));

        CommunityRequest existingRequest = communityRequestRepository.findByUser_UserIdAndCommunity_Id(user.getUserId(), communityId);
        if (existingRequest != null) return;

        CommunityRequest communityRequest = new CommunityRequest();
        communityRequest.setCommunity(community);
        communityRequest.setUser(user);
        communityRequest.setStatus("P"); //P = PENDING, A = APPROVED...will use Enums later
        communityRequestRepository.save(communityRequest);
    }

    @Transactional
    public void approveCommunityRequest(Long communityRequestId) {
        CommunityRequest communityRequest = communityRequestRepository.findById(communityRequestId)
                .orElseThrow(() -> new EntityNotFoundException("Community request does not exist"));
        communityRequest.setStatus("A");
        Community community = communityRequest.getCommunity();
        User user = communityRequest.getUser();

        // Add user to mycommunity and mycommunity to user (bidirectional many-to-many)
        if (!user.getCommunities().contains(community)) {
            user.getCommunities().add(community);
            community.getUsers().add(user);
            userRepository.save(user);
            communityRepository.save(community);
        }

        // Remove the request after approval
        communityRequestRepository.delete(communityRequest);
    }

    @Transactional
    public List<CommunityRequestDTO> getChatGroupRequests(String userId) {

        List<CommunityRequest> requests = communityRequestRepository
                .findByUser_UserIdAndStatus(userId,"P");
        return mapCommunityRequestToDto(requests);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getUserCommunities(String userId) {
        List<Map<String, Object>> userCommunities = new ArrayList<>();
        try {
            System.out.println("CommunityService - Getting communities for user: " + userId);

            // Check if user is admin
            boolean isAdmin = false;
            try {
                isAdmin = authenticationManager.isAdmin();
                System.out.println("CommunityService - User " + userId + " is admin: " + isAdmin);
            } catch (Exception e) {
                System.out.println("CommunityService - Error checking admin status, assuming not admin: " + e.getMessage());
            }

            if (isAdmin) {
                // For admins, return ALL communities as they are automatic members of every mycommunity
                List<Community> allCommunities = communityRepository.findAll();
                System.out.println("CommunityService - Admin user, returning all " + allCommunities.size() + " communities");
                for (Community community : allCommunities) {
                    Map<String, Object> communityData = new HashMap<>();
                    communityData.put("id", community.getId());
                    communityData.put("communityName", community.getName());
                    communityData.put("communityDescription", community.getDescription());
                    communityData.put("isPrivate", community.isPrivate());
                    userCommunities.add(communityData);
                    System.out.println("CommunityService - Added mycommunity for admin: " + community.getName() + " (ID: " + community.getId() + ")");
                }
            } else {
                // For regular users, return only the communities they are actually members of
                try {
                    List<Community> userCommunitiesList = communityRepository.findCommunitiesByUserId(userId);
                    System.out.println("CommunityService - Regular user, found " + userCommunitiesList.size() + " communities via query");
                    for (Community community : userCommunitiesList) {
                        Map<String, Object> communityData = new HashMap<>();
                        communityData.put("id", community.getId());
                        communityData.put("communityName", community.getName());
                        communityData.put("communityDescription", community.getDescription());
                        communityData.put("isPrivate", community.isPrivate());
                        userCommunities.add(communityData);
                        System.out.println("CommunityService - Added mycommunity: " + community.getName() + " (ID: " + community.getId() + ")");
                    }
                } catch (Exception queryException) {
                    // Fallback to the original method if the new query method doesn't exist yet
                    System.err.println("Query method not available, using fallback: " + queryException.getMessage());
                    User user = userRepository.findByUserId(userId);
                    if (user != null) {
                        Set<Community> userCommunitiesSet = user.getCommunities();
                        System.out.println("CommunityService - Found " + userCommunitiesSet.size() + " communities via user entity");
                        for (Community community : userCommunitiesSet) {
                            Map<String, Object> communityData = new HashMap<>();
                            communityData.put("id", community.getId());
                            communityData.put("communityName", community.getName());
                            communityData.put("communityDescription", community.getDescription());
                            communityData.put("isPrivate", community.isPrivate());
                            userCommunities.add(communityData);
                            System.out.println("CommunityService - Added mycommunity: " + community.getName() + " (ID: " + community.getId() + ")");
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting user communities: " + e.getMessage());
            e.printStackTrace();
        }
        return userCommunities;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllCommunitiesForAdmin(String userId) {
        List<Map<String, Object>> allCommunities = new ArrayList<>();
        try {
            // Check if user is admin
            boolean isAdmin = authenticationManager.isAdmin();

            if (isAdmin) {
                // For admins, show all communities - this is a separate method for admin management
                List<Community> communityList = communityRepository.findAll();
                System.out.println("CommunityService - Admin accessing all " + communityList.size() + " communities");
                for (Community community : communityList) {
                    Map<String, Object> communityData = new HashMap<>();
                    communityData.put("id", community.getId());
                    communityData.put("communityName", community.getName());
                    communityData.put("communityDescription", community.getDescription());
                    communityData.put("isPrivate", community.isPrivate());
                    allCommunities.add(communityData);
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting all communities for admin: " + e.getMessage());
            e.printStackTrace();
        }
        return allCommunities;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSuggestedCommunities(String userId) {
        List<Map<String, Object>> suggestedCommunities = new ArrayList<>();
        try {
            User user = userRepository.findByUserId(userId);
            List<Community> allCommunities = communityRepository.findAll();

            for (Community community : allCommunities) {
                // Skip if user is already a member using the many-to-many relationship
                if (user != null && user.getCommunities().contains(community)) {
                    continue;
                }

                // Skip private communities - they should not appear in suggestions
                if (community.isPrivate()) {
                    continue;
                }

                Map<String, Object> communityData = new HashMap<>();
                communityData.put("id", community.getId());
                communityData.put("communityName", community.getName());
                communityData.put("communityDescription", community.getDescription());
                communityData.put("isPrivate", community.isPrivate());

                // Check if user has pending request for this mycommunity
                CommunityRequest existingRequest = communityRequestRepository
                        .findByUser_UserIdAndCommunity_Id(userId, community.getId());

                if (existingRequest != null) {
                    if ("P".equals(existingRequest.getStatus())) {
                        communityData.put("requestStatus", "PENDING");
                    } else if ("R".equals(existingRequest.getStatus())) {
                        communityData.put("requestStatus", "NONE"); // Rejected requests show as available again
                    }
                } else {
                    communityData.put("requestStatus", "NONE");
                }

                suggestedCommunities.add(communityData);
            }
        } catch (Exception e) {
            System.err.println("Error getting suggested communities: " + e.getMessage());
            e.printStackTrace();
        }
        return suggestedCommunities;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPendingRequests() {
        List<Map<String, Object>> requests = new ArrayList<>();
        try {
            List<CommunityRequest> pendingRequests = communityRequestRepository.findByStatus("P");

            for (CommunityRequest request : pendingRequests) {
                Map<String, Object> requestData = new HashMap<>();
                requestData.put("id", request.getId());
                requestData.put("fullName", request.getUser().getFullName());
                requestData.put("email", request.getUser().getEmail());
                requestData.put("communityName", request.getCommunity().getName());
                requestData.put("status", request.getStatus());
                requests.add(requestData);
            }
        } catch (Exception e) {
            System.err.println("Error getting pending requests: " + e.getMessage());
            e.printStackTrace();
        }
        return requests;
    }

    @Transactional
    public void cancelCommunityRequest(String userId, Long communityId) {
        CommunityRequest request = communityRequestRepository
                .findByUser_UserIdAndCommunity_Id(userId, communityId);

        if (request != null && "P".equals(request.getStatus())) {
            communityRequestRepository.delete(request);
        }
    }

    @Transactional
    public void rejectCommunityRequest(Long requestId) {
        CommunityRequest communityRequest = communityRequestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Community request does not exist"));

        // Delete the request instead of marking as rejected
        communityRequestRepository.delete(communityRequest);
    }

    @Transactional
    public void inviteUserToCommunity(String userId, Long communityId) {
        User user = Optional.ofNullable(userRepository.findByUserId(userId))
                .orElseThrow(() -> new EntityNotFoundException("User does not exist"));
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new EntityNotFoundException("Community does not exist"));

        // Check if user is already a member using the many-to-many relationship
        if (user.getCommunities().contains(community)) {
            throw new IllegalStateException("User is already a member of this mycommunity");
        }

        // Check if invitation already exists
        CommunityInvitation existingInvitation = communityInvitationRepository
                .findByUser_UserIdAndCommunity_Id(userId, communityId);
        if (existingInvitation != null && "P".equals(existingInvitation.getStatus())) {
            throw new IllegalStateException("User already has a pending invitation to this mycommunity");
        }

        // Create new invitation
        CommunityInvitation invitation = new CommunityInvitation();
        invitation.setUser(user);
        invitation.setCommunity(community);
        invitation.setStatus("P"); // P = PENDING
        communityInvitationRepository.save(invitation);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getUserCommunityInvitations(String userId) {
        List<Map<String, Object>> invitations = new ArrayList<>();
        try {
            List<CommunityInvitation> pendingInvitations = communityInvitationRepository
                    .findByUser_UserIdAndStatus(userId, "P");

            for (CommunityInvitation invitation : pendingInvitations) {
                Map<String, Object> invitationData = new HashMap<>();
                invitationData.put("id", invitation.getId());
                invitationData.put("communityName", invitation.getCommunity().getName());
                invitationData.put("communityDescription", invitation.getCommunity().getDescription());
                invitationData.put("communityId", invitation.getCommunity().getId());
                invitations.add(invitationData);
            }
        } catch (Exception e) {
            System.err.println("Error getting user mycommunity invitations: " + e.getMessage());
            e.printStackTrace();
        }
        return invitations;
    }

    @Transactional
    public void acceptCommunityInvitation(String userId, Long invitationId) {
        CommunityInvitation invitation = communityInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new EntityNotFoundException("Community invitation does not exist"));

        // Verify the invitation belongs to the user
        if (!invitation.getUser().getUserId().equals(userId)) {
            throw new IllegalStateException("Invitation does not belong to this user");
        }

        // Add user to mycommunity
        User user = invitation.getUser();
        Community community = invitation.getCommunity();

        // Add user to mycommunity and mycommunity to user (bidirectional many-to-many)
        if (!user.getCommunities().contains(community)) {
            user.getCommunities().add(community);
            community.getUsers().add(user);
            userRepository.save(user);
            communityRepository.save(community);
        }

        // Remove the invitation
        communityInvitationRepository.delete(invitation);
    }

    @Transactional
    public void declineCommunityInvitation(String userId, Long invitationId) {
        CommunityInvitation invitation = communityInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new EntityNotFoundException("Community invitation does not exist"));

        // Verify the invitation belongs to the user
        if (!invitation.getUser().getUserId().equals(userId)) {
            throw new IllegalStateException("Invitation does not belong to this user");
        }

        // Remove the invitation
        communityInvitationRepository.delete(invitation);
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getAvailableUsersToInvite(Long communityId) {
        List<UserDTO> availableUsers = new ArrayList<>();
        try {
            Community community = communityRepository.findById(communityId)
                    .orElseThrow(() -> new EntityNotFoundException("Community does not exist"));

            List<User> allUsers = userRepository.findAll();

            for (User user : allUsers) {
                // Skip if user is already a member of this mycommunity
                // Use ID comparison instead of object equality to avoid issues with Hibernate proxies
                boolean isAlreadyMember = user.getCommunities().stream()
                        .anyMatch(userCommunity -> userCommunity.getId().equals(communityId));

                if (isAlreadyMember) {
                    System.out.println("User " + user.getEmail() + " is already a member of mycommunity " + communityId);
                    continue;
                }

                // Skip if user already has a pending invitation
                CommunityInvitation existingInvitation = communityInvitationRepository
                        .findByUser_UserIdAndCommunity_Id(user.getUserId(), communityId);
                if (existingInvitation != null && "P".equals(existingInvitation.getStatus())) {
                    System.out.println("User " + user.getEmail() + " already has pending invitation to mycommunity " + communityId);
                    continue;
                }

                // Add to available users
                UserDTO userDTO = new UserDTO();
                userDTO.setUserId(user.getUserId());
                userDTO.setEmail(user.getEmail());
                userDTO.setFirstName(user.getFirstName());
                userDTO.setLastName(user.getLastName());
                availableUsers.add(userDTO);
                System.out.println("User " + user.getEmail() + " is available to invite to mycommunity " + communityId);
            }

            System.out.println("Total available users to invite: " + availableUsers.size());
        } catch (Exception e) {
            System.err.println("Error getting available users to invite: " + e.getMessage());
            e.printStackTrace();
        }
        return availableUsers;
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getEligibleMeetingParticipants(Long communityId) {
        List<UserDTO> eligibleUsers = new ArrayList<>();
        try {
            if (communityId != null) {
                // Community-specific meeting - get only users from that mycommunity
                System.out.println("Getting participants for mycommunity-specific meeting, mycommunity ID: " + communityId);
                return getCommunityMembers(communityId);
            } else {
                // General meeting - get users who belong to at least one mycommunity
                System.out.println("Getting participants for general meeting - users with at least one mycommunity");
                List<User> allUsers = userRepository.findAll();

                for (User user : allUsers) {
                    // Check if user belongs to at least one mycommunity
                    if (user.getCommunities() != null && !user.getCommunities().isEmpty()) {
                        UserDTO userDTO = new UserDTO();
                        userDTO.setUserId(user.getUserId());
                        userDTO.setEmail(user.getEmail());
                        userDTO.setFirstName(user.getFirstName());
                        userDTO.setLastName(user.getLastName());
                        eligibleUsers.add(userDTO);
                        System.out.println("User " + user.getEmail() + " is eligible for general meeting (belongs to " + user.getCommunities().size() + " communities)");
                    } else {
                        System.out.println("User " + user.getEmail() + " is NOT eligible for general meeting (belongs to no communities)");
                    }
                }

                System.out.println("Total eligible users for general meeting: " + eligibleUsers.size());
            }
        } catch (Exception e) {
            System.err.println("Error getting eligible meeting participants: " + e.getMessage());
            e.printStackTrace();
        }
        return eligibleUsers;
    }

    private List<CommunityRequestDTO> mapCommunityRequestToDto(List<CommunityRequest> communityRequests){
        List<CommunityRequestDTO> dtos = new ArrayList<>();
        for (CommunityRequest request : communityRequests) {
            CommunityRequestDTO dto = new CommunityRequestDTO();
            dto.setCommunityName(request.getCommunity().getName());
            dto.setId(request.getCommunity().getId());
            dto.setFullName(request.getUser().getFullName());
            dto.setStatus(request.getStatus());
            dtos.add(dto);
        }
        return dtos;
    }

}
