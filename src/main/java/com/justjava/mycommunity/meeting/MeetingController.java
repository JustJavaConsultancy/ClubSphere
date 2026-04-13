package com.justjava.mycommunity.meeting;

import com.justjava.mycommunity.chat.dto.CreatSessionVO;
import com.justjava.mycommunity.chat.dto.SessionDTO;
import com.justjava.mycommunity.community.CommunityService;
import com.justjava.mycommunity.event.EventService;
import com.justjava.mycommunity.organization.Organization;
import com.justjava.mycommunity.organization.OrganizationService;
import com.justjava.mycommunity.userManagement.UserDTO;
import com.justjava.mycommunity.userManagement.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping
public class MeetingController {
    private final OrganizationService organizationService;
    private final UserService userService;
    private final EventService eventService;
    private final CommunityService communityService;

    @Value("${sendgrid.from-email}")
    String fromEmail;

    @Value("${sendgrid.api-key}")
    String apiKey;

    public MeetingController(OrganizationService organizationService, UserService userService,
                             EventService eventService, CommunityService communityService) {
        this.organizationService = organizationService;
        this.userService = userService;
        this.eventService = eventService;
        this.communityService = communityService;
    }

    @GetMapping("/meetings")
    public String listMeetings(Model model) {
        Object meetingsObj = eventService.getMeetings();
        List<SessionDTO> meetings;

        if (meetingsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<SessionDTO> rawMeetings = (List<SessionDTO>) meetingsObj;

            System.out.println("=== DEBUGGING MEETING SORTING ===");
            System.out.println("Total meetings retrieved: " + rawMeetings.size());

            // Debug: Print all meeting dates before sorting
            for (SessionDTO meeting : rawMeetings) {
                System.out.println("Meeting ID: " + meeting.getId() +
                        ", Title: " + meeting.getModuleName() +
                        ", Start Date: '" + meeting.getStartDate() + "'");
            }

            // Sort meetings by date + time + ID in descending order (most recent first)
            meetings = rawMeetings.stream()
                    .sorted((m1, m2) -> {
                        try {
                            // First, compare by date
                            String date1Str = m1.getStartDate();
                            String date2Str = m2.getStartDate();

                            if (date1Str == null || date1Str.trim().isEmpty()) {
                                System.out.println("Meeting " + m1.getId() + " has null/empty start date");
                                return 1; // Put null dates at the end
                            }
                            if (date2Str == null || date2Str.trim().isEmpty()) {
                                System.out.println("Meeting " + m2.getId() + " has null/empty start date");
                                return -1; // Put null dates at the end
                            }

                            LocalDate date1 = parseDate(date1Str, m1.getId());
                            LocalDate date2 = parseDate(date2Str, m2.getId());

                            if (date1 == null && date2 == null) {
                                // If both dates are null, sort by ID descending (newest first)
                                return Long.compare(m2.getId(), m1.getId());
                            }
                            if (date1 == null) return 1;
                            if (date2 == null) return -1;

                            // Compare dates first
                            int dateComparison = date2.compareTo(date1); // Descending order
                            if (dateComparison != 0) {
                                System.out.println("Date comparison: " + date1 + " vs " + date2 + " = " + dateComparison);
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
                                        System.out.println("Time comparison: " + time1 + " vs " + time2 + " = " + timeComparison);
                                        return timeComparison;
                                    }
                                } catch (Exception e) {
                                    System.err.println("Error parsing times for meetings " + m1.getId() + " and " + m2.getId());
                                }
                            }

                            // If dates and times are equal, sort by ID descending (newest created first)
                            int idComparison = Long.compare(m2.getId(), m1.getId());
                            System.out.println("ID comparison: " + m1.getId() + " vs " + m2.getId() + " = " + idComparison);
                            return idComparison;

                        } catch (Exception e) {
                            System.err.println("Error comparing meetings " + m1.getId() + " and " + m2.getId() + ": " + e.getMessage());
                            // Fallback to ID comparison
                            return Long.compare(m2.getId(), m1.getId());
                        }
                    })
                    .collect(Collectors.toList());

