package com.justjava.mycommunity.support;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.userManagement.UserDTO;
import com.justjava.mycommunity.userManagement.UserService;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.Map;

@Controller
@RequestMapping("/support")
public class SupportController {
    private final SupportService supportService;
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final AISupportService aISupportService;

    public SupportController(SupportService supportService, UserService userService, AuthenticationManager authenticationManager, AISupportService aISupportService){
        this.supportService = supportService;
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.aISupportService = aISupportService;
    }
    @GetMapping("/dashboard")
    public String getDashboard(HttpServletRequest request, Model model){
        request.getSession(true).setAttribute("isSupportAdmin", authenticationManager.isSupportAdmin());
        request.getSession(true).setAttribute("isAdmin", authenticationManager.isAdmin());
        request.getSession(true).setAttribute("loggedInUser", authenticationManager.get("name"));

        Integer unClaimedTicketSize = supportService.getAllUnassignedTicket().size();
        Integer myTicketSize = supportService.getTickets().size();
        Integer totalTickets = unClaimedTicketSize + myTicketSize;

        model.addAttribute("unClaimedTicketSize", unClaimedTicketSize );
        model.addAttribute("myTicketSize", myTicketSize);
        model.addAttribute("totalTickets", totalTickets);
        model.addAttribute("unAssignedTickets", supportService.getAllUnassignedTicket());
        return "support/dashboard";
    }

    @GetMapping("/my-tickets")
    public String getTickets(Model model){

        model.addAttribute("isSupportAdmin", authenticationManager.isSupportAdmin());
        model.addAttribute("tickets", supportService.getTickets());
        model.addAttribute("currentUserId",authenticationManager.get("sub"));
        return "support/tickets";
    }

    @GetMapping("/single-ticket/{id}")
    public String getSingleTicket(@PathVariable Long id, Model model){
        String loginUser = (String) authenticationManager.get("sub");

        TicketDTO singleTicket = supportService.getSingleTicket(id, loginUser);
//        System.out.println(singleTicket.getConversation());

        model.addAttribute("ticketId",id);
        model.addAttribute("senderId", authenticationManager.get("sub"));
        model.addAttribute("ticket", singleTicket);
        model.addAttribute("receiverId",singleTicket.getConversation().getReceiverId());
        model.addAttribute("messages",singleTicket.getConversation().getMessages());
        return "support/ticketDetail :: ticketDetail";
    }

    @GetMapping("/agent/claim-ticket")
    public String claimTickets(Model model){

        model.addAttribute("unAssignedTickets", supportService.getAllUnassignedTicket());
        return "support/agentTicket";
    }

    @GetMapping("/agent/claim-ticket/{id}")
    public String getSingleClaimTicket(@PathVariable Long id, Model model){
        Ticket singleClaimTicket = supportService.getTicketById(id);
        String userId = singleClaimTicket.getUserId();
        UserDTO ticketOwner = userService.getSingleUserByUserId(userId);
        String fullName = ticketOwner.getFirstName() + " " + ticketOwner.getLastName();

        model.addAttribute("username", fullName);
        model.addAttribute("ticket", singleClaimTicket);
        return "support/claimTicketModal :: ticketDetails";
    }

    @PostMapping("/submit-request")
    public String submitRequests(@RequestParam Map<String, Object> formData){
//        System.out.println("This is the submitted request" + formData);

        supportService.createTicket(formData);

        return "/support/successMessage";
    }

    @PostMapping("/submit-claim/{id}")
    public ResponseEntity<Void> submitClaim(@PathVariable Long id){
        Ticket singleClaimTicket = supportService.getTicketById(id);

        if(authenticationManager.isSupportAdmin()){
            String loginUser = (String) authenticationManager.get("sub");
            supportService.claimTicket(id, loginUser);
        }
        System.out.println("This is the submit claim");

        HttpHeaders headers = new HttpHeaders();
        headers.add("HX-Redirect", "/support/agent/claim-ticket");
        return ResponseEntity.status(HttpStatus.OK).headers(headers).build();
    }

    @PostMapping("/chat")
    public String support(@RequestParam Map<String, Object> formData, Model model){
        String response = aISupportService.supportChat(formData.get("message").toString(), (String) authenticationManager.get("sub"));
        model.addAttribute("response", response);
        return "support/AI-successMessage";
    }

}
