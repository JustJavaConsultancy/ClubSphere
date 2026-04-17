package com.justjava.mycommunity.mobile;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.dto.CreateChatDTO;
import com.justjava.mycommunity.chat.dto.CreateCommunityVO;
import com.justjava.mycommunity.chat.service.ChatService;
import com.justjava.mycommunity.community.CommunityGroupService;
import com.justjava.mycommunity.community.CommunityService;
import com.justjava.mycommunity.community.Donation;
import com.justjava.mycommunity.community.MembershipStatus;
import com.justjava.mycommunity.community.MembershipSubscription;
import com.justjava.mycommunity.community.dto.CommunityDTO;
import com.justjava.mycommunity.community.dto.SubscriptionStatus;
import com.justjava.mycommunity.community.repository.CommunityMembershipRepository;
import com.justjava.mycommunity.community.repository.DonationRepository;
import com.justjava.mycommunity.community.repository.MembershipSubscriptionRepository;
import com.justjava.mycommunity.network.NetworkService;
import com.justjava.mycommunity.posts.PostService;
import com.justjava.mycommunity.userManagement.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/mobile/my-community")
@RequiredArgsConstructor
public class MobileCommunityController {

    private final ChatService chatService;
    private final CommunityService communityService;
    private final NetworkService networkService;
    private final CommunityGroupService communityGroupService;
    private final AuthenticationManager authenticationManager;
    private final PostService postService;
    private final DonationRepository donationRepository;
    private final MembershipSubscriptionRepository membershipSubscriptionRepository;
    private final CommunityMembershipRepository communityMembershipRepository;

