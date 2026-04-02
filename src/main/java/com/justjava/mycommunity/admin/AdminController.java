package com.justjava.mycommunity.admin;

import com.justjava.mycommunity.chat.dto.SessionDTO;
import com.justjava.mycommunity.event.EventService;
import com.justjava.mycommunity.keycloak.KeycloakService;
import com.justjava.mycommunity.userManagement.UserDTO;
import com.justjava.mycommunity.userManagement.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {
    private final UserService userService;
    private final KeycloakService keycloakService;
    private final EventService eventService;

    public AdminController(UserService userService, KeycloakService keycloakService, EventService eventService) {
        this.userService = userService;
        this.keycloakService = keycloakService;
        this.eventService = eventService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model){
        List<UserDTO> users =  userService.getUsers();
        List <SessionDTO> completedSessions = eventService.getCompletedSessions();
        List <SessionDTO> upcomingSessions = eventService.getUpcomingSessions();
        List <SessionDTO> unapprovedSession = eventService.getUnApprovedSessions();
        System.out.println(upcomingSessions);
        System.out.println(completedSessions);
        System.out.println(unapprovedSession);
//        System.out.println(users);
        model.addAttribute("userCount",users.size());
        model.addAttribute("upcoming",upcomingSessions);
        model.addAttribute("completed",completedSessions);
        model.addAttribute("unapproved",unapprovedSession);
        model.addAttribute("unapprovedSize",unapprovedSession.size());
        model.addAttribute("upcomingSession",upcomingSessions.size());
        model.addAttribute("completedSession",completedSessions.size());
        model.addAttribute("users",users);
        return "admin/dashboard";
    }
    @GetMapping("/deleteUser/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId){
        keycloakService.deleteUser(userId);
        HttpHeaders headers = new HttpHeaders();
        headers.add("HX-Redirect", "/admin/dashboard");
        return ResponseEntity.status(HttpStatus.OK).headers(headers).build();
    }
}
