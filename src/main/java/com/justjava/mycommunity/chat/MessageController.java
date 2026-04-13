package com.justjava.mycommunity.chat;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.service.ChatService;
import com.justjava.mycommunity.network.NetworkService;
import com.justjava.mycommunity.userManagement.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/messages")
public class MessageController {
    private final ChatService chatService;
    private final AuthenticationManager authenticationManager;
    private final NetworkService networkService;

    public MessageController(ChatService chatService, AuthenticationManager authenticationManager, NetworkService networkService) {
        this.chatService = chatService;
        this.authenticationManager = authenticationManager;
        this.networkService = networkService;
    }

    @GetMapping
    public String allMessages(Model model, HttpServletRequest request){
        String currentUserId = (String) authenticationManager.get("sub");

        // Messages require community context
        Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");
        if (selectedCommunityId == null) {
            request.getSession().setAttribute("redirectAfterSelect", "/messages");
            return "redirect:/organizations";
        }

        // Get only network users (connected users) for this community
        List<UserDTO> users = networkService.getChatGroupUsers(currentUserId, selectedCommunityId);

        model.addAttribute("currentUser", currentUserId);
        model.addAttribute("currentUserName", authenticationManager.get("name"));
        model.addAttribute("users", users);
        return "messages/chat";
    }
}
