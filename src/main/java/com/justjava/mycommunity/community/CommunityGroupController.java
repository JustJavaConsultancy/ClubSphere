package com.justjava.mycommunity.community;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.dto.CreateChatDTO;
import com.justjava.mycommunity.chat.dto.PostDTO;
import com.justjava.mycommunity.chat.dto.SessionDTO;
import com.justjava.mycommunity.chat.model.PostMessage;
import com.justjava.mycommunity.event.EventService;
import com.justjava.mycommunity.posts.PostService;
import com.justjava.mycommunity.userManagement.UserDTO;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Controller
@RequiredArgsConstructor
public class CommunityGroupController {

    private final CommunityService communityService;
    private final CommunityGroupService communityGroupService;
    private final PostService postService;
    private final EventService eventService;
    private final AuthenticationManager authenticationManager;

    @GetMapping("/my-groups")
    public String myGroups(Model model, HttpSession session) {
        // Get current path for sidebar highlighting
        model.addAttribute("currentPath", "/my-groups");

        // Get user session data
        model.addAttribute("session", session);

        try {
            // Get current user ID
            String currentUserId = (String) authenticationManager.get("sub");
            if (currentUserId == null) {
                model.addAttribute("userGroups", List.of());
                model.addAttribute("error", "User not authenticated. Please log in again.");
                return "my-groups";
            }

            System.out.println("=== MY GROUPS DEBUG INFO ===");
            System.out.println("Current User ID: " + currentUserId);

            // Get user groups across all communities
            List<Map<String, Object>> userGroupsAcrossAllCommunities =
                    communityGroupService.getUserCommunityGroupsAcrossAllCommunities(currentUserId);

            System.out.println("=== MY GROUPS CONTROLLER DEBUG ===");
            System.out.println("User groups across all communities: " + userGroupsAcrossAllCommunities.size());

            if (userGroupsAcrossAllCommunities.isEmpty()) {
                System.out.println("No groups found for user. This might indicate:");
                System.out.println("1. User is not added as a member to any groups");
                System.out.println("2. User-group relationship is not properly maintained");
                System.out.println("3. Groups exist but user membership is not recorded");
            } else {
                System.out.println("Groups found:");
                for (Map<String, Object> group : userGroupsAcrossAllCommunities) {
                    System.out.println("- " + group.get("groupName") + " (Community: " + group.get("communityName") + ")");
                }
            }

            // Group the groups by mycommunity for easier template processing
            Map<String, List<Map<String, Object>>> groupedByCommunity = new HashMap<>();
            Set<String> uniqueCommunities = new HashSet<>();

            System.out.println("=== GROUPING COMMUNITIES DEBUG ===");
            System.out.println("Total groups to process: " + userGroupsAcrossAllCommunities.size());

            for (Map<String, Object> group : userGroupsAcrossAllCommunities) {
                String communityName = (String) group.get("communityName");
                Long communityId = (Long) group.get("communityId");

                System.out.println("Processing group: " + group.get("groupName") +
                        " | Community: " + communityName +
                        " | Community ID: " + communityId);

                if (communityName == null || communityName.trim().isEmpty()) {
                    communityName = "Unknown Community";
                }

                // Add to unique communities (excluding Unknown Community)
                if (!communityName.equals("Unknown Community")) {
                    uniqueCommunities.add(communityName);
                    System.out.println("Added to unique communities: " + communityName);
                }

                // Group by mycommunity
                groupedByCommunity.computeIfAbsent(communityName, k -> new ArrayList<>()).add(group);
                System.out.println("Added group to mycommunity '" + communityName + "'");
            }

            System.out.println("=== FINAL GROUPING RESULTS ===");
            System.out.println("Unique communities found: " + uniqueCommunities);
            System.out.println("Grouped by mycommunity keys: " + groupedByCommunity.keySet());

            for (String community : groupedByCommunity.keySet()) {
                List<Map<String, Object>> communityGroups = groupedByCommunity.get(community);
                System.out.println("Community '" + community + "' has " + communityGroups.size() + " groups:");
                for (Map<String, Object> group : communityGroups) {
                    System.out.println("  - Group: " + group.get("groupName") +
                            " | Community in group object: " + group.get("communityName") +
                            " | Community ID: " + group.get("communityId"));
                }
            }

            model.addAttribute("userGroups", userGroupsAcrossAllCommunities);
            model.addAttribute("groupedByCommunity", groupedByCommunity);
            model.addAttribute("totalCommunities", uniqueCommunities.size());
            model.addAttribute("totalGroups", userGroupsAcrossAllCommunities.size());

        } catch (Exception e) {
            model.addAttribute("userGroups", List.of());
            model.addAttribute("error", "Unable to load groups at this time. Please try again later.");
            e.printStackTrace();
        }

        return "my-groups";
    }

