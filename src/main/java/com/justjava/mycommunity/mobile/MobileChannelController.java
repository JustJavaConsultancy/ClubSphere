package com.justjava.mycommunity.mobile;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.dto.CommentDTO;
import com.justjava.mycommunity.chat.dto.CreateCommunityVO;
import com.justjava.mycommunity.chat.model.PostMessage;
import com.justjava.mycommunity.community.Community;
import com.justjava.mycommunity.community.CommunityService;
import com.justjava.mycommunity.community.dto.CommunityDTO;
import com.justjava.mycommunity.posts.PostService;
import com.justjava.mycommunity.support.AISupportService;
import com.justjava.mycommunity.support.SupportFeignClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/mobile")
public class MobileChannelController {

    private final AuthenticationManager authenticationManager;
    private final SupportFeignClient supportFeignClient;
    private final PostService postService;
    private final CommunityService communityService;
    private final AISupportService aISupportService;

    public MobileChannelController(AuthenticationManager authenticationManager,
                                   SupportFeignClient supportFeignClient,
                                   PostService postService,
                                   CommunityService communityService, AISupportService aISupportService) {
        this.authenticationManager = authenticationManager;
        this.supportFeignClient = supportFeignClient;
        this.postService = postService;
        this.communityService = communityService;
        this.aISupportService = aISupportService;
    }

    @GetMapping("/channel")
    public String channelPage(HttpServletRequest request, Model model) {
        String currentUserId = (String) authenticationManager.get("sub");
        boolean isAdmin = authenticationManager.isAdmin();

        // Clear any mycommunity-specific session data
        request.getSession().removeAttribute("selectedCommunityId");
        request.getSession().removeAttribute("selectedCommunityName");

        // Get all posts from all communities the user belongs to (no specific mycommunity selected)
        model.addAttribute("allPosts", postService.getPostsFromUserCommunities(currentUserId));

        if (authenticationManager.isSupportAdmin()) {
            return "redirect:/support/dashboard";
        }

        request.getSession(true).setAttribute("isAdmin", authenticationManager.isAdmin());
        request.getSession(true).setAttribute("isSupportAdmin", authenticationManager.isSupportAdmin());
        request.getSession().setAttribute("userId", authenticationManager.get("sub"));

        model.addAttribute("userId", authenticationManager.get("sub"));
        model.addAttribute("usersName", authenticationManager.get("name"));
        model.addAttribute("isSupportAdmin", authenticationManager.isSupportAdmin());
        model.addAttribute("isAdmin", authenticationManager.isAdmin());
        model.addAttribute("currentPath", "/channel");
        request.getSession(true).setAttribute("loggedInUser", authenticationManager.get("name"));

        return "mobile-channel";
    }

    @GetMapping("/add-community")
    public String addCommunityPage(HttpServletRequest request, Model model) {
        // Check if user is admin
        if (!authenticationManager.isAdmin()) {
            return "redirect:/mobile/organizations";
        }

        model.addAttribute("userId", authenticationManager.get("sub"));
        model.addAttribute("usersName", authenticationManager.get("name"));
        model.addAttribute("currentPath", "/add-mycommunity");

        return "mobile-add-community";
    }

    @PostMapping("/add-community")
    public String createCommunity(@RequestParam(value = "communityName", required = false) String communityName,
                                  @RequestParam(value = "communityDescription", required = false) String communityDescription,
                                  @RequestParam(value = "isPrivate", required = false) Boolean isPrivate,
                                  HttpServletRequest request,
                                  Model model) {
        System.out.println("=== Community Creation Debug ===");
        System.out.println("Community Name: " + communityName);
        System.out.println("Community Description: " + communityDescription);
        System.out.println("Is Private: " + isPrivate);
        System.out.println("Is Admin: " + authenticationManager.isAdmin());
        System.out.println("User ID: " + authenticationManager.get("sub"));

        // Check if user is admin
        if (!authenticationManager.isAdmin()) {
            System.out.println("User is not admin, redirecting to organizations");
            return "redirect:/mobile/organizations";
        }

        // Validate input parameters
        if (communityName == null || communityName.trim().isEmpty()) {
            System.out.println("Community name is null or empty");
            model.addAttribute("errorMessage", "Community name is required.");
            model.addAttribute("userId", authenticationManager.get("sub"));
            model.addAttribute("usersName", authenticationManager.get("name"));
            model.addAttribute("currentPath", "/add-mycommunity");
            return "mobile-add-community";
        }

        if (communityDescription == null || communityDescription.trim().isEmpty()) {
            System.out.println("Community description is null or empty");
            model.addAttribute("errorMessage", "Community description is required.");
            model.addAttribute("userId", authenticationManager.get("sub"));
            model.addAttribute("usersName", authenticationManager.get("name"));
            model.addAttribute("currentPath", "/add-mycommunity");
            return "mobile-add-community";
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

            CommunityDTO createdCommunity = communityService.createCommunity(createCommunityVO);

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
            return "redirect:/mobile/organizations";

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

            return "mobile-add-community";
        }
    }

