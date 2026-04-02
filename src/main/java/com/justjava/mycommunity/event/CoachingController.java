package com.justjava.mycommunity.event;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.dto.CreatSessionVO;
import com.justjava.mycommunity.chat.dto.CreateModuleDTO;
import com.justjava.mycommunity.chat.dto.SessionDTO;
import com.justjava.mycommunity.cloudinary.CloudinaryService;
import com.justjava.mycommunity.module.ModuleService;
import com.justjava.mycommunity.organization.Organization;
import com.justjava.mycommunity.organization.OrganizationService;
import com.justjava.mycommunity.userManagement.UserDTO;
import com.justjava.mycommunity.userManagement.UserService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
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
@RequestMapping
public class CoachingController {
    @Autowired
    private CloudinaryService cloudinaryService;
    private final OrganizationService organizationService;
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final EventService eventService;
    private final ModuleService moduleService;

    @Value("${sendgrid.from-email}")
    String fromEmail;

    @Value("${sendgrid.api-key}")
    String apiKey;

    public CoachingController(OrganizationService organizationService, UserService userService,
                              AuthenticationManager authenticationManager,
                              EventService eventService, ModuleService moduleService) {
        this.organizationService = organizationService;
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.eventService = eventService;
        this.moduleService = moduleService;
    }

    @GetMapping("/api/session-participants")
    @ResponseBody
    public ResponseEntity<List<UserDTO>> getEligibleSessionParticipants(
            @RequestParam(value = "communityId", required = false) Long communityId) {
        try {
            System.out.println("Getting eligible session participants for mycommunity: " + communityId);
            List<UserDTO> eligibleUsers = organizationService.getEligibleMeetingParticipants(communityId);
            System.out.println("Found " + eligibleUsers.size() + " eligible participants");
            return ResponseEntity.ok(eligibleUsers);
        } catch (Exception e) {
            System.err.println("Error getting eligible session participants: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(new ArrayList<>());
        }
    }

    @PostMapping("/api/upload-image")
    public ResponseEntity<Map<String, Object>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type) {

        Map<String, Object> response = new HashMap<>();

        try {
            // Validate file
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("message", "File is empty");
                return ResponseEntity.badRequest().body(response);
            }

            // Validate file type
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                response.put("success", false);
                response.put("message", "Only image files are allowed");
                return ResponseEntity.badRequest().body(response);
            }

            // Validate file size (2MB limit)
            if (file.getSize() > 2 * 1024 * 1024) {
                response.put("success", false);
                response.put("message", "File size must be less than 2MB");
                return ResponseEntity.badRequest().body(response);
            }

            // Determine folder name based on type
            String folderName;
            if ("signature".equals(type)) {
                folderName = "certificates/signatures";
            } else {
                folderName = "certificates/logos";
            }

            // Upload to Cloudinary using your existing service
            String publicUrl = cloudinaryService.uploadFile(file, folderName);

            if (publicUrl == null) {
                response.put("success", false);
                response.put("message", "Failed to upload image to Cloudinary");
                return ResponseEntity.internalServerError().body(response);
            }

