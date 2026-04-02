package com.justjava.mycommunity.mobile;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.service.ChatService;
import com.justjava.mycommunity.userManagement.UserDTO;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/mobile/messages")
public class MobileMessageController {
    private final ChatService chatService;
    private final AuthenticationManager authenticationManager;

    public MobileMessageController(ChatService chatService, AuthenticationManager authenticationManager) {
        this.chatService = chatService;
        this.authenticationManager = authenticationManager;
    }

    @GetMapping
    public String allMessages(Model model){
        List<UserDTO> users = chatService.getUsers();
        model.addAttribute("currentUser",authenticationManager.get("sub"));
        model.addAttribute("currentUserName",authenticationManager.get("name"));
        model.addAttribute("users",users);
        model.addAttribute("currentPath", "/messages");
        return "messages/mobile-chat";
    }
}

