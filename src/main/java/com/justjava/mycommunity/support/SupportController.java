package com.justjava.mycommunity.support;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.cloudinary.CloudinaryService;
import com.justjava.mycommunity.userManagement.UserDTO;
import com.justjava.mycommunity.userManagement.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/support")
public class SupportController {
    private final SupportService supportService;
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final AISupportService aISupportService;
    private final CloudinaryService cloudinaryService;

    public SupportController(SupportService supportService, UserService userService,
                             AuthenticationManager authenticationManager, AISupportService aISupportService,
                             CloudinaryService cloudinaryService){
        this.supportService = supportService;
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.aISupportService = aISupportService;
        this.cloudinaryService = cloudinaryService;
    }

    @GetMapping("/dashboard")
    public String getDashboard(HttpServletRequest request, Model model){
        String userId = (String) authenticationManager.get("sub");
        request.getSession(true).setAttribute("isSupportAdmin", authenticationManager.isSupportAdmin());
        request.getSession(true).setAttribute("isAdmin", authenticationManager.isAdmin());
        request.getSession(true).setAttribute("isCommunityAdmin", authenticationManager.isCommunityAdmin());
        request.getSession(true).setAttribute("isGroupAdmin", authenticationManager.isGroupAdmin());
        request.getSession(true).setAttribute("loggedInUser", authenticationManager.get("name"));

        boolean canManage = authenticationManager.isSupportAdmin()
                || authenticationManager.isAdmin()
                || authenticationManager.isCommunityAdmin()
                || authenticationManager.isGroupAdmin();

        List<Ticket> unAssignedTickets = canManage
                ? supportService.getScopedUnclaimedTickets(userId)
                : Collections.<Ticket>emptyList();
        Integer unClaimedTicketSize = unAssignedTickets.size();
        Integer myTicketSize = supportService.getTickets().size();
        Integer totalTickets = unClaimedTicketSize + myTicketSize;

        model.addAttribute("unClaimedTicketSize", unClaimedTicketSize);
        model.addAttribute("myTicketSize", myTicketSize);
        model.addAttribute("totalTickets", totalTickets);
        model.addAttribute("unAssignedTickets", unAssignedTickets);
        model.addAttribute("canManageTickets", canManage);
        return "support/dashboard";
    }

    @GetMapping("/my-tickets")
    public String getTickets(HttpServletRequest request, Model model){
        ensureSessionRoles(request.getSession(false));
        boolean canManage = authenticationManager.isSupportAdmin()
                || authenticationManager.isAdmin()
                || authenticationManager.isCommunityAdmin()
                || authenticationManager.isGroupAdmin();

        // Regular users should not access claimed-tickets view — send them to their submitted tickets
        if (!canManage) {
            return "redirect:/support/my-submitted-tickets";
        }

        String userId = (String) authenticationManager.get("sub");
        model.addAttribute("isSupportAdmin", authenticationManager.isSupportAdmin());
        model.addAttribute("canManageTickets", true);
        // Only show tickets this admin has claimed/is handling
        model.addAttribute("tickets", supportService.getScopedAgentTickets(userId));
        model.addAttribute("currentUserId", userId);
        return "support/tickets";
    }

    @GetMapping("/my-submitted-tickets")
    public String getMySubmittedTickets(HttpServletRequest request, Model model){
        ensureSessionRoles(request.getSession(false));
        boolean canManage = authenticationManager.isSupportAdmin()
                || authenticationManager.isAdmin()
                || authenticationManager.isCommunityAdmin()
                || authenticationManager.isGroupAdmin();
        model.addAttribute("isSupportAdmin", authenticationManager.isSupportAdmin());
        model.addAttribute("canManageTickets", canManage);
        model.addAttribute("tickets", supportService.getMySubmittedTickets());
        model.addAttribute("currentUserId", authenticationManager.get("sub"));
        return "support/tickets";
    }

    @GetMapping("/single-ticket/{id}")
    public String getSingleTicket(@PathVariable Long id, Model model){
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
        model.addAttribute("conversationId", singleTicket.getConversation().getId());
        model.addAttribute("receiverId", singleTicket.getConversation().getReceiverId());
        model.addAttribute("messages", singleTicket.getConversation().getMessages());
        model.addAttribute("isClosed", "Closed".equalsIgnoreCase(singleTicket.getStatus()));
        return "support/ticketDetail :: ticketDetail";
    }

    @GetMapping("/agent/claim-ticket")
    public String claimTickets(HttpServletRequest request, Model model){
        ensureSessionRoles(request.getSession(false));
        String userId = (String) authenticationManager.get("sub");
        boolean canManage = authenticationManager.isSupportAdmin()
                || authenticationManager.isAdmin()
                || authenticationManager.isCommunityAdmin()
                || authenticationManager.isGroupAdmin();
        List<Ticket> unAssigned = canManage
                ? supportService.getScopedUnclaimedTickets(userId)
                : Collections.<Ticket>emptyList();
        model.addAttribute("unAssignedTickets", unAssigned);
        model.addAttribute("canManageTickets", canManage);
        return "support/agentTicket";
    }

    @GetMapping("/agent/claim-ticket/{id}")
    public String getSingleClaimTicket(@PathVariable Long id, Model model){
        Ticket singleClaimTicket = supportService.getTicketById(id);
        String userId = singleClaimTicket.getUserId();
        UserDTO ticketOwner = userService.getSingleUserByUserId(userId);
        String fullName = ticketOwner.getFirstName() + " " + ticketOwner.getLastName();

        // Resolve community / group name so the admin knows the source
        TicketDTO dto = supportService.mapTicketToDTO(singleClaimTicket);

        model.addAttribute("username", fullName);
        model.addAttribute("ticket", singleClaimTicket);
        model.addAttribute("communityName", dto.getCommunityName());
        model.addAttribute("groupName", dto.getGroupName());
        model.addAttribute("isMobile", false);
        return "support/claimTicketModal :: ticketDetails";
    }