            // Debug: Print sorted order
            System.out.println("=== AFTER SORTING ===");
            for (int i = 0; i < meetings.size(); i++) {
                SessionDTO meeting = meetings.get(i);
                System.out.println((i+1) + ". Meeting ID: " + meeting.getId() +
                        ", Title: " + meeting.getModuleName() +
                        ", Start Date: '" + meeting.getStartDate() + "'");
            }

        } else {
            meetings = new ArrayList<>();
            System.out.println("No meetings found or invalid format");
        }

        System.out.println("Final sorted list has " + meetings.size() + " meetings");
        model.addAttribute("meetings", meetings);
        return "meeting/allMeetings";
    }

    private LocalDate parseDate(String dateStr, Long meetingId) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        // Try different date formats
        String[] formats = {
                "yyyy-MM-dd",
                "MM/dd/yyyy",
                "dd/MM/yyyy",
                "yyyy/MM/dd"
        };

        for (String format : formats) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
                LocalDate date = LocalDate.parse(dateStr.trim(), formatter);
                System.out.println("Successfully parsed date '" + dateStr + "' for meeting " + meetingId + " using format " + format + " -> " + date);
                return date;
            } catch (Exception e) {
                // Try next format
            }
        }

        System.err.println("Could not parse date '" + dateStr + "' for meeting " + meetingId + " with any known format");
        return null;
    }

    @GetMapping("/create-meeting")
    public String createMeeting(Model model, HttpServletRequest request) {
        // Get selected mycommunity context from session
        Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");
        String selectedCommunityName = (String) request.getSession().getAttribute("selectedCommunityName");

        // Require community context — redirect to organizations if not in a community
        if (selectedCommunityId == null) {
            request.getSession().setAttribute("redirectAfterSelect", "/create-meeting");
            return "redirect:/organizations";
        }

        System.out.println("Create meeting page - Community context:");
        System.out.println("Selected Community ID: " + selectedCommunityId);
        System.out.println("Selected Community Name: " + selectedCommunityName);

        model.addAttribute("selectedCommunityId", selectedCommunityId);
        model.addAttribute("selectedCommunityName", selectedCommunityName);
        return "meeting/createMeeting";
    }

    @GetMapping("/api/meeting-participants")
    @ResponseBody
    public ResponseEntity<List<UserDTO>> getEligibleMeetingParticipants(
            @RequestParam(value = "communityId", required = false) Long communityId) {
        try {
            System.out.println("Getting eligible meeting participants for mycommunity: " + communityId);
            List<UserDTO> eligibleUsers = communityService.getEligibleMeetingParticipants(communityId);
            System.out.println("Found " + eligibleUsers.size() + " eligible participants");
            return ResponseEntity.ok(eligibleUsers);
        } catch (Exception e) {
            System.err.println("Error getting eligible meeting participants: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(new ArrayList<>());
        }
    }

    @PostMapping("/create-meeting")
    public String createMeeting(@ModelAttribute CreatSessionVO createSessionVO, HttpServletRequest request) {
        System.out.println("===== Meeting Creation Form Submission =====");
        List<String> allUserIds = createSessionVO.getUsers();

        if (allUserIds != null && !allUserIds.isEmpty()) {
            sendMeetingNotifications(allUserIds);
        }

        // Get selected mycommunity ID from session for mycommunity-specific meeting creation
        Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");
        String selectedCommunityName = (String) request.getSession().getAttribute("selectedCommunityName");

        System.out.println("Creating meeting with mycommunity context:");
        System.out.println("Selected Community ID: " + selectedCommunityId);
        System.out.println("Selected Community Name: " + selectedCommunityName);

        // Create organization and meeting (without module requirement)
        Organization organization = organizationService.createDefault();
        System.out.println(createSessionVO + " This is the meeting created");

        if (selectedCommunityId != null) {
            // Create meeting for specific mycommunity
            eventService.createMeetingForOrganization(createSessionVO, organization, selectedCommunityId);
            System.out.println("Meeting created for mycommunity: " + selectedCommunityName + " (ID: " + selectedCommunityId + ")");
        } else {
            // Create general meeting
            eventService.createMeetingForOrganization(createSessionVO, organization);
            System.out.println("Meeting created as general meeting (no specific mycommunity)");
        }

        return "redirect:/meetings";
    }

    @GetMapping("/api/meetings/{id}")
    @ResponseBody
    public ResponseEntity<Object> getMeeting(@PathVariable Long id) {
        try {
            SessionDTO meetingData = eventService.getMeetingById(id);
            return ResponseEntity.ok(meetingData);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/api/meetings/{id}")
    @ResponseBody
    public ResponseEntity<String> updateMeeting(@PathVariable Long id, @RequestBody CreatSessionVO updateDTO) {
        // Making edit functionality dormant for now due to complexity
        return ResponseEntity.ok("Edit functionality temporarily disabled");
    }

    @PutMapping("/api/meetings/{id}/status")
    @ResponseBody
    public ResponseEntity<String> updateMeetingStatus(@PathVariable Long id, @RequestParam String status) {
        // Making status update functionality dormant for now due to complexity
        return ResponseEntity.ok("Status update functionality temporarily disabled");
    }

    @DeleteMapping("/api/meetings/{id}")
    @ResponseBody
    public ResponseEntity<String> deleteMeeting(@PathVariable Long id) {
        // Making delete functionality dormant for now due to complexity
        return ResponseEntity.ok("Delete functionality temporarily disabled");
    }

    private void sendMeetingNotifications(List<String> userIds) {
        try {
            Map<String, Object> variables = new HashMap<>();

            List<Map<String, Object>> toEmail = userIds.stream()
                    .map(userId -> {
                        UserDTO singleUser = userService.getSingleUser(userId);
                        Map<String, Object> emailToObj = new HashMap<>();
                        emailToObj.put("email", singleUser.getEmail());
                        return emailToObj;
                    }).toList();

            Map<String, Object> fromEmailObj = new HashMap<>();
            fromEmailObj.put("email", fromEmail);
            String processApiKey = "Bearer " + apiKey;

            String subject = "Meeting Scheduled";
            String emailBody = "A new meeting has been scheduled successfully!";

            variables.put("toEmail", toEmail);
            variables.put("fromEmail", fromEmailObj);
            variables.put("subject", subject);
            variables.put("emailBody", emailBody);
            variables.put("apiKey", processApiKey);

            System.out.println("Meeting notifications prepared for " + userIds.size() + " participants");
        } catch (Exception e) {
            System.err.println("Error sending meeting notifications: " + e.getMessage());
        }
    }
}
