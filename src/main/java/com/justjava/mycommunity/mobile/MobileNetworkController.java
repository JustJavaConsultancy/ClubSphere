package com.justjava.mycommunity.mobile;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.service.ChatService;
import com.justjava.mycommunity.userManagement.UserDTO;
import com.justjava.mycommunity.network.NetworkService;
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

    public MobileNetworkController(ChatService chatService, AuthenticationManager authenticationManager, NetworkService networkService) {
        this.chatService = chatService;
        this.authenticationManager = authenticationManager;
        this.networkService = networkService;
    }

    @GetMapping
    public String getNetwork(Model model){
        String currentUserId = (String) authenticationManager.get("sub");
        System.out.println(networkService.getChatGroupRequests(currentUserId));
        List<UserDTO> allUser = chatService.getPublicUsers();

        if (authenticationManager.isAdmin()){
            allUser = chatService.getUsers();
        }

        System.out.println("This is the current chat group users:::" + networkService.getChatGroupUsers(currentUserId));

        List <UserDTO> networkUsers = networkService.getChatGroupUsers(currentUserId);
        model.addAttribute("invitations", networkService.getChatGroupRequests(currentUserId));
        model.addAttribute("myNetwork", networkService.getChatGroupUsers(currentUserId));
        model.addAttribute("networkUsers",networkUsers.size());
        model.addAttribute("allUser", allUser);
        model.addAttribute("currentPath", "/network");
        return "network/mobile-network";
    }

    @PostMapping("/invite")
    public ResponseEntity<String> handleConnectionRequest(@RequestParam("userId") String userId) {
        System.out.println(userId);
        networkService.requestToJoinChatGroup((String) authenticationManager.get("sub"),userId);
        return ResponseEntity.ok("Connection request received for user: " + userId);
    }

    @PostMapping("/approve/{invitationId}")
    public String handleApproval(@PathVariable String invitationId,
                                 @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                                 Model model) {
        System.out.println("Approving invitation: " + invitationId);

        try {
            // Approve the request
            networkService.approveChatGroupRequest(Long.valueOf(invitationId));

            String currentUserId = (String) authenticationManager.get("sub");
            List<UserDTO> networkUsers = networkService.getChatGroupUsers(currentUserId);

            // Add updated data to model
            model.addAttribute("networkUsers", networkUsers.size());
            model.addAttribute("invitations", networkService.getChatGroupRequests(currentUserId));
            model.addAttribute("myNetwork", networkService.getChatGroupUsers(currentUserId));

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
                                  Model model) {
        System.out.println("Rejecting invitation: " + invitationId);

        try {
            // Reject the request
            networkService.rejectChatGroupRequest(Long.valueOf(invitationId));

            String currentUserId = (String) authenticationManager.get("sub");
            List<UserDTO> networkUsers = networkService.getChatGroupUsers(currentUserId);

            // Add updated data to model
            model.addAttribute("networkUsers", networkUsers.size());
            model.addAttribute("invitations", networkService.getChatGroupRequests(currentUserId));
            model.addAttribute("myNetwork", networkService.getChatGroupUsers(currentUserId));

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
    public String getInvitationsFragment(Model model) {
        String currentUserId = (String) authenticationManager.get("sub");
        model.addAttribute("invitations", networkService.getChatGroupRequests(currentUserId));
        return "network/mobile-network-fragments :: invitations-list";
    }

    @GetMapping("/network-fragment")
    public String getNetworkFragment(Model model) {
        String currentUserId = (String) authenticationManager.get("sub");
        model.addAttribute("myNetwork", networkService.getChatGroupUsers(currentUserId));
        return "network/mobile-network-fragments :: network-list";
    }

    @GetMapping("/containers-fragment")
    public String getContainersFragment(Model model) {
        String currentUserId = (String) authenticationManager.get("sub");
        List<UserDTO> networkUsers = networkService.getChatGroupUsers(currentUserId);

        model.addAttribute("networkUsers", networkUsers.size());
        model.addAttribute("invitations", networkService.getChatGroupRequests(currentUserId));
        model.addAttribute("myNetwork", networkService.getChatGroupUsers(currentUserId));

        return "network/mobile-network-fragments :: containers";
    }
}
