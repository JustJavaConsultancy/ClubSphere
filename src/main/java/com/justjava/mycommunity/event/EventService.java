package com.justjava.mycommunity.event;

import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.dto.CreatSessionVO;
import com.justjava.mycommunity.chat.dto.EventDTO;
import com.justjava.mycommunity.chat.dto.SessionDTO;
import com.justjava.mycommunity.chat.entity.User;
import com.justjava.mycommunity.chat.model.PostMessage;
import com.justjava.mycommunity.chat.repository.ChatGroupRepository;
import com.justjava.mycommunity.chat.repository.CommunityRepository;
import com.justjava.mycommunity.chat.repository.EventRepository;
import com.justjava.mycommunity.chat.repository.ModuleRepository;
import com.justjava.mycommunity.chat.repository.OrganizationRepository;
import com.justjava.mycommunity.chat.repository.ParticipantRepository;
import com.justjava.mycommunity.community.Community;
import com.justjava.mycommunity.module.Module;
import com.justjava.mycommunity.network.ChatGroup;
import com.justjava.mycommunity.organization.Organization;
import com.justjava.mycommunity.posts.Post;
import com.justjava.mycommunity.posts.PostService;
import com.justjava.mycommunity.userManagement.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.justjava.mycommunity.util.MappingUtils.mapEventsToDTO;

@Service
@RequiredArgsConstructor
public class EventService {
    private final EventRepository eventRepository;
    private final OrganizationRepository organizationRepository;
    private final CommunityRepository communityRepository;
    private final ChatGroupRepository chatGroupRepository;
    private final UserRepository userRepository;
    private final ParticipantRepository participantRepository;
    private final PostService postService;
    private final AuthenticationManager authenticationManager;
    private final ModuleRepository moduleRepository;

    /**
     * Robust admin check that handles various failure scenarios
     */
    private boolean isUserAdmin(String userId) {
        try {
            // First try the AuthenticationManager
            boolean isAdmin = authenticationManager.isAdmin();
            System.out.println("AuthenticationManager.isAdmin() returned: " + isAdmin);
            return isAdmin;
        } catch (Exception e) {
            System.out.println("AuthenticationManager.isAdmin() failed: " + e.getMessage());

            // Fallback: Check if user has admin role through other means
            try {
                User user = userRepository.findByUserId(userId);
                if (user != null) {
                    // Check if user has admin privileges through user group
                    if (user.getUserGroup() != null && !user.getUserGroup().isEmpty()) {
                        boolean isAdminByGroup = user.getUserGroup().stream()
                                .anyMatch(userGroup -> {
                                    String groupName = userGroup.getGroupName();
                                    return "ADMIN".equalsIgnoreCase(groupName) ||
                                            "ADMINISTRATOR".equalsIgnoreCase(groupName) ||
                                            "SUPER_ADMIN".equalsIgnoreCase(groupName) ||
                                            "admin".equalsIgnoreCase(groupName);
                                });
                        System.out.println("Fallback admin check by user groups: " + isAdminByGroup);
                        return isAdminByGroup;
                    }
                }
            } catch (Exception fallbackEx) {
                System.out.println("Fallback admin check also failed: " + fallbackEx.getMessage());
            }

            // Final fallback: assume not admin if all checks fail
            System.out.println("All admin checks failed, treating user as regular user");
            return false;
        }
    }

    public Event createEvent(EventDTO dto) {
        Organization organization = organizationRepository.findById(dto.getOrganizationId())
                .orElseThrow(() -> new EntityNotFoundException("Organization does not exist"));
        Event event = new Event();
        event.setTitle(dto.getTitle());
        event.setDescription(dto.getDescription());
        event.setOrganization(organization);

        // Set new fields
        if (dto.getCommunityId() != null) {
            Community community = communityRepository.findById(dto.getCommunityId())
                    .orElseThrow(() -> new EntityNotFoundException("Community does not exist"));
            event.setCommunity(community);
        }
        if (dto.getChatGroupId() != null) {
            ChatGroup group = chatGroupRepository.findById(dto.getChatGroupId())
                    .orElseThrow(() -> new EntityNotFoundException("Chat group does not exist"));
            event.setChatGroup(group);
        }
        event = eventRepository.save(event);
        return event;
    }

