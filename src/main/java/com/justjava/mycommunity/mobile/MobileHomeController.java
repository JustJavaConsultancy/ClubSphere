package com.justjava.mycommunity.mobile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.dto.SessionDTO;
import com.justjava.mycommunity.event.EventService;
import com.justjava.mycommunity.posts.PostService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Controller
@RequestMapping("/mobile")
public class MobileHomeController {

    private static final Logger log = LoggerFactory.getLogger(MobileHomeController.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final ZoneId APP_ZONE = ZoneId.of("Africa/Lagos");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final String UPCOMING = "Upcoming";
    private static final long READY_WINDOW_MINUTES = 120L;
    private static final long DEFAULT_DURATION_MINUTES = 60L;

    private final AuthenticationManager authenticationManager;
    private final PostService postService;
    private final EventService eventService;

    public MobileHomeController(AuthenticationManager authenticationManager,
                                PostService postService,
                                EventService eventService) {
        this.authenticationManager = authenticationManager;
        this.postService = postService;
        this.eventService = eventService;
    }

    /**
     * Renders the mobile home view for a selected community context.
     * Preserves all existing Thymeleaf model keys and session keys for compatibility.
     */
    @GetMapping("/home")
    public String home(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession();
        String currentUserId = (String) authenticationManager.get("sub");
        boolean isAdmin = authenticationManager.isAdmin();
        boolean isSupportAdmin = authenticationManager.isSupportAdmin();

        Long selectedCommunityId = (Long) session.getAttribute("selectedCommunityId");
        if (selectedCommunityId == null) {
            return "redirect:/mobile/organizations";
        }

        if (isSupportAdmin) {
            return "redirect:/support/dashboard";
        }

        List<SessionDTO> allEvents = mergeUserAndAdminSessions(
                eventService.getUserCoachingSessions(currentUserId),
                isAdmin ? eventService.getSessions() : List.of()
        );
        List<SessionDTO> allMeetings = mergeUserAndAdminSessions(
                eventService.getUserMeetings(currentUserId),
                isAdmin ? eventService.getMeetings() : List.of()
        );

        List<SessionDTO> upcomingEvents = filterAndSortUpcoming(allEvents);
        List<SessionDTO> upcomingMeetings = filterAndSortUpcoming(allMeetings);

        ZonedDateTime currentTime = ZonedDateTime.now(APP_ZONE);
        LocalDate currentDate = currentTime.toLocalDate();
        LocalTime currentLocalTime = LocalTime.parse(currentTime.format(TIME_FORMATTER), TIME_FORMATTER);

        List<Map<String, Object>> processedUpcomingEvents =
                toScheduleCards(upcomingEvents, currentDate, currentLocalTime);
        List<Map<String, Object>> processedUpcomingMeetings =
                toScheduleCards(upcomingMeetings, currentDate, currentLocalTime);

        String selectedCommunityName = (String) session.getAttribute("selectedCommunityName");

        model.addAttribute("allPosts", postService.getPostsFromUserCommunities(currentUserId, selectedCommunityId));
        model.addAttribute("selectedCommunityId", selectedCommunityId);
        model.addAttribute("selectedCommunityName", selectedCommunityName);

        setSessionAttributes(session, currentUserId, isAdmin, isSupportAdmin);

        model.addAttribute("userId", currentUserId);
        model.addAttribute("usersName", authenticationManager.get("name"));
        model.addAttribute("upcomingEventNum", upcomingEvents.size());
        model.addAttribute("upcomingEvents", processedUpcomingEvents);
        model.addAttribute("upcomingMeetingNum", upcomingMeetings.size());
        model.addAttribute("upcomingMeetings", processedUpcomingMeetings);
        model.addAttribute("currentTime", currentTime);
        model.addAttribute("isSupportAdmin", isSupportAdmin);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("currentPath", "/home");

        return "mobile-home";
    }

    private void setSessionAttributes(HttpSession session, String currentUserId, boolean isAdmin, boolean isSupportAdmin) {
        session.setAttribute("isAdmin", isAdmin);
        session.setAttribute("isSupportAdmin", isSupportAdmin);
        session.setAttribute("userId", currentUserId);
        session.setAttribute("loggedInUser", authenticationManager.get("name"));
    }

    private List<SessionDTO> mergeUserAndAdminSessions(List<SessionDTO> userSessions, List<SessionDTO> adminSessions) {
        if (adminSessions == null || adminSessions.isEmpty()) {
            return userSessions != null ? userSessions : List.of();
        }

        Map<Long, SessionDTO> mergedById = new LinkedHashMap<>();
        if (userSessions != null) {
            for (SessionDTO session : userSessions) {
                if (session != null && session.getId() != null) {
                    mergedById.put(session.getId(), session);
                }
            }
        }
        for (SessionDTO session : adminSessions) {
            if (session != null && session.getId() != null) {
                mergedById.putIfAbsent(session.getId(), session);
            }
        }
        return new ArrayList<>(mergedById.values());
    }

    private List<SessionDTO> filterAndSortUpcoming(List<SessionDTO> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }

        return sessions.stream()
                .filter(Objects::nonNull)
                .filter(session -> UPCOMING.equalsIgnoreCase(session.getStatus()))
                .sorted(this::compareByDateTimeThenIdDesc)
                .toList();
    }