    @GetMapping("/group")
    public String groupPage(@RequestParam Long id, Model model, HttpSession session) {
        try {
            // Get current path for sidebar highlighting
            model.addAttribute("currentPath", "/group");

            // Get user session data
            model.addAttribute("session", session);

            String currentUserId = (String) authenticationManager.get("sub");
            boolean isAdmin = authenticationManager.isAdmin();

            // Get the specific group by ID (search across all communities)
            CreateChatDTO currentGroup = null;
            Map<String, Object> normalizedCommunity = null;

            try {
                currentGroup = communityGroupService.getCommunityGroupById(id);

                // Get the mycommunity data for this specific group
                Map<String, Object> groupCommunityInfo = communityGroupService.getCommunityInfoForGroup(id);
                if (groupCommunityInfo != null) {
                    normalizedCommunity = groupCommunityInfo;
                } else {
                    // Fallback to default mycommunity
                    Object communityResponse = communityService.getCommunity();
                    if (communityResponse != null) {
                        normalizedCommunity = extractCommunityData(communityResponse);
                    }
                }

                // If we still couldn't get mycommunity data, create a basic one
                if (normalizedCommunity == null && currentGroup != null) {
                    normalizedCommunity = new HashMap<>();
                    normalizedCommunity.put("id", currentGroup.getCommunityId());
                    normalizedCommunity.put("communityName", "Community");
                    normalizedCommunity.put("communityDescription", "Community Description");
                }
            } catch (Exception e) {
                System.out.println("Error getting group by ID: " + e.getMessage());
                e.printStackTrace();
            }

            if (currentGroup == null) {
                model.addAttribute("error", "Group not found or you don't have access to it.");
                return "group";
            }

            // Verify user has access to this group
            // Admins have access to all groups, regular users only to groups they belong to
            List<Map<String, Object>> userAccessibleGroups = communityGroupService.getUserCommunityGroupsAcrossAllCommunities(currentUserId);
            boolean hasAccess = userAccessibleGroups.stream()
                    .anyMatch(group -> group.get("id").equals(id));

            if (!hasAccess) {
                model.addAttribute("error", "You don't have access to this group.");
                return "group";
            }

            // Get group members
            List<UserDTO> groupMembers = communityGroupService.getCommunityGroupUsers(id);

            // Get group posts
            List<PostDTO> groupPosts = postService.getGroupPosts(id);

            // Get user events (where user is participant) - same logic as HomeController
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
                        .filter(sessionDto -> !userEvents.stream()
                                .anyMatch(userEvent -> userEvent.getId().equals(sessionDto.getId())))
                        .toList();

                allEvents = Stream.concat(userEvents.stream(), adminCreatedEvents.stream()).toList();
                System.out.println("Admin created events added: " + adminCreatedEvents.size());
            }

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
                List<SessionDTO> meetingsObj = eventService.getMeetings();
                List<SessionDTO> allMeetingsList;
                if (meetingsObj instanceof List) {
                    allMeetingsList = meetingsObj;
                } else {
                    allMeetingsList = new java.util.ArrayList<>();
                }

                List<SessionDTO> adminCreatedMeetings = allMeetingsList.stream()
                        .filter(meetingDto -> !userMeetings.stream()
                                .anyMatch(userMeeting -> userMeeting.getId().equals(meetingDto.getId())))
                        .toList();

                allMeetings = Stream.concat(userMeetings.stream(), adminCreatedMeetings.stream()).toList();
                System.out.println("Admin created meetings added: " + adminCreatedMeetings.size());
            }

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

                            // If dates are equal, compare by time
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

