package com.justjava.mycommunity.mobile;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.dto.SessionDTO;
import com.justjava.mycommunity.community.dto.ApprovalTaskDTO;
import com.justjava.mycommunity.community.services.CommunityApprovalService;
import com.justjava.mycommunity.event.EventService;
import com.justjava.mycommunity.keycloak.KeycloakService;
import com.justjava.mycommunity.userManagement.UserDTO;
import com.justjava.mycommunity.userManagement.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/mobile/admin")
public class MobileAdminController {
    private final UserService userService;
    private final KeycloakService keycloakService;
    private final EventService eventService;
    private final AuthenticationManager authenticationManager;
    private final CommunityApprovalService approvalService;

    public MobileAdminController(UserService userService, KeycloakService keycloakService,
                                 EventService eventService, AuthenticationManager authenticationManager,
                                 CommunityApprovalService approvalService) {
        this.userService = userService;
        this.keycloakService = keycloakService;
        this.eventService = eventService;
        this.authenticationManager = authenticationManager;
        this.approvalService = approvalService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model){
        List<UserDTO> users = userService.getUsers();
        List<SessionDTO> completedSessions = eventService.getCompletedSessions();
        List<SessionDTO> upcomingSessions = eventService.getUpcomingSessions();
        List<SessionDTO> unapprovedSession = eventService.getUnApprovedSessions();
        model.addAttribute("userCount", users.size());
        model.addAttribute("upcoming", upcomingSessions);
        model.addAttribute("completed", completedSessions);
        model.addAttribute("unapproved", unapprovedSession);
        model.addAttribute("unapprovedSize", unapprovedSession.size());
        model.addAttribute("upcomingSession", upcomingSessions.size());
        model.addAttribute("completedSession", completedSessions.size());
        model.addAttribute("users", users);
        return "admin/mobile-dashboard";
    }

    @GetMapping("/deleteUser/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId){
        keycloakService.deleteUser(userId);
        HttpHeaders headers = new HttpHeaders();
        headers.add("HX-Redirect", "/mobile/admin/dashboard");
        return ResponseEntity.status(HttpStatus.OK).headers(headers).build();
    }

    @GetMapping("/approvals")
    public String getPendingApprovals(Model model, HttpSession session) {
        String adminUserId = (String) session.getAttribute("userId");
        if (adminUserId == null) adminUserId = (String) authenticationManager.get("sub");
        if (adminUserId == null) return "redirect:/login";

        List<ApprovalTaskDTO> pendingTasks = approvalService.getPendingTasks(adminUserId);
        model.addAttribute("pendingTasks", pendingTasks);
        model.addAttribute("adminUserId", adminUserId);
        model.addAttribute("pendingCount", pendingTasks.size());
        return "mobile-approvals";
    }

    @PostMapping("/approvals/{taskId}")
    public String completeApprovalTask(@PathVariable String taskId,
                                       @RequestParam("decision") String decision,
                                       @RequestParam(value = "adminNote", required = false, defaultValue = "") String adminNote,
                                       RedirectAttributes redirectAttributes) {
        try {
            boolean approved = "approve".equalsIgnoreCase(decision);
            approvalService.completeTask(taskId, approved);
            redirectAttributes.addFlashAttribute("successMessage",
                    approved ? "Membership request approved." : "Membership request rejected.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Could not process task: " + ex.getMessage());
        }
        return "redirect:/mobile/admin/approvals";
    }
}