    private int compareByDateTimeThenIdDesc(SessionDTO left, SessionDTO right) {
        try {
            LocalDate leftDate = safeParseDate(left.getStartDate());
            LocalDate rightDate = safeParseDate(right.getStartDate());
            int dateComparison = rightDate.compareTo(leftDate);
            if (dateComparison != 0) {
                return dateComparison;
            }

            LocalTime leftTime = safeParseTime(left.getStartTime());
            LocalTime rightTime = safeParseTime(right.getStartTime());
            if (leftTime != null && rightTime != null) {
                int timeComparison = rightTime.compareTo(leftTime);
                if (timeComparison != 0) {
                    return timeComparison;
                }
            }

            Long leftId = left.getId() == null ? 0L : left.getId();
            Long rightId = right.getId() == null ? 0L : right.getId();
            return Comparator.<Long>naturalOrder().reversed().compare(leftId, rightId);
        } catch (Exception ex) {
            log.warn("Failed sorting sessions {} and {}: {}", left.getId(), right.getId(), ex.getMessage());
            Long leftId = left.getId() == null ? 0L : left.getId();
            Long rightId = right.getId() == null ? 0L : right.getId();
            return Comparator.<Long>naturalOrder().reversed().compare(leftId, rightId);
        }
    }

    private List<Map<String, Object>> toScheduleCards(List<SessionDTO> sessions, LocalDate currentDate, LocalTime currentLocalTime) {
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }

        return sessions.stream()
                .map(session -> toScheduleCard(session, currentDate, currentLocalTime))
                .toList();
    }

    private Map<String, Object> toScheduleCard(SessionDTO session, LocalDate currentDate, LocalTime currentLocalTime) {
        Map<String, Object> card = OBJECT_MAPPER.convertValue(session, MAP_TYPE);
        try {
            LocalTime startTime = safeParseTime(session.getStartTime());
            LocalDate startDate = safeParseDate(session.getStartDate());
            if (startTime == null || startDate == null) {
                card.put("isReady", true);
                card.put("startTime", "TBD");
                card.put("eventEndTime", "TBD");
                return card;
            }

            long minutes = Math.abs(Duration.between(currentLocalTime, startTime).toMinutes());
            long duration = session.getDuration() != null ? session.getDuration() : DEFAULT_DURATION_MINUTES;
            LocalTime endTime = startTime.plusMinutes(duration);

            boolean isReady = isSessionReady(startDate, currentDate, minutes);
            card.put("isReady", isReady);
            card.put("startTime", startTime);
            card.put("eventEndTime", endTime);
            return card;
        } catch (Exception ex) {
            log.warn("Failed to map schedule card for session {}: {}", session.getId(), ex.getMessage());
            card.put("isReady", true);
            card.put("startTime", "TBD");
            card.put("eventEndTime", "TBD");
            return card;
        }
    }

    /**
     * Keeps existing readiness semantics for backward compatibility with the current UI flow.
     */
    private boolean isSessionReady(LocalDate sessionDate, LocalDate currentDate, long minutesDifference) {
        boolean isRelevantDate = sessionDate.isEqual(currentDate) || sessionDate.isAfter(currentDate.minusDays(1));
        if (!isRelevantDate) {
            return false;
        }
        return minutesDifference < READY_WINDOW_MINUTES || sessionDate.isAfter(currentDate);
    }

    private LocalDate safeParseDate(String value) {
        if (value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return LocalDate.parse(value.trim(), DATE_FORMATTER);
    }

    private LocalTime safeParseTime(String value) {
        if (value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return LocalTime.parse(value.trim(), TIME_FORMATTER);
    }
}