    @GetMapping
    public String mobileCommunityPage(HttpServletRequest request, Model model) {
        try {
            Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");
            if (selectedCommunityId == null) {
                return "redirect:/mobile/organizations";
            }

            CommunityDTO communityResponse = communityService.getCommunityById(selectedCommunityId);

            if (communityResponse == null) {
                model.addAttribute("errorMessage", "No community found. Please create a community first.");
                model.addAttribute("community", new HashMap<>());
                model.addAttribute("groups", new ArrayList<>());
                model.addAttribute("availableMembers", new ArrayList<>());
                model.addAttribute("availableUsersToInvite", new ArrayList<>());
                model.addAttribute("communityId", null);
                model.addAttribute("suggestedGroups", new ArrayList<>());
                model.addAttribute("groupRequests", new ArrayList<>());
                model.addAttribute("isAdmin", false);
                return "mobile-community";
            }

            Map<String, Object> normalizedCommunity = extractCommunityData(communityResponse);
            Long communityId = (Long) normalizedCommunity.get("id");

            if (communityId == null) {
                model.addAttribute("errorMessage", "Invalid community data - no ID found");
                model.addAttribute("community", normalizedCommunity);
                model.addAttribute("groups", new ArrayList<>());
                model.addAttribute("availableMembers", new ArrayList<>());
                model.addAttribute("availableUsersToInvite", new ArrayList<>());
                model.addAttribute("communityId", null);
                model.addAttribute("suggestedGroups", new ArrayList<>());
                model.addAttribute("groupRequests", new ArrayList<>());
                model.addAttribute("isAdmin", false);
                return "mobile-community";
            }

            String currentUserId = (String) authenticationManager.get("sub");
            boolean isAdmin = authenticationManager.isAdmin();

            List<UserDTO> communityMembers = new ArrayList<>();
            List<UserDTO> availableUsersToInvite = new ArrayList<>();
            try {
                communityMembers = communityService.getCommunityMembers(communityId);
                if (isAdmin) {
                    availableUsersToInvite = communityService.getAvailableUsersToInvite(communityId);
                }
            } catch (Exception e) {
                System.out.println("Mobile - Error getting community members: " + e.getMessage());
            }

            List<Map<String, Object>> processedGroups = new ArrayList<>();
            try {
                List<CreateChatDTO> groupsResponse = isAdmin
                        ? communityGroupService.getCommunityGroupsByCommunityId(communityId)
                        : communityGroupService.getUserCommunityGroups(currentUserId, communityId);

                if (groupsResponse != null) {
                    for (CreateChatDTO group : groupsResponse) {
                        Map<String, Object> processedGroup = new HashMap<>();
                        processedGroup.put("id", group.getId() != null ? group.getId() : 0L);
                        processedGroup.put("groupName", group.getGroupName() != null ? group.getGroupName() : "Unknown Group");
                        processedGroup.put("groupDescription", group.getGroupDescription() != null ? group.getGroupDescription() : "No description");
                        processedGroup.put("memberCount", group.getMemberCount() != null ? group.getMemberCount() : 0);
                        processedGroups.add(processedGroup);
                    }
                }
            } catch (Exception e) {
                System.out.println("Mobile - Error getting groups: " + e.getMessage());
                e.printStackTrace();
            }

            List<Map<String, Object>> suggestedGroups = new ArrayList<>();
            if (!isAdmin) {
                try {
                    suggestedGroups = communityGroupService.getSuggestedGroups(currentUserId, communityId);
                } catch (Exception e) {
                    System.out.println("Mobile - Error getting suggested groups: " + e.getMessage());
                }
            }

            List<Map<String, Object>> groupRequests = new ArrayList<>();
            if (isAdmin) {
                try {
                    groupRequests = communityGroupService.getPendingGroupRequests();
                } catch (Exception e) {
                    System.out.println("Mobile - Error getting group requests: " + e.getMessage());
                }
            }

            model.addAttribute("community", normalizedCommunity);
            model.addAttribute("groups", processedGroups);
            model.addAttribute("availableMembers", communityMembers);
            model.addAttribute("communityMembers", communityMembers);
            model.addAttribute("availableUsersToInvite", availableUsersToInvite);
            model.addAttribute("communityId", communityId);
            model.addAttribute("suggestedGroups", suggestedGroups);
            model.addAttribute("groupRequests", groupRequests);
            model.addAttribute("isAdmin", isAdmin);
            model.addAttribute("userId", currentUserId);
            model.addAttribute("usersName", authenticationManager.get("name"));

            boolean canUserPost = postService.canUserPostToCommunity(currentUserId, communityId);
            model.addAttribute("canUserPost", canUserPost);

            // Subscription status for the current user
            boolean hasActiveSubscription = false;
            try {
                hasActiveSubscription = communityService.hasActiveSubscription(currentUserId, communityId);
            } catch (Exception e) {
                System.out.println("Mobile - Error checking subscription status: " + e.getMessage());
            }
            model.addAttribute("hasActiveSubscription", hasActiveSubscription);

        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("errorMessage", "Error loading community data: " + e.getMessage());
            model.addAttribute("community", new HashMap<>());
            model.addAttribute("groups", new ArrayList<>());
            model.addAttribute("availableMembers", new ArrayList<>());
            model.addAttribute("communityMembers", new ArrayList<>());
            model.addAttribute("availableUsersToInvite", new ArrayList<>());
            model.addAttribute("communityId", null);
            model.addAttribute("suggestedGroups", new ArrayList<>());
            model.addAttribute("groupRequests", new ArrayList<>());
            model.addAttribute("isAdmin", false);
            model.addAttribute("canUserPost", false);
        }

        model.addAttribute("currentPath", "/my-mycommunity");
        return "mobile-community";
    }

    @PostMapping("/update-name")
    public String updateCommunityName(@RequestParam("communityName") String name,
                                      @RequestParam(value = "communityId", required = false) String communityIdStr,
                                      @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                                      RedirectAttributes redirectAttributes,
                                      Model model) {
        try {
            if (!authenticationManager.isAdmin()) {
                String errorMessage = "Access denied. Only administrators can update community settings.";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/mobile-message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/mobile/my-community";
                }
            }

            if (name == null || name.trim().isEmpty()) {
                String errorMessage = "Community name cannot be empty";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/mobile-message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/mobile/my-community";
                }
            }

            HttpServletRequest httpRequest = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            Long selectedCommunityId = (Long) httpRequest.getSession().getAttribute("selectedCommunityId");

