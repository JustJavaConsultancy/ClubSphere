package com.justjava.mycommunity.community;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.dto.CommunityRequestDTO;
import com.justjava.mycommunity.chat.dto.CreateCommunityVO;
import com.justjava.mycommunity.chat.entity.User;
import com.justjava.mycommunity.chat.repository.CommunityRepository;
import com.justjava.mycommunity.chat.repository.CommunityRequestRepository;
import com.justjava.mycommunity.chat.repository.CommunityInvitationRepository;
import com.justjava.mycommunity.chat.repository.OrganizationRepository;
import com.justjava.mycommunity.community.dto.PaymentStatus;
import com.justjava.mycommunity.community.dto.PaymentType;
import com.justjava.mycommunity.community.mapper.CommunityMapper;
import com.justjava.mycommunity.community.repository.CommunityMembershipRepository;
import com.justjava.mycommunity.community.repository.PaymentTransactionRepository;
import com.justjava.mycommunity.organization.Channel;
import com.justjava.mycommunity.organization.Organization;
import com.justjava.mycommunity.organization.TownHall;
import com.justjava.mycommunity.organization.TownHallChannelService;
import com.justjava.mycommunity.userManagement.UserRepository;
import com.justjava.mycommunity.userManagement.UserDTO;
import com.justjava.mycommunity.community.dto.CommunityDTO;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

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
    private final CommunityMembershipRepository communityMembershipRepository;
    private final RuntimeService runtimeService;
    private final CommunityMapper communityMapper;
    private final PaymentTransactionRepository paymentTransactionRepository;

    public CommunityDTO createCommunity(CreateCommunityVO dto) {
        User user = resolveUser(dto.getUserEmail());
        Channel channel = townHallChannelService.createChannel(dto.getChannelName(), dto.getChannelDescription());

        TownHall townHall = townHallChannelService.createTownHall(dto.getTownHallName(), dto.getTownHallDescription());
        Organization organization = resolveOrganization(user);
        Community community = new Community();
        community.setName(dto.getCommunityName());
        community.setDescription(dto.getCommunityDescription());
        community.setOrganization(user.getOrganization() != null ? user.getOrganization() : organization);
        community.setChannel(channel);
        community.setTownHall(townHall);
        community.setPrivate(dto.getIsPrivate() != null ? dto.getIsPrivate() : false);
        community = communityRepository.save(community);
        linkUserToCommunity(user, community, Role.CREATOR);

        return communityMapper.toDto(community,communityMembershipRepository);
    }

    private User resolveUser(String userEmail) {
        return Optional.ofNullable(userRepository.findByEmail(userEmail))
                .orElseThrow(() -> new EntityNotFoundException("User with email " + userEmail + " not found"));
    }

    private Organization resolveOrganization(User user) {
        // First, check if user already has an organization
        if (user.getOrganization() != null) {
            return user.getOrganization();
        }

        // Try to find existing organization by user's email domain or other criteria
        // For now, create a default organization if none exists
        Organization organization = new Organization();
        organization.setName("Default Organization for " + user.getEmail());
        organization.setDescription("Automatically created organization");

        organization = organizationRepository.save(organization);

        // Link organization to user
        user.setOrganization(organization);
        userRepository.save(user);

        return organization;
    }

    public CommunityDTO getCommunity() {
        String selectedCommunityIdStr = (String) authenticationManager.get("selectedCommunityId");
        if (selectedCommunityIdStr != null) {
            try {
                Long selectedCommunityId = Long.parseLong(selectedCommunityIdStr);
                Community community = communityRepository.findById(selectedCommunityId)
                        .orElseThrow(() -> new EntityNotFoundException("Selected mycommunity not found"));
                return communityMapper.toDto(community,communityMembershipRepository);
            } catch (NumberFormatException e) {
                System.err.println("Invalid mycommunity ID format: " + selectedCommunityIdStr);
            }
        }

        List<Community> communities = communityRepository.findAll();
        if (communities.isEmpty()) {
            return null;
        }

        return communityMapper.toDto(Optional.ofNullable(communities.getFirst())
                .orElseThrow(() -> new EntityNotFoundException("Community not found")),communityMembershipRepository);
    }

    public CommunityDTO getCommunityById(Long id) {
        return communityRepository.findById(id)
            .map(community -> communityMapper.toDto(community, communityMembershipRepository))
            .orElseThrow(() -> new EntityNotFoundException("Community not found"));
    }


    @Transactional
    public void updateCommunity(CreateCommunityVO vo) {
        Community community = communityRepository.findAll().getFirst();
        community.setName(vo.getCommunityName());
        community.setDescription(vo.getCommunityDescription());
        communityRepository.save(community);
    }

    @Transactional
    public void updateCommunity(CreateCommunityVO vo, Long communityId) {
        String userId = (String) authenticationManager.get("sub");
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new EntityNotFoundException("Community not found"));

        // Verify user has access to this mycommunity (either admin or member)
        boolean isAdmin = authenticationManager.isAdmin();

        // Use a query to check membership instead of accessing lazy collection
        boolean isMember = false;
        if (!isAdmin) {
            isMember = communityMembershipRepository.existsByUserIdAndCommunityIdAndStatus(
                    userId,
                    communityId,
                    MembershipStatus.APPROVED
            );
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
                .orElseThrow(() -> new EntityNotFoundException("Community does not exist"));

    Optional<CommunityMembership> existingMembershipOpt =
            communityMembershipRepository.findByUserIdAndCommunityId(userId, communityId);

    if (existingMembershipOpt.isPresent()) {
        CommunityMembership existingMembership = existingMembershipOpt.get();

        if (existingMembership.getStatus() != MembershipStatus.APPROVED) {
            existingMembership.setStatus(MembershipStatus.APPROVED);
        }

        if (existingMembership.getRole() == null) {
            existingMembership.setRole(Role.MEMBER);
        }

        communityMembershipRepository.save(existingMembership);
        return;
    }

    CommunityMembership membership = new CommunityMembership();
    membership.setUserId(user.getUserId());
    membership.setCommunityId(community.getId());
    membership.setRole(Role.MEMBER);
    membership.setStatus(MembershipStatus.APPROVED);

    communityMembershipRepository.save(membership);
}

    public Object getAllCommunityUsers(Long communityId) {
        communityRepository.findById(communityId)
                .orElseThrow(() -> new EntityNotFoundException("Community does not exist"));
        List<User> users = userRepository.findByCommunityId(communityId);
        return mapUsersToDTO(users);
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getCommunityMembers(Long communityId) {

        List<UserDTO> communityMembers = new ArrayList<>();

        try {
            // 🔹 Step 1: Get approved memberships
            List<CommunityMembership> memberships =
                    communityMembershipRepository.findByCommunityIdAndStatus(
                            communityId,
                            MembershipStatus.APPROVED
                    );

            if (memberships.isEmpty()) {
                return communityMembers;
            }

            // 🔹 Step 2: Extract userIds
            List<String> userIds = memberships.stream()
                    .map(CommunityMembership::getUserId)
                    .distinct()
                    .toList();

            // 🔹 Step 3: Fetch users in ONE query
            List<User> users = userRepository.findByUserIdIn(userIds);

            // 🔹 Step 4: Map role (optional but powerful)
            Map<String, Role> roleMap = memberships.stream()
                    .collect(Collectors.toMap(
                            CommunityMembership::getUserId,
                            CommunityMembership::getRole
                    ));

            // 🔹 Step 5: Convert to DTO
            for (User user : users) {

                UserDTO dto = new UserDTO();
                dto.setUserId(user.getUserId());
                dto.setEmail(user.getEmail());
                dto.setFirstName(user.getFirstName());
                dto.setLastName(user.getLastName());

                // 🔥 Optional: include role
                dto.setRole(roleMap.get(user.getUserId()));
                communityMembers.add(dto);
            }

        } catch (Exception e) {
            System.err.println("Error getting community members: " + e.getMessage());
            e.printStackTrace();
        }

        return communityMembers;
    }

    @Transactional
    public void requestToJoinCommunity(String requestingUserId, Long communityId) {

        // 🔹 Step 1: Validate user
        User user = Optional.ofNullable(userRepository.findByUserId(requestingUserId))
                .orElseThrow(() -> new EntityNotFoundException("User does not exist"));

        // 🔹 Step 2: Validate community
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new EntityNotFoundException("Community does not exist"));

        // 🔹 Step 3: Check existing membership
        Optional<CommunityMembership> existingOpt =
                communityMembershipRepository.findByUserIdAndCommunityId(
                        requestingUserId,
                        communityId
                );

        if (existingOpt.isPresent()) {

            CommunityMembership existing = existingOpt.get();

            // ✅ Already approved → no need to request
            if (existing.getStatus() == MembershipStatus.APPROVED) {
                throw new IllegalStateException("User is already a member of this community");
            }

            // ✅ Already pending → avoid duplicate workflow
            if (existing.getStatus() == MembershipStatus.PENDING) {
                throw new IllegalStateException("Join request already pending");
            }

            // 🔥 If previously rejected → allow re-request
            if (existing.getStatus() == MembershipStatus.REJECTED) {
                existing.setStatus(MembershipStatus.PENDING);
                communityMembershipRepository.save(existing);
            }

        } else {
            // 🔹 Create new membership request
            CommunityMembership membership = new CommunityMembership();
            membership.setUserId(requestingUserId);
            membership.setCommunityId(communityId);
            membership.setRole(Role.MEMBER);
            membership.setStatus(MembershipStatus.PENDING);

            communityMembershipRepository.save(membership);
        }

        // 🔹 Step 4: Create CommunityRequest so admin can see it in pending requests
        CommunityRequest existingRequest = communityRequestRepository
                .findByUser_UserIdAndCommunity_Id(requestingUserId, communityId);
        if (existingRequest == null) {
            CommunityRequest communityRequest = new CommunityRequest();
            communityRequest.setUser(user);
            communityRequest.setCommunity(community);
            communityRequest.setStatus("P");
            communityRequestRepository.save(communityRequest);
        }

        // 🔹 Step 5: Resolve admin (fail fast if none)
        String adminUserId = resolveCommunityAdmin(communityId);

        // 🔹 Step 5: Start Flowable process
        runtimeService.startProcessInstanceByKey(
                "communityMembershipApproval",
                Map.of(
                        "userId", requestingUserId,
                        "communityId", communityId,
                        "adminUserId", adminUserId
                )
        );

        // 🔹 Step 6: Logging (replace System.out)
/*        log.info("User {} requested to join community {}. Approval sent to admin {}",
                requestingUserId, communityId, adminUserId);*/
    }

    private String resolveCommunityAdmin(Long communityId) {
        return communityMembershipRepository
                .findFirstAdmin(communityId)
                .orElse("");
    }

    @Transactional
    public void approveCommunityRequest(Long communityRequestId) {
        CommunityRequest communityRequest = communityRequestRepository.findById(communityRequestId)
                .orElseThrow(() -> new EntityNotFoundException("Community request does not exist"));
        communityRequest.setStatus("A");
        Community community = communityRequest.getCommunity();
        User user = communityRequest.getUser();

        approveMembership(user, community);

        // Remove the request after approval
        communityRequestRepository.delete(communityRequest);
    }

    private void approveMembership(User user, Community community) {
        linkUserToCommunity(user, community, Role.MEMBER);
    }

    @Transactional
    public List<CommunityRequestDTO> getChatGroupRequests(String userId) {

        List<CommunityRequest> requests = communityRequestRepository
                .findByUser_UserIdAndStatus(userId, "P");
        return mapCommunityRequestToDto(requests);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getUserCommunities(String userId) {

        List<Map<String, Object>> userCommunities = new ArrayList<>();

        try {
            System.out.println("CommunityService - Getting communities for user: " + userId);

            boolean isAdmin = false;
            try {
                isAdmin = authenticationManager.isAdmin();
                System.out.println("CommunityService - User " + userId + " is admin: " + isAdmin);
            } catch (Exception e) {
                System.out.println("Error checking admin status: " + e.getMessage());
            }

            // 🔹 ADMIN → return all communities
            if (isAdmin) {
                List<Community> allCommunities = communityRepository.findAll();

                for (Community community : allCommunities) {
                    userCommunities.add(mapCommunity(community));
                }

                return userCommunities;
            }

            // 🔹 NORMAL USER → use membership
            List<CommunityMembership> memberships =
                    communityMembershipRepository.findByUserIdAndStatus(
                            userId,
                            MembershipStatus.APPROVED
                    );

            if (memberships.isEmpty()) {
                return userCommunities;
            }

            // 🔹 Extract community IDs
            List<Long> communityIds = memberships.stream()
                    .map(CommunityMembership::getCommunityId)
                    .distinct()
                    .toList();

            // 🔹 Fetch communities in one query
            List<Community> communities = communityRepository.findAllById(communityIds);

            for (Community community : communities) {
                userCommunities.add(mapCommunity(community));
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
            // 🔹 Get all memberships of user (single query)
            List<CommunityMembership> memberships =
                    communityMembershipRepository.findByUserId(userId);

            // 🔹 Convert to lookup map (communityId → membership)
            Map<Long, CommunityMembership> membershipMap = memberships.stream()
                    .collect(Collectors.toMap(
                            CommunityMembership::getCommunityId,
                            m -> m
                    ));

            // 🔹 Fetch only public communities
            List<Community> publicCommunities =
                    communityRepository.findByIsPrivateFalse();

            for (Community community : publicCommunities) {

                // 🔒 Extra safety: skip private communities
                if (Boolean.TRUE.equals(community.getIsPrivate())) {
                    continue;
                }

                CommunityMembership membership =
                        membershipMap.get(community.getId());

                // ❌ Skip if already approved member
                if (membership != null &&
                        membership.getStatus() == MembershipStatus.APPROVED) {
                    continue;
                }

                Map<String, Object> communityData = new HashMap<>();
                communityData.put("id", community.getId());
                communityData.put("communityName", community.getName());
                communityData.put("communityDescription", community.getDescription());
                communityData.put("isPrivate", community.isPrivate());

                // 🔹 Determine request status using membership
                if (membership != null &&
                        membership.getStatus() == MembershipStatus.PENDING) {

                    communityData.put("requestStatus", "PENDING");

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

        // Also remove the PENDING membership
        Optional<CommunityMembership> membershipOpt =
                communityMembershipRepository.findByUserIdAndCommunityId(userId, communityId);
        if (membershipOpt.isPresent() && membershipOpt.get().getStatus() == MembershipStatus.PENDING) {
            communityMembershipRepository.delete(membershipOpt.get());
        }
    }

    @Transactional
    public void rejectCommunityRequest(Long requestId) {
        CommunityRequest communityRequest = communityRequestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Community request does not exist"));

        // Update the membership status to REJECTED
        String userId = communityRequest.getUser().getUserId();
        Long communityId = communityRequest.getCommunity().getId();
        Optional<CommunityMembership> membershipOpt =
                communityMembershipRepository.findByUserIdAndCommunityId(userId, communityId);
        if (membershipOpt.isPresent()) {
            CommunityMembership membership = membershipOpt.get();
            membership.setStatus(MembershipStatus.REJECTED);
            communityMembershipRepository.save(membership);
        }

        // Delete the request
        communityRequestRepository.delete(communityRequest);
    }

    @Transactional
    public void inviteUserToCommunity(String userId, Long communityId) {

        // 🔹 Step 1: Validate user
        User user = Optional.ofNullable(userRepository.findByUserId(userId))
                .orElseThrow(() -> new EntityNotFoundException("User does not exist"));

        // 🔹 Step 2: Validate community
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new EntityNotFoundException("Community does not exist"));

        // 🔹 Step 3: Check membership (SOURCE OF TRUTH)
        Optional<CommunityMembership> membershipOpt =
                communityMembershipRepository.findByUserIdAndCommunityId(userId, communityId);

        if (membershipOpt.isPresent()) {

            CommunityMembership membership = membershipOpt.get();

            // ❌ Already member
            if (membership.getStatus() == MembershipStatus.APPROVED) {
                throw new IllegalStateException("User is already a member of this community");
            }

            // ❌ Already pending (either request or invite)
            if (membership.getStatus() == MembershipStatus.PENDING) {
                throw new IllegalStateException("User already has a pending request/invitation");
            }

            // 🔁 Previously rejected → reuse record
            if (membership.getStatus() == MembershipStatus.REJECTED) {
                membership.setStatus(MembershipStatus.PENDING);
                membership.setRole(Role.MEMBER);
                communityMembershipRepository.save(membership);
            }

        } else {
            // 🔹 Create new membership (invitation)
            CommunityMembership membership = new CommunityMembership();
            membership.setUserId(userId);
            membership.setCommunityId(communityId);
            membership.setRole(Role.MEMBER);
            membership.setStatus(MembershipStatus.PENDING);

            communityMembershipRepository.save(membership);
        }

        // 🔹 Step 4: (Optional - keep backward compatibility)
        CommunityInvitation existingInvitation =
                communityInvitationRepository.findByUser_UserIdAndCommunity_Id(userId, communityId);

        if (existingInvitation == null) {
            CommunityInvitation invitation = new CommunityInvitation();
            invitation.setUser(user);
            invitation.setCommunity(community);
            invitation.setStatus("P");
            communityInvitationRepository.save(invitation);
        }

        // 🔹 Step 5: Logging
        //log.info("User {} invited to community {}", userId, communityId);
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

        // 🔹 Step 1: Validate invitation
        CommunityInvitation invitation = communityInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new EntityNotFoundException("Community invitation does not exist"));

        // 🔹 Step 2: Validate ownership
        if (!invitation.getUser().getUserId().equals(userId)) {
            throw new IllegalStateException("Invitation does not belong to this user");
        }

        Long communityId = invitation.getCommunity().getId();

        // 🔹 Step 3: Check membership (SOURCE OF TRUTH)
        Optional<CommunityMembership> membershipOpt =
                communityMembershipRepository.findByUserIdAndCommunityId(userId, communityId);

        if (membershipOpt.isPresent()) {

            CommunityMembership membership = membershipOpt.get();

            // ✅ Already approved → nothing to do
            if (membership.getStatus() == MembershipStatus.APPROVED) {
                communityInvitationRepository.delete(invitation);
                return;
            }

            // 🔥 Accept invitation → approve membership
            membership.setStatus(MembershipStatus.APPROVED);
            membership.setRole(Role.MEMBER);

            communityMembershipRepository.save(membership);

        } else {
            // 🔹 Create membership if not exists (edge case safety)
            CommunityMembership membership = new CommunityMembership();
            membership.setUserId(userId);
            membership.setCommunityId(communityId);
            membership.setRole(Role.MEMBER);
            membership.setStatus(MembershipStatus.APPROVED);

            communityMembershipRepository.save(membership);
        }

        // 🔹 Step 4: Remove invitation
        communityInvitationRepository.delete(invitation);

        // 🔹 Step 5: Logging
        //log.info("User {} accepted invitation to community {}", userId, communityId);
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
            // 🔹 Validate community
            communityRepository.findById(communityId)
                    .orElseThrow(() -> new EntityNotFoundException("Community does not exist"));

            // 🔹 Step 1: Fetch all users
            List<User> allUsers = userRepository.findAll();

            // 🔹 Step 2: Fetch memberships (single query)
            List<CommunityMembership> memberships =
                    communityMembershipRepository.findByCommunityId(communityId);

            // 🔹 Step 3: Build membership map
            Map<String, MembershipStatus> membershipMap = memberships.stream()
                    .collect(Collectors.toMap(
                            CommunityMembership::getUserId,
                            CommunityMembership::getStatus,
                            (a, b) -> a // resolve duplicates safely
                    ));

            // 🔹 Step 4: Fetch pending invitations (single query)
            List<CommunityInvitation> invitations =
                    communityInvitationRepository.findByCommunity_IdAndStatus(communityId, "P");

            Set<String> invitedUserIds = invitations.stream()
                    .map(inv -> inv.getUser().getUserId())
                    .collect(Collectors.toSet());

            // 🔹 Step 5: Filter users
            for (User user : allUsers) {

                String userId = user.getUserId();

                // ❌ Skip if already APPROVED member
                if (membershipMap.containsKey(userId) &&
                        membershipMap.get(userId) == MembershipStatus.APPROVED) {
                    continue;
                }

                // ❌ Skip if already invited
                if (invitedUserIds.contains(userId)) {
                    continue;
                }

                // 🔹 Add to result
                UserDTO dto = new UserDTO();
                dto.setUserId(user.getUserId());
                dto.setEmail(user.getEmail());
                dto.setFirstName(user.getFirstName());
                dto.setLastName(user.getLastName());

                availableUsers.add(dto);
            }

            //log.info("Total available users to invite: {}", availableUsers.size());

        } catch (Exception e) {
            //log.error("Error getting available users to invite", e);
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
                List<String> approvedUserIds = communityMembershipRepository.findApprovedUserIds();
                if (approvedUserIds.isEmpty()) {
                    return eligibleUsers;
                }

                List<User> users = userRepository.findByUserIdIn(approvedUserIds);
                for (User user : users) {
                    UserDTO userDTO = new UserDTO();
                    userDTO.setUserId(user.getUserId());
                    userDTO.setEmail(user.getEmail());
                    userDTO.setFirstName(user.getFirstName());
                    userDTO.setLastName(user.getLastName());
                    eligibleUsers.add(userDTO);
                    System.out.println("User " + user.getEmail() + " is eligible for general meeting (approved membership found)");
                }

                System.out.println("Total eligible users for general meeting: " + eligibleUsers.size());
            }
        } catch (Exception e) {
            System.err.println("Error getting eligible meeting participants: " + e.getMessage());
            e.printStackTrace();
        }
        return eligibleUsers;
    }

    private Map<String, Object> mapCommunity(Community c) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", c.getId());
        data.put("communityName", c.getName());
        data.put("communityDescription", c.getDescription());
        data.put("isPrivate", c.isPrivate());
        return data;
    }

    private void linkUserToCommunity(User user, Community community, Role role) {

        Optional<CommunityMembership> existingOpt =
                communityMembershipRepository.findByUserIdAndCommunityId(
                        user.getUserId(),
                        community.getId()
                );

        if (existingOpt.isPresent()) {

            CommunityMembership existing = existingOpt.get();

            // 🔥 Upgrade role if needed (e.g., MEMBER → ADMIN)
            if (role == Role.ADMIN && existing.getRole() != Role.ADMIN) {
                existing.setRole(Role.ADMIN);
            }

            // ✅ Always approve the membership when linking user to community
            if (existing.getStatus() != MembershipStatus.APPROVED) {
                existing.setStatus(MembershipStatus.APPROVED);
            }

            communityMembershipRepository.save(existing);
            return;
        }

        // ✅ Create new membership
        CommunityMembership membership = new CommunityMembership();
        membership.setUserId(user.getUserId());
        membership.setCommunityId(community.getId());
        membership.setRole(role);
        membership.setStatus(MembershipStatus.APPROVED);

        communityMembershipRepository.save(membership);
    }

    private List<CommunityRequestDTO> mapCommunityRequestToDto(List<CommunityRequest> communityRequests) {
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

    @Transactional
    public void assignCommunityAdmin(String currentUserId, String targetUserId, Long communityId) {

        // 🔐 Step 1: Validate current user is admin (community-level or system-level)
        boolean isCommunityAdmin = communityMembershipRepository
                .existsByUserIdAndCommunityIdAndRoleAndStatus(
                        currentUserId,
                        communityId,
                        Role.ADMIN,
                        MembershipStatus.APPROVED
                );

        boolean isSystemAdmin = authenticationManager.isAdmin();

        if (!isCommunityAdmin && !isSystemAdmin) {
            throw new SecurityException("Only community admin can assign admin role");
        }

        // 🔹 Step 2: Validate community exists
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new EntityNotFoundException("Community not found"));

        // 🔹 Step 3: Validate target user exists
        User targetUser = userRepository.findByUserId(targetUserId);
        if (targetUser == null) {
            throw new EntityNotFoundException("Target user not found");
        }

        // 🔹 Step 4: Check existing membership
        Optional<CommunityMembership> existingOpt =
                communityMembershipRepository.findByUserIdAndCommunityId(
                        targetUserId,
                        communityId
                );

        if (existingOpt.isPresent()) {

            CommunityMembership membership = existingOpt.get();

            // 🔥 Upgrade role if needed
            if (membership.getRole() != Role.ADMIN) {
                membership.setRole(Role.ADMIN);
                membership.setStatus(MembershipStatus.APPROVED);
                communityMembershipRepository.save(membership);
            }

        } else {

            // 🔹 Create new ADMIN membership
            CommunityMembership membership = new CommunityMembership();
            membership.setUserId(targetUserId);
            membership.setCommunityId(communityId);
            membership.setRole(Role.ADMIN);
            membership.setStatus(MembershipStatus.APPROVED);

            communityMembershipRepository.save(membership);
        }
    }

    // Utility method if needed
    private Map<String, Object> extractCommunityData(Object communityResponse) {
        Map<String, Object> normalizedCommunity = new HashMap<>();

        if (communityResponse instanceof CommunityDTO communityDTO) {
            normalizedCommunity.put("id", communityDTO.getId());
            normalizedCommunity.put("communityName", communityDTO.getName());
            normalizedCommunity.put("communityDescription", communityDTO.getDescription());
        }

        return normalizedCommunity;
    }
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCommunityPayments(Long communityId) {

        List<Map<String, Object>> result = new ArrayList<>();

        try {
            // 🔹 Validate community
            communityRepository.findById(communityId)
                    .orElseThrow(() -> new EntityNotFoundException("Community not found"));

            // 🔹 Fetch successful payments
            List<PaymentTransaction> transactions = paymentTransactionRepository.
                    findByStatusAndType(PaymentStatus.SUCCESS,
                    PaymentType.SUBSCRIPTION);

            for (PaymentTransaction tx : transactions) {

                Map<String, Object> data = new HashMap<>();
                data.put("transactionId", tx.getId());
                data.put("userId", tx.getUserId());
                data.put("amount", tx.getAmount());
                data.put("type", tx.getType());
                data.put("status", tx.getStatus());
                data.put("date", tx.getCreatedAt());

                result.add(data);
            }

        } catch (Exception e) {
            //log.error("Error fetching community payments", e);
        }

        return result;
    }
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCommunityPaymentsPerMember(Long communityId) {

        List<Map<String, Object>> result = new ArrayList<>();

        try {
            // 🔹 Validate community
            communityRepository.findById(communityId)
                    .orElseThrow(() -> new EntityNotFoundException("Community not found"));

            // 🔹 Fetch successful payments
            List<PaymentTransaction> transactions =
                    paymentTransactionRepository.findByCommunityIdAndStatus(
                            communityId,
                            PaymentStatus.SUCCESS
                    );

            if (transactions.isEmpty()) {
                return result;
            }

            // 🔹 Group by userId
            Map<String, List<PaymentTransaction>> grouped =
                    transactions.stream()
                            .collect(Collectors.groupingBy(PaymentTransaction::getUserId));

            // 🔹 Fetch all users in one query
            List<String> userIds = new ArrayList<>(grouped.keySet());

            Map<String, User> userMap = userRepository.findByUserIdIn(userIds)
                    .stream()
                    .collect(Collectors.toMap(User::getUserId, u -> u));

            // 🔹 Build response
            for (Map.Entry<String, List<PaymentTransaction>> entry : grouped.entrySet()) {

                String userId = entry.getKey();
                List<PaymentTransaction> userTxs = entry.getValue();

                BigDecimal totalAmount = userTxs.stream()
                        .map(PaymentTransaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                Map<String, Object> data = new HashMap<>();

                User user = userMap.get(userId);

                data.put("userId", userId);
                data.put("firstName", user != null ? user.getFirstName() : null);
                data.put("lastName", user != null ? user.getLastName() : null);
                data.put("totalAmount", totalAmount);

                // 🔹 Add transactions
                List<Map<String, Object>> txList = userTxs.stream().map(tx -> {
                    Map<String, Object> txData = new HashMap<>();
                    txData.put("transactionId", tx.getId());
                    txData.put("amount", tx.getAmount());
                    txData.put("type", tx.getType());
                    txData.put("date", tx.getCreatedAt());
                    return txData;
                }).toList();

                data.put("transactions", txList);

                result.add(data);
            }

        } catch (Exception e) {
            //log.error("Error fetching payments per member", e);
        }

        return result;
    }
    // Utility method if needed
}