    public void addUserToEvent(String userId, Long eventId) {
        User user = Optional.ofNullable(userRepository.findByEmail(userId))
                .orElseThrow(() -> new EntityNotFoundException("User does not exist"));
        Event event =  eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event does not exist"));
        Participant participant = new Participant();
        participant.setUser(user);
        participant.setEvent(event);
        participantRepository.save(participant);
    }

    @Transactional
    public SessionDTO createSession(@Valid CreatSessionVO vo) {
        Module module = moduleRepository.findById(vo.getModuleId())
                .orElseThrow(() -> new EntityNotFoundException("Module does not exist"));

        Event event = new Event();
        event.setTitle(module.getName());
        event.setDescription(vo.getDescription());
        event.setModule(module);
        event.setSessionType(vo.getSessionType());
        event.setVideoLink(vo.getVideoLink());
        event.setStartDate(LocalDate.now());
        event.setEndDate(LocalDate.now().plusDays(2));
        event.setStartTime(LocalTime.now());

        // Certificate mapping
        event.setHasCertificate(vo.getHasCertificate());
        if (vo.getHasCertificate() && vo.getCertificateHtml() != null && !vo.getCertificateHtml().trim().isEmpty()) {
            event.setCertificateHtml(vo.getCertificateHtml());
        } else {
            event.setCertificateHtml(null);
        }

        event = eventRepository.save(event);

        List<Participant> participants = new ArrayList<>();
        Set<User> users = userRepository.findAllByUserIdIn(vo.getUsers());
        for (User user : users){
            Participant participant = new Participant();
            participant.setUser(user);
            participant.setEvent(event);
            participants.add(participant);
        }

        participantRepository.saveAll(participants);
        return getSessionEvent(event.getId());
    }

    public void createSessionForOrganization(@Valid CreatSessionVO vo, Organization organization) {
        createSessionForOrganization(vo, organization, null);
    }

    public void createSessionForOrganization(@Valid CreatSessionVO vo, Organization organization, Long communityId) {
        Module module = null;

        // Only require module for live sessions
        if ("live".equals(vo.getSessionType()) && vo.getModuleId() != null) {
            module = moduleRepository.findById(vo.getModuleId())
                    .orElseThrow(() -> new EntityNotFoundException("Module does not exist"));
        } else if ("recorded".equals(vo.getSessionType())) {
            // For recorded sessions, module is optional
            if (vo.getModuleId() != null) {
                try {
                    module = moduleRepository.findById(vo.getModuleId())
                            .orElse(null);
                } catch (Exception e) {
                    System.out.println("Module not found for recorded session, proceeding without module");
                }
            }
        }

        Set<User> users = userRepository.findAllByUserIdIn(vo.getUsers());

        Event event = new Event();

        // Set title based on module availability
        if (module != null) {
            event.setTitle(module.getName());
        } else {
            event.setTitle(vo.getDescription());
        }

        event.setDescription(vo.getDescription());
        event.setModule(module);
        event.setSessionType(vo.getSessionType());
        event.setVideoLink(vo.getVideoLink());
        event.setStartDate(LocalDate.parse(vo.getStartDate()));
        event.setStartTime(LocalTime.parse(vo.getStartTime()));
        event.setEndDate(LocalDate.parse(vo.getStartDate()).plusDays(5));
        event.setDuration(vo.getDuration());
        event.setHasCertificate(vo.getHasCertificate());
        event.setCertificateHtml(vo.getCertificateHtml());
        event.setOrganization(organization);

        // Set mycommunity if provided
        if (communityId != null) {
            Community community = communityRepository.findById(communityId)
                    .orElseThrow(() -> new EntityNotFoundException("Community does not exist"));
            event.setCommunity(community);
            System.out.println("Coaching session created for mycommunity: " + community.getName() + " (ID: " + communityId + ")");
        } else {
            System.out.println("Coaching session created without specific mycommunity (general session)");
        }

        event = eventRepository.save(event);

        Set<Participant> participants = new HashSet<>();
        for (User user : users){
            Participant participant = new Participant();
            participant.setUser(user);
            participant.setEvent(event);
            participants.add(participant);
        }

        List<Participant> participants1 = participantRepository.saveAll(participants);
        event.setParticipants(participants1);

        // Create post for coaching session - handle mycommunity context properly
        PostMessage postMessage = new PostMessage("Coaching session scheduled: " + vo.getDescription(), (String) authenticationManager.get("sub"), null);

        if (communityId != null) {
            // Community-specific coaching session
            postMessage.setPostLevel("COMMUNITY");
            postMessage.setPostLevelId(communityId);
        } else {
            // General coaching session - use COMMUNITY level but with null postLevelId to indicate general post
            postMessage.setPostLevel("COMMUNITY");
            postMessage.setPostLevelId(null);
        }

        Post post = postService.createPost(postMessage);
        event.setPost(post);
        eventRepository.save(event);
    }