            // Add data to model
            model.addAttribute("group", currentGroup);
            model.addAttribute("groupMembers", groupMembers);
            model.addAttribute("groupPosts", groupPosts);
            model.addAttribute("community", normalizedCommunity);
            model.addAttribute("upcomingEvents", newUpcomingEvents);
            model.addAttribute("upcomingMeetings", newUpcomingMeetings);
            model.addAttribute("userId", currentUserId);
            model.addAttribute("isAdmin", isAdmin);

        } catch (Exception e) {
            model.addAttribute("error", "Unable to load group at this time. Please try again later.");
            e.printStackTrace();
        }

        return "group";
    }

    @PostMapping("/group/post")
    @ResponseBody
    public Map<String, Object> createGroupPost(
            @RequestParam Long groupId,
            @RequestParam String content,
            @RequestParam(required = false) MultipartFile file,
            HttpSession httpSession) {

        Map<String, Object> response = new HashMap<>();

        try {
            // Get user ID from session
            String userId = (String) httpSession.getAttribute("userId");
            if (userId == null) {
                response.put("success", false);
                response.put("message", "User not authenticated");
                return response;
            }

            // Create PostMessage with GROUP level and groupId
            PostMessage postMessage = PostMessage.builder()
                    .content(content)
                    .userId(userId)
                    .file(file)
                    .privacy(false) // Group posts are not private
                    .postLevel("GROUP") // Set postLevel to GROUP
                    .postLevelId(groupId) // Set postLevelId to the group ID
                    .build();

            // Create the post
            postService.createPost(postMessage);

            response.put("success", true);
            response.put("message", "Post created successfully");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to create post: " + e.getMessage());
            e.printStackTrace();
        }

        return response;
    }

    @GetMapping("/group/{groupId}")
    public String redirectToGroup(@PathVariable Long groupId) {
        return "redirect:/group?id=" + groupId;
    }

    @GetMapping("/api/group/{groupId}/members")
    @ResponseBody
    public List<UserDTO> getGroupMembers(@PathVariable Long groupId) {
        try {
            // Use the existing CommunityService method to get group members
            return communityGroupService.getCommunityGroupUsers(groupId);
        } catch (Exception e) {
            System.out.println("Error getting group members: " + e.getMessage());
            e.printStackTrace();
            return List.of();
        }
    }

    @GetMapping("/api/group/{groupId}/posts")
    @ResponseBody
    public List<PostDTO> getGroupPosts(@PathVariable Long groupId) {
        try {
            return postService.getGroupPosts(groupId);
        } catch (Exception e) {
            System.out.println("Error getting group posts: " + e.getMessage());
            e.printStackTrace();
            return List.of();
        }
    }

    @GetMapping("/api/group/{groupId}/details")
    @ResponseBody
    public Map<String, Object> getGroupDetails(@PathVariable Long groupId) {
        try {
            // Get the specific group by ID (search across all communities)
            CreateChatDTO group = communityGroupService.getCommunityGroupById(groupId);

            if (group != null) {
                Map<String, Object> result = new HashMap<>();
                result.put("id", group.getId());
                result.put("groupName", group.getGroupName() != null ? group.getGroupName() : "Unknown Group");
                result.put("groupDescription", group.getGroupDescription() != null ? group.getGroupDescription() : "No description available");
                result.put("memberCount", group.getMemberCount() != null ? group.getMemberCount() : 0);

                // Get mycommunity information for this group
                try {
                    Map<String, Object> communityInfo = communityGroupService.getCommunityInfoForGroup(groupId);
                    if (communityInfo != null) {
                        result.put("communityName", communityInfo.get("communityName"));
                        result.put("communityDescription", communityInfo.get("communityDescription"));
                    } else {
                        // Fallback mycommunity info
                        result.put("communityName", "Community");
                        result.put("communityDescription", "Community Description");
                    }
                } catch (Exception e) {
                    System.out.println("Error getting mycommunity info: " + e.getMessage());
                    result.put("communityName", "Community");
                    result.put("communityDescription", "Community Description");
                }

                return result;
            }

            // Group not found
            Map<String, Object> errorDto = new HashMap<>();
            errorDto.put("groupName", "Group not found");
            errorDto.put("groupDescription", "The requested group does not exist or you don't have access to it");
            return errorDto;

        } catch (Exception e) {
            System.out.println("Error getting group details: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> errorDto = new HashMap<>();
            errorDto.put("groupName", "Error");
            errorDto.put("groupDescription", "Unable to load group details. Please try again later.");
            return errorDto;
        }
    }

    // Same method as CommunityController for extracting mycommunity data
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
}
