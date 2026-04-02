package com.justjava.mycommunity.organization;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.dto.CreateCommunityVO;
import com.justjava.mycommunity.community.Community;
import com.justjava.mycommunity.community.CommunityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class OrganizationController {

    private final AuthenticationManager authenticationManager;
    private final CommunityService communityService;

    public OrganizationController(AuthenticationManager authenticationManager,
                                  CommunityService communityService) {
        this.authenticationManager = authenticationManager;
        this.communityService = communityService;
    }

    @GetMapping("/organizations")
    public String organizationsPage(HttpServletRequest request, Model model) {
        String currentUserId = (String) authenticationManager.get("sub");
        boolean isAdmin = authenticationManager.isAdmin();

        // Clear any mycommunity-specific session data when viewing organizations page
        request.getSession().removeAttribute("selectedCommunityId");
        request.getSession().removeAttribute("selectedCommunityName");

        System.out.println("Cleared mycommunity selection - user viewing organizations page");

        // Get communities the user belongs to
        List<Map<String, Object>> userCommunities = communityService.getUserCommunities(currentUserId);

        // For non-admin users, get suggested communities and invitations
        List<Map<String, Object>> suggestedCommunities = new ArrayList<>();
        List<Map<String, Object>> communityInvitations = new ArrayList<>();
        if (!isAdmin) {
            suggestedCommunities = communityService.getSuggestedCommunities(currentUserId);
            communityInvitations = communityService.getUserCommunityInvitations(currentUserId);
        }

        // For admin users, get pending requests
        List<Map<String, Object>> communityRequests = new ArrayList<>();
        if (isAdmin) {
            communityRequests = communityService.getPendingRequests();
        }

        model.addAttribute("userCommunities", userCommunities);
        model.addAttribute("suggestedCommunities", suggestedCommunities);
        model.addAttribute("communityInvitations", communityInvitations);
        model.addAttribute("communityRequests", communityRequests);
        model.addAttribute("userId", authenticationManager.get("sub"));
        model.addAttribute("usersName", authenticationManager.get("name"));
        model.addAttribute("currentPath", "/organizations");

        // Check for success message
        String successMessage = (String) request.getSession().getAttribute("successMessage");
        if (successMessage != null) {
            model.addAttribute("successMessage", successMessage);
            request.getSession().removeAttribute("successMessage");
        }

        return "organizations";
    }

    @GetMapping("/add-community")
    public String addCommunityPage(HttpServletRequest request, Model model) {
        // Check if user is admin
        if (!authenticationManager.isAdmin()) {
            return "redirect:/organizations";
        }

        model.addAttribute("userId", authenticationManager.get("sub"));
        model.addAttribute("usersName", authenticationManager.get("name"));
        model.addAttribute("currentPath", "/add-mycommunity");

        return "add-community";
    }

    @PostMapping("/add-community")
    public String createCommunity(@RequestParam(value = "communityName", required = false) String communityName,
                                  @RequestParam(value = "communityDescription", required = false) String communityDescription,
                                  @RequestParam(value = "isPrivate", required = false) Boolean isPrivate,
                                  HttpServletRequest request,
                                  Model model) {
        System.out.println("=== Web Community Creation Debug ===");
        System.out.println("Community Name: " + communityName);
        System.out.println("Community Description: " + communityDescription);
        System.out.println("Is Private: " + isPrivate);
        System.out.println("Is Admin: " + authenticationManager.isAdmin());
        System.out.println("User ID: " + authenticationManager.get("sub"));

        // Check if user is admin
        if (!authenticationManager.isAdmin()) {
            System.out.println("User is not admin, redirecting to organizations");
            return "redirect:/organizations";
        }

        // Validate input parameters
        if (communityName == null || communityName.trim().isEmpty()) {
            System.out.println("Community name is null or empty");
            model.addAttribute("errorMessage", "Community name is required.");
            model.addAttribute("userId", authenticationManager.get("sub"));
            model.addAttribute("usersName", authenticationManager.get("name"));
            model.addAttribute("currentPath", "/add-mycommunity");
            return "add-community";
        }

        if (communityDescription == null || communityDescription.trim().isEmpty()) {
            System.out.println("Community description is null or empty");
            model.addAttribute("errorMessage", "Community description is required.");
            model.addAttribute("userId", authenticationManager.get("sub"));
            model.addAttribute("usersName", authenticationManager.get("name"));
            model.addAttribute("currentPath", "/add-mycommunity");
            return "add-community";
        }

        try {
            // Get current user email for mycommunity creation
            String currentUserId = (String) authenticationManager.get("sub");
            String userEmail = (String) authenticationManager.get("email");

            System.out.println("Creating mycommunity with user email: " + userEmail);

            // Create the mycommunity using the service with default channel and town hall values
            CreateCommunityVO createCommunityVO = CreateCommunityVO.builder()
                    .communityName(communityName.trim())
                    .communityDescription(communityDescription.trim())
                    .channelName("General")
                    .channelDescription("General discussion channel")
                    .townHallName("Community Forum")
                    .townHallDescription("Main mycommunity discussion forum")
                    .userEmail(userEmail) // Add user email to the VO
                    .isPrivate(isPrivate != null ? isPrivate : false) // Default to false if not specified
                    .build();

            System.out.println("CreateCommunityVO created: " + createCommunityVO);

            Community createdCommunity = communityService.createCommunity(createCommunityVO);

            System.out.println("Community created successfully with ID: " + createdCommunity.getId());

            // Add the current user to the newly created mycommunity
            try {
                communityService.addUserToCommunity(currentUserId, createdCommunity.getId());
                System.out.println("User added to mycommunity successfully");
            } catch (Exception e) {
                System.err.println("Error adding user to mycommunity: " + e.getMessage());
                e.printStackTrace();
            }

            // Redirect to organizations page with success message
            String successMessage = "Community '" + communityName + "' created successfully!";
            if (isPrivate != null && isPrivate) {
                successMessage += " This is a private mycommunity - only invited users can join.";
            }
            request.getSession().setAttribute("successMessage", successMessage);
            return "redirect:/organizations";

        } catch (Exception e) {
            System.err.println("Error creating mycommunity: " + e.getMessage());
            e.printStackTrace();

            // Return to form with error
            String errorMessage = "Failed to create mycommunity: " + e.getMessage();
            model.addAttribute("errorMessage", errorMessage);
            model.addAttribute("userId", authenticationManager.get("sub"));
            model.addAttribute("usersName", authenticationManager.get("name"));
            model.addAttribute("currentPath", "/add-mycommunity");
            model.addAttribute("communityName", communityName); // Preserve form data
            model.addAttribute("communityDescription", communityDescription);
            model.addAttribute("isPrivate", isPrivate);

            return "add-community";
        }
    }

    @GetMapping("/community/{communityId}")
    public String selectCommunity(@PathVariable Long communityId,
                                  HttpServletRequest request,
                                  Model model) {
        String currentUserId = (String) authenticationManager.get("sub");

        try {
            // Verify user has access to this mycommunity (either admin or member)
            boolean hasAccess = false;

            if (authenticationManager.isAdmin()) {
                hasAccess = true;
            } else {
                // Check if user is a member of this mycommunity
                List<Map<String, Object>> userCommunities = communityService.getUserCommunities(currentUserId);
                hasAccess = userCommunities.stream()
                        .anyMatch(community -> communityId.equals(community.get("id")));
            }

            if (!hasAccess) {
                System.err.println("User " + currentUserId + " does not have access to mycommunity " + communityId);
                return "redirect:/organizations?error=access_denied";
            }

            // Set the selected mycommunity in session
            request.getSession().setAttribute("selectedCommunityId", communityId);

            // Get mycommunity details and set name in session
            Community community = communityService.getCommunityById(communityId);
            if (community != null) {
                request.getSession().setAttribute("selectedCommunityName", community.getName());
                System.out.println("Selected mycommunity: " + community.getName() + " (ID: " + communityId + ")");
            }

        } catch (Exception e) {
            System.err.println("Error selecting mycommunity: " + e.getMessage());
            e.printStackTrace();
            return "redirect:/organizations?error=community_not_found";
        }

        // Redirect to mycommunity-specific home page
        return "redirect:/";
    }

    @PostMapping("/request-to-join")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestToJoinCommunity(@RequestParam("communityId") Long communityId) {
        Map<String, Object> response = new HashMap<>();
        try {
            String currentUserId = (String) authenticationManager.get("sub");
            communityService.requestToJoinCommunity(currentUserId, communityId);
            response.put("success", true);
            response.put("message", "Request sent successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error requesting to join mycommunity: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Failed to send request");
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/cancel-request")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cancelCommunityRequest(@RequestParam("communityId") Long communityId) {
        Map<String, Object> response = new HashMap<>();
        try {
            String currentUserId = (String) authenticationManager.get("sub");
            communityService.cancelCommunityRequest(currentUserId, communityId);
            response.put("success", true);
            response.put("message", "Request canceled successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error canceling mycommunity request: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Failed to cancel request");
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/approve-request")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> approveCommunityRequest(@RequestParam("requestId") Long requestId) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (!authenticationManager.isAdmin()) {
                response.put("success", false);
                response.put("message", "Unauthorized");
                return ResponseEntity.status(403).body(response);
            }

            communityService.approveCommunityRequest(requestId);
            response.put("success", true);
            response.put("message", "Request approved successfully");
            response.put("reload", true); // Signal frontend to reload data
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error approving mycommunity request: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Failed to approve request");
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/reject-request")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> rejectCommunityRequest(@RequestParam("requestId") Long requestId) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (!authenticationManager.isAdmin()) {
                response.put("success", false);
                response.put("message", "Unauthorized");
                return ResponseEntity.status(403).body(response);
            }

            communityService.rejectCommunityRequest(requestId);
            response.put("success", true);
            response.put("message", "Request rejected successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error rejecting mycommunity request: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Failed to reject request");
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/accept-invitation")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> acceptCommunityInvitation(@RequestParam("invitationId") Long invitationId) {
        Map<String, Object> response = new HashMap<>();
        try {
            String currentUserId = (String) authenticationManager.get("sub");
            communityService.acceptCommunityInvitation(currentUserId, invitationId);
            response.put("success", true);
            response.put("message", "Invitation accepted successfully");
            response.put("reload", true); // Signal frontend to reload data
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error accepting mycommunity invitation: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Failed to accept invitation");
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/decline-invitation")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> declineCommunityInvitation(@RequestParam("invitationId") Long invitationId) {
        Map<String, Object> response = new HashMap<>();
        try {
            String currentUserId = (String) authenticationManager.get("sub");
            communityService.declineCommunityInvitation(currentUserId, invitationId);
            response.put("success", true);
            response.put("message", "Invitation declined successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error declining mycommunity invitation: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Failed to decline invitation");
            return ResponseEntity.status(500).body(response);
        }
    }
}