    @Transactional
    public List<SessionDTO> getSessions() {
        List<Event> events = eventRepository.findAll().stream()
                .filter(event -> event.getModule() != null)
                .toList();
        return mapEventsToDTO(events);
    }

    @Transactional
    public List<SessionDTO> getSessionsFromUserCommunities(String userId) {
        return getSessionsFromUserCommunities(userId, null);
    }

    @Transactional
    public List<SessionDTO> getSessionsFromUserCommunities(String userId, Long selectedCommunityId) {
        try {
            System.out.println("Getting sessions for user: " + userId + ", mycommunity: " + selectedCommunityId);

            // Check if user is admin using robust method
            boolean isUserAdmin = isUserAdmin(userId);
            System.out.println("User " + userId + " admin status in getSessionsFromUserCommunities: " + isUserAdmin);

            // Get user to find their communities
            User user = userRepository.findByUserId(userId);
            if (user == null) {
                throw new EntityNotFoundException("User does not exist");
            }

            // Force initialization of communities collection
            user.getCommunities().size();

            List<Event> events = new ArrayList<>();

            if (selectedCommunityId != null) {
                // Get sessions from the specific selected mycommunity only
                System.out.println("Getting sessions for specific mycommunity: " + selectedCommunityId);
                List<Event> communityEvents = eventRepository.findAllByCommunity_Id(selectedCommunityId).stream()
                        .filter(event -> event.getModule() != null) // Only coaching sessions
                        .toList();

                // Check if user is member of this mycommunity or is admin
                boolean isMemberOfCommunity = isUserAdmin || user.getCommunities().stream()
                        .anyMatch(community -> community.getId().equals(selectedCommunityId));

                System.out.println("User is member of mycommunity " + selectedCommunityId + ": " + isMemberOfCommunity);
                System.out.println("Found " + communityEvents.size() + " sessions in mycommunity " + selectedCommunityId);

                // Only show sessions if user is a member of the mycommunity or is admin
                if (isMemberOfCommunity) {
                    events.addAll(communityEvents);
                    System.out.println("Added " + communityEvents.size() + " sessions from mycommunity " + selectedCommunityId);
                } else {
                    System.out.println("User is not a member of mycommunity " + selectedCommunityId + ", no sessions added");
                }
            } else {
                // Get sessions from all communities user belongs to ONLY (or all communities if admin)
                System.out.println("Getting sessions for all user communities");

                if (isUserAdmin) {
                    // Admin sees all coaching sessions
                    List<Event> allCoachingSessions = eventRepository.findAll().stream()
                            .filter(event -> event.getModule() != null) // Only coaching sessions
                            .toList();
                    events.addAll(allCoachingSessions);
                    System.out.println("Admin: Added " + allCoachingSessions.size() + " sessions from all communities");
                } else {
                    // Regular user sees only sessions from their communities
                    for (var community : user.getCommunities()) {
                        List<Event> communityEvents = eventRepository.findAllByCommunity_Id(community.getId()).stream()
                                .filter(event -> event.getModule() != null) // Only coaching sessions
                                .toList();
                        events.addAll(communityEvents);
                        System.out.println("Added " + communityEvents.size() + " sessions from mycommunity " + community.getName());
                    }

                    // Also include general sessions (not tied to any specific mycommunity)
                    List<Event> generalEvents = eventRepository.findAll().stream()
                            .filter(event -> event.getModule() != null && event.getCommunity() == null)
                            .toList();
                    events.addAll(generalEvents);
                    System.out.println("Added " + generalEvents.size() + " general sessions");
                }
            }

            // Sort events by date created (most recent first)
            events.sort((e1, e2) -> e2.getDateCreated().compareTo(e1.getDateCreated()));

            System.out.println("Returning " + events.size() + " sessions for user " + userId);
            return mapEventsToDTO(events);
        } catch (Exception e) {
            System.out.println("Error getting sessions from user communities: " + e.getMessage());
            e.printStackTrace();
            // Fallback to empty list if there's an error
            return new ArrayList<>();
        }
    }

