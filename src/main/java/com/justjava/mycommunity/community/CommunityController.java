package com.justjava.mycommunity.community;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.dto.CreateChatDTO;
import com.justjava.mycommunity.chat.dto.CreateCommunityVO;
import com.justjava.mycommunity.chat.service.ChatService;
import com.justjava.mycommunity.event.EventService;
import com.justjava.mycommunity.community.dto.CommunityDTO;
import com.justjava.mycommunity.community.repository.CommunityMembershipRepository;
import com.justjava.mycommunity.community.Role;
import com.justjava.mycommunity.community.MembershipStatus;
import com.justjava.mycommunity.network.NetworkDTO;
import com.justjava.mycommunity.network.NetworkNewService;
import com.justjava.mycommunity.userManagement.UserDTO;
import com.justjava.mycommunity.community.SubscriptionPlan;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/my-community")
public class CommunityController {

    @Autowired
    private ChatService chatService;
    @Autowired
    private CommunityService communityService;
    @Autowired
    private CommunityGroupService communityGroupService;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private NetworkNewService networkNewService;
    @Autowired
    private EventService eventService;
    @Autowired
    private CommunityMembershipRepository communityMembershipRepository;

    @GetMapping("/select")
    public String communitySelectionPage(Model model) {
        try {
            // Get current user ID
            String currentUserId = (String) authenticationManager.get("sub");
            if (currentUserId == null) {
                model.addAttribute("errorMessage", "User not authenticated");
                model.addAttribute("userCommunities", new ArrayList<>());
                model.addAttribute("isAdmin", false);
                return "community-selection";
            }

            // Get only communities where this user has an admin role
            List<Map<String, Object>> userCommunities = communityService.getAdminManagedCommunities(currentUserId);
            boolean isAdmin = authenticationManager.isAdmin();

            model.addAttribute("userCommunities", userCommunities);
            model.addAttribute("isAdmin", isAdmin);
            model.addAttribute("currentPath", "/my-mycommunity/select");

        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("errorMessage", "Error loading communities: " + e.getMessage());
            model.addAttribute("userCommunities", new ArrayList<>());
            model.addAttribute("isAdmin", false);
        }

        return "community-selection";
    }

    @GetMapping
    public String communityPage(@RequestParam(value = "communityId", required = false) Long communityId,
                                Model model, HttpServletRequest request) {
        try {
            // If no mycommunity ID provided, redirect to selection page
            if (communityId == null) {
                return "redirect:/my-community/select";
            }
            populateCommunityPageModel(communityId, model, request);
            model.addAttribute("requestedTab", "general");
            model.addAttribute("activeTab", "general");

        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("errorMessage", "Error loading community data: " + e.getMessage());
            return "redirect:/my-community/select";
        }

        model.addAttribute("currentPath", "/my-mycommunity");
        return "community";
    }

    @GetMapping("/tab")
    public String communityTabContent(@RequestParam("communityId") Long communityId,
                                      @RequestParam("tab") String tab,
                                      Model model,
                                      HttpServletRequest request) {
        try {
            populateCommunityPageModel(communityId, model, request);
            model.addAttribute("requestedTab", tab);
            model.addAttribute("activeTab", tab);
            return "community :: manage-tab-panel-host";
        } catch (Exception e) {
            model.addAttribute("requestedTab", "general");
            model.addAttribute("activeTab", "general");
            return "community :: manage-tab-panel-host";
        }
    }

    private void populateCommunityPageModel(Long communityId, Model model, HttpServletRequest request) {
        String currentUserId = (String) authenticationManager.get("sub");
        boolean isAdmin = authenticationManager.isAdmin();

        if (!isAdmin) {
            isAdmin = communityMembershipRepository.isUserCommunityAdmin(currentUserId, communityId);
        }

        request.getSession().setAttribute("selectedCommunityId", communityId);

        CommunityDTO community = communityService.getCommunityById(communityId);
        if (community == null) {
            throw new IllegalStateException("Community not found or access denied.");
        }

        request.getSession().setAttribute("selectedCommunityName", community.getName());

        List<UserDTO> communityMembers = isAdmin
                ? communityService.getCommunityMembersAll(communityId)
                : communityService.getCommunityMembers(communityId);
        List<UserDTO> availableUsersToInvite = communityService.getAvailableUsersToInvite(communityId);

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
            System.out.println("Error getting groups: " + e.getMessage());
            e.printStackTrace();
        }

