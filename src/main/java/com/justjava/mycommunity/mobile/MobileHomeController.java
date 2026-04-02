package com.justjava.mycommunity.mobile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.dto.SessionDTO;
import com.justjava.mycommunity.event.EventService;
import com.justjava.mycommunity.posts.PostService;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Controller
@RequestMapping("/mobile")
public class MobileHomeController {

    private final AuthenticationManager authenticationManager;
    private final PostService postService;
    private final EventService eventService;

    public MobileHomeController(AuthenticationManager authenticationManager,
                                PostService postService, EventService eventService) {
        this.authenticationManager = authenticationManager;
        this.postService = postService;
        this.eventService = eventService;
    }

    @GetMapping("/home")
    public String home(HttpServletRequest request, Model model) {
        String currentUserId = (String) authenticationManager.get("sub");
        boolean isAdmin = authenticationManager.isAdmin();

        // Check if user has selected a mycommunity, if not redirect to organizations
        Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");
        if (selectedCommunityId == null) {
            return "redirect:/mobile/organizations";
        }

        // Get user events (where user is participant)
        List<SessionDTO> userEvents = eventService.getUserCoachingSessions(currentUserId);
        System.out.println("Total user events retrieved: " + userEvents.size());

        // If admin, also get events they created
        List<SessionDTO> allEvents = userEvents;
        if (isAdmin) {
            Object sessionsObj = eventService.getSessions();
            List<SessionDTO> allSessions;
            if (sessionsObj instanceof List) {
                allSessions = (List<SessionDTO>) sessionsObj;
            } else {
                allSessions = new java.util.ArrayList<>();
            }

            List<SessionDTO> adminCreatedEvents = allSessions.stream()
                    .filter(session -> !userEvents.stream()
                            .anyMatch(userEvent -> userEvent.getId().equals(session.getId())))
                    .toList();

            allEvents = Stream.concat(userEvents.stream(), adminCreatedEvents.stream()).toList();
            System.out.println("Admin created events added: " + adminCreatedEvents.size());
        }

        // Debug: Print all events with their statuses
        allEvents.forEach(event -> {
            System.out.println("Event: " + event.getModuleName() + ", Status: " + event.getStatus() + ", Date: " + event.getStartDate());
        });

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        List<SessionDTO> upcomingEvents = allEvents.stream()
                .filter(event -> {
                    String status = event.getStatus();
                    boolean isUpcoming = "Upcoming".equalsIgnoreCase(status);
                    System.out.println("Event " + event.getModuleName() + " status: " + status + ", isUpcoming: " + isUpcoming);
                    return isUpcoming;
                })
                .sorted((e1, e2) -> {
                    try {
                        // First, compare by date
                        LocalDate date1 = LocalDate.parse(e1.getStartDate(), dateFormatter);
                        LocalDate date2 = LocalDate.parse(e2.getStartDate(), dateFormatter);

                        // Compare dates first
                        int dateComparison = date2.compareTo(date1); // Descending order
                        if (dateComparison != 0) {
                            return dateComparison;
                        }

                        // If dates are equal, compare by time
                        String time1Str = e1.getStartTime();
                        String time2Str = e2.getStartTime();

                        if (time1Str != null && time2Str != null &&
                                !time1Str.trim().isEmpty() && !time2Str.trim().isEmpty()) {
                            try {
                                LocalTime time1 = LocalTime.parse(time1Str.trim());
                                LocalTime time2 = LocalTime.parse(time2Str.trim());
                                int timeComparison = time2.compareTo(time1); // Descending order
                                if (timeComparison != 0) {
                                    return timeComparison;
                                }
                            } catch (Exception timeEx) {
                                System.err.println("Error parsing times for events " + e1.getId() + " and " + e2.getId());
                            }
                        }

                        // If dates and times are equal, sort by ID descending (newest created first)
                        return Long.compare(e2.getId(), e1.getId());

                    } catch (Exception e) {
                        System.err.println("Error comparing events " + e1.getId() + " and " + e2.getId() + ": " + e.getMessage());
                        // Fallback to ID comparison
                        return Long.compare(e2.getId(), e1.getId());
                    }
                })
                .toList();

        // Get user's meetings (where user is participant)
        List<SessionDTO> userMeetings = eventService.getUserMeetings(currentUserId);
        System.out.println("Total user meetings retrieved: " + userMeetings.size());

        // If admin, also get meetings they created
        List<SessionDTO> allMeetings = userMeetings;
        if (isAdmin) {
            Object meetingsObj = eventService.getMeetings();
            List<SessionDTO> allMeetingsList;
            if (meetingsObj instanceof List) {
                allMeetingsList = (List<SessionDTO>) meetingsObj;
            } else {
                allMeetingsList = new java.util.ArrayList<>();
            }

            List<SessionDTO> adminCreatedMeetings = allMeetingsList.stream()
                    .filter(meeting -> !userMeetings.stream()
                            .anyMatch(userMeeting -> userMeeting.getId().equals(meeting.getId())))
                    .toList();

            allMeetings = Stream.concat(userMeetings.stream(), adminCreatedMeetings.stream()).toList();
            System.out.println("Admin created meetings added: " + adminCreatedMeetings.size());
        }

        // Debug: Print all meetings with their statuses
        allMeetings.forEach(meeting -> {
            System.out.println("Meeting: " + meeting.getModuleName() + ", Status: " + meeting.getStatus() + ", Date: " + meeting.getStartDate());
        });

        List<SessionDTO> upcomingMeetings = allMeetings.stream()
                .filter(meeting -> {
                    String status = meeting.getStatus();
                    boolean isUpcoming = "Upcoming".equalsIgnoreCase(status);
                    System.out.println("Meeting " + meeting.getModuleName() + " status: " + status + ", isUpcoming: " + isUpcoming);
                    return isUpcoming;
                })
                .sorted((m1, m2) -> {
                    try {
                        // First, compare by date
                        LocalDate date1 = LocalDate.parse(m1.getStartDate(), dateFormatter);
                        LocalDate date2 = LocalDate.parse(m2.getStartDate(), dateFormatter);

                        // Compare dates first
                        int dateComparison = date2.compareTo(date1); // Descending order
                        if (dateComparison != 0) {
                            return dateComparison;
                        }

                        // If dates and times are equal, compare by time
                        String time1Str = m1.getStartTime();
                        String time2Str = m2.getStartTime();

                        if (time1Str != null && time2Str != null &&
                                !time1Str.trim().isEmpty() && !time2Str.trim().isEmpty()) {
                            try {
                                LocalTime time1 = LocalTime.parse(time1Str.trim());
                                LocalTime time2 = LocalTime.parse(time2Str.trim());
                                int timeComparison = time2.compareTo(time1); // Descending order
                                if (timeComparison != 0) {
                                    return timeComparison;
                                }
                            } catch (Exception timeEx) {
                                System.err.println("Error parsing times for meetings " + m1.getId() + " and " + m2.getId());
                            }
                        }

                        // If dates and times are equal, sort by ID descending (newest created first)
                        return Long.compare(m2.getId(), m1.getId());

                    } catch (Exception e) {
                        System.err.println("Error comparing meetings " + m1.getId() + " and " + m2.getId() + ": " + e.getMessage());
                        // Fallback to ID comparison
                        return Long.compare(m2.getId(), m1.getId());
                    }
                })
                .toList();

        System.out.println("Filtered upcoming events: " + upcomingEvents.size());
        System.out.println("Filtered upcoming meetings: " + upcomingMeetings.size());

        ObjectMapper mapper = new ObjectMapper();
        ZoneId zoneId = ZoneId.of("Africa/Lagos");
        ZonedDateTime currentTime = ZonedDateTime.now(zoneId);

        // Format to a readable string
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        String formattedCurrentTime = currentTime.format(formatter);

        LocalTime localTimeNow = LocalTime.parse(formattedCurrentTime, formatter);

        String currentDate = currentTime.format(dateFormatter);
        LocalDate localDateNow = LocalDate.parse(currentDate, dateFormatter);

        List<Map<String, Object>> newUpcomingEvents = upcomingEvents.stream()
                .map(event -> {
                    Map<String, Object> newEvent = mapper.convertValue(event, Map.class);
                    System.out.println("\nProcessing event: " + event.getModuleName() + " with start time: " + event.getStartTime());
                    try {
                        if (event.getStartTime() != null && !event.getStartTime().equals("null")) {
                            LocalTime eventStartTime = LocalTime.parse(event.getStartTime(), formatter);
                            Duration diff = Duration.between(localTimeNow, eventStartTime);
                            long minutes = Math.abs(diff.toMinutes());
                            LocalTime eventEndTime = eventStartTime.plusMinutes(event.getDuration() != null ? event.getDuration() : 60);

                            LocalDate eventStartDate = LocalDate.parse(event.getStartDate(), dateFormatter);

                            System.out.println("Event " + event.getModuleName() + " - Start: " + eventStartTime + ", End: " + eventEndTime + ", Minutes diff: " + minutes);

                            // More lenient ready condition - allow joining if it's today or within 2 hours
                            if(eventStartDate.isEqual(localDateNow) || eventStartDate.isAfter(localDateNow.minusDays(1))){
                                if(minutes < 120 || eventStartDate.isAfter(localDateNow)){
                                    newEvent.put("isReady", true);
                                    System.out.println("Event " + event.getModuleName() + " is READY for joining");
                                } else {
                                    newEvent.put("isReady", false);
                                    System.out.println("Event " + event.getModuleName() + " is NOT ready - too early");
                                }
                            } else {
                                newEvent.put("isReady", false);
                                System.out.println("Event " + event.getModuleName() + " is NOT ready - wrong date");
                            }

                            newEvent.put("eventEndTime", eventEndTime);
                            newEvent.put("startTime", eventStartTime);
                        } else {
                            System.out.println("Event " + event.getModuleName() + " has invalid start time");
                            newEvent.put("isReady", true); // Default to ready if time is invalid
                            newEvent.put("startTime", "TBD");
                            newEvent.put("eventEndTime", "TBD");
                        }
                    } catch (Exception e) {
                        System.err.println("Error processing event time for " + event.getModuleName() + ": " + e.getMessage());
                        newEvent.put("isReady", true); // Default to ready if there's an error
                        newEvent.put("startTime", "TBD");
                        newEvent.put("eventEndTime", "TBD");
                    }
                    return newEvent;
                }).toList();

        // Process upcoming meetings similar to events
        List<Map<String, Object>> newUpcomingMeetings = upcomingMeetings.stream()
                .map(meeting -> {
                    Map<String, Object> newMeeting = mapper.convertValue(meeting, Map.class);
                    System.out.println("\nProcessing meeting: " + meeting.getModuleName() + " with start time: " + meeting.getStartTime());
                    try {
                        if (meeting.getStartTime() != null && !meeting.getStartTime().equals("null")) {
                            LocalTime meetingStartTime = LocalTime.parse(meeting.getStartTime(), formatter);
                            Duration diff = Duration.between(localTimeNow, meetingStartTime);
                            long minutes = Math.abs(diff.toMinutes());
                            LocalTime meetingEndTime = meetingStartTime.plusMinutes(meeting.getDuration() != null ? meeting.getDuration() : 60);

                            LocalDate meetingStartDate = LocalDate.parse(meeting.getStartDate(), dateFormatter);

                            System.out.println("Meeting " + meeting.getModuleName() + " - Start: " + meetingStartTime + ", End: " + meetingEndTime + ", Minutes diff: " + minutes);

                            // More lenient ready condition - allow joining if it's today or within 2 hours
                            if(meetingStartDate.isEqual(localDateNow) || meetingStartDate.isAfter(localDateNow.minusDays(1))){
                                if(minutes < 120 || meetingStartDate.isAfter(localDateNow)){
                                    newMeeting.put("isReady", true);
                                    System.out.println("Meeting " + meeting.getModuleName() + " is READY for joining");
                                } else {
                                    newMeeting.put("isReady", false);
                                    System.out.println("Meeting " + meeting.getModuleName() + " is NOT ready - too early");
                                }
                            } else {
                                newMeeting.put("isReady", false);
                                System.out.println("Meeting " + meeting.getModuleName() + " is NOT ready - wrong date");
                            }

                            newMeeting.put("eventEndTime", meetingEndTime);
                            newMeeting.put("startTime", meetingStartTime);
                        } else {
                            System.out.println("Meeting " + meeting.getModuleName() + " has invalid start time");
                            newMeeting.put("isReady", true); // Default to ready if time is invalid
                            newMeeting.put("startTime", "TBD");
                            newMeeting.put("eventEndTime", "TBD");
                        }
                    } catch (Exception e) {
                        System.err.println("Error processing meeting time for " + meeting.getModuleName() + ": " + e.getMessage());
                        newMeeting.put("isReady", true); // Default to ready if there's an error
                        newMeeting.put("startTime", "TBD");
                        newMeeting.put("eventEndTime", "TBD");
                    }
                    return newMeeting;
                }).toList();

        // Get mycommunity-specific posts
        model.addAttribute("allPosts", postService.getPostsFromUserCommunities(currentUserId, selectedCommunityId));

        // Add selected mycommunity information to model
        String selectedCommunityName = (String) request.getSession().getAttribute("selectedCommunityName");
        model.addAttribute("selectedCommunityId", selectedCommunityId);
        model.addAttribute("selectedCommunityName", selectedCommunityName);

        if(authenticationManager.isSupportAdmin()){
            return "redirect:/support/dashboard";
        }

        request.getSession(true).setAttribute("isAdmin", authenticationManager.isAdmin());
        request.getSession(true).setAttribute("isSupportAdmin", authenticationManager.isSupportAdmin());
        request.getSession().setAttribute("userId", authenticationManager.get("sub"));

        // Add attributes to the model
        model.addAttribute("userId",authenticationManager.get("sub"));
        model.addAttribute("usersName", authenticationManager.get("name"));
        model.addAttribute("upcomingEventNum", upcomingEvents.size());
        model.addAttribute("upcomingEvents", newUpcomingEvents);
        model.addAttribute("upcomingMeetingNum", upcomingMeetings.size());
        model.addAttribute("upcomingMeetings", newUpcomingMeetings);
        model.addAttribute("currentTime", currentTime);
        model.addAttribute("isSupportAdmin", authenticationManager.isSupportAdmin());
        model.addAttribute("isAdmin",authenticationManager.isAdmin());
        model.addAttribute("currentPath", "/home");
        request.getSession(true).setAttribute("loggedInUser", authenticationManager.get("name"));

        return "mobile-home";
    }
}