    @Transactional
    public List<SessionDTO> getMeetingsFromUserCommunities(String userId) {
        return getMeetingsFromUserCommunities(userId, null);
    }

    @Transactional
    public List<SessionDTO> getMeetingsFromUserCommunities(String userId, Long selectedCommunityId) {
        try {
            System.out.println("Getting meetings for user: " + userId + ", mycommunity: " + selectedCommunityId);

            // Check if user is admin using robust method
            boolean isUserAdmin = isUserAdmin(userId);
            System.out.println("User " + userId + " admin status in getMeetingsFromUserCommunities: " + isUserAdmin);

            // Get user to find their communities
            User user = userRepository.findByUserId(userId);
            if (user == null) {
                throw new EntityNotFoundException("User does not exist");
            }

            // Force initialization of communities collection
            user.getCommunities().size();

            List<Event> events = new ArrayList<>();

            if (selectedCommunityId != null) {
                // Get meetings from the specific selected mycommunity only
                System.out.println("Getting meetings for specific mycommunity: " + selectedCommunityId);
                List<Event> communityEvents = eventRepository.findAllByCommunity_Id(selectedCommunityId).stream()
                        .filter(event -> event.getModule() == null) // Only meetings
                        .toList();

                // Check if user is member of this mycommunity or is admin
                boolean isMemberOfCommunity = isUserAdmin || user.getCommunities().stream()
                        .anyMatch(community -> community.getId().equals(selectedCommunityId));

                System.out.println("User is member of mycommunity " + selectedCommunityId + ": " + isMemberOfCommunity);
                System.out.println("Found " + communityEvents.size() + " meetings in mycommunity " + selectedCommunityId);

                // Only show meetings if user is a member of the mycommunity or is admin
                if (isMemberOfCommunity) {
                    events.addAll(communityEvents);
                    System.out.println("Added " + communityEvents.size() + " meetings from mycommunity " + selectedCommunityId);
                } else {
                    System.out.println("User is not a member of mycommunity " + selectedCommunityId + ", no meetings added");
                }
            } else {
                // Get meetings from all communities user belongs to ONLY (or all communities if admin)
                System.out.println("Getting meetings for all user communities");

                if (isUserAdmin) {
                    // Admin sees all meetings
                    List<Event> allMeetings = eventRepository.findAll().stream()
                            .filter(event -> event.getModule() == null) // Only meetings
                            .toList();
                    events.addAll(allMeetings);
                    System.out.println("Admin: Added " + allMeetings.size() + " meetings from all communities");
                } else {
                    // Regular user sees only meetings from their communities
                    for (var community : user.getCommunities()) {
                        List<Event> communityEvents = eventRepository.findAllByCommunity_Id(community.getId()).stream()
                                .filter(event -> event.getModule() == null) // Only meetings
                                .toList();
                        events.addAll(communityEvents);
                        System.out.println("Added " + communityEvents.size() + " meetings from mycommunity " + community.getName());
                    }

                    // Also include general meetings (not tied to any specific mycommunity)
                    List<Event> generalEvents = eventRepository.findAll().stream()
                            .filter(event -> event.getModule() == null && event.getCommunity() == null)
                            .toList();
                    events.addAll(generalEvents);
                    System.out.println("Added " + generalEvents.size() + " general meetings");
                }
            }

            // Sort events by date created (most recent first)
            events.sort((e1, e2) -> e2.getDateCreated().compareTo(e1.getDateCreated()));

            System.out.println("Returning " + events.size() + " meetings for user " + userId);
            return mapEventsToDTO(events);
        } catch (Exception e) {
            System.out.println("Error getting meetings from user communities: " + e.getMessage());
            e.printStackTrace();
            // Fallback to empty list if there's an error
            return new ArrayList<>();
        }
    }

