package com.justjava.mycommunity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.dto.CommentDTO;
import com.justjava.mycommunity.chat.dto.CreateChatDTO;
import com.justjava.mycommunity.chat.dto.SessionDTO;
import com.justjava.mycommunity.chat.model.PostMessage;
import com.justjava.mycommunity.community.CommunityGroupService;
import com.justjava.mycommunity.community.CommunityService;
import com.justjava.mycommunity.event.EventService;
import com.justjava.mycommunity.network.NetworkService;
import com.justjava.mycommunity.posts.Post;
import com.justjava.mycommunity.posts.PostService;
import com.justjava.mycommunity.support.AISupportService;
import com.justjava.mycommunity.support.SupportFeignClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class HomeController {
    @Autowired
    private CommunityService communityService;
    @Autowired
    private CommunityGroupService communityGroupService;
    @Autowired
    private NetworkService networkService;
    private final AuthenticationManager authenticationManager;
    private final SupportFeignClient supportFeignClient;
    private final PostService postService;
    private final EventService eventService;
    @Autowired
    private AISupportService aISupportService;

    public HomeController(AuthenticationManager authenticationManager,SupportFeignClient supportFeignClient,
                          PostService postService, EventService eventService) {
        this.authenticationManager = authenticationManager;
        this.supportFeignClient = supportFeignClient;
        this.postService = postService;
        this.eventService = eventService;
    }

    private Map<String, Object> extractCommunityData(Object communityResponse) {
        Map<String, Object> normalizedCommunity = new HashMap<>();

        try {
            // The response should be a Community entity
            if (communityResponse instanceof Map) {
                Map<String, Object> communityMap = (Map<String, Object>) communityResponse;
                normalizedCommunity.put("id", communityMap.get("id"));
                normalizedCommunity.put("communityName", communityMap.get("name"));
                normalizedCommunity.put("communityDescription", communityMap.get("description"));
            } else {
                // Handle entity object using reflection
                try {
                    Object idValue = communityResponse.getClass().getMethod("getId").invoke(communityResponse);
                    Object nameValue = communityResponse.getClass().getMethod("getName").invoke(communityResponse);
                    Object descValue = communityResponse.getClass().getMethod("getDescription").invoke(communityResponse);

                    normalizedCommunity.put("id", idValue);
                    normalizedCommunity.put("communityName", nameValue != null ? nameValue.toString() : "Unknown Community");
                    normalizedCommunity.put("communityDescription", descValue != null ? descValue.toString() : "No description");

                    System.out.println("Extracted from entity - ID: " + idValue + ", Name: " + nameValue + ", Description: " + descValue);
                } catch (Exception e) {
                    System.out.println("Error extracting from entity: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.out.println("Error in extractCommunityData: " + e.getMessage());
            e.printStackTrace();
        }

        return normalizedCommunity;
    }


    @GetMapping("/")
    public String home(HttpServletRequest request, Model model) {
        String currentUserId = (String) authenticationManager.get("sub");
        boolean isAdmin = authenticationManager.isAdmin();

        // Get selected mycommunity ID from session
        Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");
        String selectedCommunityName = (String) request.getSession().getAttribute("selectedCommunityName");

        System.out.println("=== HOME CONTROLLER DEBUG ===");
        System.out.println("Current User ID: " + currentUserId);
        System.out.println("Is Admin: " + isAdmin);
        System.out.println("Selected Community ID: " + selectedCommunityId);
        System.out.println("Selected Community Name: " + selectedCommunityName);

        // Get user events based on mycommunity context
        List<SessionDTO> allEvents;
        if (selectedCommunityId != null) {
            // Community-specific events
            System.out.println("Loading mycommunity-specific events for mycommunity ID: " + selectedCommunityId);
            allEvents = eventService.getSessionsFromUserCommunities(currentUserId, selectedCommunityId);
        } else {
            // Universal events from all communities
            System.out.println("Loading universal events from all communities");
            allEvents = eventService.getSessionsFromUserCommunities(currentUserId);
        }
        System.out.println("Total events retrieved: " + allEvents.size());

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
                        // First, compare by date - CHANGED TO ASCENDING ORDER (soonest first)
                        LocalDate date1 = LocalDate.parse(e1.getStartDate(), dateFormatter);
                        LocalDate date2 = LocalDate.parse(e2.getStartDate(), dateFormatter);

                        // Compare dates first - CHANGED TO ASCENDING
                        int dateComparison = date1.compareTo(date2); // Ascending order (soonest first)
                        if (dateComparison != 0) {
                            return dateComparison;
                        }

                        // If dates are equal, compare by time - CHANGED TO ASCENDING
                        String time1Str = e1.getStartTime();
                        String time2Str = e2.getStartTime();

                        if (time1Str != null && time2Str != null &&
                                !time1Str.trim().isEmpty() && !time2Str.trim().isEmpty()) {
                            try {
                                LocalTime time1 = LocalTime.parse(time1Str.trim());
                                LocalTime time2 = LocalTime.parse(time2Str.trim());
                                int timeComparison = time1.compareTo(time2); // Ascending order (earlier times first)
                                if (timeComparison != 0) {
                                    return timeComparison;
                                }
                            } catch (Exception timeEx) {
                                System.err.println("Error parsing times for events " + e1.getId() + " and " + e2.getId());
                            }
                        }

                        // If dates and times are equal, sort by ID ascending (oldest created first)
                        return Long.compare(e1.getId(), e2.getId());

                    } catch (Exception e) {
                        System.err.println("Error comparing events " + e1.getId() + " and " + e2.getId() + ": " + e.getMessage());
                        // Fallback to ID comparison
                        return Long.compare(e1.getId(), e2.getId());
                    }
                })
                .toList();

        // Get user's meetings based on mycommunity context
        List<SessionDTO> allMeetings;
        if (selectedCommunityId != null) {
            // Community-specific meetings
            System.out.println("Loading mycommunity-specific meetings for mycommunity ID: " + selectedCommunityId);
            allMeetings = eventService.getMeetingsFromUserCommunities(currentUserId, selectedCommunityId);
        } else {
            // Universal meetings from all communities
            System.out.println("Loading universal meetings from all communities");
            allMeetings = eventService.getMeetingsFromUserCommunities(currentUserId);
        }
        System.out.println("Total meetings retrieved: " + allMeetings.size());

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
                        // First, compare by date - CHANGED TO ASCENDING ORDER (soonest first)
                        LocalDate date1 = LocalDate.parse(m1.getStartDate(), dateFormatter);
                        LocalDate date2 = LocalDate.parse(m2.getStartDate(), dateFormatter);

                        // Compare dates first - CHANGED TO ASCENDING
                        int dateComparison = date1.compareTo(date2); // Ascending order (soonest first)
                        if (dateComparison != 0) {
                            return dateComparison;
                        }

                        // If dates are equal, compare by time - CHANGED TO ASCENDING
                        String time1Str = m1.getStartTime();
                        String time2Str = m2.getStartTime();

                        if (time1Str != null && time2Str != null &&
                                !time1Str.trim().isEmpty() && !time2Str.trim().isEmpty()) {
                            try {
                                LocalTime time1 = LocalTime.parse(time1Str.trim());
                                LocalTime time2 = LocalTime.parse(time2Str.trim());
                                int timeComparison = time1.compareTo(time2); // Ascending order (earlier times first)
                                if (timeComparison != 0) {
                                    return timeComparison;
                                }
                            } catch (Exception timeEx) {
                                System.err.println("Error parsing times for meetings " + m1.getId() + " and " + m2.getId());
                            }
                        }

                        // If dates and times are equal, sort by ID ascending (oldest created first)
                        return Long.compare(m1.getId(), m2.getId());

                    } catch (Exception e) {
                        System.err.println("Error comparing meetings " + m1.getId() + " and " + m2.getId() + ": " + e.getMessage());
                        // Fallback to ID comparison
                        return Long.compare(m1.getId(), m2.getId());
                    }
                })
                .toList();

        System.out.println("Filtered upcoming events: " + upcomingEvents.size());
        System.out.println("Filtered upcoming meetings: " + upcomingMeetings.size());

        ObjectMapper mapper = new ObjectMapper();
        ZoneId zoneId = ZoneId.of("Africa/Lagos");
        ZonedDateTime currentTime = ZonedDateTime.now(zoneId);

        // Format to a readable string
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        String formattedCurrentTime = currentTime.format(timeFormatter);

        LocalTime localTimeNow = LocalTime.parse(formattedCurrentTime, timeFormatter);

        String currentDate = currentTime.format(dateFormatter);
        LocalDate localDateNow = LocalDate.parse(currentDate, dateFormatter);

        // Process upcoming events with date information
        List<Map<String, Object>> newUpcomingEvents = upcomingEvents.stream()
                .map(event -> {
                    Map<String, Object> newEvent = mapper.convertValue(event, Map.class);
                    System.out.println("\nProcessing event: " + event.getModuleName() + " with start time: " + event.getStartTime() + " and date: " + event.getStartDate());

                    try {
                        if (event.getStartTime() != null && !event.getStartTime().equals("null")) {
                            LocalTime eventStartTime = LocalTime.parse(event.getStartTime(), timeFormatter);
                            LocalDate eventStartDate = LocalDate.parse(event.getStartDate(), dateFormatter);

                            // Calculate time difference more accurately
                            ZonedDateTime eventDateTime = ZonedDateTime.of(eventStartDate, eventStartTime, zoneId);
                            Duration timeUntilEvent = Duration.between(currentTime, eventDateTime);
                            long minutesUntilEvent = timeUntilEvent.toMinutes();

                            System.out.println("Event " + event.getModuleName() + " - Minutes until event: " + minutesUntilEvent);

                            // Set isReady based on reasonable time window
                            // Allow joining 30 minutes before until the event duration
                            if (minutesUntilEvent >= -30 && minutesUntilEvent <= (event.getDuration() != null ? event.getDuration() : 60)) {
                                newEvent.put("isReady", true);
                                System.out.println("Event " + event.getModuleName() + " is READY for joining");
                            } else {
                                newEvent.put("isReady", false);
                                System.out.println("Event " + event.getModuleName() + " is NOT ready - outside time window");
                            }

                            // Add date and time information
                            newEvent.put("eventEndTime", eventStartTime.plusMinutes(event.getDuration() != null ? event.getDuration() : 60));
                            newEvent.put("startTime", eventStartTime);
                            newEvent.put("startDate", eventStartDate.format(dateFormatter)); // Add formatted date
                            newEvent.put("startDateTime", eventDateTime.toString()); // Add full datetime as string

                            // You can also add a formatted display date if needed
                            DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy");
                            newEvent.put("displayDate", eventStartDate.format(displayFormatter));

                        } else {
                            System.out.println("Event " + event.getModuleName() + " has invalid start time");
                            newEvent.put("isReady", false);
                            newEvent.put("startTime", "TBD");
                            newEvent.put("eventEndTime", "TBD");
                            newEvent.put("startDate", event.getStartDate()); // Still pass the original date string
                            newEvent.put("displayDate", "Date TBD");
                        }
                    } catch (Exception e) {
                        System.err.println("Error processing event time for " + event.getModuleName() + ": " + e.getMessage());
                        newEvent.put("isReady", false);
                        newEvent.put("startTime", "TBD");
                        newEvent.put("eventEndTime", "TBD");
                        newEvent.put("startDate", event.getStartDate()); // Pass original date even on error
                        newEvent.put("displayDate", "Date TBD");
                    }
                    return newEvent;
                }).toList();

        // Process upcoming meetings with date information
        List<Map<String, Object>> newUpcomingMeetings = upcomingMeetings.stream()
                .map(meeting -> {
                    Map<String, Object> newMeeting = mapper.convertValue(meeting, Map.class);
                    System.out.println("\nProcessing meeting: " + meeting.getModuleName() + " with start time: " + meeting.getStartTime() + " and date: " + meeting.getStartDate());

                    try {
                        if (meeting.getStartTime() != null && !meeting.getStartTime().equals("null")) {
                            LocalTime meetingStartTime = LocalTime.parse(meeting.getStartTime(), timeFormatter);
                            LocalDate meetingStartDate = LocalDate.parse(meeting.getStartDate(), dateFormatter);

                            // Calculate time difference more accurately (using the same logic as events)
                            ZonedDateTime meetingDateTime = ZonedDateTime.of(meetingStartDate, meetingStartTime, zoneId);
                            Duration timeUntilMeeting = Duration.between(currentTime, meetingDateTime);
                            long minutesUntilMeeting = timeUntilMeeting.toMinutes();

                            System.out.println("Meeting " + meeting.getModuleName() + " - Minutes until meeting: " + minutesUntilMeeting);

                            // Set isReady based on reasonable time window
                            // Allow joining 30 minutes before until the meeting duration
                            if (minutesUntilMeeting >= -30 && minutesUntilMeeting <= (meeting.getDuration() != null ? meeting.getDuration() : 60)) {
                                newMeeting.put("isReady", true);
                                System.out.println("Meeting " + meeting.getModuleName() + " is READY for joining");
                            } else {
                                newMeeting.put("isReady", false);
                                System.out.println("Meeting " + meeting.getModuleName() + " is NOT ready - outside time window");
                            }

                            // Add date and time information
                            newMeeting.put("eventEndTime", meetingStartTime.plusMinutes(meeting.getDuration() != null ? meeting.getDuration() : 60));
                            newMeeting.put("startTime", meetingStartTime);
                            newMeeting.put("startDate", meetingStartDate.format(dateFormatter)); // Add formatted date
                            newMeeting.put("startDateTime", meetingDateTime.toString()); // Add full datetime as string

                            // You can also add a formatted display date if needed
                            DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy");
                            newMeeting.put("displayDate", meetingStartDate.format(displayFormatter));

                        } else {
                            System.out.println("Meeting " + meeting.getModuleName() + " has invalid start time");
                            newMeeting.put("isReady", false); // Changed to false for consistency
                            newMeeting.put("startTime", "TBD");
                            newMeeting.put("eventEndTime", "TBD");
                            newMeeting.put("startDate", meeting.getStartDate()); // Still pass the original date string
                            newMeeting.put("displayDate", "Date TBD");
                        }
                    } catch (Exception e) {
                        System.err.println("Error processing meeting time for " + meeting.getModuleName() + ": " + e.getMessage());
                        newMeeting.put("isReady", false); // Changed to false for consistency
                        newMeeting.put("startTime", "TBD");
                        newMeeting.put("eventEndTime", "TBD");
                        newMeeting.put("startDate", meeting.getStartDate()); // Pass original date even on error
                        newMeeting.put("displayDate", "Date TBD");
                    }
                    return newMeeting;
                }).toList();

        // Handle posts based on mycommunity selection
        if (selectedCommunityId != null) {
            // Community-specific content
            System.out.println("Loading mycommunity-specific content for mycommunity ID: " + selectedCommunityId);
            System.out.println("Selected mycommunity name: " + selectedCommunityName);
            System.out.println("User is admin: " + authenticationManager.isAdmin());

            // Always use getPostsFromUserCommunities for consistent filtering logic
            model.addAttribute("allPosts", postService.getPostsFromUserCommunities(currentUserId, selectedCommunityId));
            model.addAttribute("selectedCommunityName", selectedCommunityName);
            model.addAttribute("selectedCommunityId", selectedCommunityId);
        } else {
            // Universal content from all communities
            System.out.println("Loading universal content from all communities");
            System.out.println("User is admin: " + authenticationManager.isAdmin());

            // Always use getPostsFromUserCommunities for consistent filtering logic
            model.addAttribute("allPosts", postService.getPostsFromUserCommunities(currentUserId));
            model.addAttribute("selectedCommunityName", null);
            model.addAttribute("selectedCommunityId", null);
        }

        if(authenticationManager.isSupportAdmin()){
            return "redirect:/support/dashboard";
        }
        Object communityResponse = communityService.getCommunity();
        Map<String, Object> normalizedCommunity = extractCommunityData(communityResponse);
        Long communityId = (Long) normalizedCommunity.get("id");
        List<Map<String, Object>> processedGroups = new ArrayList<>();
        try {
            List<CreateChatDTO> groupsResponse = communityGroupService.getCommunityGroupsByCommunityId(communityId);
            System.out.println("Groups response: " + groupsResponse);
            System.out.println("Groups response type: " + (groupsResponse != null ? groupsResponse.getClass().getName() : "null"));

            if (groupsResponse != null) {
                for (CreateChatDTO group : groupsResponse) {
                    Map<String, Object> processedGroup = new HashMap<>();

                    // Use the actual database ID from the DTO
                    processedGroup.put("id", group.getId() != null ? group.getId() : 0L);
                    processedGroup.put("groupName", group.getGroupName() != null ? group.getGroupName() : "Unknown Group");
                    processedGroup.put("groupDescription", group.getGroupDescription() != null ? group.getGroupDescription() : "No description");
                    processedGroup.put("memberCount", group.getMemberCount() != null ? group.getMemberCount() : 0);

                    processedGroups.add(processedGroup);
                }
            }
        } catch (Exception e) {
            System.out.println("Error getting groups: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("This is the total number of mycommunity " + normalizedCommunity.size());
        System.out.println("This is the total number of groups " + processedGroups.size());


        request.getSession(true).setAttribute("isAdmin", authenticationManager.isAdmin());
        request.getSession(true).setAttribute("isSupportAdmin", authenticationManager.isSupportAdmin());
        request.getSession().setAttribute("userId", authenticationManager.get("sub"));
        System.out.println("Normalized Community number: " + normalizedCommunity.size());

        // Get user's communities for correct count
        List<Map<String, Object>> userCommunities = communityService.getUserCommunities(currentUserId);

        // Add attributes to the model
        model.addAttribute("myNetwork", networkService.getChatGroupUsers(currentUserId).size());
        model.addAttribute("community", userCommunities.size()); // Fixed: use user's communities count
        model.addAttribute("groups", processedGroups.size());
        model.addAttribute("userId",authenticationManager.get("sub"));
        model.addAttribute("usersName", authenticationManager.get("name"));
        model.addAttribute("upcomingEventNum", upcomingEvents.size());
        model.addAttribute("upcomingEvents", newUpcomingEvents);
        model.addAttribute("upcomingMeetingNum", upcomingMeetings.size());
        model.addAttribute("upcomingMeetings", newUpcomingMeetings);
        model.addAttribute("currentTime", currentTime);
        model.addAttribute("isSupportAdmin", authenticationManager.isSupportAdmin());
        model.addAttribute("isAdmin",authenticationManager.isAdmin());
        // Check if user can post - community-aware check
        boolean canUserPost;
        if (selectedCommunityId != null) {
            // When viewing a specific community, check if user is admin/creator of that community
            canUserPost = postService.canUserPostToCommunity(currentUserId, selectedCommunityId);
        } else {
            // Universal view - check if user is admin/creator of any community
            canUserPost = postService.canUserPost(currentUserId);
        }
        System.out.println("HomeController - User " + currentUserId + " can post: " + canUserPost);
        System.out.println("HomeController - User communities count: " + userCommunities.size());

        model.addAttribute("canUserPost", canUserPost);
        model.addAttribute("userCommunities", userCommunities);
        request.getSession(true).setAttribute("loggedInUser", authenticationManager.get("name"));

        System.out.println("=== END HOME CONTROLLER DEBUG ===");
        return "home";
    }
    @PostMapping("/create-post")
    public String handlePost(
            @RequestParam("content") String content,
            @RequestParam(value = "image", required = false) MultipartFile image,
            HttpServletRequest request,
            Model model
    ) {
        String currentUserId = (String) authenticationManager.get("sub");
        System.out.println("Post content: " + content);

        // Get selected community context
        Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");

        // Check if user can post (community-aware)
        boolean canPost;
        if (selectedCommunityId != null) {
            canPost = postService.canUserPostToCommunity(currentUserId, selectedCommunityId);
        } else {
            canPost = postService.canUserPost(currentUserId);
        }
        if (!canPost) {
            model.addAttribute("errorMessage", "Only community administrators can create posts.");
            return "error-fragment"; // Return error fragment
        }

        if (image != null && !image.isEmpty()) {
            System.out.println("Uploaded file: " + image.getOriginalFilename());
        } else {
            System.out.println("No file uploaded.");
        }

        try {

            PostMessage postMessage = new PostMessage(content, currentUserId, image);
            if (selectedCommunityId != null) {
                postMessage.setPostLevel("COMMUNITY");
                postMessage.setPostLevelId(selectedCommunityId);
            }

            Post createdPost = postService.createPost(postMessage);
            model.addAttribute("newPost", createdPost);
        } catch (IllegalStateException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "error-fragment"; // Return error fragment
        }

        return ""; // Return a fragment
    }

    @PostMapping("/api/chat/send")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleChatMessage(@RequestParam("message") String message) {
        Map<String, Object> response = new HashMap<>();
        try {
            // Send it using Feign client
            String aiResponse = aISupportService.supportChat(message, (String) authenticationManager.get("sub"));
            response.put("status", "success");
            response.put("response", aiResponse);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace(); // log for debugging
            response.put("status", "error");
            response.put("response", "Sorry, something went wrong.");
            return ResponseEntity.status(500).body(response);
        }
    }
    @PostMapping("/post-comment")
    public String postComment(@RequestParam("comment") String comment,
                              @RequestParam("postId") String postId,
                              Model model) {
        // Get current user from security context
        String currentUserId = (String) authenticationManager.get("sub");
        // Build DTO
        CommentDTO commentDTO = new CommentDTO();
        commentDTO.setComment(comment);
        commentDTO.setPostId(Long.valueOf(postId));
        commentDTO.setUserId(currentUserId);

        // Save comment
        postService.createComment(commentDTO);
        model.addAttribute("selectPostID",postId);
        model.addAttribute("refreshComment",postService.getComments(Long.valueOf(postId)));
        return "";
    }
}
