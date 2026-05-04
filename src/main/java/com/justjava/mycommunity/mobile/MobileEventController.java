package com.justjava.mycommunity.mobile;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.community.Community;
import com.justjava.mycommunity.community.repository.DonationRepository;
import com.justjava.mycommunity.event.Event;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/mobile/events")
@RequiredArgsConstructor
public class MobileEventController {

    private final EventService eventService;
    private final AuthenticationManager authenticationManager;
    private final DonationRepository donationRepository;

    @GetMapping("/create")
    public String createEventPage(HttpServletRequest request, Model model) {
        String userId = (String) authenticationManager.get("sub");
        boolean isAdmin = Boolean.TRUE.equals(authenticationManager.isAdmin());
        boolean isCommunityAdmin = Boolean.TRUE.equals(authenticationManager.isCommunityAdmin());

        if (!isAdmin && !isCommunityAdmin) {
            return "redirect:/mobile/home";
        }

        Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");

        // If inside a specific community, lock dropdown to that community only
        List<Community> adminCommunities = eventService.getAdminCommunities(userId);
        List<Community> communities = selectedCommunityId != null
                ? adminCommunities.stream().filter(c -> c.getId().equals(selectedCommunityId)).toList()
                : adminCommunities;

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
        return "mobile-events";
    }

    @PostMapping("/create")
    public String submitCreateEvent(@RequestParam String title,
                                    @RequestParam String description,
                                    @RequestParam(required = false) Long communityId,
                                    @RequestParam(required = false) String startDate,
                                    RedirectAttributes redirectAttributes) {
        boolean isAdmin = Boolean.TRUE.equals(authenticationManager.isAdmin());
        boolean isCommunityAdmin = Boolean.TRUE.equals(authenticationManager.isCommunityAdmin());
        if (!isAdmin && !isCommunityAdmin) {
            return "redirect:/mobile/home";
        }

        LocalDate start = (startDate != null && !startDate.isBlank()) ? LocalDate.parse(startDate) : null;
        eventService.createSimpleEvent(title, description, communityId, start, null);
        redirectAttributes.addFlashAttribute("successMessage", "Event created successfully!");
        return "redirect:/mobile/events/create";
    }
}
