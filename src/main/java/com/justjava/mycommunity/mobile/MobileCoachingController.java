package com.justjava.mycommunity.mobile;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.dto.CreatSessionVO;
import com.justjava.mycommunity.chat.dto.CreateModuleDTO;
import com.justjava.mycommunity.chat.dto.SessionDTO;
import com.justjava.mycommunity.event.EventService;
import com.justjava.mycommunity.module.ModuleService;
import com.justjava.mycommunity.organization.Organization;
import com.justjava.mycommunity.organization.OrganizationService;
import com.justjava.mycommunity.userManagement.UserDTO;
import com.justjava.mycommunity.userManagement.UserService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/mobile")
public class MobileCoachingController {
    private final OrganizationService organizationService;
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final EventService eventService;
    private final ModuleService moduleService;

    @Value("${sendgrid.from-email}")
    String fromEmail;

    @Value("${sendgrid.api-key}")
    String apiKey;

    public MobileCoachingController(OrganizationService organizationService, UserService userService,
                                    AuthenticationManager authenticationManager, EventService eventService,
                                    ModuleService moduleService) {
        this.organizationService = organizationService;
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.eventService = eventService;
        this.moduleService = moduleService;
    }

    @GetMapping("/sessions")
    public String listSession(Model model){
        Object sessionsObj = eventService.getSessions();
        List<SessionDTO> sessions;

        if (sessionsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<SessionDTO> rawSessions = (List<SessionDTO>) sessionsObj;

            System.out.println("=== DEBUGGING MOBILE COACHING SESSION SORTING ===");
            System.out.println("Total sessions retrieved: " + rawSessions.size());

            // Debug: Print all session dates before sorting
            for (SessionDTO session : rawSessions) {
                System.out.println("Session ID: " + session.getId() +
                        ", Title: " + session.getModuleName() +
                        ", Start Date: '" + session.getStartDate() + "'");
            }

            // Sort sessions by date + time + ID in descending order (most recent first)
            sessions = rawSessions.stream()
                    .sorted((s1, s2) -> {
                        try {
                            // First, compare by date
                            String date1Str = s1.getStartDate();
                            String date2Str = s2.getStartDate();

                            if (date1Str == null || date1Str.trim().isEmpty()) {
                                System.out.println("Session " + s1.getId() + " has null/empty start date");
                                return 1; // Put null dates at the end
                            }
                            if (date2Str == null || date2Str.trim().isEmpty()) {
                                System.out.println("Session " + s2.getId() + " has null/empty start date");
                                return -1; // Put null dates at the end
                            }

                            LocalDate date1 = parseDate(date1Str, s1.getId());
                            LocalDate date2 = parseDate(date2Str, s2.getId());

                            if (date1 == null && date2 == null) {
                                // If both dates are null, sort by ID descending (newest first)
                                return Long.compare(s2.getId(), s1.getId());
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
                            String time1Str = s1.getStartTime();
                            String time2Str = s2.getStartTime();

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
                                    System.err.println("Error parsing times for sessions " + s1.getId() + " and " + s2.getId());
                                }
                            }

                            // If dates and times are equal, sort by ID descending (newest created first)
                            int idComparison = Long.compare(s2.getId(), s1.getId());
                            System.out.println("ID comparison: " + s1.getId() + " vs " + s2.getId() + " = " + idComparison);
                            return idComparison;

                        } catch (Exception e) {
                            System.err.println("Error comparing sessions " + s1.getId() + " and " + s2.getId() + ": " + e.getMessage());
                            // Fallback to ID comparison
                            return Long.compare(s2.getId(), s1.getId());
                        }
                    })
                    .collect(Collectors.toList());

            // Debug: Print sorted order
            System.out.println("=== AFTER SORTING ===");
            for (int i = 0; i < sessions.size(); i++) {
                SessionDTO session = sessions.get(i);
                System.out.println((i+1) + ". Session ID: " + session.getId() +
                        ", Title: " + session.getModuleName() +
                        ", Start Date: '" + session.getStartDate() + "'");
            }

        } else {
            sessions = new ArrayList<>();
            System.out.println("No sessions found or invalid format");
        }