            response.put("success", true);
            response.put("publicUrl", publicUrl);
            response.put("message", "Image uploaded successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to upload image: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/sessions")
    public String listSession(Model model){
        Object sessionsObj = eventService.getSessions();
        List<SessionDTO> sessions;

        if (sessionsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<SessionDTO> rawSessions = (List<SessionDTO>) sessionsObj;

            System.out.println("=== DEBUGGING COACHING SESSION SORTING ===");
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
                        ", Start Date: '" + session.getStartDate() +
                        "', has certificate: '" + session.isHasCertificate() + "'");
            }

        } else {
            sessions = new ArrayList<>();
            System.out.println("No sessions found or invalid format");
        }

        System.out.println("Final sorted list has " + sessions.size() + " sessions");
        model.addAttribute("sessions", sessions);
        model.addAttribute("modules", moduleService.getModules());
        return "/coaching/allSession";
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
    public String createSession(Model model, HttpServletRequest request) {
        // Get selected mycommunity context from session
        Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");
        String selectedCommunityName = (String) request.getSession().getAttribute("selectedCommunityName");

        System.out.println("Create session page - Community context:");
        System.out.println("Selected Community ID: " + selectedCommunityId);
        System.out.println("Selected Community Name: " + selectedCommunityName);

        model.addAttribute("modules", moduleService.getModules());
        model.addAttribute("selectedCommunityId", selectedCommunityId);
        model.addAttribute("selectedCommunityName", selectedCommunityName);
        return "/coaching/createSession";
    }

    @GetMapping("/add-model")
    public String addModel(){
        return "/coaching/addModel";
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
            return "redirect:/create-session"; // success redirect
        } catch (EntityNotFoundException e) {
            e.printStackTrace();
            return "error-page"; // or a proper error template
        } catch (Exception e) {
            e.printStackTrace();
            return "error-page"; // generic error view
        }
    }

    @PostMapping("/create-session")
    public String createSession(@ModelAttribute CreatSessionVO createSessionVO,
                                @RequestParam(value = "videoFile", required = false) MultipartFile videoFile,
                                HttpServletRequest request) {
        System.out.println("===== Coaching Session Creation Form Submission =====");

        // Get selected mycommunity ID from session for mycommunity-specific session creation
        Long selectedCommunityId = (Long) request.getSession().getAttribute("selectedCommunityId");
        String selectedCommunityName = (String) request.getSession().getAttribute("selectedCommunityName");

        System.out.println("Creating coaching session with mycommunity context:");
        System.out.println("Selected Community ID: " + selectedCommunityId);
        System.out.println("Selected Community Name: " + selectedCommunityName);

        // Process video file with Cloudinary
        if (videoFile != null && !videoFile.isEmpty()) {
            System.out.println("===== Video File Information =====");
            System.out.println("Video File Name: " + videoFile.getOriginalFilename());
            System.out.println("Video File Size: " + videoFile.getSize() + " bytes");
            System.out.println("Video File Content Type: " + videoFile.getContentType());

            // Convert bytes to MB for better readability
            double fileSizeMB = videoFile.getSize() / (1024.0 * 1024.0);
            System.out.println("Video File Size (MB): " + String.format("%.2f", fileSizeMB) + " MB");

            // Check if file is actually a video
            // In your controller - use the simple version
            if (videoFile.getContentType() != null && videoFile.getContentType().startsWith("video/")) {
                System.out.println("File Type: Valid Video File");

                try {
                    // Use the simple upload method - NO EAGER TRANSFORMATIONS
                    String videoLink = cloudinaryService.uploadVideo(videoFile, "sessions/videos");

                    // OR use the ObjectUtils version:
                    // String videoLink = cloudinaryService.uploadVideoWithObjectUtils(videoFile, "sessions/videos");

                    if (videoLink != null) {
                        System.out.println("Video uploaded successfully to Cloudinary");
                        System.out.println("Generated Video Link: " + videoLink);

                        // Set the generated link back to createSessionVO
                        createSessionVO.setVideoLink(videoLink);

                    } else {
                        System.out.println("ERROR: Failed to upload video to Cloudinary");
                    }

                } catch (Exception e) {
                    System.out.println("ERROR during video upload: " + e.getMessage());
                    e.printStackTrace();
                    return "redirect:/sessions?error=upload_failed";
                }

            } else {
                System.out.println("File Type: WARNING - Not a recognized video format");
                return "redirect:/sessions?error=invalid_file_type";
            }
            System.out.println("===== End Video File Information =====");
        } else {
            System.out.println("No video file uploaded or video file is empty");

            // Check if this is a recorded session without a video
            if ("recorded".equals(createSessionVO.getSessionType())) {
                System.out.println("WARNING: Recorded session selected but no video file provided");
                // You might want to add validation error here
            }
        }

        // Extract and process certificate HTML
        String certificateHtml = createSessionVO.getCertificateHtml();
        if (certificateHtml != null && !certificateHtml.trim().isEmpty()) {
            System.out.println("===== Certificate Information =====");
            System.out.println("Certificate HTML received:");
            System.out.println("Length: " + certificateHtml.length());
            System.out.println("Preview: " + certificateHtml.substring(0, Math.min(200, certificateHtml.length())) + "...");

            // You can save the certificate HTML to database, file system, or process it further
            System.out.println("===== End Certificate Information =====");
        } else {
            System.out.println("No certificate HTML provided or certification disabled");
        }

        // Print session type information
        System.out.println("===== Session Information =====");
        System.out.println("Session Type: " + createSessionVO.getSessionType());
        System.out.println("Module ID: " + createSessionVO.getModuleId());
        System.out.println("Has Certificate: " + createSessionVO.getHasCertificate());
        System.out.println("Description: " + createSessionVO.getDescription());
        System.out.println("Start Date: " + createSessionVO.getStartDate());
        System.out.println("Start Time: " + createSessionVO.getStartTime());
        System.out.println("Duration: " + createSessionVO.getDuration());
        System.out.println("Video Link: " + createSessionVO.getVideoLink()); // This will show the Cloudinary URL

        List<String> allUserIds = createSessionVO.getUsers();
        System.out.println("Number of Participants: " + (allUserIds != null ? allUserIds.size() : 0));
        if (allUserIds != null && !allUserIds.isEmpty()) {
            System.out.println("Participant IDs: " + String.join(", ", allUserIds));
        }
        System.out.println("===== End Session Information =====");

        Map<String, Object> variables = new HashMap<>();

        List<Map<String, Object>> toEmail = allUserIds.stream()
                .map(userId -> {
                    UserDTO singleUser = userService.getSingleUser(userId);
                    Map<String, Object> emailToObj = new HashMap<>();
                    emailToObj.put("email", singleUser.getEmail());
                    return emailToObj;
                }).toList();

        Map<String, Object> fromEmailObj = new HashMap<>();
        fromEmailObj.put("email", fromEmail);
        apiKey = "Bearer " + apiKey;

        String loginUser = (String) authenticationManager.get("sub");

        String subject = "Session Created";

        // Update email body to include video link if available
        String emailBody = "Session has been created successfully!!!";
        if (createSessionVO.getVideoLink() != null && !createSessionVO.getVideoLink().isEmpty()) {
            emailBody += "\n\nVideo URL: " + createSessionVO.getVideoLink();
        }

        variables.put("toEmail", toEmail);
        variables.put("fromEmail", fromEmailObj);
        variables.put("subject", subject);
        variables.put("emailBody", emailBody);
        variables.put("apiKey", apiKey);

        // Create organization and session (without module requirement for recorded sessions)
        Organization organization = organizationService.createDefault();
        System.out.println("Full CreateSessionVO object: " + createSessionVO);

        if (selectedCommunityId != null) {
            // Create session for specific mycommunity
            eventService.createSessionForOrganization(createSessionVO, organization, selectedCommunityId);
            System.out.println("Coaching session created for mycommunity: " + selectedCommunityName + " (ID: " + selectedCommunityId + ")");
        } else {
            // Create general session
            eventService.createSessionForOrganization(createSessionVO, organization);
            System.out.println("Coaching session created as general session (no specific mycommunity)");
        }

        return "redirect:/sessions";
    }
    @GetMapping("/certificate")
    public String certificatePage(Model model) {
        String currentUserId = (String) authenticationManager.get("sub");
        // Fetch all sessions from the service
        List<SessionDTO> allSessions = eventService.getUserCoachingSessions(currentUserId);

        List <SessionDTO> completedSessions = allSessions.stream().filter(SessionDTO::isCompleted).toList();

        List <SessionDTO> upcomingSessions = allSessions.stream().
                filter(session -> !session.isCompleted()).toList();
        // Filter only the ones with hasCertificate == true
        List<SessionDTO> certificateSessions = allSessions.stream()
                .filter(SessionDTO::isHasCertificates)
                .collect(Collectors.toList());
        List <SessionDTO> certificateCompletedSessions = completedSessions.stream()
                .filter(SessionDTO::isHasCertificates)
                .toList();
        List <SessionDTO> certificateUpcomingSessions = upcomingSessions.stream()
                .filter(SessionDTO::isHasCertificates)
                .toList();
        System.out.println("Filtered certificate sessions: " + certificateSessions.size());
        // Attach filtered list to model
        model.addAttribute("completedSessionsCount", certificateCompletedSessions.size());
        model.addAttribute("upcomingSessionsCount", certificateUpcomingSessions.size());
        model.addAttribute("sessionsCount", certificateSessions.size());
        model.addAttribute("sessions", certificateSessions);
        model.addAttribute("completedSessions", certificateCompletedSessions);
        model.addAttribute("upcomingSessions", certificateUpcomingSessions);
        return "/certificate/certificate";
    }
    @GetMapping("/certificate/preview/{id}")
    @ResponseBody
    public String previewCertificate(@PathVariable("id") Long sessionId) {
        try {
            SessionDTO session = eventService.getSessionById(sessionId);

            if (session == null) {
                return """
            <div class="text-center py-8 text-red-500">
                <span class="material-icons text-4xl mb-2">error</span>
                <p>Certificate not found</p>
            </div>
            """;
            }

            String participantName = (String) authenticationManager.get("name");
            String moduleName = session.getModuleName() != null
                    ? session.getModuleName()
                    : "Training Program";

            String description = session.getDescription() != null
                    ? session.getDescription()
                    : "Successfully completed the training";

            String completionDate = "Not specified";
            if (session.getStartDate() != null) {
                try {
                    LocalDate date = LocalDate.parse(session.getStartDate());
                    completionDate = date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"));
                } catch (Exception ignore) {
                    completionDate = session.getStartDate();
                }
            }

            String certId = "CERT-" + sessionId;

            // Get the certificate HTML from the session
            String certificateHtml = session.getCertificateHtml();

            if (certificateHtml == null || certificateHtml.trim().isEmpty()) {
                return """
            <div class="text-center py-8 text-yellow-500">
                <span class="material-icons text-4xl mb-2">warning</span>
                <p>No certificate template available for this session</p>
            </div>
            """;
            }

            // Replace placeholder text with actual values
            String processedHtml = certificateHtml
                    // Replace participant name placeholders (various formats)
                    .replace("[Participant Name]", participantName)
                    .replace("Participant Name", participantName)
                    .replace("[Student Name]", participantName)
                    .replace("Student Name", participantName)
                    .replace("[Name]", participantName)

                    // Replace module/course name placeholders
                    .replace("[Course Name]", moduleName)
                    .replace("[Module Name]", moduleName)
                    .replace("[Training Name]", moduleName)

                    // Replace completion date placeholders
                    .replace("[Completion Date]", completionDate)
                    .replace("[Date]", completionDate)
                    .replace("[Issue Date]", completionDate)

                    // Replace certificate ID placeholders
                    .replace("[Certificate ID]", certId)
                    .replace("[Cert ID]", certId)
                    .replace("CERT-001", certId)

                    // Replace description if needed
                    .replace("[Description]", description);

            return processedHtml;

        } catch (Exception e) {
            System.out.println("Error generating certificate preview for session ID "
                    + sessionId + ": " + e.getMessage());
            e.printStackTrace();

            return """
        <div class="text-center py-8 text-red-500">
            <span class="material-icons text-4xl mb-2">error_outline</span>
            <h3 class="text-lg font-semibold mb-2">Error Loading Certificate</h3>
            <p class="text-sm">Please try again later</p>
        </div>
        """;
        }
    }

    @GetMapping("/recorded-session/{sessionId}")
    public String recordedSession(@PathVariable Long sessionId, Model model) {
        SessionDTO session = eventService.getSessionById(sessionId);
        System.out.println(session);
        model.addAttribute("singleSession", session);
        return "coaching/recordedSession";
    }
    @PostMapping("/set-aS-completed")
    public String setASCompleted(@RequestParam Long sessionId) {
        SessionDTO singleSession = eventService.getSessionById(sessionId);
        singleSession.setStatus("Completed");


        System.out.println("This is the status- " + singleSession.getStatus());
        return "redirect:/";
    }

}
