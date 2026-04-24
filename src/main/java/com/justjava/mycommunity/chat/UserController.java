package com.justjava.mycommunity.chat;

import com.justjava.mycommunity.chat.dto.CreatSessionVO;
import com.justjava.mycommunity.chat.dto.CreateModuleDTO;
import com.justjava.mycommunity.chat.model.PostMessage;
import com.justjava.mycommunity.community.CommunityGroupService;
import com.justjava.mycommunity.community.CommunityService;
import com.justjava.mycommunity.event.EventService;
import com.justjava.mycommunity.module.ModuleService;
import com.justjava.mycommunity.network.NetworkService;
import com.justjava.mycommunity.organization.OrganizationService;
import com.justjava.mycommunity.posts.PostService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.justjava.mycommunity.account.AuthenticationManager;
import com.justjava.mycommunity.chat.dto.CreateChatDTO;
import com.justjava.mycommunity.chat.dto.CreateCommunityVO;
import com.justjava.mycommunity.chat.dto.CreateOrgDTO;
import com.justjava.mycommunity.chat.dto.EventDTO;
import com.justjava.mycommunity.chat.service.ChatService;
import com.justjava.mycommunity.keycloak.KeycloakService;
import com.justjava.mycommunity.userManagement.UserDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api-v")
@RequiredArgsConstructor
public class UserController {
    private final KeycloakService keycloakService;
    private final ChatService chatService;
    private final OnlineEventListener onlineEventListener;
    private final AuthenticationManager authenticationManager;
    private final OrganizationService organizationService;
    private final CommunityService communityService;
    private final NetworkService networkService;
    private final PostService postService;
    private final EventService eventService;
    private final CommunityGroupService communityGroupService;
    private final ModuleService moduleService;

    @GetMapping("/users")
    public ResponseEntity<List<UserDTO>> getUsers(){
        List<UserDTO> users = chatService.getUsers();
        return ResponseEntity.ok().body(users);
    }

    @GetMapping("/publicUsers")
    public ResponseEntity<List<UserDTO>> getPublicUsers(){
        List<UserDTO> users = chatService.getPublicUsers();
        return ResponseEntity.ok().body(users);
    }
    @GetMapping("/publicNetworkUsers")
    public ResponseEntity<List<UserDTO>> getPublicNetworkUsers(
            @RequestParam(value = "communityId", required = false) Long communityId){
        String currentUserId = (String) authenticationManager.get("sub");

        // Get users scoped to community if communityId is provided
        List<UserDTO> users;
        if (communityId != null) {
            users = communityService.getCommunityMembersExcludingUser(communityId, currentUserId);
        } else {
            users = chatService.getPublicUsers();
        }

        // Get current user's network (community-scoped if communityId provided)
        List<UserDTO> networkUsers;
        if (communityId != null) {
            networkUsers = networkService.getChatGroupUsers(currentUserId, communityId);
        } else {
            networkUsers = networkService.getChatGroupUsers(currentUserId);
        }

        // Create a Set of network user IDs for faster lookup
        Set<String> networkUserIds = networkUsers.stream()
                .map(UserDTO::getUserId)
                .collect(Collectors.toSet());

        // Filter out users that are already in the network
        List<UserDTO> filteredUsers = users.stream()
                .filter(user -> !networkUserIds.contains(user.getUserId()))
                .collect(Collectors.toList());

        return ResponseEntity.ok().body(filteredUsers);
    }

    @PostMapping("/conversations")
    public ResponseEntity<?> createConversation(@RequestParam List<String> conversationIds){
        String conversationId = chatService.createConversation(conversationIds).getId().toString();
        return ResponseEntity.ok(conversationId);
    }

    @GetMapping("/conversations")
    public ResponseEntity<?> getConversations(/*@PathVariable String userId*/){
        String userId = authenticationManager.get("sub").toString();
        return ResponseEntity.ok(chatService.getConversations(userId));
    }

