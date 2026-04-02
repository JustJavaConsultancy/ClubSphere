package com.justjava.mycommunity.community;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.dto.CommunityGroupRequestDTO;
import com.justjava.mycommunity.chat.dto.CreateChatDTO;
import com.justjava.mycommunity.chat.entity.User;
import com.justjava.mycommunity.chat.repository.CommunityGroupRepository;
import com.justjava.mycommunity.chat.repository.CommunityGroupRequestRepository;
import com.justjava.mycommunity.chat.repository.CommunityRepository;
import com.justjava.mycommunity.userManagement.UserDTO;
import com.justjava.mycommunity.userManagement.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.justjava.mycommunity.util.MappingUtils.mapUsersToDTO;

@Service
public class CommunityGroupService {

    private final CommunityGroupRepository communityGroupRepository;
    private final UserRepository userRepository;
    private final CommunityService communityService;
    private final CommunityGroupRequestRepository communityGroupRequestRepository;
    private final AuthenticationManager authenticationManager;
    private final CommunityRepository communityRepository;

    public CommunityGroupService(CommunityGroupRepository communityGroupRepository,
                                 UserRepository userRepository,
                                 CommunityService communityService,
                                 CommunityGroupRequestRepository communityGroupRequestRepository,
                                 AuthenticationManager authenticationManager,
                                 CommunityRepository communityRepository) {
        this.communityGroupRepository = communityGroupRepository;
        this.userRepository = userRepository;
        this.communityService = communityService;
        this.communityGroupRequestRepository = communityGroupRequestRepository;
        this.authenticationManager = authenticationManager;
        this.communityRepository = communityRepository;
    }

