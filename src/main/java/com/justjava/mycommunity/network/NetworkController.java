package com.justjava.mycommunity.network;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.service.ChatService;
import com.justjava.mycommunity.community.CommunityService;
import com.justjava.mycommunity.userManagement.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/network")
public class NetworkController {
    private final ChatService chatService;
    private final AuthenticationManager authenticationManager;
    private final NetworkService networkService;
    private final CommunityService communityService;

    public NetworkController(ChatService chatService, AuthenticationManager authenticationManager,
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
            // Store intended destination so user gets redirected back after selecting a community
            request.getSession().setAttribute("redirectAfterSelect", "/network");
            return "redirect:/organizations";
        }

        String selectedCommunityName = (String) request.getSession().getAttribute("selectedCommunityName");

        // Get community members (excluding current user) instead of all public users
        List<UserDTO> allUser = communityService.getCommunityMembersExcludingUser(selectedCommunityId, currentUserId);

        // Community-scoped network users
        List<UserDTO> networkUsers = networkService.getChatGroupUsers(currentUserId, selectedCommunityId);

        // Filter out users that are already in the network
        List<UserDTO> filteredUsers = allUser.stream()
                .filter(user -> !isUserInNetwork(user, networkUsers))
                .collect(Collectors.toList());

        model.addAttribute("invitations", networkService.getChatGroupRequests(currentUserId, selectedCommunityId));
        model.addAttribute("myNetwork", networkUsers);
        model.addAttribute("networkUsers", networkUsers.size());
        model.addAttribute("allUser", filteredUsers);
        model.addAttribute("selectedCommunityId", selectedCommunityId);
        model.addAttribute("selectedCommunityName", selectedCommunityName);
        return "/network/mainPage";
    }

    // Helper method to check if a user is in the network
    private boolean isUserInNetwork(UserDTO user, List<UserDTO> networkUsers) {
        return networkUsers.stream()
                .anyMatch(networkUser -> networkUser.getUserId().equals(user.getUserId()));
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
    public String handleApproval(@PathVariable String invitationId, Model model, HttpServletRequest request) {
        System.out.println(invitationId);
        networkService.approveChatGroupRequest(Long.valueOf(invitationId));
        String currentUserId = (String) authenticationManager.get("sub");
        Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");

        List<UserDTO> networkUsers = networkService.getChatGroupUsers(currentUserId, selectedCommunityId);
        model.addAttribute("networkUsers", networkUsers.size());
        model.addAttribute("invitations", networkService.getChatGroupRequests(currentUserId, selectedCommunityId));
        model.addAttribute("myNetwork", networkUsers);

        // Return both fragments in one HTML response
        return "network/invitations-and-network :: containers";
    }


    @GetMapping("/invitations-fragment")
    public String getInvitationsFragment(Model model, HttpServletRequest request) {
        String currentUserId = (String) authenticationManager.get("sub");
        Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");
        model.addAttribute("invitations", networkService.getChatGroupRequests(currentUserId, selectedCommunityId));
        return "network/fragments :: invitations-list";
    }

    @GetMapping("/network-fragment")
    public String getNetworkFragment(Model model, HttpServletRequest request) {
        String currentUserId = (String) authenticationManager.get("sub");
        Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");
        model.addAttribute("myNetwork", networkService.getChatGroupUsers(currentUserId, selectedCommunityId));
        return "network/fragments :: network-list";
    }
}