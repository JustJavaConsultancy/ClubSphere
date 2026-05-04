package com.justjava.mycommunity.event;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.community.Community;
import com.justjava.mycommunity.community.repository.DonationRepository;
import com.justjava.mycommunity.userManagement.UserRepository;
import com.justjava.mycommunity.chat.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;
    private final AuthenticationManager authenticationManager;
    private final DonationRepository donationRepository;
    private final UserRepository userRepository;

    public EventController(EventService eventService, AuthenticationManager authenticationManager,
                           DonationRepository donationRepository, UserRepository userRepository) {
        this.eventService = eventService;
        this.authenticationManager = authenticationManager;
        this.donationRepository = donationRepository;
        this.userRepository = userRepository;
    }

    /** Create-event page: shows the form and lists events already created in the user's communities. */
    @GetMapping("/create")
    public String createEventPage(HttpServletRequest request, Model model) {
        String userId = (String) authenticationManager.get("sub");
        boolean isAdmin = Boolean.TRUE.equals(authenticationManager.isAdmin());
        boolean isCommunityAdmin = Boolean.TRUE.equals(authenticationManager.isCommunityAdmin());

        if (!isAdmin && !isCommunityAdmin) {
            return "redirect:/";
        }

        Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");

        // If inside a specific community, lock dropdown to that community only
        List<Community> communities;
        if (selectedCommunityId != null) {
            communities = eventService.getAdminCommunities(userId).stream()
                    .filter(c -> c.getId().equals(selectedCommunityId))
                    .toList();
        } else {
            communities = eventService.getAdminCommunities(userId);
        }

        List<Event> events = eventService.getEventsFromUserCommunities(userId, selectedCommunityId);

        // Build donation totals map: eventId -> total amount
        Map<Long, BigDecimal> donationTotals = events.stream().collect(Collectors.toMap(
                Event::getId,
                e -> donationRepository.findByEventId(e.getId()).stream()
                        .map(d -> d.getAmount() != null ? d.getAmount() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        ));

        model.addAttribute("communities", communities);
        model.addAttribute("selectedCommunityId", selectedCommunityId);
        model.addAttribute("events", events);
        model.addAttribute("donationTotals", donationTotals);
        model.addAttribute("currentPath", "/events/create");
        model.addAttribute("canCreate", !communities.isEmpty());
        return "events";
    }

    /** Handle form submission to create a new event. */
    @PostMapping("/create")
    public String submitCreateEvent(@RequestParam String title,
                                    @RequestParam String description,
                                    @RequestParam(required = false) Long communityId,
                                    @RequestParam(required = false) String startDate,
                                    RedirectAttributes redirectAttributes) {
        boolean isAdmin = Boolean.TRUE.equals(authenticationManager.isAdmin());
        boolean isCommunityAdmin = Boolean.TRUE.equals(authenticationManager.isCommunityAdmin());
        if (!isAdmin && !isCommunityAdmin) {
            return "redirect:/";
        }

        LocalDate start = (startDate != null && !startDate.isBlank()) ? LocalDate.parse(startDate) : null;

        eventService.createSimpleEvent(title, description, communityId, start, null);
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

    /** Returns donations for a specific event — used by the events page modal */
    @GetMapping("/api/donations/{eventId}")
    @ResponseBody
    public List<Map<String, Object>> getEventDonations(@PathVariable Long eventId) {
        var donations = donationRepository.findByEventId(eventId);
        List<String> userIds = donations.stream().map(d -> d.getUserId()).distinct().toList();
        Map<String, User> userMap = userRepository.findByUserIdIn(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getUserId, u -> u));
        return donations.stream().map(d -> {
            User u = userMap.get(d.getUserId());
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("donationId", d.getId());
            m.put("firstName", u != null ? u.getFirstName() : null);
            m.put("lastName", u != null ? u.getLastName() : null);
            m.put("amount", d.getAmount());
            m.put("message", d.getMessage());
            m.put("donatedAt", d.getDonatedAt());
            return m;
        }).toList();
    }
}
