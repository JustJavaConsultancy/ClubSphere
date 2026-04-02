package com.justjava.mycommunity.chat;

import com.justjava.mycommunity.chat.dto.CommentDTO;
import com.justjava.mycommunity.chat.dto.CreatSessionVO;
import com.justjava.mycommunity.chat.dto.SessionDTO;
import com.justjava.mycommunity.chat.model.PostMessage;
import com.justjava.mycommunity.event.EventService;
import com.justjava.mycommunity.posts.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.service.ChatService;
import com.justjava.mycommunity.userManagement.UserDTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;
    private final AuthenticationManager authenticationManager;
    private final PostService postService;
    private final EventService eventService;

    @GetMapping("/chat")
    public String chatPage(Model model)
    {
        List<UserDTO> users = chatService.getUsers();
        System.out.println(users);
        System.out.println(authenticationManager.get("name"));
        model.addAttribute("currentUser",authenticationManager.get("sub"));
        model.addAttribute("currentUserName",authenticationManager.get("name"));
        model.addAttribute("users",users);
        return "messages/chat";
    }
    
    @GetMapping("/videocall")
    public String videoCallPage(Model model) {
        return "videocall";
    }
    
    // Video call endpoint
    @GetMapping("/api/chat/video-call/user-info")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getVideoCallUserInfo(Authentication authentication) {
        try {
            String currentUserId = getCurrentUserId(authentication);
            String currentUserName = getCurrentUserName(authentication);
            
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("userId", currentUserId);
            userInfo.put("userName", currentUserName);
            userInfo.put("status", "success");
            
            return ResponseEntity.ok(userInfo);
        } catch (Exception e) {
            System.err.println("Error getting video call user info: " + e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Failed to get user info");
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage message) {
        System.out.println(" I received ===="+message);
        if (Boolean.TRUE.equals(message.getTownHall())) {
            chatService.sendTownHallMessage(message);
        }

        if (Boolean.TRUE.equals(message.getChannel())) {
            chatService.sendChannelMessage(message);
        }

        String userId = (String) authenticationManager.get("sub");
        String destination = "/topic/group/" + message.getReceiverId();
        String notification= "/topic/notification/" + message.getSenderId();
        //message.setSenderId("449a5325-da3e-4692-93ea-ce8da8346e2f");
        chatService.newMessage(message);
        messagingTemplate.convertAndSend(destination, message);
        messagingTemplate.convertAndSend(notification, message);
    }


    @MessageMapping("/support.sendMessage")
    public void sendSupportMessage(@Payload ChatMessage message) {

        chatService.sendSupportMessage(message);
        messagingTemplate.convertAndSend("/topic/support/" + message.getReceiverId(), message);
    }

    @MessageMapping("/post.sendMessage")
    public void sendPost(@Payload PostMessage post) {

        System.out.println(" I received ===="+post.toString());
        postService.createPost(post);
        String destination;
        if (post.isPrivacy()){
            destination = "/topic/posts/private";
        }else {
            destination = "/topic/posts";
        }
        messagingTemplate.convertAndSend(destination, post);
    }

    @MessageMapping("/event.sendMessage")
    public void sendEvent(@Payload CreatSessionVO vo){

        System.out.println(" I received ===="+vo.toString());
        SessionDTO dto = eventService.createSession(vo);
        String destination = "/topic/events";
        messagingTemplate.convertAndSend(destination, dto);
    }

    @MessageMapping("/comment.sendMessage")
    public void sendComment(@Payload CommentDTO dto){

        System.out.println(" I received ===="+dto.toString());
        dto = postService.createComment(dto);
        String destination = "/topic/post/" + dto.getPostId();
        messagingTemplate.convertAndSend(destination, dto);
    }

    private String getCurrentUserName(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser) {
            OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
            String firstName = oidcUser.getClaimAsString("given_name");
            String lastName = oidcUser.getClaimAsString("family_name");

            if (firstName != null && lastName != null) {
                return firstName + " " + lastName;
            } else if (firstName != null) {
                return firstName;
            } else {
                return oidcUser.getClaimAsString("preferred_username");
            }
        }
        return "User"; // Fallback for testing
    }
    private String getCurrentUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser) {
            OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
            return oidcUser.getSubject(); // This is the user ID from Keycloak
        }
        return "anonymous"; // Fallback for testing
    }
}