        model.addAttribute("community", community);
        model.addAttribute("groups", processedGroups);
        model.addAttribute("communityMembers", communityMembers);
        model.addAttribute("availableMembers", communityMembers);
        model.addAttribute("availableUsersToInvite", availableUsersToInvite);
        model.addAttribute("communityId", communityId);
        model.addAttribute("isAdmin", isAdmin);

        boolean isCreator = communityMembershipRepository.isUserCommunityCreator(currentUserId, communityId);
        model.addAttribute("isCreator", isCreator);

        Map<String, Object> suspensionInfo = communityService.getCurrentUserSuspension(currentUserId, communityId);
        model.addAttribute("isSuspended", suspensionInfo != null);
        model.addAttribute("suspensionInfo", suspensionInfo);

        boolean hasActiveSubscription = false;
        try {
            hasActiveSubscription = communityService.hasActiveSubscription(currentUserId, communityId);
        } catch (Exception e) {
            System.out.println("Error checking subscription status: " + e.getMessage());
        }
        model.addAttribute("hasActiveSubscription", hasActiveSubscription);
        SubscriptionPlan activeSubscriptionPlan = null;
        try {
            activeSubscriptionPlan = communityService.getActiveSubscriptionPlan(communityId).orElse(null);
        } catch (Exception e) {
            System.out.println("Error loading active subscription plan: " + e.getMessage());
        }
        model.addAttribute("activeSubscriptionPlan", activeSubscriptionPlan);

