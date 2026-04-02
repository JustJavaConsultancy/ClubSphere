package com.justjava.mycommunity.network;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.service.ChatService;
import com.justjava.mycommunity.userManagement.UserDTO;
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

    public NetworkController(ChatService chatService, AuthenticationManager authenticationManager, NetworkService networkService) {
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

        System.out.println(networkService.getChatGroupUsers(currentUserId));
        System.out.println("This is the current chat group users:::" + networkService.getChatGroupUsers(currentUserId));

        List<UserDTO> networkUsers = networkService.getChatGroupUsers(currentUserId);

        // Filter out users that are already in the network
        List<UserDTO> filteredUsers = allUser.stream()
                .filter(user -> !isUserInNetwork(user, networkUsers))
                .collect(Collectors.toList());

        model.addAttribute("invitations", networkService.getChatGroupRequests(currentUserId));
        model.addAttribute("myNetwork", networkUsers);
        model.addAttribute("networkUsers", networkUsers.size());
        model.addAttribute("allUser", filteredUsers); // Use filtered list instead of allUser
        return "/network/mainPage";
    }

    // Helper method to check if a user is in the network
    private boolean isUserInNetwork(UserDTO user, List<UserDTO> networkUsers) {
        return networkUsers.stream()
                .anyMatch(networkUser -> networkUser.getUserId().equals(user.getUserId()));
    }

    @PostMapping("/invite")
    public ResponseEntity<String> handleConnectionRequest(@RequestParam("userId") String userId) {
        System.out.println(userId);
        networkService.requestToJoinChatGroup((String) authenticationManager.get("sub"),userId);
        return ResponseEntity.ok("Connection request received for user: " + userId);
    }

    @PostMapping("/approve/{invitationId}")
    public String handleApproval(@PathVariable String invitationId, Model model) {
        System.out.println(invitationId);
        networkService.approveChatGroupRequest(Long.valueOf(invitationId));
        String currentUserId = (String) authenticationManager.get("sub");
        List <UserDTO> networkUsers = networkService.getChatGroupUsers(currentUserId);
        model.addAttribute("networkUsers",networkUsers.size());
        model.addAttribute("invitations", networkService.getChatGroupRequests(currentUserId));
        model.addAttribute("myNetwork", networkService.getChatGroupUsers(currentUserId));

        // Return both fragments in one HTML response
        return "network/invitations-and-network :: containers";
    }


    @GetMapping("/invitations-fragment")
    public String getInvitationsFragment(Model model) {
        String currentUserId = (String) authenticationManager.get("sub");
        model.addAttribute("invitations", networkService.getChatGroupRequests(currentUserId));
        return "network/fragments :: invitations-list";
    }

    @GetMapping("/network-fragment")
    public String getNetworkFragment(Model model) {
        String currentUserId = (String) authenticationManager.get("sub");
        model.addAttribute("myNetwork", networkService.getChatGroupUsers(currentUserId));
        return "network/fragments :: network-list";
    }
}