    @PostMapping("/create-organization")
    public ResponseEntity<?> createOrganization(@RequestBody CreateOrgDTO dto){
        try {
            var o = organizationService.createOrganization(dto);
            return ResponseEntity.ok(o);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/organization/add-user")
    public ResponseEntity<?> addUser(@RequestParam String email, @RequestParam Long orgId){
        try {
            organizationService.addUserToOrganization(email, orgId);
            return ResponseEntity.ok("User successfully added");
        } catch (EntityNotFoundException e) {
            e.printStackTrace();
            return ResponseEntity.ok(e.getMessage());
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/online-users")
    public ResponseEntity<?> getOnlineUsers(){
        return ResponseEntity.ok(onlineEventListener.getOnlineUsers());
    }


    @PostMapping("/auth")
    public ResponseEntity<?> authenticate(@RequestParam String username, @RequestParam String password){
        Map o = (Map) keycloakService.authenticate(username, password);
        return ResponseEntity.ok(o);
    }

    @GetMapping("/organization")
    public ResponseEntity<?> getOrganizations(){
        return ResponseEntity.ok(organizationService.getOrganizations());
    }

    @GetMapping("/organization/members/{orgId}")
    public ResponseEntity<?> getOrganizationMembers(@PathVariable Long orgId){
        return ResponseEntity.ok(organizationService.getOrgMembers(orgId));
    }

    @PostMapping("/community/create")
    public ResponseEntity<?> createCommunity(@RequestBody CreateCommunityVO dto){
        try {
            var o = communityService.createCommunity(dto);
            return ResponseEntity.ok(o);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/community/update-default")
    public ResponseEntity<?> updateDefaultCommunity(@RequestBody CreateCommunityVO dto){
        try {
            communityService.updateCommunity(dto);
            return ResponseEntity.ok("Updated successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/group/update/{id}")
    public ResponseEntity<?> updateChatGroup(@RequestBody CreateChatDTO dto,
                                             @PathVariable(name = "id") Long groupId){
        try {
            networkService.updateChatGroup(dto, groupId);
            return ResponseEntity.ok("Updated successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/community/delete/{id}")
    public ResponseEntity<?> deleteChatGroup(@PathVariable(name = "id") Long groupId){
        try {
            communityGroupService.deleteCommunityGroup(groupId);
            return ResponseEntity.ok("Deleted successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/community/groups/{id}")
    public ResponseEntity<?> getCommunityGroups(@PathVariable(name = "id") Long communityId){
        try {
            var o = communityGroupService.getCommunityGroupsByCommunityId(communityId);
            return ResponseEntity.ok(o);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/community/default")
    public ResponseEntity<?> getDefaultCommunity(){
        try {
            var o = communityService.getCommunity();
            return ResponseEntity.ok(o);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/community/user")
    public ResponseEntity<?> addUserToCommunity(@RequestParam String userId,
                                                @RequestParam(required = false, defaultValue = "122") Long communityId){
        try {
            communityService.addUserToCommunity(userId, communityId);
            return ResponseEntity.ok("User successfully added");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/community/users")
    public ResponseEntity<?> getAllCommunityUsers(@RequestParam Long communityId){

        try {
            var o = communityService.getAllCommunityUsers(communityId);
            return ResponseEntity.ok(o);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/group/create")
    public ResponseEntity<?> createChatGroup(@RequestBody CreateChatDTO dto){
        try {
            var o = communityGroupService.createCommunityGroup(dto);
            return ResponseEntity.ok(o);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/group/user")
    public ResponseEntity<?> addUserToGroup(@RequestParam String userId, @RequestParam Long groupId){
        try {
            networkService.addUserToGroup(userId, groupId);
            return ResponseEntity.ok("User successfully added");
        } catch (EntityNotFoundException e) {
            e.printStackTrace();
            return ResponseEntity.ok(e.getMessage());
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/event/create")
    public ResponseEntity<?> createEvent(@RequestBody @Valid EventDTO dto){
        try {
            if (!authenticationManager.isAdmin() && !authenticationManager.isCommunityAdmin()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("Only community administrators can create events");
            }
            var o = eventService.createEvent(dto);
            return ResponseEntity.ok(o);
        } catch (EntityNotFoundException e) {
            e.printStackTrace();
            return ResponseEntity.ok(e.getMessage());
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    @PostMapping("/event/user")
    public ResponseEntity<?> addUserToEvent(@RequestParam String userId,  @RequestParam Long eventId){
        try {
            eventService.addUserToEvent(userId, eventId);
            return ResponseEntity.ok("User successfully added");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/event")
    public ResponseEntity<?> getUserEvents(@RequestParam String userId){
        try {
            var o = eventService.getUserCoachingSessions(userId);
            return ResponseEntity.ok(o);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(value = "/module/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createModule(
            @RequestParam("name") String name,
            @RequestParam(value = "description") String description,
            @RequestParam(value = "price" ) Double price,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            CreateModuleDTO dto = new CreateModuleDTO(name,description,price);
            if(file != null)
                dto.setFile(file.getBytes());
            moduleService.createModule(dto);
            return ResponseEntity.ok("Module successfully created");
        } catch (EntityNotFoundException e) {
            e.printStackTrace();
            return ResponseEntity.ok(e.getMessage());
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/module")
    public ResponseEntity<?> getModules(){

        try{
            var o = moduleService.getModules();
            return ResponseEntity.ok(o);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/session/create")
    public ResponseEntity<?> createSession(@RequestBody @Valid CreatSessionVO vo){
        try {
            eventService.createSession(vo);
            return ResponseEntity.ok("Session successfully created");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

    }

    @GetMapping("/session")
    public ResponseEntity<?> getSessions(){
        try {
            var o = eventService.getSessions();
            return ResponseEntity.ok(o);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/session/approve/{id}")
    public ResponseEntity<?> approveSession(@PathVariable(name = "id") Long eventId){
        try {
            eventService.approveSession(eventId);
            return ResponseEntity.ok("Event successfully approved");
        }catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/post/create")
    public ResponseEntity<?> createPost(@RequestParam String content,
                                        @RequestParam String userId,
                                        @RequestParam MultipartFile file){

        try {
            postService.createPost(new PostMessage(content, userId, file));
            return ResponseEntity.ok("Post successfully created");
        }catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/posts")
    public ResponseEntity<?> getPosts(){
        try{
            var o = postService.getPosts();
            return ResponseEntity.ok(o);
        }catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