    @Transactional
    public List<SessionDTO> getCompletedSessions() {
        List<Event> events = eventRepository.findAll();
        List<SessionDTO> sessionDTOs = mapEventsToDTO(events);

        LocalDate currentDate = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        List<SessionDTO> futureSessions = sessionDTOs.stream()
                .filter(session -> {
                    try {
                        LocalDate sessionDate = LocalDate.parse(session.getStartDate(), formatter);
                        return sessionDate.isBefore(currentDate);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return false;
                    }
                })
                .collect(Collectors.toList());

        return futureSessions;
    }

    @Transactional
    public List<SessionDTO> getUpcomingSessions() {
        List<Event> events = eventRepository.findAll();
        List<SessionDTO> sessionDTOs = mapEventsToDTO(events);

        LocalDate currentDate = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        List<SessionDTO> pastSessions = sessionDTOs.stream()
                .filter(session -> {
                    try {
                        LocalDate sessionDate = LocalDate.parse(session.getStartDate(), formatter);
                        return !sessionDate.isBefore(currentDate);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return false;
                    }
                })
                .collect(Collectors.toList());
        return pastSessions;
    }

    @Transactional
    public List<SessionDTO> getUnApprovedSessions() {
        List<Event> events = eventRepository.findAllByApproved(false);
        return mapEventsToDTO(events);
    }

    @Transactional
    public List<SessionDTO> getApprovedSessions() {
        List<Event> events = eventRepository.findAllByApproved(true);
        return mapEventsToDTO(events);
    }

    public void approveSession(Long eventId){
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event does not exist"));
        event.setApproved(true);
        eventRepository.save(event);
    }

    public void bookSession(Long eventId){
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event does not exist"));
        event.setApproved(false);
        eventRepository.save(event);
    }

    @Transactional
    public SessionDTO getSessionEvent(Long eventId) {
        Event event =  eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event does not exist"));
        return mapEventsToDTO(Collections.singletonList(event)).getFirst();
    }

    public void createMeetingForOrganization(@Valid CreatSessionVO vo, Organization organization) {
        createMeetingForOrganization(vo, organization, null);
    }

