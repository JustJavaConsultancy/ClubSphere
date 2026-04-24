package com.justjava.mycommunity.mobile;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.support.SupportService;
import com.justjava.mycommunity.support.Ticket;
import com.justjava.mycommunity.support.TicketDTO;
import com.justjava.mycommunity.userManagement.UserDTO;
import com.justjava.mycommunity.userManagement.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/mobile/support")
@RequiredArgsConstructor
public class MobileSupportController {

    private final SupportService supportService;
    private final AuthenticationManager authenticationManager;
    private final UserService userService;

    private boolean canManage() {
        return authenticationManager.isSupportAdmin()
                || authenticationManager.isAdmin()
                || authenticationManager.isCommunityAdmin();
    }

    @GetMapping("/my-tickets")
    public String getTickets(Model model) {
        boolean manage = canManage();
        model.addAttribute("isSupportAdmin", authenticationManager.isSupportAdmin());
        model.addAttribute("canManageTickets", manage);
        model.addAttribute("tickets", supportService.getTickets());
        model.addAttribute("currentUserId", authenticationManager.get("sub"));
        return "support/mobile-tickets";
    }

    @GetMapping("/single-ticket/{id}")
    public String getSingleTicket(@PathVariable Long id, Model model) {
        String loginUser = (String) authenticationManager.get("sub");
        TicketDTO singleTicket = supportService.getSingleTicket(id, loginUser);

        model.addAttribute("ticketId", id);
        model.addAttribute("senderId", loginUser);
        model.addAttribute("ticket", singleTicket);
        model.addAttribute("isSupportAdmin", authenticationManager.isSupportAdmin());
        model.addAttribute("canManageTickets", canManage());
        model.addAttribute("conversationId", singleTicket.getConversation().getId());
        model.addAttribute("receiverId", singleTicket.getConversation().getReceiverId());
        model.addAttribute("messages", singleTicket.getConversation().getMessages());
        model.addAttribute("isClosed", "Closed".equalsIgnoreCase(singleTicket.getStatus()));
        return "support/ticketDetail :: ticketDetail";
    }

    // ── Agent / Admin endpoints ──────────────────────────────────────────────

    @GetMapping("/agent/claim-ticket")
    public String claimTickets(Model model) {
        String userId = (String) authenticationManager.get("sub");
        boolean manage = canManage();
        List<Ticket> unAssigned = manage
                ? supportService.getScopedUnclaimedTickets(userId)
                : Collections.emptyList();
        model.addAttribute("unAssignedTickets", unAssigned);
        model.addAttribute("canManageTickets", manage);
        return "support/mobile-agent-tickets";
    }

    @GetMapping("/agent/claim-ticket/{id}")
    public String getSingleClaimTicket(@PathVariable Long id, Model model) {
        Ticket ticket = supportService.getTicketById(id);
        UserDTO ticketOwner = userService.getSingleUserByUserId(ticket.getUserId());
        model.addAttribute("username", ticketOwner.getFirstName() + " " + ticketOwner.getLastName());
        model.addAttribute("ticket", ticket);
        model.addAttribute("isMobile", true);
        return "support/claimTicketModal :: ticketDetails";
    }

    @PostMapping("/submit-claim/{id}")
    public ResponseEntity<Void> submitClaim(@PathVariable Long id) {
        String loginUser = (String) authenticationManager.get("sub");
        Ticket ticket = supportService.getTicketById(id);
        boolean canClaim = authenticationManager.isSupportAdmin()
                || authenticationManager.isAdmin()
                || (ticket != null && supportService.canManageTicket(loginUser, ticket));
        if (canClaim) supportService.claimTicket(id, loginUser);
        HttpHeaders headers = new HttpHeaders();
        headers.add("HX-Redirect", "/mobile/support/agent/claim-ticket");
        return ResponseEntity.status(HttpStatus.OK).headers(headers).build();
    }

    @PostMapping("/close-ticket/{id}")
    public ResponseEntity<Void> closeTicket(@PathVariable Long id) {
        String loginUser = (String) authenticationManager.get("sub");
        Ticket ticket = supportService.getTicketById(id);
        boolean canClose = authenticationManager.isSupportAdmin()
                || authenticationManager.isAdmin()
                || (ticket != null && supportService.canManageTicket(loginUser, ticket));
        if (canClose) supportService.closeTicket(id);
        HttpHeaders headers = new HttpHeaders();
        headers.add("HX-Redirect", "/mobile/support/my-tickets");
        return ResponseEntity.status(HttpStatus.OK).headers(headers).build();
    }

    @PostMapping("/create-meeting/{ticketId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createMeeting(@PathVariable Long ticketId) {
        Map<String, Object> response = new HashMap<>();
        try {
            String agentUserId = (String) authenticationManager.get("sub");
            Ticket ticket = supportService.getTicketById(ticketId);
            boolean manage = authenticationManager.isSupportAdmin()
                    || authenticationManager.isAdmin()
                    || (ticket != null && supportService.canManageTicket(agentUserId, ticket));
            if (!manage) {
                response.put("success", false);
                response.put("message", "Not authorized");
                return ResponseEntity.status(403).body(response);
            }
            if (ticket == null) {
                response.put("success", false); response.put("message", "Ticket not found");
                return ResponseEntity.status(404).body(response);
            }
            if ("Closed".equalsIgnoreCase(ticket.getStatus())) {
                response.put("success", false); response.put("message", "Ticket is closed");
                return ResponseEntity.badRequest().body(response);
            }
            String agentName = (String) authenticationManager.get("name");
            String roomId = "support_ticket_" + ticketId + "_" + System.currentTimeMillis();
            String meetingUrl = "/videocall?roomID=" + roomId
                    + "&userName=" + java.net.URLEncoder.encode(agentName != null ? agentName : "Support Agent", "UTF-8")
                    + "&userID=" + java.net.URLEncoder.encode(agentUserId, "UTF-8")
                    + "&meetingName=" + java.net.URLEncoder.encode("Support: " + ticket.getSubject(), "UTF-8");
            String userMeetingLink = "/videocall?roomID=" + roomId
                    + "&meetingName=" + java.net.URLEncoder.encode("Support: " + ticket.getSubject(), "UTF-8");
            if (ticket.getConversation() != null) {
                String msg = "📹 A live meeting session has been started. Click <a href=\""
                        + userMeetingLink + "\" target=\"_blank\" style=\"color:#2563eb;font-weight:600;text-decoration:underline;\">here</a> to join.";
                supportService.sendSystemMessage(ticket.getConversation().getId(), agentUserId, msg);
            }
            response.put("success", true);
            response.put("meetingUrl", meetingUrl);
            response.put("roomId", roomId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
