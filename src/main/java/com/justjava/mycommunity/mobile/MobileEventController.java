package com.justjava.mycommunity.mobile;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.event.EventService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/mobile/events")
@RequiredArgsConstructor
public class MobileEventController {

    private final EventService eventService;
    private final AuthenticationManager authenticationManager;

    @GetMapping("/create")
    public String createEventPage(HttpServletRequest request, Model model) {
        String userId = (String) authenticationManager.get("sub");
        Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");

        model.addAttribute("communities", eventService.getUserCommunities(userId));
        model.addAttribute("selectedCommunityId", selectedCommunityId);
        model.addAttribute("events", eventService.getEventsFromUserCommunities(userId, selectedCommunityId));
        model.addAttribute("currentPath", "/events/create");
        return "mobile-events";
    }

    @PostMapping("/create")
    public String submitCreateEvent(@RequestParam String title,
                                    @RequestParam String description,
                                    @RequestParam(required = false) Long communityId,
                                    RedirectAttributes redirectAttributes) {
        eventService.createSimpleEvent(title, description, communityId);
        redirectAttributes.addFlashAttribute("successMessage", "Event created successfully!");
        return "redirect:/mobile/events/create";
    }
}