    public void createMeetingForOrganization(@Valid CreatSessionVO vo, Organization organization, Long communityId) {
        Set<User> users = userRepository.findAllByUserIdIn(vo.getUsers());

        Event event = new Event();
        event.setTitle(vo.getDescription());
        event.setDescription(vo.getDescription());
        event.setSessionType(vo.getSessionType());
        event.setVideoLink(vo.getVideoLink());
        event.setStartDate(LocalDate.parse(vo.getStartDate()));
        event.setStartTime(LocalTime.parse(vo.getStartTime()));
        event.setEndDate(LocalDate.parse(vo.getStartDate()).plusDays(1));
        event.setDuration(vo.getDuration());
        event.setOrganization(organization);
        event.setHasCertificate(vo.getHasCertificate());
        event.setCertificateHtml(vo.getCertificateHtml());

        // Set mycommunity if provided
        if (communityId != null) {
            Community community = communityRepository.findById(communityId)
                    .orElseThrow(() -> new EntityNotFoundException("Community does not exist"));
            event.setCommunity(community);
            System.out.println("Meeting created for mycommunity: " + community.getName() + " (ID: " + communityId + ")");
        } else {
            System.out.println("Meeting created without specific mycommunity (general meeting)");
        }

        event = eventRepository.save(event);

        Set<Participant> participants = new HashSet<>();
        for (User user : users) {
            Participant participant = new Participant();
            participant.setUser(user);
            participant.setEvent(event);
            participants.add(participant);
        }

        List<Participant> participants1 = participantRepository.saveAll(participants);
        event.setParticipants(participants1);

        // Create post for meeting - handle mycommunity context properly
        PostMessage postMessage = new PostMessage("Meeting scheduled: " + vo.getDescription(), (String) authenticationManager.get("sub"), null);

        if (communityId != null) {
            // Community-specific meeting
            postMessage.setPostLevel("COMMUNITY");
            postMessage.setPostLevelId(communityId);
        } else {
            // General meeting - use COMMUNITY level but with null postLevelId to indicate general post
            postMessage.setPostLevel("COMMUNITY");
            postMessage.setPostLevelId(null);
        }

        Post post = postService.createPost(postMessage);
        event.setPost(post);
        eventRepository.save(event);
    }

    @Transactional
    public List<SessionDTO> getMeetings() {
        List<Event> events = eventRepository.findAll().stream()
                .filter(event -> event.getModule() == null).toList();
        return mapEventsToDTO(events);
    }

    @Transactional
    public SessionDTO getMeetingById(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Meeting does not exist"));
        return mapEventsToDTO(Collections.singletonList(event)).getFirst();
    }

    @Transactional
    public List<SessionDTO> getUserMeetings(String userId) {
        User user = Optional.ofNullable(userRepository.findByUserId(userId))
                .orElseThrow(() -> new EntityNotFoundException("User does not exist"));
        List<Event> events = eventRepository.findUserEvents(user.getUserId())
                .stream().filter(event -> event.getModule() == null).toList();
        return mapEventsToDTO(events);
    }

    @Transactional
    public List<SessionDTO> getUserCoachingSessions(String userId) {
        User user = Optional.ofNullable(userRepository.findByUserId(userId))
                .orElseThrow(() -> new EntityNotFoundException("User does not exist"));
        List<Event> events = eventRepository.findUserEvents(user.getUserId())
                .stream().filter(event -> event.getModule() != null).toList();
        return mapEventsToDTO(events);
    }

    public List<Event> getMyEvents(String userId) {
        User user = Optional.ofNullable(userRepository.findByUserId(userId))
                .orElseThrow(() -> new EntityNotFoundException("User does not exist"));
        List<Participant> participants = participantRepository.findByUser_UserId(userId);
        List<Event> myEvents = new ArrayList<>();
        for (Participant participant : participants) {
            myEvents.add(participant.getEvent());
        }
        return myEvents;
    }

    @Transactional(readOnly = true)
    public SessionDTO getSessionById(Long sessionId) {
        Event event = eventRepository.findById(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Session does not exist"));
        return mapEventsToDTO(Collections.singletonList(event)).getFirst();
    }

    // New method to get events by session type
    @Transactional
    public List<SessionDTO> getEventsBySessionType(String sessionType) {
        List<Event> events = eventRepository.findAll().stream()
                .filter(event -> sessionType.equals(event.getSessionType()))
                .toList();
        return mapEventsToDTO(events);
    }

    @Transactional
    public List<SessionDTO> getCommunityEvents(Long communityId){
        List<Event> events = eventRepository.findAllByCommunity_Id(communityId);
        return mapEventsToDTO(events);
    }

    @Transactional
    public List<SessionDTO> getGroupEvents(Long chatGroupId){
        List<Event> events = eventRepository.findAllByChatGroup_Id(chatGroupId);
        return mapEventsToDTO(events);
    }
}