    @PostMapping("/submit-request")
    public String submitRequests(@RequestParam Map<String, Object> formData, Model model){
        try {
            supportService.createTicket(formData);
            return "/support/successMessage";
        } catch (IllegalArgumentException | SecurityException e) {
            model.addAttribute("error", e.getMessage());
            return "/support/successMessage";
        }
    }

    @PostMapping("/submit-claim/{id}")
    public ResponseEntity<Void> submitClaim(@PathVariable Long id){
        String loginUser = (String) authenticationManager.get("sub");
        Ticket ticket = supportService.getTicketById(id);
        boolean canClaim = authenticationManager.isSupportAdmin()
                || authenticationManager.isAdmin()
                || (ticket != null && supportService.canManageTicket(loginUser, ticket));

        if (canClaim) {
            supportService.claimTicket(id, loginUser);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add("HX-Redirect", "/support/agent/claim-ticket");
        return ResponseEntity.status(HttpStatus.OK).headers(headers).build();
    }

    @PostMapping("/close-ticket/{id}")
    public ResponseEntity<Void> closeTicket(@PathVariable Long id){
        String loginUser = (String) authenticationManager.get("sub");
        Ticket ticket = supportService.getTicketById(id);
        boolean canClose = authenticationManager.isSupportAdmin()
                || authenticationManager.isAdmin()
                || (ticket != null && supportService.canManageTicket(loginUser, ticket));
        if (canClose) {
            supportService.closeTicket(id);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.add("HX-Redirect", "/support/my-tickets");
        return ResponseEntity.status(HttpStatus.OK).headers(headers).build();
    }

    @PostMapping("/create-meeting/{ticketId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createTicketMeeting(@PathVariable Long ticketId) {
        Map<String, Object> response = new HashMap<>();
        try {
            String agentUserId = (String) authenticationManager.get("sub");
            Ticket ticket = supportService.getTicketById(ticketId);
            boolean canManage = authenticationManager.isSupportAdmin()
                    || authenticationManager.isAdmin()
                    || (ticket != null && supportService.canManageTicket(agentUserId, ticket));

            if (!canManage) {
                response.put("success", false);
                response.put("message", "Only support admins or community/group admins can create meetings");
                return ResponseEntity.status(403).body(response);
            }

            if (ticket == null) {
                response.put("success", false);
                response.put("message", "Ticket not found");
                return ResponseEntity.status(404).body(response);
            }

            if ("Closed".equalsIgnoreCase(ticket.getStatus())) {
                response.put("success", false);
                response.put("message", "Cannot create meeting for a closed ticket");
                return ResponseEntity.badRequest().body(response);
            }

            String agentName = (String) authenticationManager.get("name");

            // Generate a unique room ID for this ticket
            String roomId = "support_ticket_" + ticketId + "_" + System.currentTimeMillis();
            String meetingUrl = "/videocall?roomID=" + roomId
                    + "&userName=" + java.net.URLEncoder.encode(agentName != null ? agentName : "Support Agent", "UTF-8")
                    + "&userID=" + java.net.URLEncoder.encode(agentUserId, "UTF-8")
                    + "&meetingName=" + java.net.URLEncoder.encode("Support: " + ticket.getSubject(), "UTF-8");

            // Build a shareable link for the ticket user (no userID/userName so they get prompted)
            String userMeetingLink = "/videocall?roomID=" + roomId
                    + "&meetingName=" + java.net.URLEncoder.encode("Support: " + ticket.getSubject(), "UTF-8");

            // Send the meeting link as a message in the ticket conversation
            if (ticket.getConversation() != null) {
                String meetingMessage = "📹 A live meeting session has been started for this ticket. Click <a href=\""
                        + userMeetingLink + "\" target=\"_blank\" style=\"color:#2563eb;font-weight:600;text-decoration:underline;\">here</a> to join.";
                supportService.sendSystemMessage(ticket.getConversation().getId(), agentUserId, meetingMessage);
            }

            response.put("success", true);
            response.put("meetingUrl", meetingUrl);
            response.put("roomId", roomId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Error creating meeting: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
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
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Upload failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/chat")
    public String support(@RequestParam Map<String, Object> formData, Model model){
        String response = aISupportService.supportChat(formData.get("message").toString(), (String) authenticationManager.get("sub"));
        model.addAttribute("response", response);
        return "support/AI-successMessage";
    }

    private void ensureSessionRoles(HttpSession session) {
        if (session == null) return;
        if (session.getAttribute("isSupportAdmin") == null)
            session.setAttribute("isSupportAdmin", authenticationManager.isSupportAdmin());
        if (session.getAttribute("isAdmin") == null)
            session.setAttribute("isAdmin", authenticationManager.isAdmin());
        if (session.getAttribute("isCommunityAdmin") == null)
            session.setAttribute("isCommunityAdmin", authenticationManager.isCommunityAdmin());
        if (session.getAttribute("isGroupAdmin") == null)
            session.setAttribute("isGroupAdmin", authenticationManager.isGroupAdmin());
        if (session.getAttribute("loggedInUser") == null)
            session.setAttribute("loggedInUser", authenticationManager.get("name"));
    }
}
