package com.justjava.mycommunity.community;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.dto.CommunityGroupRequestDTO;
import com.justjava.mycommunity.chat.dto.CreateChatDTO;
import com.justjava.mycommunity.chat.entity.User;
import com.justjava.mycommunity.chat.repository.CommunityGroupRepository;
import com.justjava.mycommunity.chat.repository.CommunityGroupRequestRepository;
import com.justjava.mycommunity.chat.repository.CommunityRepository;
import com.justjava.mycommunity.community.dto.CommunityDTO;
import com.justjava.mycommunity.community.repository.CommunityGroupMembershipRepository;
import com.justjava.mycommunity.community.repository.CommunityMembershipRepository;
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
import java.util.stream.Collectors;

import static com.justjava.mycommunity.util.MappingUtils.mapUsersToDTO;

@Service
public class CommunityGroupService {

    private final CommunityGroupRepository communityGroupRepository;
    private final UserRepository userRepository;
    private final CommunityService communityService;
    private final CommunityGroupRequestRepository communityGroupRequestRepository;
    private final AuthenticationManager authenticationManager;
    private final CommunityRepository communityRepository;
    private final CommunityMembershipRepository communityMembershipRepository;
    private final CommunityGroupMembershipRepository communityGroupMembershipRepository;

    public CommunityGroupService(CommunityGroupRepository communityGroupRepository,
                                 UserRepository userRepository,
                                 CommunityService communityService,
                                 CommunityGroupRequestRepository communityGroupRequestRepository,
                                 AuthenticationManager authenticationManager,
                                 CommunityRepository communityRepository,
                                 CommunityMembershipRepository communityMembershipRepository,
                                 CommunityGroupMembershipRepository communityGroupMembershipRepository) {
        this.communityGroupRepository = communityGroupRepository;
        this.userRepository = userRepository;
        this.communityService = communityService;
        this.communityGroupRequestRepository = communityGroupRequestRepository;
        this.authenticationManager = authenticationManager;
        this.communityRepository = communityRepository;
        this.communityMembershipRepository = communityMembershipRepository;
        this.communityGroupMembershipRepository = communityGroupMembershipRepository;
    }

    private int getGroupMemberCount(Long groupId) {
        return (int) communityGroupMembershipRepository.countByCommunityGroup_Id(groupId);
    }

    private Optional<CommunityMembership> getApprovedCommunityMembership(String userId, Long communityId) {
        return communityMembershipRepository.findByUserIdAndCommunityId(userId, communityId)
                .filter(cm -> cm.getStatus() == MembershipStatus.APPROVED);
    }

    private void addMembershipToGroup(CommunityGroup group, CommunityMembership communityMembership, Role role) {
        Optional<CommunityGroupMembership> existing = communityGroupMembershipRepository
                .findByCommunityGroupAndCommunityMembership(group, communityMembership);
        if (existing.isPresent()) {
            CommunityGroupMembership existingMembership = existing.get();
            if (role != null && existingMembership.getRole() != role) {
                existingMembership.setRole(role);
                communityGroupMembershipRepository.save(existingMembership);
            }
            return;
        }

        CommunityGroupMembership groupMembership = new CommunityGroupMembership();
        groupMembership.setCommunityGroup(group);
        groupMembership.setCommunityMembership(communityMembership);
        groupMembership.setRole(role != null ? role : Role.MEMBER);
        communityGroupMembershipRepository.save(groupMembership);
    }

    @Transactional
    public CreateChatDTO getCommunityGroupById(Long communityGroupId) {
        CommunityGroup communityGroup = communityGroupRepository.findById(communityGroupId)
                .orElseThrow(() -> new EntityNotFoundException("Community Group does not exist"));
        CreateChatDTO dto = new CreateChatDTO();
        dto.setId(communityGroup.getId());
        dto.setGroupName(communityGroup.getName());
        dto.setGroupDescription(communityGroup.getDescription());
        dto.setMemberCount(getGroupMemberCount(communityGroup.getId()));
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
        List<CommunityGroup> communityGroups = communityGroupRepository.findByCommunity_Id(communityId);

        return communityGroups.stream().map(c -> {
            CreateChatDTO dto = new CreateChatDTO();
            dto.setId(c.getId());
            dto.setGroupName(c.getName());
            dto.setGroupDescription(c.getDescription());
            dto.setMemberCount(getGroupMemberCount(c.getId()));
            dto.setCommunityId(c.getCommunity().getId());
            return dto;
        }).toList();
    }