            if (selectedCommunityId == null) {
                String errorMessage = "No community selected";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/mobile-message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/mobile/organizations";
                }
            }

            CommunityDTO currentResponse = communityService.getCommunityById(selectedCommunityId);
            if (currentResponse == null) {
                String errorMessage = "Could not retrieve current community data";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/mobile-message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/mobile/my-community";
                }
            }

            CreateCommunityVO dto = CreateCommunityVO.builder()
                    .communityName(name.trim())
                    .communityDescription(currentResponse.getDescription())
                    .build();

            communityService.updateCommunity(dto, selectedCommunityId);

            String successMessage = "Community name updated successfully!";
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("successMessage", successMessage);
                return "fragments/mobile-message :: success";
            } else {
                redirectAttributes.addFlashAttribute("successMessage", successMessage);
                return "redirect:/mobile/my-community";
            }

        } catch (Exception e) {
            String errorMessage = "Error updating community name: " + e.getMessage();
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("errorMessage", errorMessage);
                return "fragments/mobile-message :: error";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                return "redirect:/mobile/my-community";
            }
        }
    }

    @PostMapping("/update-description")
    public String updateCommunityDescription(@RequestParam("communityDescription") String description,
                                             @RequestParam(value = "communityId", required = false) String communityIdStr,
                                             @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                                             RedirectAttributes redirectAttributes,
                                             Model model) {
        try {
            if (!authenticationManager.isAdmin()) {
                String errorMessage = "Access denied. Only administrators can update community settings.";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/mobile-message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/mobile/my-community";
                }
            }

            if (description == null || description.trim().isEmpty()) {
                String errorMessage = "Community description cannot be empty";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/mobile-message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/mobile/my-community";
                }
            }

            HttpServletRequest httpRequest = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            Long selectedCommunityId = (Long) httpRequest.getSession().getAttribute("selectedCommunityId");

            if (selectedCommunityId == null) {
                String errorMessage = "No community selected";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/mobile-message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/mobile/organizations";
                }
            }

            CommunityDTO currentResponse = communityService.getCommunityById(selectedCommunityId);
            if (currentResponse == null) {
                String errorMessage = "Could not retrieve current community data";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/mobile-message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/mobile/my-community";
                }
            }

            CreateCommunityVO dto = CreateCommunityVO.builder()
                    .communityName(currentResponse.getName())
                    .communityDescription(description.trim())
                    .build();

            communityService.updateCommunity(dto, selectedCommunityId);

            String successMessage = "Community description updated successfully!";
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("successMessage", successMessage);
                return "fragments/mobile-message :: success";
            } else {
                redirectAttributes.addFlashAttribute("successMessage", successMessage);
                return "redirect:/mobile/my-community";
            }

        } catch (Exception e) {
            String errorMessage = "Error updating community description: " + e.getMessage();
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("errorMessage", errorMessage);
                return "fragments/mobile-message :: error";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                return "redirect:/mobile/my-community";
            }
        }
    }

    @PostMapping("/groups/add")
    public String addGroup(@RequestParam("groupName") String name,
                           @RequestParam("groupDescription") String description,
                           @RequestParam(value = "communityId", required = false) String communityIdStr,
                           @RequestParam(value = "selectedMembers", required = false) String selectedMembersStr,
                           @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                           RedirectAttributes redirectAttributes,
                           Model model) {
        try {
            if (!authenticationManager.isAdmin()) {
                String errorMessage = "Access denied. Only administrators can create groups.";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/mobile-message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/mobile/my-community";
                }
            }

            HttpServletRequest httpRequest = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            Long selectedCommunityId = (Long) httpRequest.getSession().getAttribute("selectedCommunityId");

            if (selectedCommunityId == null) {
                String errorMessage = "No community selected";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/mobile-message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/mobile/organizations";
                }
            }

            Long communityId = selectedCommunityId;

            List<String> selectedMembers = new ArrayList<>();
            if (selectedMembersStr != null && !selectedMembersStr.trim().isEmpty()) {
                selectedMembers = Arrays.asList(selectedMembersStr.split(","));
            }

            CreateChatDTO dto = new CreateChatDTO();
            dto.setGroupName(name);
            dto.setGroupDescription(description);
            dto.setCommunityId(communityId);
            dto.setMemberCount(selectedMembers.size());

            Object response = communityGroupService.createCommunityGroup(dto);

            if (!selectedMembers.isEmpty() && response != null) {
                Long groupId = null;

                if (response instanceof Map) {
                    Map<String, Object> groupResponse = (Map<String, Object>) response;
                    Object idObj = groupResponse.get("id");
                    if (idObj instanceof Number) {
                        groupId = ((Number) idObj).longValue();
                    }
                } else {
                    try {
                        Object idValue = response.getClass().getMethod("getId").invoke(response);
                        if (idValue instanceof Number) {
                            groupId = ((Number) idValue).longValue();
                        }
                    } catch (Exception ignored) {
                    }
                }

                if (groupId != null) {
                    for (String memberId : selectedMembers) {
                        try {
                            communityGroupService.addUserToCommunityGroup(memberId.trim(), groupId);
                        } catch (Exception e) {
                            System.err.println("Mobile - Error adding member " + memberId + " to group: " + e.getMessage());
                        }
                    }
                }
            }

            if (isHtmxRequest(hxRequest)) {
                return getGroupsList(model);
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Group '" + name + "' created successfully!");
                return "redirect:/mobile/my-community";
            }

        } catch (Exception e) {
            String errorMessage = "Error creating group: " + e.getMessage();
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("errorMessage", errorMessage);
                return "fragments/mobile-message :: error";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                return "redirect:/mobile/my-community";
            }
        }
    }

    @PostMapping("/assignAdmin/{userId}/{communityId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> assignAdmin(
            @PathVariable("userId") String userId,
            @PathVariable("communityId") Long communityId) {
        Map<String, Object> response = new HashMap<>();
        try {
            String currentUser = (String) authenticationManager.get("sub");
            communityService.assignCommunityAdmin(currentUser, userId, communityId);
            response.put("success", true);
            response.put("message", "Admin role assigned successfully");
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(403).body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to assign admin role: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    private Map<String, Object> extractCommunityData(CommunityDTO communityResponse) {
        Map<String, Object> normalizedCommunity = new HashMap<>();
        if (communityResponse != null) {
            normalizedCommunity.put("id", communityResponse.getId());
            normalizedCommunity.put("communityName", communityResponse.getName());
            normalizedCommunity.put("communityDescription", communityResponse.getDescription());
            normalizedCommunity.put("isPrivate", communityResponse.getIsPrivate());
            normalizedCommunity.put("communityPrivacy", communityResponse.getCommunityPrivacy() != null ? communityResponse.getCommunityPrivacy() : false);
        }
        return normalizedCommunity;
    }

    // HTMX endpoint for updating community privacy
    @PostMapping("/update-privacy")
    @ResponseBody
    public String updateCommunityPrivacy(@RequestParam("communityId") Long communityId,
                                         @RequestParam("status") String status) {
        try {
            if (!authenticationManager.isAdmin()) {
                return "❌ Only administrators can change community privacy";
            }

            CommunityDTO currentResponse = communityService.getCommunityById(communityId);
            if (currentResponse == null) {
                return "❌ Community not found";
            }

            boolean isPrivate = "private".equalsIgnoreCase(status);

            CreateCommunityVO dto = CreateCommunityVO.builder()
                    .communityName(currentResponse.getName())
                    .communityDescription(currentResponse.getDescription())
                    .isPrivate(isPrivate)
                    .build();

            communityService.updateCommunity(dto, communityId);

            if (isPrivate) {
                return "🔒 Community is now Private";
            } else {
                return "✅ Community is now Public";
            }

        } catch (Exception e) {
            return "❌ Error updating privacy: " + e.getMessage();
        }
    }

    private boolean isHtmxRequest(String hxRequest) {
        return "true".equals(hxRequest);
    }

    @GetMapping("/groups")
    public String getGroupsList(Model model) {
        try {
            HttpServletRequest httpRequest = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            Long selectedCommunityId = (Long) httpRequest.getSession().getAttribute("selectedCommunityId");

            if (selectedCommunityId == null) {
                model.addAttribute("groups", new ArrayList<>());
                return "fragments/mobile-groups :: groups-list";
            }

            CommunityDTO communityResponse = communityService.getCommunityById(selectedCommunityId);
            if (communityResponse == null) {
                model.addAttribute("groups", new ArrayList<>());
                return "fragments/mobile-groups :: groups-list";
            }

            Long communityId = communityResponse.getId();
            if (communityId == null) {
                model.addAttribute("groups", new ArrayList<>());
                return "fragments/mobile-groups :: groups-list";
            }

            String currentUserId = (String) authenticationManager.get("sub");
            boolean isAdmin = authenticationManager.isAdmin();

            List<Map<String, Object>> processedGroups = new ArrayList<>();
            List<CreateChatDTO> groupsResponse = isAdmin
                    ? communityGroupService.getCommunityGroupsByCommunityId(communityId)
                    : communityGroupService.getUserCommunityGroups(currentUserId, communityId);

            if (groupsResponse != null) {
                for (CreateChatDTO group : groupsResponse) {
                    Map<String, Object> processedGroup = new HashMap<>();
                    processedGroup.put("id", group.getId() != null ? group.getId() : 0L);
                    processedGroup.put("groupName", group.getGroupName() != null ? group.getGroupName() : "Unknown Group");
                    processedGroup.put("groupDescription", group.getGroupDescription() != null ? group.getGroupDescription() : "No description");
                    processedGroup.put("memberCount", group.getMemberCount() != null ? group.getMemberCount() : 0);
                    processedGroups.add(processedGroup);
                }
            }

            model.addAttribute("groups", processedGroups);
            model.addAttribute("isAdmin", isAdmin);
            return "fragments/mobile-groups :: groups-list";

        } catch (Exception e) {
            model.addAttribute("groups", new ArrayList<>());
            model.addAttribute("isAdmin", false);
            return "fragments/mobile-groups :: groups-list";
        }
    }

    @GetMapping("/dashboard")
    public String mobileDashboard(@RequestParam("communityId") Long communityId, Model model, HttpServletRequest request) {
        try {
            boolean isAdmin = authenticationManager.isAdmin();
            if (!isAdmin) {
                return "redirect:/mobile/my-community";
            }

            CommunityDTO community = communityService.getCommunityById(communityId);
            if (community == null) {
                return "redirect:/mobile/organizations";
            }

            request.getSession().setAttribute("selectedCommunityId", communityId);
            request.getSession().setAttribute("selectedCommunityName", community.getName());

            int totalMembers = communityMembershipRepository
                    .findByCommunityIdAndStatus(communityId, MembershipStatus.APPROVED).size();

            List<Donation> donations = donationRepository.findByCommunityId(communityId);
            BigDecimal totalDonations = donations.stream()
                    .map(Donation::getAmount).filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<MembershipSubscription> subscriptions = membershipSubscriptionRepository.findByCommunityId(communityId);
            long activeSubscriptions = subscriptions.stream()
                    .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE).count();
            BigDecimal totalSubscriptionRevenue = subscriptions.stream()
                    .map(MembershipSubscription::getAmount).filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalRevenue = totalDonations.add(totalSubscriptionRevenue);

            model.addAttribute("community", community);
            model.addAttribute("communityId", communityId);
            model.addAttribute("isAdmin", isAdmin);
            model.addAttribute("totalMembers", totalMembers);
            model.addAttribute("totalDonations", totalDonations);
            model.addAttribute("donationCount", donations.size());
            model.addAttribute("activeSubscriptions", activeSubscriptions);
            model.addAttribute("totalSubscriptions", subscriptions.size());
            model.addAttribute("totalRevenue", totalRevenue);

            return "mobile-community-dashboard";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/mobile/my-community";
        }
    }
}