        System.out.println("Final sorted list has " + sessions.size() + " sessions");
        model.addAttribute("sessions", sessions);
        model.addAttribute("modules", moduleService.getModules());
        return "coaching/mobile-allSession";
    }

    private LocalDate parseDate(String dateStr, Long sessionId) {
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
                System.out.println("Successfully parsed date '" + dateStr + "' for session " + sessionId + " using format " + format + " -> " + date);
                return date;
            } catch (Exception e) {
                // Try next format
            }
        }

        System.err.println("Could not parse date '" + dateStr + "' for session " + sessionId + " with any known format");
        return null;
    }

    @GetMapping("/create-session")
    public String createSession(Model model, HttpServletRequest request){
        // Get selected community context from session
        Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");
        String selectedCommunityName = (String) request.getSession().getAttribute("selectedCommunityName");

        // Require community context — redirect to organizations if not in a community
        if (selectedCommunityId == null) {
            request.getSession().setAttribute("redirectAfterSelect", "/mobile/create-session");
            return "redirect:/mobile/organizations";
        }

        model.addAttribute("modules", moduleService.getModules());
        model.addAttribute("selectedCommunityId", selectedCommunityId);
        model.addAttribute("selectedCommunityName", selectedCommunityName);
        return "coaching/mobile-createSession";
    }

    @GetMapping("/add-model")
    public String addModel(){
        return "coaching/mobile-addModel";
    }

    @GetMapping("/recorded-session/{sessionId}")
    public String recordedSession(@PathVariable Long sessionId, Model model) {
        com.justjava.mycommunity.chat.dto.SessionDTO session = eventService.getSessionById(sessionId);
        model.addAttribute("singleSession", session);
        return "coaching/mobile-recordedSession";
    }

    @PostMapping("/set-aS-completed")
    public String setASCompleted(@RequestParam Long sessionId) {
        com.justjava.mycommunity.chat.dto.SessionDTO singleSession = eventService.getSessionById(sessionId);
        singleSession.setStatus("Completed");
        return "redirect:/mobile/sessions";
    }

    @GetMapping("/certificate")
    public String certificatePage(Model model) {
        String currentUserId = (String) authenticationManager.get("sub");
        List<com.justjava.mycommunity.chat.dto.SessionDTO> allSessions = eventService.getUserCoachingSessions(currentUserId);
        List<com.justjava.mycommunity.chat.dto.SessionDTO> completedSessions = allSessions.stream().filter(s -> "Completed".equalsIgnoreCase(s.getStatus())).toList();
        List<com.justjava.mycommunity.chat.dto.SessionDTO> upcomingSessions = allSessions.stream().filter(s -> !"Completed".equalsIgnoreCase(s.getStatus())).toList();
        List<com.justjava.mycommunity.chat.dto.SessionDTO> certificateSessions = allSessions.stream().filter(com.justjava.mycommunity.chat.dto.SessionDTO::isHasCertificates).toList();
        List<com.justjava.mycommunity.chat.dto.SessionDTO> certificateCompletedSessions = completedSessions.stream().filter(com.justjava.mycommunity.chat.dto.SessionDTO::isHasCertificates).toList();
        List<com.justjava.mycommunity.chat.dto.SessionDTO> certificateUpcomingSessions = upcomingSessions.stream().filter(com.justjava.mycommunity.chat.dto.SessionDTO::isHasCertificates).toList();
        model.addAttribute("completedSessionsCount", certificateCompletedSessions.size());
        model.addAttribute("upcomingSessionsCount", certificateUpcomingSessions.size());
        model.addAttribute("sessionsCount", certificateSessions.size());
        model.addAttribute("sessions", certificateSessions);
        model.addAttribute("completedSessions", certificateCompletedSessions);
        model.addAttribute("upcomingSessions", certificateUpcomingSessions);
        return "coaching/mobile-certificate";
    }

    @GetMapping("/certificate/preview/{id}")
    public String certificatePreview(@PathVariable Long id, Model model) {
        try {
            com.justjava.mycommunity.chat.dto.SessionDTO session = eventService.getSessionById(id);
            model.addAttribute("session", session);
            model.addAttribute("certificateHtml", session.getCertificateHtml());
        } catch (Exception e) {
            model.addAttribute("error", "Certificate not found.");
        }
        return "certificate/mobile-certificate-preview";
    }

    @PostMapping(value = "/module/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String createModule(
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("price") Double price,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            CreateModuleDTO dto = new CreateModuleDTO(name, description, price);
            if (file != null && !file.isEmpty()) {
                dto.setFile(file.getBytes());
            }
            moduleService.createModule(dto);
            return "redirect:/mobile/create-session";
        } catch (EntityNotFoundException e) {
            e.printStackTrace();
            return "error-page";
        } catch (Exception e) {
            e.printStackTrace();
            return "error-page";
        }
    }

    @PostMapping("/create-session")
    public String createSession(@ModelAttribute CreatSessionVO createSessionVO, HttpServletRequest request) {
        System.out.println("===== Mobile Form Submission =====");
        List<String> allUserIds = createSessionVO.getUsers();

        Map<String, Object> variables = new HashMap<>();

        List<Map<String, Object>> toEmail = allUserIds.stream()
                .map(userId -> {
                    UserDTO singleUser = userService.getSingleUser(userId);
                    Map<String, Object> emailToObj = new HashMap<>();
                    emailToObj.put("email", singleUser.getEmail());
                    return emailToObj ;
                }).toList();

        Map<String, Object> fromEmailObj = new HashMap<>();
        fromEmailObj.put("email", fromEmail);
        apiKey = "Bearer " + apiKey;

        String loginUser = (String) authenticationManager.get("sub");
        String subject = "Session Created";
        String emailBody = "Session has been created successfully!!!";

        variables.put("toEmail", toEmail);
        variables.put("fromEmail", fromEmailObj);
        variables.put("subject", subject);
        variables.put("emailBody", emailBody);
        variables.put("apiKey", apiKey);

        // Get selected community ID from session for community-specific session creation
        Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");
        String selectedCommunityName = (String) request.getSession().getAttribute("selectedCommunityName");

        Organization organization = organizationService.createDefault();
        System.out.println(createSessionVO + "This is the mobile session created");

        if (selectedCommunityId != null) {
            eventService.createSessionForOrganization(createSessionVO, organization, selectedCommunityId);
            System.out.println("Mobile session created for community: " + selectedCommunityName + " (ID: " + selectedCommunityId + ")");
        } else {
            eventService.createSessionForOrganization(createSessionVO, organization);
            System.out.println("Mobile session created as general session (no specific community)");
        }

        return "redirect:/mobile/sessions";
    }
}
