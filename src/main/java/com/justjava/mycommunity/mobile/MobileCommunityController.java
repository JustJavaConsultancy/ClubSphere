package com.justjava.mycommunity.mobile;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.dto.CreateChatDTO;
import com.justjava.mycommunity.chat.dto.CreateCommunityVO;
import com.justjava.mycommunity.chat.service.ChatService;
import com.justjava.mycommunity.community.CommunityGroupService;
import com.justjava.mycommunity.community.CommunityService;
import com.justjava.mycommunity.userManagement.UserDTO;
import com.justjava.mycommunity.network.NetworkService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/mobile/my-community")
public class MobileCommunityController {

    @Autowired
    private ChatService chatService;
    @Autowired
    private CommunityService communityService;
    @Autowired
    private NetworkService networkService;
    @Autowired
    private CommunityGroupService communityGroupService;
    @Autowired
    private AuthenticationManager authenticationManager;

    @GetMapping
    public String mobileCommunityPage(HttpServletRequest request, Model model) {
        try {
            // Check if user has selected a mycommunity, if not redirect to organizations
            Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");
            if (selectedCommunityId == null) {
                return "redirect:/mobile/organizations";
            }

            // Get the selected mycommunity
            Object communityResponse = communityService.getCommunityById(selectedCommunityId);
            System.out.println("=== MOBILE COMMUNITY DEBUG INFO ===");
            System.out.println("Community Response Type: " + (communityResponse != null ? communityResponse.getClass().getName() : "null"));
            System.out.println("Community Response: " + communityResponse);

            if (communityResponse == null) {
                model.addAttribute("errorMessage", "No mycommunity found. Please create a mycommunity first.");
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

            // Extract mycommunity data
            Map<String, Object> normalizedCommunity = extractCommunityData(communityResponse);
            Long communityId = (Long) normalizedCommunity.get("id");

            if (communityId == null) {
                model.addAttribute("errorMessage", "Invalid mycommunity data - no ID found");
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

            System.out.println("Mobile - Final communityId: " + communityId);
            System.out.println("Mobile - Final normalized mycommunity: " + normalizedCommunity);

            // Get current user info
            String currentUserId = (String) authenticationManager.get("sub");
            boolean isAdmin = authenticationManager.isAdmin();

            // Get mycommunity members for group creation (only for admins)
            List<UserDTO> communityMembers = new ArrayList<>();
            List<UserDTO> availableUsersToInvite = new ArrayList<>();
            try {
                if (isAdmin) {
                    // Get users who are already members of this mycommunity for group creation
                    communityMembers = communityService.getCommunityMembers(communityId);
                    System.out.println("Mobile - Community members count: " + communityMembers.size());

                    // Get users who are not already in the mycommunity for invitations
                    availableUsersToInvite = communityService.getAvailableUsersToInvite(communityId);
                    System.out.println("Mobile - Available users to invite count: " + availableUsersToInvite.size());
                }
            } catch (Exception e) {
                System.out.println("Mobile - Error getting mycommunity members: " + e.getMessage());
            }

            // Get user's groups (groups they belong to)
            List<Map<String, Object>> processedGroups = new ArrayList<>();
            try {
                List<CreateChatDTO> groupsResponse;
                if (isAdmin) {
                    // Admins see all groups in the mycommunity
                    groupsResponse = communityGroupService.getCommunityGroupsByCommunityId(communityId);
                } else {
                    // Regular users see only their groups
                    groupsResponse = communityGroupService.getUserCommunityGroups(currentUserId, communityId);
                }

                System.out.println("Mobile - Groups response: " + groupsResponse);

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

            // Get suggested groups for regular users (groups they don't belong to)
            List<Map<String, Object>> suggestedGroups = new ArrayList<>();
            if (!isAdmin) {
                try {
                    suggestedGroups = communityGroupService.getSuggestedGroups(currentUserId, communityId);
                } catch (Exception e) {
                    System.out.println("Mobile - Error getting suggested groups: " + e.getMessage());
                }
            }

            // Get group requests for admin
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
            model.addAttribute("availableMembers", communityMembers); // Now only mycommunity members
            model.addAttribute("availableUsersToInvite", availableUsersToInvite);
            model.addAttribute("communityId", communityId);
            model.addAttribute("suggestedGroups", suggestedGroups);
            model.addAttribute("groupRequests", groupRequests);
            model.addAttribute("isAdmin", isAdmin);

        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("errorMessage", "Error loading mycommunity data: " + e.getMessage());
            model.addAttribute("community", new HashMap<>());
            model.addAttribute("groups", new ArrayList<>());
            model.addAttribute("availableMembers", new ArrayList<>());
            model.addAttribute("availableUsersToInvite", new ArrayList<>());
            model.addAttribute("communityId", null);
            model.addAttribute("suggestedGroups", new ArrayList<>());
            model.addAttribute("groupRequests", new ArrayList<>());
            model.addAttribute("isAdmin", false);
        }

        model.addAttribute("currentPath", "/my-mycommunity");
        return "mobile-community";
    }

    private Map<String, Object> extractCommunityData(Object communityResponse) {
        Map<String, Object> normalizedCommunity = new HashMap<>();

        try {
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

                    System.out.println("Mobile - Extracted from entity - ID: " + idValue + ", Name: " + nameValue + ", Description: " + descValue + ", IsPrivate: " + isPrivateValue);
                } catch (Exception e) {
                    System.out.println("Mobile - Error extracting from entity: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.out.println("Mobile - Error in extractCommunityData: " + e.getMessage());
            e.printStackTrace();
        }

        return normalizedCommunity;
    }

    // HTMX endpoint for updating mycommunity name (ADMIN ONLY)
    @PostMapping("/update-name")
    public String updateCommunityName(@RequestParam("communityName") String name,
                                      @RequestParam(value = "communityId", required = false) String communityIdStr,
                                      @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                                      RedirectAttributes redirectAttributes,
                                      Model model) {
        try {
            // Check if user is admin
            if (!authenticationManager.isAdmin()) {
                String errorMessage = "Access denied. Only administrators can update mycommunity settings.";
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

            // Get the selected mycommunity ID from session
            HttpServletRequest httpRequest = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            Long selectedCommunityId = (Long) httpRequest.getSession().getAttribute("selectedCommunityId");

            if (selectedCommunityId == null) {
                String errorMessage = "No mycommunity selected";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/mobile-message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/mobile/organizations";
                }
            }

            // Get current mycommunity data using the selected mycommunity ID
            Object currentResponse = communityService.getCommunityById(selectedCommunityId);
            if (currentResponse == null) {
                String errorMessage = "Could not retrieve current mycommunity data";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/mobile-message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/mobile/my-community";
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

            // Update the specific mycommunity
            communityService.updateCommunity(dto);

            String successMessage = "Community name updated successfully!";
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("successMessage", successMessage);
                return "fragments/mobile-message :: success";
            } else {
                redirectAttributes.addFlashAttribute("successMessage", successMessage);
                return "redirect:/mobile/my-community";
            }

        } catch (Exception e) {
            System.out.println("Mobile - Error updating mycommunity name: " + e.getMessage());
            e.printStackTrace();
            String errorMessage = "Error updating mycommunity name: " + e.getMessage();
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("errorMessage", errorMessage);
                return "fragments/mobile-message :: error";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                return "redirect:/mobile/my-community";
            }
        }
    }

    // HTMX endpoint for updating mycommunity description (ADMIN ONLY)
    @PostMapping("/update-description")
    public String updateCommunityDescription(@RequestParam("communityDescription") String description,
                                             @RequestParam(value = "communityId", required = false) String communityIdStr,
                                             @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                                             RedirectAttributes redirectAttributes,
                                             Model model) {
        try {
            // Check if user is admin
            if (!authenticationManager.isAdmin()) {
                String errorMessage = "Access denied. Only administrators can update mycommunity settings.";
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

            // Get the selected mycommunity ID from session
            HttpServletRequest httpRequest = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            Long selectedCommunityId = (Long) httpRequest.getSession().getAttribute("selectedCommunityId");

            if (selectedCommunityId == null) {
                String errorMessage = "No mycommunity selected";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/mobile-message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/mobile/organizations";
                }
            }

            // Get current mycommunity data using the selected mycommunity ID
            Object currentResponse = communityService.getCommunityById(selectedCommunityId);
            if (currentResponse == null) {
                String errorMessage = "Could not retrieve current mycommunity data";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/mobile-message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/mobile/my-community";
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

            communityService.updateCommunity(dto);

            String successMessage = "Community description updated successfully!";
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("successMessage", successMessage);
                return "fragments/mobile-message :: success";
            } else {
                redirectAttributes.addFlashAttribute("successMessage", successMessage);
                return "redirect:/mobile/my-community";
            }

        } catch (Exception e) {
            System.out.println("Mobile - Error updating mycommunity description: " + e.getMessage());
            e.printStackTrace();
            String errorMessage = "Error updating mycommunity description: " + e.getMessage();
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("errorMessage", errorMessage);
                return "fragments/mobile-message :: error";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                return "redirect:/mobile/my-community";
            }
        }
    }

    // HTMX endpoint for adding groups (ADMIN ONLY)
    @PostMapping("/groups/add")
    public String addGroup(@RequestParam("groupName") String name,
                           @RequestParam("groupDescription") String description,
                           @RequestParam(value = "communityId", required = false) String communityIdStr,
                           @RequestParam(value = "selectedMembers", required = false) String selectedMembersStr,
                           @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                           RedirectAttributes redirectAttributes,
                           Model model) {
        try {
            // Check if user is admin
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

            // Get the selected mycommunity ID from session
            HttpServletRequest httpRequest = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            Long selectedCommunityId = (Long) httpRequest.getSession().getAttribute("selectedCommunityId");

            if (selectedCommunityId == null) {
                String errorMessage = "No mycommunity selected";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/mobile-message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/mobile/organizations";
                }
            }

            Long communityId = selectedCommunityId;

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

            Object response = communityGroupService.createCommunityGroup(dto);
            System.out.println("Mobile - Create group response: " + response);

            // Add members to group if any selected
            if (!selectedMembers.isEmpty() && response != null) {
                Long groupId = null;

                // Extract group ID from response
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
                    } catch (Exception e) {
                        System.out.println("Mobile - Could not extract group ID from response: " + e.getMessage());
                    }
                }

                if (groupId != null) {
                    for (String memberId : selectedMembers) {
                        try {
                            // Note: Users should already be in the mycommunity, so we just add them to the group
                            communityGroupService.addUserToCommunityGroup(memberId.trim(), groupId);
                            System.out.println("Mobile - Added user " + memberId + " to group " + groupId);
                        } catch (Exception e) {
                            System.err.println("Mobile - Error adding member " + memberId + " to group: " + e.getMessage());
                        }
                    }
                }
            }

            String successMessage = "Group '" + name + "' created successfully!";
            if (isHtmxRequest(hxRequest)) {
                // Return the updated groups list
                return getGroupsList(model);
            } else {
                redirectAttributes.addFlashAttribute("successMessage", successMessage);
                return "redirect:/mobile/my-community";
            }

        } catch (Exception e) {
            e.printStackTrace();
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

    // HTMX endpoint for editing groups (ADMIN ONLY)
    @PostMapping("/groups/{id}/edit")
    public String editGroup(@PathVariable Long id,
                            @RequestParam("groupName") String name,
                            @RequestParam("groupDescription") String description,
                            @RequestParam(value = "communityId", required = false) String communityIdStr,
                            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                            RedirectAttributes redirectAttributes,
                            Model model) {
        try {
            // Check if user is admin
            if (!authenticationManager.isAdmin()) {
                String errorMessage = "Access denied. Only administrators can edit groups.";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/mobile-message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/mobile/my-community";
                }
            }

            // Get the selected mycommunity ID from session
            HttpServletRequest httpRequest = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            Long selectedCommunityId = (Long) httpRequest.getSession().getAttribute("selectedCommunityId");

            if (selectedCommunityId == null) {
                String errorMessage = "No mycommunity selected";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/mobile-message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/mobile/organizations";
                }
            }

            Long communityId = selectedCommunityId;

            CreateChatDTO dto = new CreateChatDTO();
            dto.setGroupName(name);
            dto.setGroupDescription(description);
            dto.setCommunityId(communityId);

            communityGroupService.updateCommunityGroup(dto, id);

            if (isHtmxRequest(hxRequest)) {
                // Return the updated groups list
                return getGroupsList(model);
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Group updated successfully!");
                return "redirect:/mobile/my-community";
            }

        } catch (Exception e) {
            e.printStackTrace();
            String errorMessage = "Error updating group: " + e.getMessage();
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("errorMessage", errorMessage);
                return "fragments/mobile-message :: error";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                return "redirect:/mobile/my-community";
            }
        }
    }

    // HTMX endpoint for deleting groups (ADMIN ONLY)
    @PostMapping("/groups/{id}/delete")
    public String deleteGroup(@PathVariable Long id,
                              @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                              RedirectAttributes redirectAttributes,
                              Model model) {
        try {
            // Check if user is admin
            if (!authenticationManager.isAdmin()) {
                String errorMessage = "Access denied. Only administrators can delete groups.";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/mobile-message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/mobile/my-community";
                }
            }

            communityGroupService.deleteCommunityGroup(id);

            String successMessage = "Group deleted successfully!";
            if (isHtmxRequest(hxRequest)) {
                // Return the updated groups list
                return getGroupsList(model);
            } else {
                redirectAttributes.addFlashAttribute("successMessage", successMessage);
                return "redirect:/mobile/my-community";
            }

        } catch (Exception e) {
            e.printStackTrace();
            String errorMessage = "Error deleting group: " + e.getMessage();
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("errorMessage", errorMessage);
                return "fragments/mobile-message :: error";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                return "redirect:/mobile/my-community";
            }
        }
    }

    // HTMX endpoint for requesting to join a group
    @PostMapping("/groups/{id}/request")
    public String requestToJoinGroup(@PathVariable Long id,
                                     @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                                     RedirectAttributes redirectAttributes,
                                     Model model) {
        try {
            String currentUserId = (String) authenticationManager.get("sub");
            if (currentUserId == null) {
                String errorMessage = "User not authenticated";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/mobile-message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/mobile/my-community";
                }
            }

            communityGroupService.requestToJoinCommunityGroup(currentUserId, id);

            String successMessage = "Request to join group sent successfully!";
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("successMessage", successMessage);
                return "fragments/mobile-message :: success";
            } else {
                redirectAttributes.addFlashAttribute("successMessage", successMessage);
                return "redirect:/mobile/my-community";
            }

        } catch (Exception e) {
            System.out.println("Mobile - Error requesting to join group: " + e.getMessage());
            e.printStackTrace();
            String errorMessage = "Error sending join request: " + e.getMessage();
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("errorMessage", errorMessage);
                return "fragments/mobile-message :: error";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                return "redirect:/mobile/my-community";
            }
        }
    }

    // HTMX endpoint for approving group requests (ADMIN ONLY)
    @PostMapping("/group-requests/{id}/approve")
    public String approveGroupRequest(@PathVariable Long id,
                                      @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                                      RedirectAttributes redirectAttributes,
                                      Model model) {
        try {
            // Check if user is admin
            if (!authenticationManager.isAdmin()) {
                String errorMessage = "Access denied. Only administrators can approve group requests.";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/mobile-message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/mobile/my-community";
                }
            }

            communityGroupService.approveCommunityGroupRequest(id);

            String successMessage = "Group request approved successfully!";
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("successMessage", successMessage);
                return "fragments/mobile-message :: success";
            } else {
                redirectAttributes.addFlashAttribute("successMessage", successMessage);
                return "redirect:/mobile/my-community";
            }

        } catch (Exception e) {
            System.out.println("Mobile - Error approving group request: " + e.getMessage());
            e.printStackTrace();
            String errorMessage = "Error approving request: " + e.getMessage();
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("errorMessage", errorMessage);
                return "fragments/mobile-message :: error";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                return "redirect:/mobile/my-community";
            }
        }
    }

    // HTMX endpoint for rejecting group requests (ADMIN ONLY)
    @PostMapping("/group-requests/{id}/reject")
    public String rejectGroupRequest(@PathVariable Long id,
                                     @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                                     RedirectAttributes redirectAttributes,
                                     Model model) {
        try {
            // Check if user is admin
            if (!authenticationManager.isAdmin()) {
                String errorMessage = "Access denied. Only administrators can reject group requests.";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/mobile-message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/mobile/my-community";
                }
            }

            communityGroupService.rejectCommunityGroupRequest(id);

            String successMessage = "Group request rejected successfully!";
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("successMessage", successMessage);
                return "fragments/mobile-message :: success";
            } else {
                redirectAttributes.addFlashAttribute("successMessage", successMessage);
                return "redirect:/mobile/my-community";
            }

        } catch (Exception e) {
            System.out.println("Mobile - Error rejecting group request: " + e.getMessage());
            e.printStackTrace();
            String errorMessage = "Error rejecting request: " + e.getMessage();
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("errorMessage", errorMessage);
                return "fragments/mobile-message :: error";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                return "redirect:/mobile/my-community";
            }
        }
    }

    // HTMX endpoint for inviting members (ADMIN ONLY)
    @PostMapping("/invite")
    public String inviteMember(@RequestParam("userId") String userId,
                               @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                               RedirectAttributes redirectAttributes,
                               Model model) {
        try {
            // Check if user is admin
            if (!authenticationManager.isAdmin()) {
                String errorMessage = "Access denied. Only administrators can invite members.";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/mobile-message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/mobile/my-community";
                }
            }

            // Get the selected mycommunity ID from session
            HttpServletRequest httpRequest = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            Long selectedCommunityId = (Long) httpRequest.getSession().getAttribute("selectedCommunityId");

            if (selectedCommunityId == null) {
                String errorMessage = "No mycommunity selected";
                if (isHtmxRequest(hxRequest)) {
                    model.addAttribute("errorMessage", errorMessage);
                    return "fragments/mobile-message :: error";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                    return "redirect:/mobile/organizations";
                }
            }

            Long communityId = selectedCommunityId;

            // Send invitation
            communityService.inviteUserToCommunity(userId, communityId);

            String successMessage = "Invitation sent successfully!";
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("successMessage", successMessage);
                return "fragments/mobile-message :: success";
            } else {
                redirectAttributes.addFlashAttribute("successMessage", successMessage);
                return "redirect:/mobile/my-community";
            }

        } catch (Exception e) {
            System.out.println("Mobile - Error inviting user: " + e.getMessage());
            e.printStackTrace();
            String errorMessage = "Error sending invitation: " + e.getMessage();
            if (isHtmxRequest(hxRequest)) {
                model.addAttribute("errorMessage", errorMessage);
                return "fragments/mobile-message :: error";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
                return "redirect:/mobile/my-community";
            }
        }
    }

    // Get groups list for HTMX updates
    @GetMapping("/groups")
    public String getGroupsList(Model model) {
        try {
            // Get the selected mycommunity ID from session
            HttpServletRequest httpRequest = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            Long selectedCommunityId = (Long) httpRequest.getSession().getAttribute("selectedCommunityId");

            if (selectedCommunityId == null) {
                model.addAttribute("groups", new ArrayList<>());
                return "fragments/mobile-groups :: groups-list";
            }

            Object communityResponse = communityService.getCommunityById(selectedCommunityId);
            if (communityResponse == null) {
                model.addAttribute("groups", new ArrayList<>());
                return "fragments/mobile-groups :: groups-list";
            }

            Map<String, Object> community = extractCommunityData(communityResponse);
            Long communityId = (Long) community.get("id");
            if (communityId == null) {
                model.addAttribute("groups", new ArrayList<>());
                return "fragments/mobile-groups :: groups-list";
            }

            String currentUserId = (String) authenticationManager.get("sub");
            boolean isAdmin = authenticationManager.isAdmin();

            List<Map<String, Object>> processedGroups = new ArrayList<>();
            List<CreateChatDTO> groupsResponse;

            if (isAdmin) {
                // Admins see all groups
                groupsResponse = communityGroupService.getCommunityGroupsByCommunityId(communityId);
            } else {
                // Regular users see only their groups
                groupsResponse = communityGroupService.getUserCommunityGroups(currentUserId, communityId);
            }

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
            System.out.println("Mobile - Error in getGroupsList: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("groups", new ArrayList<>());
            model.addAttribute("isAdmin", false);
            return "fragments/mobile-groups :: groups-list";
        }
    }

    // Get suggested groups for HTMX updates
    @GetMapping("/suggested-groups")
    public String getSuggestedGroups(Model model) {
        try {
            // Get the selected mycommunity ID from session
            HttpServletRequest httpRequest = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            Long selectedCommunityId = (Long) httpRequest.getSession().getAttribute("selectedCommunityId");

            if (selectedCommunityId == null) {
                model.addAttribute("suggestedGroups", new ArrayList<>());
                return "fragments/mobile-suggested-groups :: suggested-groups-list";
            }

            Object communityResponse = communityService.getCommunityById(selectedCommunityId);
            if (communityResponse == null) {
                model.addAttribute("suggestedGroups", new ArrayList<>());
                return "fragments/mobile-suggested-groups :: suggested-groups-list";
            }

            Map<String, Object> community = extractCommunityData(communityResponse);
            Long communityId = (Long) community.get("id");
            if (communityId == null) {
                model.addAttribute("suggestedGroups", new ArrayList<>());
                return "fragments/mobile-suggested-groups :: suggested-groups-list";
            }

            String currentUserId = (String) authenticationManager.get("sub");
            List<Map<String, Object>> suggestedGroups = communityGroupService.getSuggestedGroups(currentUserId, communityId);

            model.addAttribute("suggestedGroups", suggestedGroups);
            return "fragments/mobile-suggested-groups :: suggested-groups-list";

        } catch (Exception e) {
            System.out.println("Mobile - Error in getSuggestedGroups: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("suggestedGroups", new ArrayList<>());
            return "fragments/mobile-suggested-groups :: suggested-groups-list";
        }
    }

    // Get group requests for HTMX updates
    @GetMapping("/group-requests")
    public String getGroupRequests(Model model) {
        try {
            // Check if user is admin
            if (!authenticationManager.isAdmin()) {
                model.addAttribute("groupRequests", new ArrayList<>());
                return "fragments/mobile-group-requests :: group-requests-list";
            }

            List<Map<String, Object>> groupRequests = communityGroupService.getPendingGroupRequests();
            model.addAttribute("groupRequests", groupRequests);
            return "fragments/mobile-group-requests :: group-requests-list";

        } catch (Exception e) {
            System.out.println("Mobile - Error in getGroupRequests: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("groupRequests", new ArrayList<>());
            return "fragments/mobile-group-requests :: group-requests-list";
        }
    }

    // Get group details for editing (ADMIN ONLY)
    @GetMapping("/groups/{id}")
    @ResponseBody
    public Map<String, Object> getGroup(@PathVariable Long id) {
        try {
            // Check if user is admin
            if (!authenticationManager.isAdmin()) {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("error", "Access denied. Only administrators can view group details.");
                return errorResult;
            }

            CreateChatDTO group = communityGroupService.getCommunityGroupById(id);
            Map<String, Object> result = new HashMap<>();
            result.put("id", group.getId());
            result.put("groupName", group.getGroupName());
            result.put("groupDescription", group.getGroupDescription());
            result.put("memberCount", group.getMemberCount());
            return result;
        } catch (Exception e) {
            System.out.println("Mobile - Error in getGroup: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "Error loading group details");
            return errorResult;
        }
    }

    // Utility method to check if request is from HTMX
    private boolean isHtmxRequest(String hxRequest) {
        return "true".equals(hxRequest);
    }
}
