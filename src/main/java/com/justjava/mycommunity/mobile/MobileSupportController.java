package com.justjava.mycommunity.mobile;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.cloudinary.CloudinaryService;
import com.justjava.mycommunity.support.AISupportService;
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
import org.springframework.web.multipart.MultipartFile;

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
    private final CloudinaryService cloudinaryService;
    private final AISupportService aiSupportService;

    private boolean canManage() {
        return authenticationManager.isSupportAdmin()
                || authenticationManager.isAdmin()
                || authenticationManager.isCommunityAdmin()
                || authenticationManager.isGroupAdmin();
    }

    @GetMapping("/my-tickets")
    public String getTickets(Model model) {
        boolean manage = canManage();
        if (!manage) {
            return "redirect:/mobile/support/my-submitted-tickets";
        }
        String userId = (String) authenticationManager.get("sub");
        model.addAttribute("isSupportAdmin", authenticationManager.isSupportAdmin());
        model.addAttribute("canManageTickets", true);
        model.addAttribute("tickets", supportService.getScopedAgentTickets(userId));
        model.addAttribute("currentUserId", userId);
        model.addAttribute("pageType", "claimed");
        model.addAttribute("pageTitle", "Claimed Tickets");
        model.addAttribute("pageSubtitle", "Tickets you are handling from other users");
        return "support/mobile-tickets";
    }

    @GetMapping("/my-submitted-tickets")
    public String getMySubmittedTickets(Model model) {
        boolean manage = canManage();
        model.addAttribute("isSupportAdmin", authenticationManager.isSupportAdmin());
        model.addAttribute("canManageTickets", manage);
        model.addAttribute("tickets", supportService.getMySubmittedTickets());
        model.addAttribute("currentUserId", authenticationManager.get("sub"));
        model.addAttribute("pageType", "submitted");
        model.addAttribute("pageTitle", "My Submitted Tickets");
        model.addAttribute("pageSubtitle", "Support requests you have raised");
        return "support/mobile-tickets";
    }

    @GetMapping("/single-ticket/{id}")
    public String getSingleTicket(@PathVariable Long id, Model model) {
        String loginUser = (String) authenticationManager.get("sub");
        TicketDTO singleTicket = supportService.getSingleTicket(id, loginUser);
        Ticket rawTicket = supportService.getTicketById(id);
        boolean canManage = authenticationManager.isSupportAdmin()
                || authenticationManager.isAdmin()
                || (rawTicket != null && supportService.canManageTicket(loginUser, rawTicket));

        model.addAttribute("ticketId", id);
        model.addAttribute("senderId", loginUser);
        model.addAttribute("ticket", singleTicket);
        model.addAttribute("isSupportAdmin", authenticationManager.isSupportAdmin());
        model.addAttribute("canManage", canManage);
        model.addAttribute("canManageTickets", canManage());
        model.addAttribute("conversationId", singleTicket.getConversation().getId());
        model.addAttribute("receiverId", singleTicket.getConversation().getReceiverId());
        model.addAttribute("messages", singleTicket.getConversation().getMessages());
        model.addAttribute("isClosed", "Closed".equalsIgnoreCase(singleTicket.getStatus()));
        model.addAttribute("isMobile", true);
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
        // Resolve community / group name for the modal
        TicketDTO dto = supportService.mapTicketToDTO(ticket);
        model.addAttribute("communityName", dto.getCommunityName());
        model.addAttribute("groupName", dto.getGroupName());
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

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        String userId = (String) authenticationManager.get("sub");
        boolean manage = canManage();
        List<Ticket> unAssigned = manage ? supportService.getScopedUnclaimedTickets(userId) : Collections.emptyList();
        model.addAttribute("unClaimedTicketSize", unAssigned.size());
        model.addAttribute("myTicketSize", supportService.getMySubmittedTickets().size());
        model.addAttribute("unAssignedTickets", unAssigned);
        model.addAttribute("canManageTickets", manage);
        model.addAttribute("isSupportAdmin", authenticationManager.isSupportAdmin());
        return "support/mobile-support-dashboard";
    }

    @PostMapping("/submit-request")
    @ResponseBody
    public ResponseEntity<String> submitRequest(@RequestParam Map<String, Object> formData) {
        try {
            supportService.createTicket(formData);
            String html = "<div style='display:flex;flex-direction:column;align-items:center;padding:24px 16px;text-align:center;'>" +
                "<div style='width:56px;height:56px;background:#dcfce7;border-radius:50%;display:flex;align-items:center;justify-content:center;margin-bottom:14px;'>" +
                "<svg style='width:28px;height:28px;color:#16a34a;' fill='none' stroke='currentColor' viewBox='0 0 24 24'><path stroke-linecap='round' stroke-linejoin='round' stroke-width='3' d='M5 13l4 4L19 7'/></svg>" +
                "</div>" +
                "<h3 style='font-size:1rem;font-weight:700;color:#1f2937;margin:0 0 6px 0;'>Request Submitted!</h3>" +
                "<p style='font-size:13px;color:#6b7280;margin:0 0 16px 0;'>Your ticket has been created. Our team will get back to you soon.</p>" +
                "<a href='/mobile/support/my-submitted-tickets' style='display:inline-block;padding:10px 22px;background:linear-gradient(135deg,#4f46e5,#6366f1);color:white;border-radius:12px;font-weight:600;font-size:14px;text-decoration:none;'>View My Tickets</a>" +
                "</div>";
            return ResponseEntity.ok(html);
        } catch (IllegalArgumentException | SecurityException e) {
            String html = "<div style='display:flex;flex-direction:column;align-items:center;padding:24px 16px;text-align:center;'>" +
                "<div style='width:56px;height:56px;background:#fee2e2;border-radius:50%;display:flex;align-items:center;justify-content:center;margin-bottom:14px;'>" +
                "<svg style='width:28px;height:28px;color:#ef4444;' fill='none' stroke='currentColor' viewBox='0 0 24 24'><path stroke-linecap='round' stroke-linejoin='round' stroke-width='2' d='M6 18L18 6M6 6l12 12'/></svg>" +
                "</div>" +
                "<h3 style='font-size:1rem;font-weight:700;color:#dc2626;margin:0 0 6px 0;'>Could Not Submit</h3>" +
                "<p style='font-size:13px;color:#6b7280;margin:0;'>" + e.getMessage() + "</p>" +
                "</div>";
            return ResponseEntity.ok(html);
        } catch (Exception e) {
            String html = "<div style='padding:16px;text-align:center;color:#dc2626;font-size:14px;'>❌ Something went wrong. Please try again.</div>";
            return ResponseEntity.ok(html);
        }
    }

    @PostMapping("/upload-attachment")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadAttachment(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("message", "File is empty");
                return ResponseEntity.badRequest().body(response);
            }
            String url = cloudinaryService.uploadFile(file, "support/attachments");
            if (url == null) {
                response.put("success", false);
                response.put("message", "Failed to upload file");
                return ResponseEntity.status(500).body(response);
            }
            response.put("success", true);
            response.put("url", url);
            response.put("fileName", file.getOriginalFilename());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Upload failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/chat")
    public String aiChat(@RequestParam Map<String, Object> formData, Model model) {
        String resp = aiSupportService.supportChat(
                formData.get("message").toString(), (String) authenticationManager.get("sub"));
        model.addAttribute("response", resp);
        return "support/AI-successMessage";
    }
}

