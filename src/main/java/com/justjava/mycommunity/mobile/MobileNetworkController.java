package com.justjava.mycommunity.mobile;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.service.ChatService;
import com.justjava.mycommunity.community.CommunityService;
import com.justjava.mycommunity.userManagement.UserDTO;
import com.justjava.mycommunity.network.NetworkService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/mobile/network")
public class MobileNetworkController {
    private final ChatService chatService;
    private final AuthenticationManager authenticationManager;
    private final NetworkService networkService;
    private final CommunityService communityService;

    public MobileNetworkController(ChatService chatService, AuthenticationManager authenticationManager,
                                   NetworkService networkService, CommunityService communityService) {
        this.chatService = chatService;
        this.authenticationManager = authenticationManager;
        this.networkService = networkService;
        this.communityService = communityService;
    }

    @GetMapping
    public String getNetwork(Model model, HttpServletRequest request){
        String currentUserId = (String) authenticationManager.get("sub");

        // Get selected community from session - network requires community context
        Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");
        if (selectedCommunityId == null) {
            return "redirect:/mobile/organizations";
        }

        String selectedCommunityName = (String) request.getSession().getAttribute("selectedCommunityName");

        // Get community members (excluding current user) instead of all public users
        List<UserDTO> allUser = communityService.getCommunityMembersExcludingUser(selectedCommunityId, currentUserId);

        // Community-scoped network users
        List<UserDTO> networkUsers = networkService.getChatGroupUsers(currentUserId, selectedCommunityId);

        model.addAttribute("invitations", networkService.getChatGroupRequests(currentUserId, selectedCommunityId));
        model.addAttribute("myNetwork", networkUsers);
        model.addAttribute("networkUsers", networkUsers.size());
        model.addAttribute("allUser", allUser);
        model.addAttribute("selectedCommunityId", selectedCommunityId);
        model.addAttribute("selectedCommunityName", selectedCommunityName);
        model.addAttribute("currentPath", "/network");
        return "network/mobile-network";
    }

    @PostMapping("/invite")
    public ResponseEntity<String> handleConnectionRequest(@RequestParam("userId") String userId,
                                                          HttpServletRequest request) {
        Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");
        if (selectedCommunityId == null) {
            return ResponseEntity.badRequest().body("No community selected");
        }
        System.out.println(userId);
        networkService.requestToJoinChatGroup((String) authenticationManager.get("sub"), userId, selectedCommunityId);
        return ResponseEntity.ok("Connection request received for user: " + userId);
    }

    @PostMapping("/approve/{invitationId}")
    public String handleApproval(@PathVariable String invitationId,
                                 @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                                 HttpServletRequest request,
                                 Model model) {
        System.out.println("Approving invitation: " + invitationId);

        try {
            // Approve the request
            networkService.approveChatGroupRequest(Long.valueOf(invitationId));

            String currentUserId = (String) authenticationManager.get("sub");
            Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");

            List<UserDTO> networkUsers = networkService.getChatGroupUsers(currentUserId, selectedCommunityId);

            // Add updated data to model
            model.addAttribute("networkUsers", networkUsers.size());
            model.addAttribute("invitations", networkService.getChatGroupRequests(currentUserId, selectedCommunityId));
            model.addAttribute("myNetwork", networkUsers);

            // If it's an HTMX request, return the fragment that updates multiple containers
            if ("true".equals(hxRequest)) {
                return "network/mobile-network-fragments :: containers";
            } else {
                // For non-HTMX requests, redirect to the main page
                return "redirect:/mobile/network";
            }

        } catch (Exception e) {
            System.err.println("Error approving invitation: " + e.getMessage());
            e.printStackTrace();

            if ("true".equals(hxRequest)) {
                model.addAttribute("errorMessage", "Error approving invitation: " + e.getMessage());
                return "network/mobile-network-fragments :: error";
            } else {
                return "redirect:/mobile/network?error=approval_failed";
            }
        }
    }

    @PostMapping("/reject/{invitationId}")
    public String handleRejection(@PathVariable String invitationId,
                                  @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                                  HttpServletRequest request,
                                  Model model) {
        System.out.println("Rejecting invitation: " + invitationId);

        try {
            // Reject the request
            networkService.rejectChatGroupRequest(Long.valueOf(invitationId));

            String currentUserId = (String) authenticationManager.get("sub");
            Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");

            List<UserDTO> networkUsers = networkService.getChatGroupUsers(currentUserId, selectedCommunityId);

            // Add updated data to model
            model.addAttribute("networkUsers", networkUsers.size());
            model.addAttribute("invitations", networkService.getChatGroupRequests(currentUserId, selectedCommunityId));
            model.addAttribute("myNetwork", networkUsers);

            // If it's an HTMX request, return the fragment that updates multiple containers
            if ("true".equals(hxRequest)) {
                return "network/mobile-network-fragments :: containers";
            } else {
                // For non-HTMX requests, redirect to the main page
                return "redirect:/mobile/network";
            }

        } catch (Exception e) {
            System.err.println("Error rejecting invitation: " + e.getMessage());
            e.printStackTrace();

            if ("true".equals(hxRequest)) {
                model.addAttribute("errorMessage", "Error rejecting invitation: " + e.getMessage());
                return "network/mobile-network-fragments :: error";
            } else {
                return "redirect:/mobile/network?error=rejection_failed";
            }
        }
    }

    @GetMapping("/invitations-fragment")
    public String getInvitationsFragment(Model model, HttpServletRequest request) {
        String currentUserId = (String) authenticationManager.get("sub");
        Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");
        model.addAttribute("invitations", networkService.getChatGroupRequests(currentUserId, selectedCommunityId));
        return "network/mobile-network-fragments :: invitations-list";
    }

    @GetMapping("/network-fragment")
    public String getNetworkFragment(Model model, HttpServletRequest request) {
        String currentUserId = (String) authenticationManager.get("sub");
        Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");
        model.addAttribute("myNetwork", networkService.getChatGroupUsers(currentUserId, selectedCommunityId));
        return "network/mobile-network-fragments :: network-list";
    }

    @GetMapping("/containers-fragment")
    public String getContainersFragment(Model model, HttpServletRequest request) {
        String currentUserId = (String) authenticationManager.get("sub");
        Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");
        List<UserDTO> networkUsers = networkService.getChatGroupUsers(currentUserId, selectedCommunityId);

        model.addAttribute("networkUsers", networkUsers.size());
        model.addAttribute("invitations", networkService.getChatGroupRequests(currentUserId, selectedCommunityId));
        model.addAttribute("myNetwork", networkUsers);

        return "network/mobile-network-fragments :: containers";
    }
}
