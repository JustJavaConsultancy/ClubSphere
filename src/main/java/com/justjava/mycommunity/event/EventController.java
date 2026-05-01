package com.justjava.mycommunity.event;

import com.justjava.mycommunity.account.AuthenticationManager;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;
    private final AuthenticationManager authenticationManager;

    public EventController(EventService eventService, AuthenticationManager authenticationManager) {
        this.eventService = eventService;
        this.authenticationManager = authenticationManager;
    }

    /** Create-event page: shows the form and lists events already created in the user's communities. */
    @GetMapping("/create")
    public String createEventPage(HttpServletRequest request, Model model) {
        String userId = (String) authenticationManager.get("sub");
        Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");

        model.addAttribute("communities", eventService.getUserCommunities(userId));
        model.addAttribute("selectedCommunityId", selectedCommunityId);
        model.addAttribute("events", eventService.getEventsFromUserCommunities(userId, selectedCommunityId));
        model.addAttribute("currentPath", "/events/create");
        return "events";
    }

    /** Handle form submission to create a new event. */
    @PostMapping("/create")
    public String submitCreateEvent(@RequestParam String title,
                                    @RequestParam String description,
                                    @RequestParam(required = false) Long communityId,
                                    RedirectAttributes redirectAttributes) {
        eventService.createSimpleEvent(title, description, communityId);
        redirectAttributes.addFlashAttribute("successMessage", "Event created successfully!");
        return "redirect:/events/create";
    }

    @GetMapping("/list")
    public String listEvents(HttpServletRequest request, Model model) {
        String userId = (String) authenticationManager.get("sub");
        Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");

        model.addAttribute("events", eventService.getEventsFromUserCommunities(userId, selectedCommunityId));
        model.addAttribute("currentPath", "/events");
        return "events";
    }
}
