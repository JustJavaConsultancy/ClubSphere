package com.justjava.mycommunity.mobile;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.dto.CommentDTO;
import com.justjava.mycommunity.chat.dto.CreateChatDTO;
import com.justjava.mycommunity.chat.dto.PostDTO;
import com.justjava.mycommunity.chat.model.PostMessage;
import com.justjava.mycommunity.community.CommunityGroupService;
import com.justjava.mycommunity.community.CommunityService;
import com.justjava.mycommunity.posts.PostService;
import com.justjava.mycommunity.userManagement.UserDTO;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/mobile")
@RequiredArgsConstructor
public class MobileGroupController {

    private final CommunityService communityService;
    private final CommunityGroupService communityGroupService;
    private final PostService postService;
    private final AuthenticationManager authenticationManager;

    @GetMapping("/my-groups")
    public String myGroups(Model model, HttpSession session) {
        String currentUserId = (String) session.getAttribute("userId");
        if (currentUserId == null) currentUserId = (String) authenticationManager.get("sub");
        try {
            List<Map<String, Object>> userGroups = communityGroupService.getUserCommunityGroupsAcrossAllCommunities(currentUserId);
            model.addAttribute("userGroups", userGroups);
        } catch (Exception e) {
            model.addAttribute("userGroups", List.of());
        }
        model.addAttribute("currentPath", "/my-groups");
        return "mobile-my-groups";
    }

    @GetMapping("/group")
    public String mobileGroupPage(@RequestParam Long id, Model model, HttpSession session) {
        try {
            model.addAttribute("currentPath", "/group");
            model.addAttribute("session", session);

            String currentUserId = (String) authenticationManager.get("sub");
            boolean isAdmin = authenticationManager.isAdmin();

            // Get the specific group by ID
            CreateChatDTO currentGroup = null;
            Map<String, Object> normalizedCommunity = null;

            try {
                currentGroup = communityGroupService.getCommunityGroupById(id);

                // Get the community data for this group
                Map<String, Object> groupCommunityInfo = communityGroupService.getCommunityInfoForGroup(id);
                if (groupCommunityInfo != null) {
                    normalizedCommunity = groupCommunityInfo;
                } else {
                    // Fallback to default community
                    Object communityResponse = communityService.getCommunity();
                    if (communityResponse != null) {
                        normalizedCommunity = extractCommunityData(communityResponse);
                    }
                }

                if (normalizedCommunity == null && currentGroup != null) {
                    normalizedCommunity = new HashMap<>();
                    normalizedCommunity.put("id", currentGroup.getCommunityId());
                    normalizedCommunity.put("communityName", "Community");
                    normalizedCommunity.put("communityDescription", "Community Description");
                }
            } catch (Exception e) {
                System.out.println("Mobile - Error getting group by ID: " + e.getMessage());
                e.printStackTrace();
            }

            if (currentGroup == null) {
                model.addAttribute("error", "Group not found or you don't have access to it.");
                return "mobile-group";
            }

            // Verify user has access to this group
            List<Map<String, Object>> userAccessibleGroups = communityGroupService.getUserCommunityGroupsAcrossAllCommunities(currentUserId);
            boolean hasAccess = userAccessibleGroups.stream()
                    .anyMatch(group -> group.get("id").equals(id));

            if (!hasAccess) {
                model.addAttribute("error", "You don't have access to this group.");
                return "mobile-group";
            }

            // Get group members
            List<UserDTO> groupMembers = communityGroupService.getCommunityGroupUsers(id);

            // Get group posts
            List<PostDTO> groupPosts = postService.getGroupPosts(id);

            // Add data to model
            model.addAttribute("group", currentGroup);
            model.addAttribute("groupMembers", groupMembers);
            model.addAttribute("groupPosts", groupPosts);
            model.addAttribute("community", normalizedCommunity);
            model.addAttribute("userId", currentUserId);
            model.addAttribute("isAdmin", isAdmin);
            // Group admin check
            boolean isGroupAdmin = communityGroupService.isUserGroupAdmin(currentUserId, id);
            model.addAttribute("isGroupAdmin", isGroupAdmin);

        } catch (Exception e) {
            model.addAttribute("error", "Unable to load group at this time. Please try again later.");
            e.printStackTrace();
        }

        return "mobile-group";
    }

    @PostMapping("/group/post")
    @ResponseBody
    public Map<String, Object> createGroupPost(
            @RequestParam Long groupId,
            @RequestParam String content,
            @RequestParam(required = false) MultipartFile file,
            HttpSession httpSession) {

        Map<String, Object> response = new HashMap<>();

        try {
            String userId = (String) httpSession.getAttribute("userId");
            if (userId == null) {
                userId = (String) authenticationManager.get("sub");
            }
            if (userId == null) {
                response.put("success", false);
                response.put("message", "User not authenticated");
                return response;
            }

            PostMessage postMessage = PostMessage.builder()
                    .content(content)
                    .userId(userId)
                    .file(file)
                    .privacy(false)
                    .postLevel("GROUP")
                    .postLevelId(groupId)
                    .build();

            postService.createPost(postMessage);

            response.put("success", true);
            response.put("message", "Post created successfully");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to create post: " + e.getMessage());
            e.printStackTrace();
        }

        return response;
    }

    @PostMapping("/group/post-comment")
    public String postComment(@RequestParam("comment") String comment,
                              @RequestParam("postId") String postId,
                              @RequestParam("groupId") String groupId,
                              Model model) {
        String currentUserId = (String) authenticationManager.get("sub");
        CommentDTO commentDTO = new CommentDTO();
        commentDTO.setComment(comment);
        commentDTO.setPostId(Long.valueOf(postId));
        commentDTO.setUserId(currentUserId);

        postService.createComment(commentDTO);

        // Redirect back to the group page
        return "redirect:/mobile/group?id=" + groupId;
    }

    private Map<String, Object> extractCommunityData(Object communityResponse) {
        Map<String, Object> normalizedCommunity = new HashMap<>();
        try {
            if (communityResponse instanceof Map) {
                Map<String, Object> communityMap = (Map<String, Object>) communityResponse;
                normalizedCommunity.put("id", communityMap.get("id"));
                normalizedCommunity.put("communityName", communityMap.get("name"));
                normalizedCommunity.put("communityDescription", communityMap.get("description"));
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
                }
            }
        } catch (Exception e) {
            System.out.println("Error in extractCommunityData: " + e.getMessage());
        }
        return normalizedCommunity;
    }
}


