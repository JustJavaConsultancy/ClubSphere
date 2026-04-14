package com.justjava.mycommunity.mobile;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.support.SupportService;
import com.justjava.mycommunity.support.TicketDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/mobile/support")
@RequiredArgsConstructor
public class MobileSupportController {

    private final SupportService supportService;
    private final AuthenticationManager authenticationManager;

    @GetMapping("/my-tickets")
    public String getTickets(Model model) {
        model.addAttribute("isSupportAdmin", authenticationManager.isSupportAdmin());
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
        model.addAttribute("conversationId", singleTicket.getConversation().getId());
        model.addAttribute("receiverId", singleTicket.getConversation().getReceiverId());
        model.addAttribute("messages", singleTicket.getConversation().getMessages());
        model.addAttribute("isClosed", "Closed".equalsIgnoreCase(singleTicket.getStatus()));
        return "support/ticketDetail :: ticketDetail";
    }
}

