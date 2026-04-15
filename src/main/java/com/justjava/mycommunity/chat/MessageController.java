package com.justjava.mycommunity.chat;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.service.ChatService;
import com.justjava.mycommunity.network.NetworkConnectionDTO;
import com.justjava.mycommunity.network.NetworkNewService;
import com.justjava.mycommunity.network.NetworkService;
import com.justjava.mycommunity.userManagement.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/messages")
public class MessageController {
    private final ChatService chatService;
    private final AuthenticationManager authenticationManager;
    private final NetworkService networkService;
    private final NetworkNewService networkNewService;

    public MessageController(ChatService chatService, AuthenticationManager authenticationManager,
                             NetworkService networkService, NetworkNewService networkNewService) {
        this.chatService = chatService;
        this.authenticationManager = authenticationManager;
        this.networkService = networkService;
        this.networkNewService = networkNewService;
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

        // Get accepted network connections — these are the people the user can message.
        // Each connection carries the network name and community name so the UI can display it.
        List<NetworkConnectionDTO> connections = networkNewService.getUserConnections(currentUserId);

        // Build a user list from connections (for backward compatibility with the chat template)
        List<UserDTO> users = connections.stream()
                .map(conn -> UserDTO.builder()
                        .userId(conn.getConnectedUserId())
                        .firstName(conn.getConnectedUserName().split(" ")[0])
                        .lastName(conn.getConnectedUserName().contains(" ")
                                ? conn.getConnectedUserName().substring(conn.getConnectedUserName().indexOf(" ") + 1)
                                : "")
                        .build())
                .toList();

        // Build a map of userId → networkName for the template to display
        Map<String, String> userNetworkMap = new HashMap<>();
        // Build a map of userId → communityName for the template to display
        Map<String, String> userCommunityMap = new HashMap<>();
        for (NetworkConnectionDTO conn : connections) {
            userNetworkMap.put(conn.getConnectedUserId(), conn.getNetworkName());
            userCommunityMap.put(conn.getConnectedUserId(), conn.getCommunityName());
        }

        // Build a JSON-safe map for JS: userId → {networkName, communityName}
        // Serialized manually to avoid Thymeleaf inline-JS parsing issues with curly braces
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (NetworkConnectionDTO conn : connections) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(conn.getConnectedUserId())).append("\":")
              .append("{\"networkName\":\"").append(escapeJson(conn.getNetworkName())).append("\",")
              .append("\"communityName\":\"").append(escapeJson(conn.getCommunityName())).append("\"}");
        }
        sb.append("}");

        model.addAttribute("currentUser", currentUserId);
        model.addAttribute("currentUserName", authenticationManager.get("name"));
        model.addAttribute("users", users);
        model.addAttribute("connections", connections);
        model.addAttribute("userNetworkMap", userNetworkMap);
        model.addAttribute("userCommunityMap", userCommunityMap);
        model.addAttribute("connectionInfoJson", sb.toString());
        return "messages/chat";
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "\\r");
    }
}