    @Transactional
    public List<CreateChatDTO> getUserCommunityGroups(String userId, Long communityId) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            return new ArrayList<>();
        }

        List<CommunityGroup> groups = communityGroupMembershipRepository
                .findGroupsByUserIdAndStatus(userId, MembershipStatus.APPROVED)
                .stream()
                .filter(group -> group.getCommunity() != null && group.getCommunity().getId().equals(communityId))
                .toList();

        return groups.stream().map(c -> {
            CreateChatDTO dto = new CreateChatDTO();
            dto.setId(c.getId());
            dto.setGroupName(c.getName());
            dto.setGroupDescription(c.getDescription());
            dto.setMemberCount(getGroupMemberCount(c.getId()));
            dto.setCommunityId(c.getCommunity().getId());
            return dto;
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getUserCommunityGroupsAcrossAllCommunities(String userId) {
        List<Map<String, Object>> userGroupsWithCommunity = new ArrayList<>();
        try {
            User user = userRepository.findByUserId(userId);
            if (user == null) {
                return userGroupsWithCommunity;
            }

            boolean isAdmin = authenticationManager.isAdmin();
            List<CommunityGroup> targetGroups;

            if (isAdmin) {
                targetGroups = communityGroupRepository.findAll();
            } else {
                targetGroups = communityGroupMembershipRepository
                        .findGroupsByUserIdAndStatus(userId, MembershipStatus.APPROVED);
            }

            for (CommunityGroup group : new HashSet<>(targetGroups)) {
                Map<String, Object> groupData = new HashMap<>();
                groupData.put("id", group.getId());
                groupData.put("groupName", group.getName());
                groupData.put("groupDescription", group.getDescription());
                groupData.put("memberCount", getGroupMemberCount(group.getId()));

                Community community = group.getCommunity();
                if (community != null) {
                    groupData.put("communityId", community.getId());
                    groupData.put("communityName", community.getName());
                    groupData.put("communityDescription", community.getDescription());
                } else {
                    groupData.put("communityId", null);
                    groupData.put("communityName", "Unknown Community");
                    groupData.put("communityDescription", "No mycommunity information available");
                }

                userGroupsWithCommunity.add(groupData);
            }

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
        } catch (Exception e) {
            System.err.println("Error getting user groups across all communities: " + e.getMessage());
            e.printStackTrace();
        }
        return userGroupsWithCommunity;
    }

    @Transactional
    public CommunityGroup createCommunityGroup(CreateChatDTO dto) {
        Community community;
        if (dto.getCommunityId() != null) {
            community = communityRepository.findById(dto.getCommunityId())
                    .orElseThrow(() -> new EntityNotFoundException("Community not found with ID: " + dto.getCommunityId()));
        } else {
            CommunityDTO communityDTO = communityService.getCommunity();
            community = communityRepository.findById(communityDTO.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Community not found with ID: " + communityDTO.getId()));
        }

        CommunityGroup communityGroup = CommunityGroup.builder()
                .name(dto.getGroupName())
                .description(dto.getGroupDescription())
                .build();

        community.addCommunityGroup(communityGroup);
        CommunityGroup savedGroup = communityGroupRepository.saveAndFlush(communityGroup);

        try {
            String creatorUserId = (String) authenticationManager.get("sub");
            if (creatorUserId != null) {
                addUserToCommunityGroup(creatorUserId, savedGroup.getId(), Role.ADMIN);
            }
        } catch (Exception e) {
            System.err.println("Error auto-adding creator to group: " + e.getMessage());
            e.printStackTrace();
        }

        return savedGroup;
    }

    @Transactional
    public void addUserToCommunityGroup(String userId, Long communityGroupId) {
        addUserToCommunityGroup(userId, communityGroupId, Role.MEMBER);
    }

    @Transactional
    public void addUserToCommunityGroup(String userId, Long communityGroupId, Role role) {
        CommunityGroup communityGroup = communityGroupRepository.findById(communityGroupId)
                .orElseThrow(() -> new EntityNotFoundException("Community Group does not exist"));

        User user = Optional.ofNullable(userRepository.findByUserId(userId))
                .orElseThrow(() -> new EntityNotFoundException("User does not exist"));

        Long communityId = Optional.ofNullable(communityGroup.getCommunity())
                .map(Community::getId)
                .orElseThrow(() -> new IllegalStateException("Group is not linked to a community"));

        CommunityMembership communityMembership = getApprovedCommunityMembership(user.getUserId(), communityId)
                .orElseThrow(() -> new IllegalStateException("User must be an approved member of the community before joining group"));

        addMembershipToGroup(communityGroup, communityMembership, role);
    }

    @Transactional
    public void updateCommunityGroup(CreateChatDTO dto, Long communityGroupId) {
        CommunityGroup communityGroup = communityGroupRepository.findById(communityGroupId)
                .orElseThrow(() -> new EntityNotFoundException("Community Group does not exist"));

        communityGroup.setName(dto.getGroupName());
        communityGroup.setDescription(dto.getGroupDescription());
        communityGroupRepository.save(communityGroup);
    }

    @Transactional
    public void deleteCommunityGroup(Long communityGroupId) {
        CommunityGroup communityGroup = communityGroupRepository.findById(communityGroupId)
                .orElseThrow(() -> new EntityNotFoundException("Community Group does not exist"));

        if (communityGroup.getCommunity() != null) {
            communityGroup.getCommunity().removeCommunityGroup(communityGroup);
        }

        communityGroupMembershipRepository.deleteByCommunityGroup_Id(communityGroupId);
        communityGroupRepository.delete(communityGroup);
    }

    @Transactional
    public List<UserDTO> getCommunityGroupUsers(Long communityGroupId) {
        communityGroupRepository.findById(communityGroupId)
                .orElseThrow(() -> new EntityNotFoundException("Community Group does not exist"));

        List<CommunityGroupMembership> groupMemberships =
                communityGroupMembershipRepository.findByCommunityGroup_Id(communityGroupId);

        List<String> userIds = groupMemberships.stream()
                .map(CommunityGroupMembership::getCommunityMembership)
                .map(CommunityMembership::getUserId)
                .distinct()
                .toList();

        if (userIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<User> users = userRepository.findByUserIdIn(userIds);
        return mapUsersToDTO(users);
    }

    public void requestToJoinCommunityGroup(String requestingUserId, Long communityGroupId) {
        User user = Optional.ofNullable(userRepository.findByUserId(requestingUserId))
                .orElseThrow(() -> new EntityNotFoundException("User does not exist"));

        CommunityGroup group = communityGroupRepository.findById(communityGroupId)
                .orElseThrow(() -> new EntityNotFoundException("Community Group does not exist"));

        Long communityId = Optional.ofNullable(group.getCommunity())
                .map(Community::getId)
                .orElseThrow(() -> new IllegalStateException("Group is not linked to a community"));

        getApprovedCommunityMembership(user.getUserId(), communityId)
                .orElseThrow(() -> new IllegalStateException("User must first be an approved community member"));

        var request = communityGroupRequestRepository.findByUser_UserIdAndGroup_Id(user.getUserId(), communityGroupId);
        if (request != null) {
            return;
        }

        CommunityGroupRequest groupRequest = new CommunityGroupRequest();
        groupRequest.setGroup(group);
        groupRequest.setUser(user);
        groupRequest.setStatus("P");
        communityGroupRequestRepository.save(groupRequest);
    }

    @Transactional(readOnly = true)
    public boolean isUserGroupAdmin(String userId, Long groupId) {
        return communityGroupMembershipRepository.isUserGroupAdmin(userId, groupId);
    }

    @Transactional
    public void promoteMemberToAdmin(Long communityGroupId, String targetUserId, String requestingUserId) {
        boolean requesterIsAdmin = communityGroupMembershipRepository.isUserGroupAdmin(requestingUserId, communityGroupId);
        if (!requesterIsAdmin && !authenticationManager.isAdmin()) {
            throw new SecurityException("Only group admins can promote members");
        }
        CommunityGroup group = communityGroupRepository.findById(communityGroupId)
                .orElseThrow(() -> new EntityNotFoundException("Community Group does not exist"));
        CommunityMembership membership = getApprovedCommunityMembership(targetUserId, group.getCommunity().getId())
                .orElseThrow(() -> new IllegalStateException("User is not an approved community member"));
        CommunityGroupMembership groupMembership = communityGroupMembershipRepository
                .findByCommunityGroupAndCommunityMembership(group, membership)
                .orElseThrow(() -> new EntityNotFoundException("User is not a member of this group"));
        groupMembership.setRole(Role.ADMIN);
        communityGroupMembershipRepository.save(groupMembership);
    }

    @Transactional
    public void demoteAdminToMember(Long communityGroupId, String targetUserId, String requestingUserId) {
        boolean requesterIsAdmin = communityGroupMembershipRepository.isUserGroupAdmin(requestingUserId, communityGroupId);
        if (!requesterIsAdmin && !authenticationManager.isAdmin()) {
            throw new SecurityException("Only group admins can demote admins");
        }
        CommunityGroup group = communityGroupRepository.findById(communityGroupId)
                .orElseThrow(() -> new EntityNotFoundException("Community Group does not exist"));
        CommunityMembership membership = getApprovedCommunityMembership(targetUserId, group.getCommunity().getId())
                .orElseThrow(() -> new IllegalStateException("User is not an approved community member"));
        CommunityGroupMembership groupMembership = communityGroupMembershipRepository
                .findByCommunityGroupAndCommunityMembership(group, membership)
                .orElseThrow(() -> new EntityNotFoundException("User is not a member of this group"));
        groupMembership.setRole(Role.MEMBER);
        communityGroupMembershipRepository.save(groupMembership);
    }

    @Transactional
    public void inviteMemberToGroup(Long communityGroupId, String targetUserId, String requestingUserId) {
        boolean requesterIsAdmin = communityGroupMembershipRepository.isUserGroupAdmin(requestingUserId, communityGroupId);
        if (!requesterIsAdmin && !authenticationManager.isAdmin()) {
            throw new SecurityException("Only group admins can invite members");
        }
        addUserToCommunityGroup(targetUserId, communityGroupId, Role.MEMBER);
    }

    @Transactional
    public void removeMemberFromGroup(Long communityGroupId, String targetUserId, String requestingUserId) {
        boolean requesterIsAdmin = communityGroupMembershipRepository.isUserGroupAdmin(requestingUserId, communityGroupId);
        if (!requesterIsAdmin && !authenticationManager.isAdmin()) {
            throw new SecurityException("Only group admins can remove members");
        }
        CommunityGroup group = communityGroupRepository.findById(communityGroupId)
                .orElseThrow(() -> new EntityNotFoundException("Community Group does not exist"));
        CommunityMembership membership = getApprovedCommunityMembership(targetUserId, group.getCommunity().getId())
                .orElseThrow(() -> new IllegalStateException("User is not an approved community member"));
        communityGroupMembershipRepository.findByCommunityGroupAndCommunityMembership(group, membership)
                .ifPresent(communityGroupMembershipRepository::delete);
    }

    /** Returns group members with their roles for the management panel */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getGroupMembersWithRoles(Long communityGroupId) {
        List<CommunityGroupMembership> memberships = communityGroupMembershipRepository.findByCommunityGroup_Id(communityGroupId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (CommunityGroupMembership m : memberships) {
            String userId = m.getCommunityMembership().getUserId();
            User user = userRepository.findByUserId(userId);
            if (user == null) continue;
            Map<String, Object> entry = new HashMap<>();
            entry.put("userId", userId);
            entry.put("fullName", user.getFullName());
            entry.put("email", user.getEmail());
            entry.put("role", m.getRole() != null ? m.getRole().name() : "MEMBER");
            result.add(entry);
        }
        return result;
    }

    @Transactional
    public void approveCommunityGroupRequest(Long communityGroupRequestId) {
        CommunityGroupRequest communityRequest = communityGroupRequestRepository.findById(communityGroupRequestId)
                .orElseThrow(() -> new EntityNotFoundException("Community request does not exist"));
        communityRequest.setStatus("A");

        addUserToCommunityGroup(communityRequest.getUser().getUserId(), communityRequest.getGroup().getId());
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

            List<CommunityGroup> allGroups = communityGroupRepository.findByCommunity_Id(communityId);
            Set<Long> userGroupIds = communityGroupMembershipRepository
                    .findGroupsByUserIdAndStatus(userId, MembershipStatus.APPROVED)
                    .stream()
                    .map(CommunityGroup::getId)
                    .collect(Collectors.toSet());

            for (CommunityGroup group : allGroups) {
                if (userGroupIds.contains(group.getId())) {
                    continue;
                }

                Map<String, Object> groupData = new HashMap<>();
                groupData.put("id", group.getId());
                groupData.put("groupName", group.getName());
                groupData.put("groupDescription", group.getDescription());
                groupData.put("memberCount", getGroupMemberCount(group.getId()));

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