    @GetMapping("/organizations")
    public String organizationsPage(HttpServletRequest request, Model model,
                                    @RequestParam(value = "tab", required = false) String tab) {
        String currentUserId = (String) authenticationManager.get("sub");
        boolean isAdmin = authenticationManager.isAdmin();

        // Clear any mycommunity-specific session data
        request.getSession().removeAttribute("selectedCommunityId");
        request.getSession().removeAttribute("selectedCommunityName");

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

        // Get user subscriptions
        List<Map<String, Object>> userSubscriptions = communityService.getUserSubscriptions(currentUserId);

        model.addAttribute("userCommunities", userCommunities);
        model.addAttribute("suggestedCommunities", suggestedCommunities);
        model.addAttribute("communityInvitations", communityInvitations);
        model.addAttribute("communityRequests", communityRequests);
        model.addAttribute("userSubscriptions", userSubscriptions);
        model.addAttribute("userId", authenticationManager.get("sub"));
        model.addAttribute("usersName", authenticationManager.get("name"));
        model.addAttribute("currentPath", "/organizations");
        model.addAttribute("activeTab", tab != null ? tab : "my-communities");

        // Check for success message
        String successMessage = (String) request.getSession().getAttribute("successMessage");
        if (successMessage != null) {
            model.addAttribute("successMessage", successMessage);
            request.getSession().removeAttribute("successMessage");
        }

        return "mobile-organizations";
    }

    @GetMapping("/community/{communityId}")
    public String selectCommunity(@PathVariable Long communityId,
                                  HttpServletRequest request,
                                  Model model) {
        // Set the selected mycommunity in session
        request.getSession().setAttribute("selectedCommunityId", communityId);

        // Get mycommunity details and set name in session
        try {
            CommunityDTO community = communityService.getCommunityById(communityId);
            if (community != null) {
                request.getSession().setAttribute("selectedCommunityName", community.getName());
            }
        } catch (Exception e) {
            System.err.println("Error getting mycommunity details: " + e.getMessage());
        }

        // Redirect to mycommunity-specific home page
        return "redirect:/mobile/home";
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

    @PostMapping("/create-post")
    public String handlePost(
            @RequestParam("content") String content,
            @RequestParam(value = "image", required = false) MultipartFile image,
            HttpServletRequest request
    ) {
        String currentUserId = (String) authenticationManager.get("sub");
        System.out.println("Post content: " + content);

        // Get selected community context and check admin/creator permission
        Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");
        boolean canPost;
        if (selectedCommunityId != null) {
            canPost = postService.canUserPostToCommunity(currentUserId, selectedCommunityId);
        } else {
            canPost = postService.canUserPost(currentUserId);
        }
        if (!canPost) {
            return ""; // Silently reject - form shouldn't be visible anyway
        }

        if (image != null && !image.isEmpty()) {
            System.out.println("Uploaded file: " + image.getOriginalFilename());
        } else {
            System.out.println("No file uploaded.");
        }

        PostMessage postMessage = new PostMessage(content, currentUserId, image);
        if (selectedCommunityId != null) {
            postMessage.setPostLevel("COMMUNITY");
            postMessage.setPostLevelId(selectedCommunityId);
        }
        postService.createPost(postMessage);

        return "";
    }

    @PostMapping("/api/chat/send")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleChatMessage(@RequestParam("message") String message) {
        Map<String, Object> response = new HashMap<>();
        try {
            String aiResponse = aISupportService.supportChat(message, (String) authenticationManager.get("sub"));
            response.put("status", "success");
            response.put("response", aiResponse);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("status", "error");
            response.put("response", "Sorry, something went wrong.");
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/post-comment")
    public String postComment(@RequestParam("comment") String comment,
                              @RequestParam("postId") String postId,
                              Model model) {
        String currentUserId = (String) authenticationManager.get("sub");
        CommentDTO commentDTO = new CommentDTO();
        commentDTO.setComment(comment);
        commentDTO.setPostId(Long.valueOf(postId));
        commentDTO.setUserId(currentUserId);

        postService.createComment(commentDTO);
        model.addAttribute("selectPostID", postId);
        model.addAttribute("refreshComment", postService.getComments(Long.valueOf(postId)));
        return "";
    }

    private Map<String, Object> extractCommunityData(Object communityResponse) {
        Map<String, Object> normalizedCommunity = new HashMap<>();

        try {
            if (communityResponse instanceof Map) {
                Map<String, Object> communityMap = (Map<String, Object>) communityResponse;
                normalizedCommunity.put("id", communityMap.get("id"));
                normalizedCommunity.put("communityName", communityMap.get("name"));
                normalizedCommunity.put("communityDescription", communityMap.get("description"));
            } else if (communityResponse instanceof Community) {
                Community community = (Community) communityResponse;
                normalizedCommunity.put("id", community.getId());
                normalizedCommunity.put("communityName", community.getName() != null ? community.getName() : "Unknown Community");
                normalizedCommunity.put("communityDescription", community.getDescription() != null ? community.getDescription() : "No description");
            } else {
                try {
                    Object idValue = communityResponse.getClass().getMethod("getId").invoke(communityResponse);
                    Object nameValue = communityResponse.getClass().getMethod("getName").invoke(communityResponse);
                    Object descValue = communityResponse.getClass().getMethod("getDescription").invoke(communityResponse);

                    normalizedCommunity.put("id", idValue);
                    normalizedCommunity.put("communityName", nameValue != null ? nameValue.toString() : "Unknown Community");
                    normalizedCommunity.put("communityDescription", descValue != null ? descValue.toString() : "No description");
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
}