    @Transactional
    public CreateChatDTO getCommunityGroupById(Long communityGroupId) {
        CommunityGroup communityGroup = communityGroupRepository.findById(communityGroupId)
                .orElseThrow(() -> new EntityNotFoundException("Community Group does not exist"));
        CreateChatDTO dto = new CreateChatDTO();
        dto.setId(communityGroup.getId());
        dto.setGroupName(communityGroup.getName());
        dto.setGroupDescription(communityGroup.getDescription());
        // Fix: handle null users collection
        dto.setMemberCount(communityGroup.getUsers() != null ? communityGroup.getUsers().size() : 0);
        dto.setCommunityId(communityGroup.getCommunity().getId());
        return dto;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCommunityInfoForGroup(Long communityGroupId) {
        CommunityGroup communityGroup = communityGroupRepository.findById(communityGroupId)
                .orElseThrow(() -> new EntityNotFoundException("Community Group does not exist"));

        Community community = communityGroup.getCommunity();
        Map<String, Object> communityInfo = new HashMap<>();

        if (community != null) {
            communityInfo.put("id", community.getId());
            communityInfo.put("communityName", community.getName());
            communityInfo.put("communityDescription", community.getDescription());
        } else {
            communityInfo.put("id", null);
            communityInfo.put("communityName", "Unknown Community");
            communityInfo.put("communityDescription", "No mycommunity information available");
        }

        return communityInfo;
    }

    @Transactional
    public List<CreateChatDTO> getCommunityGroupsByCommunityId(Long communityId) {
        System.out.println("=== Getting groups for mycommunity ID: " + communityId + " ===");
        List<CommunityGroup> communityGroups = communityGroupRepository.findByCommunity_Id(communityId);
        System.out.println("Found " + communityGroups.size() + " groups in database for mycommunity " + communityId);

        return communityGroups.stream().map(c -> {
            CreateChatDTO dto = new CreateChatDTO();
            dto.setId(c.getId());
            dto.setGroupName(c.getName());
            dto.setGroupDescription(c.getDescription());
            // Fix: handle null users collection
            dto.setMemberCount(c.getUsers() != null ? c.getUsers().size() : 0);
            dto.setCommunityId(c.getCommunity().getId());
            System.out.println("Mapped group: " + c.getName() + " (ID: " + c.getId() + ") from mycommunity: " + c.getCommunity().getId());
            return dto;
        }).toList();
    }

    @Transactional
    public List<CreateChatDTO> getUserCommunityGroups(String userId, Long communityId) {
        System.out.println("=== Getting user groups for userId: " + userId + " in mycommunity: " + communityId + " ===");
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            System.out.println("User not found: " + userId);
            return new ArrayList<>();
        }

        List<CommunityGroup> allGroups = communityGroupRepository.findByCommunity_Id(communityId);
        Set<CommunityGroup> userGroups = user.getCommunityGroup();

        System.out.println("Total groups in mycommunity: " + allGroups.size());
        System.out.println("User belongs to " + userGroups.size() + " groups total");

        List<CreateChatDTO> result = allGroups.stream()
                .filter(userGroups::contains) // Only groups the user belongs to
                .map(c -> {
                    CreateChatDTO dto = new CreateChatDTO();
                    dto.setId(c.getId());
                    dto.setGroupName(c.getName());
                    dto.setGroupDescription(c.getDescription());
                    // Fix: handle null users collection
                    dto.setMemberCount(c.getUsers() != null ? c.getUsers().size() : 0);
                    dto.setCommunityId(c.getCommunity().getId());
                    System.out.println("User is member of group: " + c.getName() + " (ID: " + c.getId() + ")");
                    return dto;
                }).toList();

        System.out.println("Returning " + result.size() + " groups for user");
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getUserCommunityGroupsAcrossAllCommunities(String userId) {
        List<Map<String, Object>> userGroupsWithCommunity = new ArrayList<>();
        try {
            User user = userRepository.findByUserId(userId);
            if (user == null) {
                System.err.println("User not found with ID: " + userId);
                return userGroupsWithCommunity;
            }

            System.out.println("=== DEBUG: Getting user groups for userId: " + userId + " ===");

            // Check if user is admin
            boolean isAdmin = authenticationManager.isAdmin();
            System.out.println("User is admin: " + isAdmin);

            Set<CommunityGroup> targetGroups = new HashSet<>();

            if (isAdmin) {
                // For admins, get ALL groups across ALL communities
                List<CommunityGroup> allGroups = communityGroupRepository.findAll();
                targetGroups.addAll(allGroups);
                System.out.println("Admin access: Retrieved all " + allGroups.size() + " groups in system");
            } else {
                // For regular users, get only groups they belong to
                Set<CommunityGroup> userGroups = user.getCommunityGroup();
                System.out.println("User belongs to " + userGroups.size() + " groups directly");

                // Also check from the group side to ensure bidirectional consistency
                List<CommunityGroup> allGroups = communityGroupRepository.findAll();
                System.out.println("Total groups in system: " + allGroups.size());

                Set<CommunityGroup> groupsContainingUser = new HashSet<>();
                for (CommunityGroup group : allGroups) {
                    // Fix: handle null users collection
                    if (group.getUsers() != null && group.getUsers().contains(user)) {
                        groupsContainingUser.add(group);
                        System.out.println("Found user in group: " + group.getName() + " (ID: " + group.getId() + ")");
                    }
                }
                System.out.println("Groups containing user from group side: " + groupsContainingUser.size());

                // Use the union of both approaches to ensure we don't miss any groups
                targetGroups.addAll(userGroups);
                targetGroups.addAll(groupsContainingUser);
            }

            System.out.println("Target groups to process: " + targetGroups.size());

            for (CommunityGroup group : targetGroups) {
                Map<String, Object> groupData = new HashMap<>();
                groupData.put("id", group.getId());
                groupData.put("groupName", group.getName());
                groupData.put("groupDescription", group.getDescription());
                // Fix: handle null users collection
                groupData.put("memberCount", group.getUsers() != null ? group.getUsers().size() : 0);

                // Add mycommunity information
                Community community = group.getCommunity();
                if (community != null) {
                    groupData.put("communityId", community.getId());
                    groupData.put("communityName", community.getName());
                    groupData.put("communityDescription", community.getDescription());
                    System.out.println("Added group: " + group.getName() + " from mycommunity: " + community.getName() + " (ID: " + community.getId() + ")");
                } else {
                    groupData.put("communityId", null);
                    groupData.put("communityName", "Unknown Community");
                    groupData.put("communityDescription", "No mycommunity information available");
                    System.out.println("Added group: " + group.getName() + " from UNKNOWN mycommunity");
                }

                userGroupsWithCommunity.add(groupData);
            }

            // Sort by mycommunity name, then by group name
            userGroupsWithCommunity.sort((g1, g2) -> {
                String community1 = (String) g1.get("communityName");
                String community2 = (String) g2.get("communityName");
                int communityComparison = community1.compareTo(community2);
                if (communityComparison != 0) {
                    return communityComparison;
                }
                String group1 = (String) g1.get("groupName");
                String group2 = (String) g2.get("groupName");
                return group1.compareTo(group2);
            });

            System.out.println("Total groups found: " + userGroupsWithCommunity.size());

            // Debug: Print mycommunity breakdown
            Map<String, Integer> communityBreakdown = new HashMap<>();
            for (Map<String, Object> group : userGroupsWithCommunity) {
                String communityName = (String) group.get("communityName");
                communityBreakdown.put(communityName, communityBreakdown.getOrDefault(communityName, 0) + 1);
            }
            System.out.println("Community breakdown: " + communityBreakdown);

        } catch (Exception e) {
            System.err.println("Error getting user groups across all communities: " + e.getMessage());
            e.printStackTrace();
        }
        return userGroupsWithCommunity;
    }

    @Transactional
    public CommunityGroup createCommunityGroup(CreateChatDTO dto) {
        System.out.println("=== Creating mycommunity group ===");
        System.out.println("Group name: " + dto.getGroupName());
        System.out.println("Group description: " + dto.getGroupDescription());
        System.out.println("Community ID from DTO: " + dto.getCommunityId());

        // Use the mycommunity ID from the DTO instead of getting the default mycommunity
        Community community;
        if (dto.getCommunityId() != null) {
            community = communityRepository.findById(dto.getCommunityId())
                    .orElseThrow(() -> new EntityNotFoundException("Community not found with ID: " + dto.getCommunityId()));
            System.out.println("Using specified mycommunity: " + community.getName() + " (ID: " + community.getId() + ")");
        } else {
            // Fallback to default mycommunity if no ID specified
            community = communityService.getCommunity();
            System.out.println("Using default mycommunity: " + community.getName() + " (ID: " + community.getId() + ")");
        }

        CommunityGroup communityGroup = CommunityGroup.builder()
                .name(dto.getGroupName())
                .description(dto.getGroupDescription())
                .community(community)
                .build();

        // Initialize users collection if null
        if (communityGroup.getUsers() == null) {
            communityGroup.setUsers(new HashSet<>());
        }

        System.out.println("Saving mycommunity group...");
        CommunityGroup savedGroup = communityGroupRepository.save(communityGroup);
        communityGroupRepository.flush(); // Ensure immediate persistence
        System.out.println("Group saved with ID: " + savedGroup.getId());

        // Auto-add creator to the group in a separate transaction to ensure the group exists
        try {
            String creatorUserId = (String) authenticationManager.get("sub");
            if (creatorUserId != null) {
                System.out.println("Auto-adding creator (userId: " + creatorUserId + ") to group: " + savedGroup.getName());

                // Add the creator to the group immediately
                User creator = userRepository.findByUserId(creatorUserId);
                if (creator != null) {
                    // Refresh the saved group to ensure we have the latest state
                    CommunityGroup refreshedGroup = communityGroupRepository.findById(savedGroup.getId()).orElse(savedGroup);

                    // Initialize users collection if null
                    if (refreshedGroup.getUsers() == null) {
                        refreshedGroup.setUsers(new HashSet<>());
                    }

                    // Add bidirectional relationship
                    refreshedGroup.getUsers().add(creator);
                    creator.getCommunityGroup().add(refreshedGroup);

                    // Save both entities
                    userRepository.save(creator);
                    CommunityGroup finalGroup = communityGroupRepository.save(refreshedGroup);

                    // Flush to ensure immediate persistence
                    userRepository.flush();
                    communityGroupRepository.flush();

                    System.out.println("Successfully added creator to group. Group now has " + finalGroup.getUsers().size() + " members");

                    // Update the returned group reference
                    savedGroup = finalGroup;
                } else {
                    System.err.println("Creator user not found: " + creatorUserId);
                }
            } else {
                System.err.println("Creator user ID is null, cannot add to group");
            }
        } catch (Exception e) {
            System.err.println("Error auto-adding creator to group: " + e.getMessage());
            e.printStackTrace();
        }

        // Final verification
        CommunityGroup verifyGroup = communityGroupRepository.findById(savedGroup.getId()).orElse(null);
        if (verifyGroup != null) {
            int memberCount = verifyGroup.getUsers() != null ? verifyGroup.getUsers().size() : 0;
            System.out.println("Final verification: Group exists with " + memberCount + " members in mycommunity ID: " + verifyGroup.getCommunity().getId());
        } else {
            System.err.println("ERROR: Group was not properly saved to database!");
        }

        return savedGroup;
    }

    @Transactional
    public void addUserToCommunityGroup(String userId, Long communityGroupId) {
        System.out.println("=== Adding user " + userId + " to group " + communityGroupId + " ===");

        CommunityGroup communityGroup = communityGroupRepository.findById(communityGroupId)
                .orElseThrow(() -> new EntityNotFoundException("Community Group does not exist"));
        User user = Optional.ofNullable(userRepository.findByUserId(userId))
                .orElseThrow(() -> new EntityNotFoundException("User does not exist"));

        System.out.println("Found group: " + communityGroup.getName());
        System.out.println("Found user: " + user.getEmail());
        System.out.println("Current group members: " + (communityGroup.getUsers() != null ? communityGroup.getUsers().size() : 0));
        System.out.println("User's current groups: " + user.getCommunityGroup().size());

        // Initialize users collection if null
        if (communityGroup.getUsers() == null) {
            communityGroup.setUsers(new HashSet<>());
        }

        // Check if user is already in the group
        if (!communityGroup.getUsers().contains(user)) {
            // Add bidirectional relationship
            communityGroup.getUsers().add(user);
            user.getCommunityGroup().add(communityGroup);

            // Save both entities to ensure persistence
            userRepository.save(user);
            communityGroupRepository.save(communityGroup);

            // Flush to ensure immediate persistence
            userRepository.flush();
            communityGroupRepository.flush();

            System.out.println("Successfully added user to group");
            System.out.println("New group member count: " + (communityGroup.getUsers() != null ? communityGroup.getUsers().size() : 0));
            System.out.println("User's new group count: " + user.getCommunityGroup().size());

            // Verify the relationship was saved correctly
            CommunityGroup verifyGroup = communityGroupRepository.findById(communityGroupId).orElse(null);
            User verifyUser = userRepository.findByUserId(userId);
            if (verifyGroup != null && verifyUser != null) {
                boolean groupHasUser = verifyGroup.getUsers() != null && verifyGroup.getUsers().contains(verifyUser);
                boolean userHasGroup = verifyUser.getCommunityGroup().contains(verifyGroup);
                System.out.println("Verification - Group has user: " + groupHasUser + ", User has group: " + userHasGroup);

                if (!groupHasUser || !userHasGroup) {
                    System.err.println("WARNING: Bidirectional relationship not properly saved!");
                }
            }
        } else {
            System.out.println("User is already a member of this group");
        }
    }

    @Transactional
    public void updateCommunityGroup(CreateChatDTO dto, Long communityGroupId) {
        System.out.println("=== Updating mycommunity group " + communityGroupId + " ===");
        CommunityGroup communityGroup = communityGroupRepository.findById(communityGroupId)
                .orElseThrow(() -> new EntityNotFoundException("Community Group does not exist"));

        System.out.println("Updating group: " + communityGroup.getName() + " -> " + dto.getGroupName());
        communityGroup.setName(dto.getGroupName());
        communityGroup.setDescription(dto.getGroupDescription());

        CommunityGroup savedGroup = communityGroupRepository.save(communityGroup);
        System.out.println("Group updated successfully: " + savedGroup.getName());
    }

    @Transactional
    public void deleteCommunityGroup(Long communityGroupId) {
        System.out.println("=== Deleting mycommunity group " + communityGroupId + " ===");
        CommunityGroup communityGroup = communityGroupRepository.findById(communityGroupId)
                .orElseThrow(() -> new EntityNotFoundException("Community Group does not exist"));

        System.out.println("Deleting group: " + communityGroup.getName());
        Set<User> users = communityGroup.getUsers();
        if (users != null) {
            communityGroup.getUsers().clear();
            users.forEach(user -> user.getCommunityGroup().remove(communityGroup));
            userRepository.saveAll(users);
        }
        communityGroupRepository.delete(communityGroup);
        System.out.println("Group deleted successfully");
    }

    @Transactional
    public List<UserDTO> getCommunityGroupUsers(Long communityGroupId) {
        CommunityGroup communityGroup = communityGroupRepository.findById(communityGroupId)
                .orElseThrow(() -> new EntityNotFoundException("Community Group does not exist"));

        Set<User> users = communityGroup.getUsers();
        return mapUsersToDTO(users);
    }

    public void requestToJoinCommunityGroup(String requestingUserId, Long communityGroupId) {

        User user = Optional.ofNullable(userRepository.findByUserId(requestingUserId))
                .orElseThrow(() -> new EntityNotFoundException("User does not exist"));
        CommunityGroup group = communityGroupRepository.findById(communityGroupId)
                .orElseThrow(() -> new EntityNotFoundException("Community does not exist"));

        var request = communityGroupRequestRepository.findByUser_UserIdAndGroup_Id(user.getUserId(), communityGroupId);
        if (request != null) return;
        CommunityGroupRequest groupRequest = new CommunityGroupRequest();
        groupRequest.setGroup(group);
        groupRequest.setUser(user);
        groupRequest.setStatus("P"); //P = PENDING, A = APPROVED...will use Enums later
        communityGroupRequestRepository.save(groupRequest);
    }

    @Transactional
    public void approveCommunityGroupRequest(Long communityGroupRequestId) {
        CommunityGroupRequest communityRequest = communityGroupRequestRepository.findById(communityGroupRequestId)
                .orElseThrow(() -> new EntityNotFoundException("Community request does not exist"));
        communityRequest.setStatus("A");
        CommunityGroup group = communityRequest.getGroup();

        // Add user to group
        if (!group.getUsers().contains(communityRequest.getUser())) {
            group.getUsers().add(communityRequest.getUser());
            communityRequest.getUser().getCommunityGroup().add(group);
        }

        // Save changes
        communityGroupRepository.save(group);
        userRepository.save(communityRequest.getUser());

        // Remove the request after approval
        communityGroupRequestRepository.delete(communityRequest);
    }

    @Transactional
    public List<CommunityGroupRequestDTO> getCommunityGroupRequests(String userId) {

        List<CommunityGroupRequest> requests = communityGroupRequestRepository
                .findByUser_UserIdAndStatus(userId, "P");
        return mapCommunityGroupRequestsToDto(requests);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSuggestedGroups(String userId, Long communityId) {
        List<Map<String, Object>> suggestedGroups = new ArrayList<>();
        try {
            User user = userRepository.findByUserId(userId);
            if (user == null) {
                return suggestedGroups;
            }

            // Get all groups in the mycommunity
            List<CommunityGroup> allGroups = communityGroupRepository.findByCommunity_Id(communityId);

            // Get user's current groups
            Set<CommunityGroup> userGroups = user.getCommunityGroup();

            for (CommunityGroup group : allGroups) {
                // Skip if user is already a member
                if (userGroups.contains(group)) {
                    continue;
                }

                Map<String, Object> groupData = new HashMap<>();
                groupData.put("id", group.getId());
                groupData.put("groupName", group.getName());
                groupData.put("groupDescription", group.getDescription());
                // Fix: handle null users collection
                groupData.put("memberCount", group.getUsers() != null ? group.getUsers().size() : 0);

                // Check if user has pending request for this group
                CommunityGroupRequest existingRequest = communityGroupRequestRepository
                        .findByUser_UserIdAndGroup_Id(userId, group.getId());

                if (existingRequest != null && "P".equals(existingRequest.getStatus())) {
                    groupData.put("requestStatus", "PENDING");
                } else {
                    groupData.put("requestStatus", "NONE");
                }

                suggestedGroups.add(groupData);
            }
        } catch (Exception e) {
            System.err.println("Error getting suggested groups: " + e.getMessage());
            e.printStackTrace();
        }
        return suggestedGroups;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPendingGroupRequests() {
        List<Map<String, Object>> requests = new ArrayList<>();
        try {
            List<CommunityGroupRequest> pendingRequests = communityGroupRequestRepository.findByStatus("P");

            for (CommunityGroupRequest request : pendingRequests) {
                Map<String, Object> requestData = new HashMap<>();
                requestData.put("id", request.getId());
                requestData.put("fullName", request.getUser().getFullName());
                requestData.put("email", request.getUser().getEmail());
                requestData.put("groupName", request.getGroup().getName());
                requestData.put("status", request.getStatus());
                requests.add(requestData);
            }
        } catch (Exception e) {
            System.err.println("Error getting pending group requests: " + e.getMessage());
            e.printStackTrace();
        }
        return requests;
    }

    @Transactional
    public void rejectCommunityGroupRequest(Long requestId) {
        CommunityGroupRequest request = communityGroupRequestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Community group request does not exist"));

        // Delete the request instead of marking as rejected
        communityGroupRequestRepository.delete(request);
    }

    private List<CommunityGroupRequestDTO> mapCommunityGroupRequestsToDto(List<CommunityGroupRequest> communityGroupRequests) {
        List<CommunityGroupRequestDTO> dtos = new ArrayList<>();
        for (CommunityGroupRequest request : communityGroupRequests) {
            CommunityGroupRequestDTO dto = new CommunityGroupRequestDTO();
            dto.setCommunityName(request.getGroup().getName());
            dto.setId(request.getGroup().getId());
            dto.setFullName(request.getUser().getFullName());
            dto.setStatus(request.getStatus());
            dtos.add(dto);
        }
        return dtos;
    }
}