        List<NetworkDTO> communityNetworks = new ArrayList<>();
        try {
            communityNetworks = isAdmin
                    ? networkNewService.getAllNetworksInCommunity(communityId, currentUserId)
                    : networkNewService.getUserNetworksInCommunity(currentUserId, communityId);
        } catch (Exception e) {
            System.out.println("Error loading networks: " + e.getMessage());
        }
        model.addAttribute("communityNetworks", communityNetworks);
        model.addAttribute("communityEvents", eventService.getCommunityDonationEvents(communityId));
    }
    @PostMapping("assignAdmin/{userId}/{communityId}")
    public String assignAdmin(
            @PathVariable("userId") String userId,
            @PathVariable("communityId") Long communityId,
            HttpServletRequest request
    ) {
        String currentUser = (String) authenticationManager.get("sub");
        communityService.assignCommunityAdmin(currentUser, userId, communityId);

        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/");
    }

    @PostMapping("removeAdmin/{userId}/{communityId}")
    public String removeAdmin(
            @PathVariable("userId") String userId,
            @PathVariable("communityId") Long communityId,
            HttpServletRequest request
    ) {
        String currentUser = (String) authenticationManager.get("sub");
        communityService.removeCommunityAdmin(currentUser, userId, communityId);
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/");
    }

    @PostMapping("/members/{communityId}/{userId}/suspend")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> suspendMember(
            @PathVariable Long communityId,
            @PathVariable String userId,
            @RequestParam(value = "suspendedUntil", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime suspendedUntil,
            @RequestParam(value = "reason", required = false) String reason) {
        Map<String, Object> response = new HashMap<>();
        try {
            String currentUserId = (String) authenticationManager.get("sub");
            communityService.suspendCommunityMember(currentUserId, userId, communityId, suspendedUntil, reason);
            response.put("success", true);
            response.put("message", "Member suspended successfully");
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(403).body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to suspend member: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/members/{communityId}/{userId}/unsuspend")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> unsuspendMember(
            @PathVariable Long communityId,
            @PathVariable String userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            String currentUserId = (String) authenticationManager.get("sub");
            communityService.unsuspendCommunityMember(currentUserId, userId, communityId);
            response.put("success", true);
            response.put("message", "Member unsuspended successfully");
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(403).body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to unsuspend member: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    private Map<String, Object> extractCommunityData(Object communityResponse) {
        Map<String, Object> normalizedCommunity = new HashMap<>();

        try {
            // The response should be a Community entity
            if (communityResponse instanceof Map) {
                Map<String, Object> communityMap = (Map<String, Object>) communityResponse;
                normalizedCommunity.put("id", communityMap.get("id"));
                normalizedCommunity.put("communityName", communityMap.get("name"));
                normalizedCommunity.put("communityDescription", communityMap.get("description"));
                normalizedCommunity.put("isPrivate", communityMap.get("isPrivate"));
            } else {
                // Handle entity object using reflection
                try {
                    Object idValue = communityResponse.getClass().getMethod("getId").invoke(communityResponse);
                    Object nameValue = communityResponse.getClass().getMethod("getName").invoke(communityResponse);
                    Object descValue = communityResponse.getClass().getMethod("getDescription").invoke(communityResponse);
                    Object isPrivateValue = null;

                    // Try to get isPrivate field
                    try {
                        isPrivateValue = communityResponse.getClass().getMethod("isPrivate").invoke(communityResponse);
                    } catch (Exception e) {
                        // If isPrivate method doesn't exist, default to false
                        isPrivateValue = false;
                    }

                    normalizedCommunity.put("id", idValue);
                    normalizedCommunity.put("communityName", nameValue != null ? nameValue.toString() : "Unknown Community");
                    normalizedCommunity.put("communityDescription", descValue != null ? descValue.toString() : "No description");
                    normalizedCommunity.put("isPrivate", isPrivateValue != null ? (Boolean) isPrivateValue : false);

                    System.out.println("Extracted from entity - ID: " + idValue + ", Name: " + nameValue + ", Description: " + descValue + ", IsPrivate: " + isPrivateValue);
                } catch (Exception e) {
                    System.out.println("Error extracting from entity: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.out.println("Error in extractCommunityData: " + e.getMessage());
            e.printStackTrace();
        }

        return normalizedCommunity;
    }

    // HTMX endpoint for updating mycommunity name
    @PostMapping("/update-name")
    public String updateCommunityName(@RequestParam("communityName") String name,
                                      @RequestParam(value = "communityId", required = true) Long communityId,
                                      @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                                      RedirectAttributes redirectAttributes,
                                      Model model) {
        try {
            // Check if user is admin
            if (!authenticationManager.isAdmin()) {
                String errorMessage = "Only administrators can update club settings";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/my-mycommunity?communityId=" + communityId;
                }
            }

            if (name == null || name.trim().isEmpty()) {
                String errorMessage = "Club name cannot be empty";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/my-mycommunity?communityId=" + communityId;
                }
            }

            // Get current mycommunity data by ID
            Object currentResponse = communityService.getCommunityById(communityId);
            if (currentResponse == null) {
                String errorMessage = "Could not retrieve current mycommunity data";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/my-community/select";
                }
            }

            Map<String, Object> currentCommunity = extractCommunityData(currentResponse);
            String currentDescription = (String) currentCommunity.get("communityDescription");
            if (currentDescription == null) {
                currentDescription = "Welcome";
            }

            CreateCommunityVO dto = CreateCommunityVO.builder()
                    .communityName(name.trim())
                    .communityDescription(currentDescription)
                    .build();

            communityService.updateCommunity(dto, communityId);

            String successMessage = "Club name updated successfully!";
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("successMessage", successMessage);
                return "fragments/message :: success";
            } else {
                redirectAttributes.addFlashAttribute("successMessage", successMessage);
                return "redirect:/my-mycommunity?communityId=" + communityId;
            }

        } catch (Exception e) {
            System.out.println("Error updating mycommunity name: " + e.getMessage());
            e.printStackTrace();
            String errorMessage = "Error updating mycommunity name: " + e.getMessage();
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("errorMessage", errorMessage);
                return "fragments/message :: error";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                return "redirect:/my-mycommunity?communityId=" + communityId;
            }
        }
    }

    // HTMX endpoint for updating mycommunity description
    @PostMapping("/update-description")
    public String updateCommunityDescription(@RequestParam("communityDescription") String description,
                                             @RequestParam(value = "communityId", required = true) Long communityId,
                                             @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                                             RedirectAttributes redirectAttributes,
                                             Model model) {
        try {
            // Check if user is admin
            if (!authenticationManager.isAdmin()) {
                String errorMessage = "Only administrators can update club settings";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/my-mycommunity?communityId=" + communityId;
                }
            }

            if (description == null || description.trim().isEmpty()) {
                String errorMessage = "Club description cannot be empty";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/my-mycommunity?communityId=" + communityId;
                }
            }

            // Get current mycommunity data by ID
            Object currentResponse = communityService.getCommunityById(communityId);
            if (currentResponse == null) {
                String errorMessage = "Could not retrieve current mycommunity data";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/my-community/select";
                }
            }

            Map<String, Object> currentCommunity = extractCommunityData(currentResponse);
            String currentName = (String) currentCommunity.get("communityName");
            if (currentName == null) {
                currentName = "Community";
            }

            CreateCommunityVO dto = CreateCommunityVO.builder()
                    .communityName(currentName)
                    .communityDescription(description.trim())
                    .build();

            communityService.updateCommunity(dto, communityId);

            String successMessage = "Club description updated successfully!";
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("successMessage", successMessage);
                return "fragments/message :: success";
            } else {
                redirectAttributes.addFlashAttribute("successMessage", successMessage);
                return "redirect:/my-mycommunity?communityId=" + communityId;
            }

        } catch (Exception e) {
            System.out.println("Error updating mycommunity description: " + e.getMessage());
            e.printStackTrace();
            String errorMessage = "Error updating mycommunity description: " + e.getMessage();
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("errorMessage", errorMessage);
                return "fragments/message :: error";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                return "redirect:/my-mycommunity?communityId=" + communityId;
            }
        }
    }

    // HTMX endpoint for adding groups
    @PostMapping("/groups/add")
    public String addGroup(@RequestParam("groupName") String name,
                           @RequestParam("groupDescription") String description,
                           @RequestParam(value = "communityId", required = true) Long communityId,
                           @RequestParam(value = "selectedMembers", required = false) String selectedMembersStr,
                           @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                           RedirectAttributes redirectAttributes,
                           Model model) {
        try {
            // Check if user is admin
            if (!authenticationManager.isAdmin()) {
                String errorMessage = "Only administrators can create groups";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/my-mycommunity?communityId=" + communityId;
                }
            }

            // Validate mycommunity ID
            if (communityId == null) {
                String errorMessage = "Community ID is required";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/my-community/select";
                }
            }

            // Verify mycommunity exists and user has access
            Object communityResponse = communityService.getCommunityById(communityId);
            if (communityResponse == null) {
                String errorMessage = "Club not found or access denied";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/my-community/select";
                }
            }

            // Parse selected members
            List<String> selectedMembers = new ArrayList<>();
            if (selectedMembersStr != null && !selectedMembersStr.trim().isEmpty()) {
                selectedMembers = Arrays.asList(selectedMembersStr.split(","));
            }

            CreateChatDTO dto = new CreateChatDTO();
            dto.setGroupName(name);
            dto.setGroupDescription(description);
            dto.setCommunityId(communityId);
            dto.setMemberCount(selectedMembers.size());

            System.out.println("=== Creating group in mycommunity " + communityId + " ===");
            System.out.println("Group name: " + name);
            System.out.println("Selected members: " + selectedMembers.size());

            CommunityGroup response = communityGroupService.createCommunityGroup(dto);
            System.out.println("Create group response: " + response);
            System.out.println("Created group ID: " + (response != null ? response.getId() : "null"));
            System.out.println("Created group mycommunity ID: " + (response != null && response.getCommunity() != null ? response.getCommunity().getId() : "null"));

            // Verify the group was created successfully
            if (response == null || response.getId() == null) {
                String errorMessage = "Failed to create group - no valid response received";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/my-mycommunity?communityId=" + communityId;
                }
            }

            Long groupId = response.getId();
            System.out.println("Group created successfully with ID: " + groupId);

            // Add members to group if any selected
            if (!selectedMembers.isEmpty()) {
                System.out.println("Adding " + selectedMembers.size() + " members to group " + groupId);

                for (String memberId : selectedMembers) {
                    try {
                        // Add users to the mycommunity first to ensure they're part of the mycommunity
                        communityService.addUserToCommunity(memberId.trim(), communityId);
                        System.out.println("Added user " + memberId + " to mycommunity " + communityId);

                        // Then try to add them to the specific group
                        communityGroupService.addUserToCommunityGroup(memberId.trim(), groupId);
                        System.out.println("Added user " + memberId + " to group " + groupId);
                    } catch (Exception e) {
                        System.err.println("Error adding member " + memberId + " to group: " + e.getMessage());
                        e.printStackTrace();
                        // Continue with other members even if one fails
                    }
                }
            }

            // Final verification that the group exists and has the expected members
            try {
                CreateChatDTO verifyGroup = communityGroupService.getCommunityGroupById(groupId);
                System.out.println("Final verification: Group '" + verifyGroup.getGroupName() + "' has " + verifyGroup.getMemberCount() + " members");
            } catch (Exception e) {
                System.err.println("Warning: Could not verify created group: " + e.getMessage());
            }

            String successMessage = "Group '" + name + "' created successfully!";
            if (isHtmxRequest(hxRequest)) {
                // Return the new group row HTML
                Map<String, Object> newGroup = new HashMap<>();

                newGroup.put("id", groupId);
                newGroup.put("groupName", name);
                newGroup.put("groupDescription", description);
                newGroup.put("memberCount", selectedMembers.size() + 1); // +1 for admin who is auto-added

                model.addAttribute("group", newGroup);
                System.out.println("Returning group row for group ID: " + groupId);
                return "fragments/group-row :: group-row";
            } else {
                redirectAttributes.addFlashAttribute("successMessage", successMessage);
                return "redirect:/my-mycommunity?communityId=" + communityId;
            }

        } catch (Exception e) {
            e.printStackTrace();
            String errorMessage = "Error creating group: " + e.getMessage();
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("errorMessage", errorMessage);
                return "fragments/message :: error";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                return "redirect:/my-mycommunity?communityId=" + communityId;
            }
        }
    }

    // HTMX endpoint for editing groups
    @PostMapping("/groups/{id}/edit")
    public String editGroup(@PathVariable Long id,
                            @RequestParam("groupName") String name,
                            @RequestParam("groupDescription") String description,
                            @RequestParam(value = "communityId", required = true) Long communityId,
                            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                            RedirectAttributes redirectAttributes,
                            Model model) {
        try {
            // Check if user is admin
            if (!authenticationManager.isAdmin()) {
                String errorMessage = "Only administrators can edit groups";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/my-mycommunity?communityId=" + communityId;
                }
            }

            // Validate mycommunity ID and verify access
            if (communityId == null) {
                String errorMessage = "Community ID is required";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/my-community/select";
                }
            }

            CreateChatDTO dto = new CreateChatDTO();
            dto.setGroupName(name);
            dto.setGroupDescription(description);
            dto.setCommunityId(communityId);

            communityGroupService.updateCommunityGroup(dto, id);

            if (isHtmxRequest(hxRequest)) {
                // Return the updated group row
                List<CreateChatDTO> groupsResponse = communityGroupService.getCommunityGroupsByCommunityId(communityId);
                if (groupsResponse != null) {
                    for (CreateChatDTO group : groupsResponse) {
                        if (group.getId() != null && group.getId().equals(id)) {
                            Map<String, Object> processedGroup = new HashMap<>();
                            processedGroup.put("id", group.getId());
                            processedGroup.put("groupName", group.getGroupName());
                            processedGroup.put("groupDescription", group.getGroupDescription());
                            processedGroup.put("memberCount", group.getMemberCount());

                            model.addAttribute("group", processedGroup);
                            return "fragments/group-row :: group-row";
                        }
                    }
                }

                // Fallback if group not found
                model.addAttribute("errorMessage", "Group not found after update");
                return "fragments/message :: error";
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Group updated successfully!");
                return "redirect:/my-mycommunity?communityId=" + communityId;
            }

        } catch (Exception e) {
            e.printStackTrace();
            String errorMessage = "Error updating group: " + e.getMessage();
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("errorMessage", errorMessage);
                return "fragments/message :: error";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                return "redirect:/my-mycommunity?communityId=" + communityId;
            }
        }
    }

    // HTMX endpoint for deleting groups
    @PostMapping("/groups/{id}/delete")
    public String deleteGroup(@PathVariable Long id,
                              @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                              RedirectAttributes redirectAttributes,
                              Model model) {
        try {
            // Check if user is admin
            if (!authenticationManager.isAdmin()) {
                String errorMessage = "Only administrators can delete groups";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/my-community/select";
                }
            }

            communityGroupService.deleteCommunityGroup(id);

            String successMessage = "Group deleted successfully!";
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("successMessage", successMessage);
                return "fragments/message :: success";
            } else {
                redirectAttributes.addFlashAttribute("successMessage", successMessage);
                return "redirect:/my-community/select";
            }

        } catch (Exception e) {
            e.printStackTrace();
            String errorMessage = "Error deleting group: " + e.getMessage();
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("errorMessage", errorMessage);
                return "fragments/message :: error";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                return "redirect:/my-community/select";
            }
        }
    }

    // HTMX endpoint for inviting members
    @PostMapping("/invite")
    public String inviteMember(@RequestParam("userId") String userId,
                               @RequestParam(value = "communityId", required = true) Long communityId,
                               @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                               RedirectAttributes redirectAttributes,
                               Model model) {
        try {
            // Check if user is admin or if mycommunity is public (for public communities, any member can invite)
            Object communityResponse = communityService.getCommunityById(communityId);
            Map<String, Object> community = extractCommunityData(communityResponse);
            Boolean isPrivate = (Boolean) community.get("isPrivate");

            if (isPrivate != null && isPrivate && !authenticationManager.isAdmin()) {
                String errorMessage = "Only administrators can invite users to private communities";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/my-mycommunity?communityId=" + communityId;
                }
            }

            // Send invitation
            communityService.inviteUserToCommunity(userId, communityId);

            String successMessage = "Invitation sent successfully!";
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("successMessage", successMessage);
                return "fragments/message :: success";
            } else {
                redirectAttributes.addFlashAttribute("successMessage", successMessage);
                return "redirect:/my-mycommunity?communityId=" + communityId;
            }

        } catch (Exception e) {
            System.out.println("Error inviting user: " + e.getMessage());
            e.printStackTrace();
            String errorMessage = "Error sending invitation: " + e.getMessage();
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("errorMessage", errorMessage);
                return "fragments/message :: error";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                return "redirect:/my-mycommunity?communityId=" + communityId;
            }
        }
    }

    // Existing endpoint for getting group details (used by edit modal)
    @GetMapping("/groups/{id}")
    @ResponseBody
    public Map<String, Object> getGroup(@PathVariable Long id, @RequestParam(value = "communityId", required = true) Long communityId) {
        try {
            // Validate mycommunity ID
            if (communityId == null) {
                return new HashMap<>();
            }

            List<CreateChatDTO> groupsResponse = communityGroupService.getCommunityGroupsByCommunityId(communityId);

            if (groupsResponse != null) {
                for (CreateChatDTO group : groupsResponse) {
                    // Use the actual database ID
                    if (group.getId() != null && group.getId().equals(id)) {
                        Map<String, Object> result = new HashMap<>();
                        result.put("id", group.getId());
                        result.put("groupName", group.getGroupName());
                        result.put("groupDescription", group.getGroupDescription());
                        result.put("memberCount", group.getMemberCount());
                        return result;
                    }
                }
            }

            return new HashMap<>();

        } catch (Exception e) {
            System.out.println("Error in getGroup: " + e.getMessage());
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    // HTMX endpoint for getting a single group row (for updates)
    @GetMapping("/groups/{id}/row")
    public String getGroupRow(@PathVariable Long id, @RequestParam(value = "communityId", required = true) Long communityId, Model model) {
        try {
            // Validate mycommunity ID
            if (communityId == null) {
                return "fragments/group-row :: empty";
            }

            List<CreateChatDTO> groupsResponse = communityGroupService.getCommunityGroupsByCommunityId(communityId);

            if (groupsResponse != null) {
                for (CreateChatDTO group : groupsResponse) {
                    if (group.getId() != null && group.getId().equals(id)) {
                        Map<String, Object> processedGroup = new HashMap<>();
                        processedGroup.put("id", group.getId());
                        processedGroup.put("groupName", group.getGroupName());
                        processedGroup.put("groupDescription", group.getGroupDescription());
                        processedGroup.put("memberCount", group.getMemberCount());

                        model.addAttribute("group", processedGroup);
                        return "fragments/group-row :: group-row";
                    }
                }
            }

            return "fragments/group-row :: empty";

        } catch (Exception e) {
            System.out.println("Error in getGroupRow: " + e.getMessage());
            e.printStackTrace();
            return "fragments/group-row :: empty";
        }
    }

    // HTMX endpoint for empty groups state
    @GetMapping("/groups/empty-state")
    public String getEmptyGroupsState() {
        return "fragments/empty-groups :: empty-state";
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
                return "❌ Club not found";
            }

            boolean isPrivate = "private".equalsIgnoreCase(status);

            CreateCommunityVO dto = CreateCommunityVO.builder()
                    .communityName(currentResponse.getName())
                    .communityDescription(currentResponse.getDescription())
                    .isPrivate(isPrivate)
                    .build();

            communityService.updateCommunity(dto, communityId);

            if (isPrivate) {
                return "🔒 Club is now Private";
            } else {
                return "✅ Club is now Public";
            }

        } catch (Exception e) {
            return "❌ Error updating privacy: " + e.getMessage();
        }
    }

    // Utility method to check if request is from HTMX
    private boolean isHtmxRequest(String hxRequest) {
        return "true".equals(hxRequest);
    }
}
