package com.justjava.mycommunity.mobile;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.dto.CreateChatDTO;
import com.justjava.mycommunity.chat.dto.CreateCommunityVO;
import com.justjava.mycommunity.chat.service.ChatService;
import com.justjava.mycommunity.community.CommunityGroupService;
import com.justjava.mycommunity.community.CommunityService;
import com.justjava.mycommunity.community.dto.CommunityDTO;
import com.justjava.mycommunity.network.NetworkService;
import com.justjava.mycommunity.userManagement.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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
@RequiredArgsConstructor
public class MobileCommunityController {

    private final ChatService chatService;
    private final CommunityService communityService;
    private final NetworkService networkService;
    private final CommunityGroupService communityGroupService;
    private final AuthenticationManager authenticationManager;

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
                if (isAdmin) {
                    communityMembers = communityService.getCommunityMembers(communityId);
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
            model.addAttribute("availableUsersToInvite", availableUsersToInvite);
            model.addAttribute("communityId", communityId);
            model.addAttribute("suggestedGroups", suggestedGroups);
            model.addAttribute("groupRequests", groupRequests);
            model.addAttribute("isAdmin", isAdmin);

        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("errorMessage", "Error loading community data: " + e.getMessage());
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

    private Map<String, Object> extractCommunityData(CommunityDTO communityResponse) {
        Map<String, Object> normalizedCommunity = new HashMap<>();
        if (communityResponse != null) {
            normalizedCommunity.put("id", communityResponse.getId());
            normalizedCommunity.put("communityName", communityResponse.getName());
            normalizedCommunity.put("communityDescription", communityResponse.getDescription());
            normalizedCommunity.put("isPrivate", communityResponse.getIsPrivate());
        }
        return normalizedCommunity;
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
}