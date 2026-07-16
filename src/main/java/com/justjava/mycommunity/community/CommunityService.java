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
import com.justjava.mycommunity.community.dto.BillingCycle;
import com.justjava.mycommunity.community.dto.SubscriptionStatus;
import com.justjava.mycommunity.community.mapper.CommunityMapper;
import com.justjava.mycommunity.community.repository.CommunityMembershipRepository;
import com.justjava.mycommunity.community.repository.DonationRepository;
import com.justjava.mycommunity.community.repository.MembershipSubscriptionRepository;
import com.justjava.mycommunity.community.repository.PaymentTransactionRepository;
import com.justjava.mycommunity.community.repository.SubscriptionPlanRepository;
import com.justjava.mycommunity.community.services.SubscriptionBillingService;
import com.justjava.mycommunity.event.Event;
import com.justjava.mycommunity.chat.repository.EventRepository;
import com.justjava.mycommunity.organization.Channel;
import com.justjava.mycommunity.organization.Organization;
import com.justjava.mycommunity.organization.TownHall;
import com.justjava.mycommunity.organization.TownHallChannelService;
import com.justjava.mycommunity.invoice.Invoice;
import com.justjava.mycommunity.invoice.InvoiceRepository;
import com.justjava.mycommunity.invoice.Status;
import com.justjava.mycommunity.keycloak.KeycloakAdminService;
import com.justjava.mycommunity.keycloak.KeycloakResource;
import com.justjava.mycommunity.userManagement.UserRepository;
import com.justjava.mycommunity.userManagement.UserDTO;
import com.justjava.mycommunity.community.dto.CommunityDTO;
import com.justjava.mycommunity.util.ResendService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private final MembershipSubscriptionRepository membershipSubscriptionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final DonationRepository donationRepository;
    private final EventRepository eventRepository;
    private final ResendService resendService;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionBillingService subscriptionBillingService;
    private final InvoiceRepository invoiceRepository;
    private final KeycloakAdminService keycloakAdminService;


    public CommunityDTO createCommunity(CreateCommunityVO dto) {
        User user = resolveUser(dto);
        Channel channel = townHallChannelService.createChannel(dto.getChannelName(), dto.getChannelDescription());

        TownHall townHall = townHallChannelService.createTownHall(dto.getTownHallName(), dto.getTownHallDescription());
        Organization organization = resolveOrganization(user);
        Community community = new Community();
        community.setName(dto.getCommunityName());
        community.setDescription(dto.getCommunityDescription());
        community.setOrganization(user.getOrganization() != null ? user.getOrganization() : organization);
        community.setChannel(channel);
        community.setTownHall(townHall);
        boolean isPrivate = dto.getIsPrivate() != null ? dto.getIsPrivate() : false;
        community.setPrivate(isPrivate);
        community.setCommunityPrivacy(isPrivate);
        community = communityRepository.save(community);
        linkUserToCommunity(user, community, Role.CREATOR);

        return communityMapper.toDto(community,communityMembershipRepository);
    }

    /**
     * If this user registered via the landing-page "Register your community" flow,
     * their Keycloak user has attributes describing the club to create. Do it now,
     * then clear the attributes so the flow only ever fires once.
     */
    @Transactional
    public boolean processPendingClubCreation(String userId) {
        System.out.println("[CLUB-CREATE] === processPendingClubCreation called with userId=" + userId + " ===");
        if (userId == null || userId.isBlank()) {
            System.out.println("[CLUB-CREATE] ABORT: userId is null/blank");
            return false;
        }

        // Dump ALL Keycloak attributes for this user so we can see exactly what's there
        Map<String, List<String>> allAttrs =
                keycloakAdminService.dumpUserAttributes(KeycloakResource.COMMUNITY_REALM, userId);

        java.util.function.Function<String, String> attr = key -> {
            List<String> v = allAttrs.get(key);
            return (v == null || v.isEmpty()) ? null : v.get(0);
        };

        String pending = attr.apply("pendingClubCreation");
        System.out.println("[CLUB-CREATE] pendingClubCreation attribute value = " + pending);
        if (pending == null || !"true".equalsIgnoreCase(pending.trim())) {
            System.out.println("[CLUB-CREATE] ABORT: pendingClubCreation is not 'true' (was: " + pending + ")");
            return false;
        }

        String clubName = attr.apply("clubName");
        clubName = clubName == null ? "" : clubName.trim();
        System.out.println("[CLUB-CREATE] clubName = '" + clubName + "'");
        if (clubName.isEmpty()) {
            System.out.println("[CLUB-CREATE] ABORT: clubName is empty — clearing attrs");
            keycloakAdminService.clearUserAttributes(
                    KeycloakResource.COMMUNITY_REALM, userId,
                    List.of("pendingClubCreation", "clubName", "clubDescription", "clubPrivacy"));
            return false;
        }

        String clubDescription = attr.apply("clubDescription");
        clubDescription = clubDescription == null ? "" : clubDescription;
        String clubPrivacyRaw = attr.apply("clubPrivacy");
        boolean isPrivate = "true".equalsIgnoreCase(clubPrivacyRaw) || "private".equalsIgnoreCase(clubPrivacyRaw);
        System.out.println("[CLUB-CREATE] clubDescription = '" + clubDescription + "' clubPrivacyRaw='" + clubPrivacyRaw + "' isPrivate=" + isPrivate);

        User localUser = userRepository.findByUserId(userId);
        System.out.println("[CLUB-CREATE] local User lookup by userId: " + (localUser == null ? "null" : ("id=" + localUser.getId() + " email=" + localUser.getEmail())));
        if (localUser == null || localUser.getEmail() == null) {
            System.out.println("[CLUB-CREATE] ABORT: local user not yet persisted — will retry on next request. Attributes left in place.");
            return false;
        }

        CreateCommunityVO vo = CreateCommunityVO.builder()
                .communityName(clubName)
                .communityDescription(clubDescription)
                .channelName(clubName)
                .channelDescription(clubDescription)
                .townHallName(clubName)
                .townHallDescription(clubDescription)
                .userId(userId)
                .userEmail(localUser.getEmail())
                .isPrivate(isPrivate)
                .build();

        try {
            System.out.println("[CLUB-CREATE] Calling createCommunity(...) with VO for '" + clubName + "'");
            CommunityDTO created = createCommunity(vo);
            System.out.println("[CLUB-CREATE] SUCCESS: created community id=" + (created != null ? created.getId() : "?"));
            return true;
        } catch (RuntimeException ex) {
            System.err.println("[CLUB-CREATE] FAILED createCommunity: " + ex.getClass().getName() + ": " + ex.getMessage());
            ex.printStackTrace();
            throw ex;
        } finally {
            System.out.println("[CLUB-CREATE] Clearing pending* attributes on Keycloak user " + userId);
            keycloakAdminService.clearUserAttributes(
                    KeycloakResource.COMMUNITY_REALM, userId,
                    List.of("pendingClubCreation", "clubName", "clubDescription", "clubPrivacy"));
        }
    }

    private User resolveUser(CreateCommunityVO dto) {
        // Prefer userId (Keycloak sub) — it's unique. Email lookups can throw
        // NonUniqueResultException when the same address exists on multiple rows
        // (e.g. after a Keycloak re-sync).
        if (dto.getUserId() != null && !dto.getUserId().isBlank()) {
            User byId = userRepository.findByUserId(dto.getUserId());
            if (byId != null) return byId;
        }
        if (dto.getUserEmail() != null && !dto.getUserEmail().isBlank()) {
            User byEmail = userRepository.findFirstByEmailOrderByIdAsc(dto.getUserEmail());
            if (byEmail != null) return byEmail;
        }
        throw new EntityNotFoundException(
                "User not found (userId=" + dto.getUserId() + ", email=" + dto.getUserEmail() + ")");
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
                .orElseThrow(() -> new EntityNotFoundException("Club not found")),communityMembershipRepository);
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
            isMember = communityMembershipRepository.existsActiveMembership(userId, communityId);
        }

        if (!isAdmin && !isMember) {
            throw new SecurityException("User does not have permission to update this mycommunity");
        }

        community.setName(vo.getCommunityName());
        community.setDescription(vo.getCommunityDescription());
        if (vo.getIsPrivate() != null) {
            community.setCommunityPrivacy(vo.getIsPrivate());
            community.setPrivate(vo.getIsPrivate());
        }
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

            if (isSuspensionActive(existingMembership)) {
                throw new IllegalStateException("User is currently suspended in this community");
            }

            if (existingMembership.getStatus() != MembershipStatus.APPROVED) {
                existingMembership.setStatus(MembershipStatus.APPROVED);
            }

            if (existingMembership.getRole() == null) {
                existingMembership.setRole(Role.MEMBER);
            }
            clearSuspension(existingMembership);

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
                    communityMembershipRepository.findActiveByCommunityId(communityId);

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

            // 🔹 Step 4: Map role and status (optional but powerful)
            Map<String, Role> roleMap = memberships.stream()
                    .collect(Collectors.toMap(
                            CommunityMembership::getUserId,
                            CommunityMembership::getRole
                    ));

            Map<String, MembershipStatus> statusMap = memberships.stream()
                    .collect(Collectors.toMap(
                            CommunityMembership::getUserId,
                            CommunityMembership::getStatus
                    ));

            // 🔹 Step 5: Convert to DTO
            for (User user : users) {

                UserDTO dto = new UserDTO();
                dto.setUserId(user.getUserId());
                dto.setEmail(user.getEmail());
                dto.setFirstName(user.getFirstName());
                dto.setLastName(user.getLastName());

                // 🔥 Optional: include role and status
                dto.setRole(roleMap.get(user.getUserId()));
                dto.setStatus(statusMap.get(user.getUserId()).name());
                communityMembers.add(dto);
            }
            applyMemberPaymentTotals(communityId, communityMembers);

        } catch (Exception e) {
            System.err.println("Error getting community members: " + e.getMessage());
            e.printStackTrace();
        }

        return communityMembers;
    }

    /**
     * Get ALL community members (APPROVED + SUSPENDED) with suspension details.
     * Intended for admin views so admins can see and manage suspended members.
     */
    public List<UserDTO> getCommunityMembersAll(Long communityId) {
        List<UserDTO> communityMembers = new ArrayList<>();
        try {
            List<CommunityMembership> memberships = communityMembershipRepository.findByCommunityId(communityId)
                    .stream()
                    .filter(m -> m.getStatus() == MembershipStatus.APPROVED || m.getStatus() == MembershipStatus.SUSPENDED)
                    .toList();

            if (memberships.isEmpty()) return communityMembers;

            List<String> userIds = memberships.stream().map(CommunityMembership::getUserId).distinct().toList();
            List<com.justjava.mycommunity.chat.entity.User> users = userRepository.findByUserIdIn(userIds);
            Map<String, CommunityMembership> membershipMap = memberships.stream()
                    .collect(Collectors.toMap(CommunityMembership::getUserId, m -> m));

            for (com.justjava.mycommunity.chat.entity.User user : users) {
                CommunityMembership m = membershipMap.get(user.getUserId());
                if (m == null) continue;
                UserDTO dto = new UserDTO();
                dto.setUserId(user.getUserId());
                dto.setEmail(user.getEmail());
                dto.setFirstName(user.getFirstName());
                dto.setLastName(user.getLastName());
                dto.setRole(m.getRole());
                dto.setStatus(m.getStatus().name());
                boolean activelySuspended = m.getStatus() == MembershipStatus.SUSPENDED
                        && (m.getSuspendedUntil() == null || m.getSuspendedUntil().isAfter(LocalDateTime.now()));
                dto.setSuspended(activelySuspended);
                dto.setSuspendedUntil(m.getSuspendedUntil());
                dto.setSuspensionReason(m.getSuspensionReason());
                communityMembers.add(dto);
            }
            applyMemberPaymentTotals(communityId, communityMembers);
        } catch (Exception e) {
            System.err.println("Error getting all community members: " + e.getMessage());
            e.printStackTrace();
        }
        return communityMembers;
    }

    private void applyMemberPaymentTotals(Long communityId, List<UserDTO> communityMembers) {
        if (communityMembers == null || communityMembers.isEmpty()) return;

        List<PaymentTransaction> successfulTransactions =
                paymentTransactionRepository.findByCommunityIdAndStatus(communityId, PaymentStatus.SUCCESS);

        if (successfulTransactions.isEmpty()) {
            for (UserDTO member : communityMembers) {
                member.setTotalDonationPaid(BigDecimal.ZERO);
                member.setTotalSubscriptionPaid(BigDecimal.ZERO);
                member.setTotalPaymentToDate(BigDecimal.ZERO);
            }
            return;
        }

        Map<String, BigDecimal> donationTotalsByUser = new HashMap<>();
        Map<String, BigDecimal> subscriptionTotalsByUser = new HashMap<>();

        for (PaymentTransaction tx : successfulTransactions) {
            if (tx.getUserId() == null || tx.getAmount() == null || tx.getType() == null) continue;
            if (tx.getType() == PaymentType.DONATION) {
                donationTotalsByUser.merge(tx.getUserId(), tx.getAmount(), BigDecimal::add);
            } else if (tx.getType() == PaymentType.SUBSCRIPTION) {
                subscriptionTotalsByUser.merge(tx.getUserId(), tx.getAmount(), BigDecimal::add);
            }
        }

        for (UserDTO member : communityMembers) {
            BigDecimal donationTotal = donationTotalsByUser.getOrDefault(member.getUserId(), BigDecimal.ZERO);
            BigDecimal subscriptionTotal = subscriptionTotalsByUser.getOrDefault(member.getUserId(), BigDecimal.ZERO);
            member.setTotalDonationPaid(donationTotal);
            member.setTotalSubscriptionPaid(subscriptionTotal);
            member.setTotalPaymentToDate(donationTotal.add(subscriptionTotal));
        }
    }

    /**
     * Get all approved community members excluding the specified user.
     * Used for network page to show potential connections within the same community.
     */
    @Transactional(readOnly = true)
    public List<UserDTO> getCommunityMembersExcludingUser(Long communityId, String excludeUserId) {
        return getCommunityMembers(communityId).stream()
                .filter(dto -> !dto.getUserId().equals(excludeUserId))
                .toList();
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

            if (isSuspensionActive(existing)) {
                throw new IllegalStateException("User is currently suspended in this community");
            }

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

            // 🔹 NORMAL USER → use membership (include actively-suspended memberships)
            List<CommunityMembership> memberships =
                    communityMembershipRepository.findApprovedOrSuspendedByUserId(userId);

            if (memberships.isEmpty()) {
                return userCommunities;
            }

            // 🔹 Build a quick lookup of communityId → membership for suspension info
            Map<Long, CommunityMembership> membershipByComId = new HashMap<>();
            for (CommunityMembership m : memberships) {
                membershipByComId.put(m.getCommunityId(), m);
            }

            // 🔹 Extract community IDs
            List<Long> communityIds = memberships.stream()
                    .map(CommunityMembership::getCommunityId)
                    .distinct()
                    .toList();

            // 🔹 Fetch communities in one query
            List<Community> communities = communityRepository.findAllById(communityIds);

            for (Community community : communities) {
                Map<String, Object> communityMap = mapCommunity(community);
                CommunityMembership m = membershipByComId.get(community.getId());
                boolean activelySuspended = m != null
                        && m.getStatus() == MembershipStatus.SUSPENDED
                        && (m.getSuspendedUntil() == null || m.getSuspendedUntil().isAfter(java.time.LocalDateTime.now()));
                communityMap.put("suspended", activelySuspended);
                if (activelySuspended && m != null) {
                    communityMap.put("suspendedUntil", m.getSuspendedUntil());
                    communityMap.put("suspensionReason", m.getSuspensionReason());
                }
                userCommunities.add(communityMap);
            }

        } catch (Exception e) {
            System.err.println("Error getting user communities: " + e.getMessage());
            e.printStackTrace();
        }

        return userCommunities;
    }

    /**
     * Returns only communities where the current user has an ADMIN role.
     * For platform-level admins, returns all communities.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAdminManagedCommunities(String userId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            boolean isPlatformAdmin = authenticationManager.isAdmin();
            if (isPlatformAdmin) {
                communityRepository.findAll().forEach(c -> result.add(mapCommunity(c)));
                return result;
            }
            // Non-platform admin: only communities where the user holds ADMIN/CREATOR role
            List<Long> adminCommunityIds = communityMembershipRepository.findAdminCommunityIdsByUserId(userId);
            if (!adminCommunityIds.isEmpty()) {
                communityRepository.findAllById(adminCommunityIds).forEach(c -> result.add(mapCommunity(c)));
            }
        } catch (Exception e) {
            System.err.println("Error getting admin-managed communities for user " + userId + ": " + e.getMessage());
        }
        return result;
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

            if (isSuspensionActive(membership)) {
                throw new IllegalStateException("User is currently suspended in this community");
            }

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
                clearSuspension(membership);
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

        // 🔹 Step 5: Send invitation email to the invited user
        try {
            String invitedEmail = user.getEmail();
            String invitedName = (user.getFirstName() != null ? user.getFirstName() : "") +
                    (user.getLastName() != null ? " " + user.getLastName() : "");
            String communityName = community.getName();

            String htmlContent = "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;background:#f9fafb;padding:20px;'>" +
                    "<div style='max-width:520px;margin:auto;background:#fff;border-radius:12px;padding:32px;box-shadow:0 2px 8px rgba(0,0,0,0.08);'>" +
                    "<h2 style='color:#2A2494;margin-bottom:8px;'>You've Been Invited!</h2>" +
                    "<p style='color:#374151;'>Hi " + invitedName.trim() + ",</p>" +
                    "<p style='color:#374151;'>You have been invited to join the club <strong>" + communityName + "</strong> on <strong>Clubknit</strong>.</p>" +
                    "<p style='color:#374151;'>Log in to your Clubknit account and go to the <strong>Invitations</strong> tab in Clubs to accept or decline.</p>" +
                    "<div style='margin:24px 0;display:flex;gap:12px;justify-content:center;flex-wrap:wrap;'>" +
                    "<a href='https://clubknit.app/organizations?tab=invitations' style='background:#2A2494;color:#fff;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:600;display:inline-block;'>View on Web</a>" +
                    "<a href='https://clubknit.app/mobile/organizations?tab=invitations' style='background:#3730a3;color:#fff;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:600;display:inline-block;'>View on Mobile</a>" +
                    "</div>" +
                    "<p style='color:#9ca3af;font-size:12px;margin-top:24px;'>If you did not expect this invitation, you can safely ignore this email.</p>" +
                    "</div></body></html>";

            resendService.sendHtmlEmail(invitedEmail, "You've been invited to join " + communityName + " on Clubknit", htmlContent);
        } catch (Exception e) {
            System.err.println("Failed to send invitation email: " + e.getMessage());
        }

        // 🔹 Step 6: Logging
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

            if (isSuspensionActive(membership)) {
                throw new IllegalStateException("Membership is currently suspended");
            }

            // 🔥 Accept invitation → approve membership
            membership.setStatus(MembershipStatus.APPROVED);
            membership.setRole(Role.MEMBER);
            clearSuspension(membership);

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

    /**
     * Returns suspension info for the given user in the given community,
     * or null if they are not currently suspended.
     */
    public Map<String, Object> getCurrentUserSuspension(String userId, Long communityId) {
        try {
            Optional<CommunityMembership> opt = communityMembershipRepository.findByUserIdAndCommunityId(userId, communityId);
            if (opt.isEmpty()) return null;
            CommunityMembership m = opt.get();
            if (m.getStatus() != MembershipStatus.SUSPENDED) return null;
            // Check if suspension has expired
            if (m.getSuspendedUntil() != null && !m.getSuspendedUntil().isAfter(java.time.LocalDateTime.now())) {
                // auto-lift
                m.setStatus(MembershipStatus.APPROVED);
                clearSuspension(m);
                communityMembershipRepository.save(m);
                return null;
            }
            Map<String, Object> info = new HashMap<>();
            info.put("suspendedUntil", m.getSuspendedUntil());
            info.put("suspensionReason", m.getSuspensionReason());
            return info;
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public void suspendCommunityMember(String adminUserId,
                                       String targetUserId,
                                       Long communityId,
                                       LocalDateTime suspendedUntil,
                                       String reason) {
        boolean isSystemAdmin = authenticationManager.isAdmin();
        boolean isCommunityAdmin = communityMembershipRepository.isUserCommunityAdmin(adminUserId, communityId);
        if (!isSystemAdmin && !isCommunityAdmin) {
            throw new SecurityException("Only community admins can suspend members");
        }

        CommunityMembership membership = communityMembershipRepository
                .findByUserIdAndCommunityId(targetUserId, communityId)
                .orElseThrow(() -> new EntityNotFoundException("Membership not found"));

        if (membership.getRole() == Role.CREATOR) {
            throw new IllegalStateException("Creators cannot be suspended");
        }
        if (membership.getRole() == Role.ADMIN) {
            throw new IllegalStateException("Admins cannot be suspended");
        }
        if (membership.getStatus() != MembershipStatus.APPROVED && membership.getStatus() != MembershipStatus.SUSPENDED) {
            throw new IllegalStateException("Only approved members can be suspended");
        }
        if (suspendedUntil != null && !suspendedUntil.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Suspension end time must be in the future");
        }

        membership.setStatus(MembershipStatus.SUSPENDED);
        membership.setSuspendedAt(LocalDateTime.now());
        membership.setSuspendedUntil(suspendedUntil);
        membership.setSuspensionReason(reason == null || reason.isBlank() ? null : reason.trim());
        membership.setSuspendedByUserId(adminUserId);
        communityMembershipRepository.save(membership);
    }

    @Transactional
    public void unsuspendCommunityMember(String adminUserId, String targetUserId, Long communityId) {
        boolean isSystemAdmin = authenticationManager.isAdmin();
        boolean isCommunityAdmin = communityMembershipRepository.isUserCommunityAdmin(adminUserId, communityId);
        if (!isSystemAdmin && !isCommunityAdmin) {
            throw new SecurityException("Only community admins can unsuspend members");
        }

        CommunityMembership membership = communityMembershipRepository
                .findByUserIdAndCommunityId(targetUserId, communityId)
                .orElseThrow(() -> new EntityNotFoundException("Membership not found"));

        if (membership.getStatus() != MembershipStatus.SUSPENDED) {
            throw new IllegalStateException("Member is not suspended");
        }

        membership.setStatus(MembershipStatus.APPROVED);
        clearSuspension(membership);
        communityMembershipRepository.save(membership);
    }

    private boolean isSuspensionActive(CommunityMembership membership) {
        if (membership.getStatus() != MembershipStatus.SUSPENDED) {
            return false;
        }
        if (membership.getSuspendedUntil() == null) {
            return true;
        }
        if (membership.getSuspendedUntil().isAfter(LocalDateTime.now())) {
            return true;
        }
        membership.setStatus(MembershipStatus.APPROVED);
        clearSuspension(membership);
        communityMembershipRepository.save(membership);
        return false;
    }

    private void clearSuspension(CommunityMembership membership) {
        membership.setSuspendedAt(null);
        membership.setSuspendedUntil(null);
        membership.setSuspensionReason(null);
        membership.setSuspendedByUserId(null);
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
            clearSuspension(existing);

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
                .isUserCommunityAdmin(currentUserId, communityId);

        boolean isSystemAdmin = authenticationManager.isAdmin();

        if (!isCommunityAdmin && !isSystemAdmin) {
            throw new SecurityException("Only community admin can assign admin role");
        }

        // 🔹 Step 2: Validate community exists
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new EntityNotFoundException("Club not found"));

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
                clearSuspension(membership);
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

    public void removeCommunityAdmin(String currentUserId, String targetUserId, Long communityId) {
        boolean isCreator = communityMembershipRepository.isUserCommunityCreator(currentUserId, communityId);
        boolean isSystemAdmin = authenticationManager.isAdmin();
        if (!isCreator && !isSystemAdmin) {
            throw new SecurityException("Only the community creator can remove an admin");
        }
        CommunityMembership membership = communityMembershipRepository
                .findByUserIdAndCommunityId(targetUserId, communityId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Membership not found"));
        if (membership.getRole() != Role.ADMIN) {
            throw new IllegalStateException("Target user is not an admin");
        }
        membership.setRole(Role.MEMBER);
        communityMembershipRepository.save(membership);
    }

    // ══════════════════ SUBSCRIPTIONS ══════════════════

    public boolean hasActiveSubscription(String userId, Long communityId) {
        return membershipSubscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .stream()
                .anyMatch(sub -> communityId.equals(sub.getCommunityId()));
    }

    @Transactional
    public SubscriptionPlan upsertSubscriptionPlan(Long communityId, Long planId, String name, BillingCycle billingCycle, BigDecimal amount) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Subscription name is required");
        }
        if (billingCycle == null) {
            throw new IllegalArgumentException("Billing cycle is required");
        }
        if (billingCycle == BillingCycle.ANNUALLY) {
            billingCycle = BillingCycle.YEARLY;
        }
        if (amount == null || amount.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Subscription amount must be at least N1");
        }

        String normalizedName = name.trim();
        Optional<SubscriptionPlan> existingByName =
                subscriptionPlanRepository.findByCommunityIdAndNameIgnoreCase(communityId, normalizedName);

        SubscriptionPlan plan;
        if (planId != null) {
            plan = subscriptionPlanRepository.findById(planId)
                    .orElseThrow(() -> new IllegalArgumentException("Subscription plan not found"));
            if (!communityId.equals(plan.getCommunityId())) {
                throw new IllegalArgumentException("Subscription plan does not belong to this community");
            }
            if (existingByName.isPresent() && !existingByName.get().getId().equals(planId)) {
                throw new IllegalArgumentException("Subscription name already exists in this community");
            }
        } else {
            if (existingByName.isPresent()) {
                throw new IllegalArgumentException("Subscription name already exists in this community");
            }
            plan = new SubscriptionPlan();
            plan.setCommunityId(communityId);
        }

        plan.setName(normalizedName);
        plan.setAmount(amount);
        plan.setBillingCycle(billingCycle);
        plan.setActive(true);
        SubscriptionPlan savedPlan = subscriptionPlanRepository.save(plan);
        autoSubscribeMembersForPlan(communityId, savedPlan);
        return savedPlan;
    }

    @Transactional
    public Map<String, Object> autoSubscribeMembersForPlan(Long communityId, SubscriptionPlan plan) {
        if (plan == null || plan.getId() == null) {
            throw new IllegalArgumentException("A valid subscription plan is required");
        }

        List<CommunityMembership> activeMemberships = communityMembershipRepository.findActiveByCommunityId(communityId);
        LocalDateTime now = LocalDateTime.now();

        int membersEvaluated = 0;
        int subscriptionsCreated = 0;
        int subscriptionsUpdated = 0;
        int invoicesCreated = 0;
        int invoicesSkipped = 0;
        int errors = 0;

        for (CommunityMembership membership : activeMemberships) {
            membersEvaluated++;
            try {
                String userId = membership.getUserId();
                Optional<MembershipSubscription> existingOpt =
                        membershipSubscriptionRepository.findByUserIdAndPlanId(userId, plan.getId());

                MembershipSubscription subscription;
                if (existingOpt.isEmpty()) {
                    subscription = new MembershipSubscription();
                    subscription.setUserId(userId);
                    subscription.setCommunityId(communityId);
                    subscription.setStartDate(now);
                    subscriptionsCreated++;
                } else {
                    subscription = existingOpt.get();
                    subscriptionsUpdated++;
                    if (subscription.getStartDate() == null) {
                        subscription.setStartDate(now);
                    }
                }

                subscription.setPlanId(plan.getId());
                subscription.setAmount(plan.getAmount());
                subscription.setStatus(SubscriptionStatus.ACTIVE);
                subscription.setNextBillingDate(now);
                membershipSubscriptionRepository.save(subscription);

                if (subscriptionBillingService.generateInvoiceForCycle(subscription, now).isPresent()) {
                    invoicesCreated++;
                } else {
                    invoicesSkipped++;
                }

                subscription.setNextBillingDate(
                        subscriptionBillingService.calculateNextBillingDate(now, plan.getBillingCycle())
                );
                membershipSubscriptionRepository.save(subscription);
            } catch (Exception e) {
                errors++;
                System.err.println("Failed auto-subscribing member in communityId=" + communityId + ": " + e.getMessage());
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("membersEvaluated", membersEvaluated);
        summary.put("subscriptionsCreated", subscriptionsCreated);
        summary.put("subscriptionsUpdated", subscriptionsUpdated);
        summary.put("invoicesCreated", invoicesCreated);
        summary.put("invoicesSkipped", invoicesSkipped);
        summary.put("errors", errors);
        return summary;
    }

    @Transactional(readOnly = true)
    public Optional<SubscriptionPlan> getActiveSubscriptionPlan(Long communityId) {
        return subscriptionPlanRepository.findByCommunityIdAndActiveTrue(communityId)
                .stream()
                .findFirst();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlan> getActiveSubscriptionPlans(Long communityId) {
        return subscriptionPlanRepository.findByCommunityIdAndActiveTrue(communityId);
    }

    @Transactional
    public void startSubscription(String userId, Long communityId, BigDecimal amount) {
        startSubscription(userId, communityId, amount, null);
    }

    @Transactional
    public void startSubscription(String userId, Long communityId, BigDecimal amount, String paystackRef) {

        // Validate membership (warn but don't block — payment already processed)
        boolean isMember = communityMembershipRepository
                .existsActiveMembership(userId, communityId);
        if (!isMember) {
            System.out.println("WARNING: startSubscription called for non-approved member userId=" + userId + " communityId=" + communityId + " — proceeding anyway since payment was verified.");
        }

        boolean hasActiveInCommunity = membershipSubscriptionRepository
                .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .stream()
                .anyMatch(sub -> communityId.equals(sub.getCommunityId()));
        if (hasActiveInCommunity) {
            throw new IllegalStateException("User already has active subscription(s) for this community");
        }

        SubscriptionPlan plan = getActiveSubscriptionPlan(communityId)
                .orElseThrow(() -> new IllegalStateException("No active subscription plan configured for this community"));
        BigDecimal subscriptionAmount = plan.getAmount();
        if (subscriptionAmount == null || subscriptionAmount.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalStateException("Active subscription plan amount is invalid");
        }

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        MembershipSubscription sub = new MembershipSubscription();
        sub.setUserId(userId);
        sub.setCommunityId(communityId);
        sub.setPlanId(plan.getId());
        sub.setAmount(subscriptionAmount);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartDate(now);
        sub.setNextBillingDate(now);
        membershipSubscriptionRepository.save(sub);
        Optional<Invoice> initialInvoiceOpt = subscriptionBillingService.generateInitialInvoice(sub);
        if (paystackRef != null && !paystackRef.isBlank() && initialInvoiceOpt.isPresent()) {
            Invoice initialInvoice = initialInvoiceOpt.get();
            initialInvoice.setStatus(Status.PAID);
            invoiceRepository.save(initialInvoice);
        }
        sub.setNextBillingDate(subscriptionBillingService.calculateNextBillingDate(now, plan.getBillingCycle()));
        membershipSubscriptionRepository.save(sub);

        PaymentTransaction tx = new PaymentTransaction();
        tx.setUserId(userId);
        tx.setCommunityId(communityId);
        tx.setAmount(subscriptionAmount);
        tx.setType(PaymentType.SUBSCRIPTION);
        tx.setStatus(PaymentStatus.PENDING);
        tx.setProviderRef(paystackRef != null ? paystackRef : "SUB-" + sub.getId());
        tx.setCreatedAt(now);
        paymentTransactionRepository.save(tx);
    }

    @Transactional
    public void cancelSubscription(String userId, Long subscriptionId) {

        MembershipSubscription sub = membershipSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new EntityNotFoundException("Subscription not found"));

        if (!sub.getUserId().equals(userId)) {
            throw new SecurityException("You can only cancel your own subscription");
        }

        if (sub.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("Subscription is not active");
        }

        sub.setStatus(SubscriptionStatus.CANCELLED);
        membershipSubscriptionRepository.save(sub);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCommunitySubscriptions(Long communityId) {

        List<Map<String, Object>> result = new ArrayList<>();

        List<MembershipSubscription> subscriptions = membershipSubscriptionRepository.findByCommunityId(communityId);
        if (subscriptions.isEmpty()) return result;

        // Fetch users in batch
        List<String> userIds = subscriptions.stream()
                .map(MembershipSubscription::getUserId)
                .distinct()
                .toList();

        Map<String, User> userMap = userRepository.findByUserIdIn(userIds)
                .stream()
                .collect(Collectors.toMap(User::getUserId, u -> u, (a, b) -> a));

        for (MembershipSubscription sub : subscriptions) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("subscriptionId", sub.getId());
            entry.put("userId", sub.getUserId());

            User user = userMap.get(sub.getUserId());
            entry.put("firstName", user != null ? user.getFirstName() : "Unknown");
            entry.put("lastName", user != null ? user.getLastName() : "");
            entry.put("email", user != null ? user.getEmail() : "");

            entry.put("amount", sub.getAmount());
            entry.put("status", sub.getStatus() != null ? sub.getStatus().name() : "UNKNOWN");
            entry.put("startDate", sub.getStartDate());
            entry.put("nextBillingDate", sub.getNextBillingDate());
            result.add(entry);
        }

        // Sort newest first
        result.sort((a, b) -> {
            Comparable da = (Comparable) b.get("startDate");
            Comparable db = (Comparable) a.get("startDate");
            if (da == null && db == null) return 0;
            if (da == null) return 1;
            if (db == null) return -1;
            return da.compareTo(db);
        });

        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getUserActiveSubscriptions(String userId) {
        List<MembershipSubscription> subscriptions =
                membershipSubscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
        return mapSubscriptionsForUser(subscriptions);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getUserPaidSubscriptions(String userId) {
        List<MembershipSubscription> subscriptions = membershipSubscriptionRepository.findByUserId(userId);
        if (subscriptions.isEmpty()) return new ArrayList<>();

        List<MembershipSubscription> paidSubscriptions = subscriptions.stream()
                .filter(this::hasPaidInvoiceForSubscription)
                .toList();
        return mapSubscriptionsForUser(paidSubscriptions);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCommunityActiveSubscriptions(Long communityId) {
        List<MembershipSubscription> subscriptions =
                membershipSubscriptionRepository.findByCommunityIdAndStatus(communityId, SubscriptionStatus.ACTIVE);
        return mapSubscriptionsForCommunity(subscriptions);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCommunityPaidActiveSubscriptions(Long communityId) {
        List<MembershipSubscription> activeSubscriptions =
                membershipSubscriptionRepository.findByCommunityIdAndStatus(communityId, SubscriptionStatus.ACTIVE);
        if (activeSubscriptions.isEmpty()) return new ArrayList<>();

        List<MembershipSubscription> paidActiveSubscriptions = activeSubscriptions.stream()
                .filter(this::hasPaidInvoiceForSubscription)
                .toList();
        return mapSubscriptionsForCommunity(paidActiveSubscriptions);
    }

    @Transactional(readOnly = true)
    public boolean hasPaidSubscription(String userId, Long communityId) {
        return membershipSubscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .stream()
                .filter(sub -> communityId.equals(sub.getCommunityId()))
                .anyMatch(this::hasPaidInvoiceForSubscription);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getUserSubscriptionsByStatus(String userId, SubscriptionStatus status) {
        return mapSubscriptionsForUser(membershipSubscriptionRepository.findByUserIdAndStatus(userId, status));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCommunitySubscriptionsByStatus(Long communityId, SubscriptionStatus status) {
        return mapSubscriptionsForCommunity(membershipSubscriptionRepository.findByCommunityIdAndStatus(communityId, status));
    }

    @Transactional(readOnly = true)
    public List<Invoice> getSubscriptionInvoices(Long subscriptionId) {
        return invoiceRepository.findByMerchantIdStartingWith("SUB-" + subscriptionId + "-");
    }

    @Transactional(readOnly = true)
    public List<Invoice> getPaidSubscriptionInvoices(Long subscriptionId) {
        return invoiceRepository.findByMerchantIdStartingWithAndStatus("SUB-" + subscriptionId + "-", Status.PAID);
    }

    @Transactional(readOnly = true)
    public List<Invoice> getUserSubscriptionInvoices(String userId) {
        List<MembershipSubscription> subscriptions = membershipSubscriptionRepository.findByUserId(userId);
        if (subscriptions.isEmpty()) return new ArrayList<>();

        List<Invoice> invoices = new ArrayList<>();
        for (MembershipSubscription sub : subscriptions) {
            invoices.addAll(invoiceRepository.findByMerchantIdStartingWith("SUB-" + sub.getId() + "-"));
        }

        invoices.sort((a, b) -> {
            if (a.getDueDate() == null && b.getDueDate() == null) return 0;
            if (a.getDueDate() == null) return 1;
            if (b.getDueDate() == null) return -1;
            return b.getDueDate().compareTo(a.getDueDate());
        });
        return invoices;
    }

    @Transactional(readOnly = true)
    public List<Invoice> getUserSubscriptionInvoices(String userId, Long subscriptionId) {
        if (subscriptionId == null) {
            return getUserSubscriptionInvoices(userId);
        }
        MembershipSubscription subscription = membershipSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new EntityNotFoundException("Subscription not found"));
        if (!userId.equals(subscription.getUserId())) {
            throw new SecurityException("You can only view invoices for your own subscription");
        }
        List<Invoice> invoices = new ArrayList<>(
                invoiceRepository.findByMerchantIdStartingWith("SUB-" + subscription.getId() + "-")
        );
        invoices.sort((a, b) -> {
            if (a.getDueDate() == null && b.getDueDate() == null) return 0;
            if (a.getDueDate() == null) return 1;
            if (b.getDueDate() == null) return -1;
            return b.getDueDate().compareTo(a.getDueDate());
        });
        return invoices;
    }

    @Transactional(readOnly = true)
    public Optional<Invoice> getUserSubscriptionInvoiceById(String userId, Long invoiceId) {
        if (invoiceId == null) return Optional.empty();
        return getUserSubscriptionInvoices(userId).stream()
                .filter(inv -> invoiceId.equals(inv.getId()))
                .findFirst();
    }

    @Transactional
    public void markUserSubscriptionInvoicePaid(String userId, Long invoiceId) {
        Invoice invoice = getUserSubscriptionInvoiceById(userId, invoiceId)
                .orElseThrow(() -> new SecurityException("Invoice not found for this user"));
        if (invoice.getStatus() != Status.PAID) {
            invoice.setStatus(Status.PAID);
            invoiceRepository.save(invoice);
        }
    }

    private boolean hasPaidInvoiceForSubscription(MembershipSubscription subscription) {
        return !invoiceRepository.findByMerchantIdStartingWithAndStatus(
                "SUB-" + subscription.getId() + "-", Status.PAID
        ).isEmpty();
    }

    private List<Map<String, Object>> mapSubscriptionsForUser(List<MembershipSubscription> subscriptions) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (subscriptions.isEmpty()) return result;

        List<Long> communityIds = subscriptions.stream()
                .map(MembershipSubscription::getCommunityId)
                .distinct()
                .toList();

        Map<Long, String> communityNameMap = new HashMap<>();
        for (Long cId : communityIds) {
            communityRepository.findById(cId).ifPresent(c -> communityNameMap.put(cId, c.getName()));
        }
        Map<Long, String> planNameMap = subscriptionPlanRepository.findAllById(
                        subscriptions.stream()
                                .map(MembershipSubscription::getPlanId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList())
                .stream()
                .collect(Collectors.toMap(SubscriptionPlan::getId, SubscriptionPlan::getName, (a, b) -> a));

        for (MembershipSubscription sub : subscriptions) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("subscriptionId", sub.getId());
            entry.put("communityId", sub.getCommunityId());
            entry.put("communityName", communityNameMap.getOrDefault(sub.getCommunityId(), "Unknown Community"));
            entry.put("planName", planNameMap.getOrDefault(sub.getPlanId(), "Subscription"));
            entry.put("amount", sub.getAmount());
            entry.put("status", sub.getStatus() != null ? sub.getStatus().name() : "UNKNOWN");
            entry.put("startDate", sub.getStartDate());
            entry.put("nextBillingDate", sub.getNextBillingDate());
            entry.put("hasPaidInvoice", hasPaidInvoiceForSubscription(sub));
            result.add(entry);
        }

        result.sort((a, b) -> {
            Comparable da = (Comparable) b.get("startDate");
            Comparable db = (Comparable) a.get("startDate");
            if (da == null && db == null) return 0;
            if (da == null) return 1;
            if (db == null) return -1;
            return da.compareTo(db);
        });

        return result;
    }

    private List<Map<String, Object>> mapSubscriptionsForCommunity(List<MembershipSubscription> subscriptions) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (subscriptions.isEmpty()) return result;

        List<String> userIds = subscriptions.stream()
                .map(MembershipSubscription::getUserId)
                .distinct()
                .toList();

        Map<String, User> userMap = userRepository.findByUserIdIn(userIds)
                .stream()
                .collect(Collectors.toMap(User::getUserId, u -> u, (a, b) -> a));
        Map<Long, String> planNameMap = subscriptionPlanRepository.findAllById(
                        subscriptions.stream()
                                .map(MembershipSubscription::getPlanId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList())
                .stream()
                .collect(Collectors.toMap(SubscriptionPlan::getId, SubscriptionPlan::getName, (a, b) -> a));

        for (MembershipSubscription sub : subscriptions) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("subscriptionId", sub.getId());
            entry.put("userId", sub.getUserId());
            User user = userMap.get(sub.getUserId());
            entry.put("firstName", user != null ? user.getFirstName() : "Unknown");
            entry.put("lastName", user != null ? user.getLastName() : "");
            entry.put("email", user != null ? user.getEmail() : "");
            entry.put("planName", planNameMap.getOrDefault(sub.getPlanId(), "Subscription"));
            entry.put("amount", sub.getAmount());
            entry.put("status", sub.getStatus() != null ? sub.getStatus().name() : "UNKNOWN");
            entry.put("startDate", sub.getStartDate());
            entry.put("nextBillingDate", sub.getNextBillingDate());
            entry.put("hasPaidInvoice", hasPaidInvoiceForSubscription(sub));
            result.add(entry);
        }

        result.sort((a, b) -> {
            Comparable da = (Comparable) b.get("startDate");
            Comparable db = (Comparable) a.get("startDate");
            if (da == null && db == null) return 0;
            if (da == null) return 1;
            if (db == null) return -1;
            return da.compareTo(db);
        });

        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getUserSubscriptions(String userId) {
        List<MembershipSubscription> subscriptions = membershipSubscriptionRepository.findByUserId(userId);
        return mapSubscriptionsForUser(subscriptions);
    }

    // ══════════════════ DONATIONS ══════════════════

    @Transactional
    public void makeDonation(String userId, Long communityId, Long eventId, BigDecimal amount, String message) {
        makeDonation(userId, communityId, eventId, amount, message, null);
    }

    @Transactional
    public void makeDonation(String userId, Long communityId, Long eventId, BigDecimal amount, String message, String paystackRef) {

        // Validate membership (warn but don't block — payment already processed)
        boolean isMember = communityMembershipRepository
                .existsActiveMembership(userId, communityId);
        if (!isMember) {
            System.out.println("WARNING: makeDonation called for non-approved member userId=" + userId + " communityId=" + communityId + " — proceeding anyway since payment was verified.");
        }

        // Validate event
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found"));

        if (!event.getCommunity().getId().equals(communityId)) {
            throw new IllegalArgumentException("Event does not belong to this community");
        }

        if (amount == null || amount.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Donation amount must be at least ₦1");
        }

        // Save donation record
        Donation donation = new Donation();
        donation.setUserId(userId);
        donation.setCommunityId(communityId);
        donation.setEventId(eventId);
        donation.setAmount(amount);
        donation.setMessage(message != null && !message.isBlank() ? message.trim() : null);
        donation.setDonatedAt(java.time.LocalDateTime.now());
        donation.setStatus(PaymentStatus.SUCCESS);
        donationRepository.save(donation);

        // Save payment transaction
        PaymentTransaction tx = new PaymentTransaction();
        tx.setUserId(userId);
        tx.setCommunityId(communityId);
        tx.setAmount(amount);
        tx.setType(PaymentType.DONATION);
        tx.setStatus(PaymentStatus.SUCCESS);
        tx.setProviderRef(paystackRef != null ? paystackRef : "DON-" + donation.getId());
        tx.setCreatedAt(java.time.LocalDateTime.now());
        paymentTransactionRepository.save(tx);
    }

    @Transactional
    public void promiseDonation(String userId, Long communityId, Long eventId, BigDecimal amount, String message) {

        // Validate membership (warn but don't block — pledge can be made)
        boolean isMember = communityMembershipRepository
                .existsActiveMembership(userId, communityId);
        if (!isMember) {
            System.out.println("WARNING: promiseDonation called for non-approved member userId=" + userId + " communityId=" + communityId + " — proceeding anyway.");
        }

        // Validate event
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found"));

        if (!event.getCommunity().getId().equals(communityId)) {
            throw new IllegalArgumentException("Event does not belong to this community");
        }

        if (amount == null || amount.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Donation amount must be at least ₦1");
        }

        // Save donation pledge record
        Donation donation = new Donation();
        donation.setUserId(userId);
        donation.setCommunityId(communityId);
        donation.setEventId(eventId);
        donation.setAmount(amount);
        donation.setMessage(message != null && !message.isBlank() ? message.trim() : null);
        donation.setDonatedAt(java.time.LocalDateTime.now());
        donation.setStatus(PaymentStatus.PENDING);
        donationRepository.save(donation);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCommunityDonations(Long communityId) {

        List<Map<String, Object>> result = new ArrayList<>();

        List<Donation> donations = donationRepository.findByCommunityId(communityId);
        if (donations.isEmpty()) return result;

        // Fetch users in batch
        List<String> userIds = donations.stream()
                .map(Donation::getUserId)
                .distinct()
                .toList();

        Map<String, User> userMap = userRepository.findByUserIdIn(userIds)
                .stream()
                .collect(Collectors.toMap(User::getUserId, u -> u));

        // Fetch events in batch
        List<Long> eventIds = donations.stream()
                .map(Donation::getEventId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Event> eventMap = eventRepository.findAllById(eventIds)
                .stream()
                .collect(Collectors.toMap(Event::getId, e -> e));

        for (Donation don : donations) {
            User user = userMap.get(don.getUserId());
            Event event = eventMap.get(don.getEventId());

            Map<String, Object> data = new HashMap<>();
            data.put("donationId", don.getId());
            data.put("userId", don.getUserId());
            data.put("firstName", user != null ? user.getFirstName() : null);
            data.put("lastName", user != null ? user.getLastName() : null);
            data.put("email", user != null ? user.getEmail() : null);
            data.put("eventId", don.getEventId());
            data.put("eventTitle", event != null ? event.getTitle() : "Unknown Event");
            data.put("amount", don.getAmount());
            data.put("message", don.getMessage());
            data.put("donatedAt", don.getDonatedAt());
            data.put("status", don.getStatus() != null ? don.getStatus().name() : "UNKNOWN");
            result.add(data);
        }

        // Sort newest first
        result.sort((a, b) -> {
            java.time.LocalDateTime da = (java.time.LocalDateTime) a.get("donatedAt");
            java.time.LocalDateTime db = (java.time.LocalDateTime) b.get("donatedAt");
            if (da == null || db == null) return 0;
            return db.compareTo(da);
        });

        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getUserDonations(String userId) {

        List<Map<String, Object>> result = new ArrayList<>();

        List<Donation> donations = donationRepository.findByUserId(userId);
        if (donations.isEmpty()) return result;

        // Fetch community names in batch
        List<Long> communityIds = donations.stream()
                .map(Donation::getCommunityId)
                .distinct()
                .toList();

        Map<Long, Community> communityMap = communityRepository.findAllById(communityIds)
                .stream()
                .collect(Collectors.toMap(Community::getId, c -> c));

        // Fetch events in batch
        List<Long> eventIds = donations.stream()
                .map(Donation::getEventId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Event> eventMap = eventRepository.findAllById(eventIds)
                .stream()
                .collect(Collectors.toMap(Event::getId, e -> e));

        for (Donation don : donations) {
            Community community = communityMap.get(don.getCommunityId());
            Event event = eventMap.get(don.getEventId());

            Map<String, Object> data = new HashMap<>();
            data.put("donationId", don.getId());
            data.put("communityId", don.getCommunityId());
            data.put("communityName", community != null ? community.getName() : "Unknown Community");
            data.put("eventId", don.getEventId());
            data.put("eventTitle", event != null ? event.getTitle() : "Unknown Event");
            data.put("amount", don.getAmount());
            data.put("message", don.getMessage());
            data.put("donatedAt", don.getDonatedAt());
            data.put("status", don.getStatus() != null ? don.getStatus().name() : "UNKNOWN");
            result.add(data);
        }

        // Sort newest first
        result.sort((a, b) -> {
            java.time.LocalDateTime da = (java.time.LocalDateTime) a.get("donatedAt");
            java.time.LocalDateTime db = (java.time.LocalDateTime) b.get("donatedAt");
            if (da == null || db == null) return 0;
            return db.compareTo(da);
        });
        return result;
    }

    @Transactional
    public void fulfillDonationPromise(Long donationId, String paystackRef) {
        Donation donation = donationRepository.findById(donationId)
                .orElseThrow(() -> new EntityNotFoundException("Donation pledge not found"));

        if (donation.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Donation is not in pending status");
        }

        donation.setStatus(PaymentStatus.SUCCESS);
        donationRepository.save(donation);

        // Record payment transaction
        PaymentTransaction tx = new PaymentTransaction();
        tx.setUserId(donation.getUserId());
        tx.setCommunityId(donation.getCommunityId());
        tx.setAmount(donation.getAmount());
        tx.setType(PaymentType.DONATION);
        tx.setStatus(PaymentStatus.SUCCESS);
        tx.setProviderRef(paystackRef != null ? paystackRef : "DON-" + donation.getId());
        tx.setCreatedAt(java.time.LocalDateTime.now());
        paymentTransactionRepository.save(tx);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getUserPendingDonations(String userId) {

        List<Map<String, Object>> result = new ArrayList<>();

        List<Donation> donations = donationRepository.findByUserIdAndStatus(userId, PaymentStatus.PENDING);
        if (donations.isEmpty()) return result;

        // Fetch community names in batch
        List<Long> communityIds = donations.stream()
                .map(Donation::getCommunityId)
                .distinct()
                .toList();

        Map<Long, Community> communityMap = communityRepository.findAllById(communityIds)
                .stream()
                .collect(Collectors.toMap(Community::getId, c -> c));

        // Fetch events in batch
        List<Long> eventIds = donations.stream()
                .map(Donation::getEventId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Event> eventMap = eventRepository.findAllById(eventIds)
                .stream()
                .collect(Collectors.toMap(Event::getId, e -> e));

        for (Donation don : donations) {
            Community community = communityMap.get(don.getCommunityId());
            Event event = eventMap.get(don.getEventId());

            Map<String, Object> data = new HashMap<>();
            data.put("donationId", don.getId());
            data.put("communityId", don.getCommunityId());
            data.put("communityName", community != null ? community.getName() : "Unknown Community");
            data.put("eventId", don.getEventId());
            data.put("eventTitle", event != null ? event.getTitle() : "Unknown Event");
            data.put("amount", don.getAmount());
            data.put("message", don.getMessage());
            data.put("donatedAt", don.getDonatedAt());
            data.put("status", don.getStatus() != null ? don.getStatus().name() : "UNKNOWN");
            result.add(data);
        }

        // Sort newest first
        result.sort((a, b) -> {
            java.time.LocalDateTime da = (java.time.LocalDateTime) a.get("donatedAt");
            java.time.LocalDateTime db = (java.time.LocalDateTime) b.get("donatedAt");
            if (da == null || db == null) return 0;
            return db.compareTo(da);
        });

        return result;
    }

    // ══════════════════ DASHBOARD STATS HELPERS ══════════════════

    @Transactional(readOnly = true)
    public BigDecimal getTotalDonationAmountForUserInCommunity(String userId, Long communityId) {
        List<Donation> donations = donationRepository.findByUserId(userId);
        return donations.stream()
                .filter(d -> d.getCommunityId().equals(communityId) && d.getStatus() == PaymentStatus.SUCCESS)
                .map(Donation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalPromisedDonationAmountForUserInCommunity(String userId, Long communityId) {
        List<Donation> donations = donationRepository.findByUserId(userId);
        return donations.stream()
                .filter(d -> d.getCommunityId().equals(communityId) && d.getStatus() == PaymentStatus.PENDING)
                .map(Donation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalSubscriptionAmountForUserInCommunity(String userId, Long communityId) {
        List<MembershipSubscription> subscriptions = membershipSubscriptionRepository.findByUserId(userId);
        return subscriptions.stream()
                .filter(s -> s.getCommunityId().equals(communityId) && s.getStatus() == SubscriptionStatus.ACTIVE)
                .map(MembershipSubscription::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalPendingSubscriptionInvoiceAmountForUserInCommunity(String userId, Long communityId) {
        List<MembershipSubscription> subscriptions = membershipSubscriptionRepository.findByUserId(userId);
        BigDecimal totalPending = BigDecimal.ZERO;
        
        for (MembershipSubscription sub : subscriptions) {
            if (sub.getCommunityId().equals(communityId) && sub.getStatus() == SubscriptionStatus.ACTIVE) {
                // Get all unpaid invoices for this subscription
                List<Invoice> pendingInvoices = invoiceRepository.findByMerchantIdStartingWithAndStatus(
                        "SUB-" + sub.getId() + "-", Status.NEW
                );
                totalPending = totalPending.add(
                        pendingInvoices.stream()
                                .map(Invoice::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                );
            }
        }
        
        return totalPending;
    }
}
